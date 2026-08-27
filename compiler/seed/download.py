#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import zipfile
from pathlib import Path, PurePosixPath


REPOSITORY = Path(__file__).resolve().parents[2]
COMPILER = REPOSITORY / "compiler"
METADATA = COMPILER / "seed" / "metadata.json"
DESTINATION = COMPILER / "build" / "trusted seed"


class DownloadFailure(RuntimeError):
    pass


def fail(message: str) -> None:
    raise DownloadFailure(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def safe_member(name: str) -> bool:
    path = PurePosixPath(name)
    return not path.is_absolute() and ".." not in path.parts and "\\" not in name


def extract(archive: Path, destination: Path) -> None:
    if archive.suffix == ".zip":
        with zipfile.ZipFile(archive) as package:
            if any(not safe_member(info.filename) for info in package.infolist()):
                fail("released compiler archive contains an unsafe path")
            package.extractall(destination)
        return
    with tarfile.open(archive, "r:gz") as package:
        if any(not safe_member(member.name) for member in package.getmembers()):
            fail("released compiler archive contains an unsafe path")
        package.extractall(destination, filter="data")


def manifest_entries(manifest: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        fields = line.split(None, 1)
        if len(fields) != 2 or re.fullmatch(r"[0-9a-fA-F]{64}", fields[0]) is None:
            fail(f"malformed checksum entry in {manifest.name}: {line!r}")
        name = fields[1].lstrip("*")
        if not safe_member(name) or name in entries:
            fail(f"unsafe or duplicate checksum path in {manifest.name}: {name!r}")
        entries[name] = fields[0].lower()
    return entries


def validate_embedded_manifest(root: Path) -> None:
    manifest = root / "SHA256SUMS"
    if not manifest.is_file():
        fail("released native seed has no embedded SHA256SUMS")
    entries = manifest_entries(manifest)
    files = {
        path.relative_to(root).as_posix(): path
        for path in root.rglob("*")
        if path.is_file() and path != manifest
    }
    if set(entries) != set(files):
        missing = sorted(set(files) - set(entries))
        unexpected = sorted(set(entries) - set(files))
        fail(f"embedded checksum inventory mismatch: missing={missing}, unexpected={unexpected}")
    for name, path in files.items():
        if sha256(path) != entries[name]:
            fail(f"embedded checksum mismatch: {name}")


def main() -> int:
    metadata = json.loads(METADATA.read_text(encoding="utf-8"))
    version = metadata.get("version")
    targets = metadata.get("targets")
    target = os.environ.get("SOL_SEED_TARGET")
    repository = os.environ.get("GITHUB_REPOSITORY", "StarDragonStudios/sol-lang")
    if not isinstance(version, str) or not isinstance(targets, list):
        fail("seed metadata is invalid")
    if target not in targets:
        fail(f"SOL_SEED_TARGET must identify a declared target, got {target!r}")
    extension = ".zip" if target.startswith("windows-") else ".tar.gz"
    asset_name = f"sol-bootstrap-{version}-{target}{extension}"
    release_tag = f"v{version}"
    if DESTINATION.exists():
        shutil.rmtree(DESTINATION)
    DESTINATION.mkdir(parents=True)
    result = subprocess.run(
        [
            "gh", "release", "download", release_tag,
            "--repo", repository,
            "--pattern", asset_name,
            "--pattern", "SHA256SUMS",
            "--dir", str(DESTINATION),
        ],
        cwd=REPOSITORY,
        check=False,
    )
    if result.returncode != 0:
        fail(f"failed to download trusted compiler assets from {release_tag}")
    archive = DESTINATION / asset_name
    checksums = DESTINATION / "SHA256SUMS"
    if not archive.is_file() or not checksums.is_file():
        fail("released compiler archive or checksum manifest is missing")
    expected = None
    expected = manifest_entries(checksums).get(asset_name)
    if expected is None or expected.lower() != sha256(archive):
        fail(f"released compiler checksum mismatch: {asset_name}")
    extract(archive, DESTINATION)
    root = DESTINATION / f"sol-bootstrap-{version}-{target}"
    if not root.is_dir():
        fail(f"released native seed root is missing after extraction: {root}")
    validate_embedded_manifest(root)
    forbidden = [path for path in root.rglob("*") if path.suffix.lower() in {".jar", ".class", ".java"}]
    if forbidden:
        fail(f"released native seed contains a Java artifact: {forbidden[0]}")
    compiler = root / "bin" / ("solc.bat" if target.startswith("windows-") else "solc")
    if not compiler.is_file():
        fail(f"released compiler launcher is missing after extraction: {compiler}")
    github_environment = os.environ.get("GITHUB_ENV")
    if github_environment:
        with Path(github_environment).open("a", encoding="utf-8", newline="\n") as stream:
            stream.write(f"SOLC={compiler.resolve()}\n")
    print(compiler.resolve())
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except DownloadFailure as error:
        print(f"trusted seed error: {error}", file=sys.stderr)
        raise SystemExit(1)
