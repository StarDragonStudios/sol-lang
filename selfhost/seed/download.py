#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import zipfile
from pathlib import Path, PurePosixPath


REPOSITORY = Path(__file__).resolve().parents[2]
SELFHOST = REPOSITORY / "selfhost"
METADATA = SELFHOST / "seed" / "metadata.json"
DESTINATION = SELFHOST / "build" / "trusted seed"


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
    asset_name = f"sol-{version}-{target}{extension}"
    release_tag = f"v{version}"
    if DESTINATION.exists():
        shutil.rmtree(DESTINATION)
    DESTINATION.mkdir(parents=True)
    result = subprocess.run(
        [
            "gh", "release", "download", release_tag,
            "--repo", repository,
            "--pattern", asset_name,
            "--pattern", "SHA256SUMS.txt",
            "--dir", str(DESTINATION),
        ],
        cwd=REPOSITORY,
        check=False,
    )
    if result.returncode != 0:
        fail(f"failed to download trusted compiler assets from {release_tag}")
    archive = DESTINATION / asset_name
    checksums = DESTINATION / "SHA256SUMS.txt"
    if not archive.is_file() or not checksums.is_file():
        fail("released compiler archive or checksum manifest is missing")
    expected = None
    for line in checksums.read_text(encoding="utf-8").splitlines():
        fields = line.split(None, 1)
        if len(fields) == 2 and fields[1].lstrip("*") == asset_name:
            expected = fields[0]
            break
    if expected is None or expected.lower() != sha256(archive):
        fail(f"released compiler checksum mismatch: {asset_name}")
    extract(archive, DESTINATION)
    root = DESTINATION / f"sol-{version}-{target}"
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
