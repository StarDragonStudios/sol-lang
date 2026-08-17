#!/usr/bin/env python3
from __future__ import annotations

import gzip
import hashlib
import json
import os
import platform
import re
import shutil
import stat
import struct
import subprocess
import sys
import tarfile
import zipfile
from pathlib import Path, PurePosixPath


REPOSITORY = Path(__file__).resolve().parents[2]
SELFHOST = REPOSITORY / "selfhost"
SEED = SELFHOST / "seed"
METADATA = SEED / "metadata.json"
DEFAULT_BUILD = SELFHOST / "build" / "seed artifacts"
IS_WINDOWS = os.name == "nt"
ARCHIVE_EPOCH = (1980, 1, 1, 0, 0, 0)


class SeedFailure(RuntimeError):
    pass


def fail(message: str) -> None:
    raise SeedFailure(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def canonical_json(document: object) -> bytes:
    return (json.dumps(document, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def load_metadata() -> tuple[str, list[str]]:
    document = json.loads(METADATA.read_text(encoding="utf-8"))
    version = document.get("version")
    targets = document.get("targets")
    if not isinstance(version, str) or not version or not isinstance(targets, list):
        fail("seed metadata must define a version and targets")
    if any(not isinstance(target, str) or not target for target in targets) or targets != sorted(set(targets)):
        fail("seed targets must be unique and sorted")
    return version, targets


def host_target() -> str:
    system = platform.system().lower()
    machine = platform.machine().lower()
    operating_system = {"darwin": "macosx", "linux": "linux", "windows": "windows"}.get(system)
    architecture = {"amd64": "x86_64", "x86_64": "x86_64", "aarch64": "arm64", "arm64": "arm64"}.get(machine)
    if operating_system is None or architecture is None:
        fail(f"unsupported seed host: {system}-{machine}")
    return f"{operating_system}-{architecture}"


def executable_command(executable: Path, arguments: list[str]) -> list[str]:
    if IS_WINDOWS and executable.suffix.lower() in {".bat", ".cmd"}:
        return [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", "call", str(executable), *arguments]
    return [str(executable), *arguments]


def invoke(
    executable: Path,
    arguments: list[str],
    *,
    environment: dict[str, str],
    cwd: Path = REPOSITORY,
    input_text: str | None = None,
    expected: int = 0,
    capture: bool = False,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        executable_command(executable, arguments),
        cwd=cwd,
        env=environment,
        input=input_text,
        text=True,
        encoding="utf-8",
        errors="strict",
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=False,
    )
    if result.returncode != expected:
        detail = ""
        if capture:
            detail = f"\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        fail(f"command failed with status {result.returncode}, expected {expected}: {executable}{detail}")
    return result


def output_executable(path: Path) -> Path:
    if IS_WINDOWS and path.suffix.lower() != ".exe":
        return Path(str(path) + ".exe")
    return path


def copy_file(source: Path, destination: Path, *, executable: bool = False) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    destination.chmod(0o755 if executable else 0o644)


def copy_tree(source: Path, destination: Path) -> None:
    for path in sorted(source.rglob("*"), key=lambda item: item.relative_to(source).as_posix()):
        relative = path.relative_to(source)
        if path.is_dir():
            (destination / relative).mkdir(parents=True, exist_ok=True)
        elif path.is_file():
            copy_file(path, destination / relative)
        else:
            fail(f"seed input is not a regular file or directory: {path}")


def source_revision() -> str:
    configured = os.environ.get("SOL_SEED_SOURCE_REVISION") or os.environ.get("GITHUB_SHA")
    if configured:
        return configured
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=REPOSITORY,
        text=True,
        encoding="utf-8",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    return result.stdout.strip() if result.returncode == 0 else "unknown"


def linker_identity(environment: dict[str, str]) -> str:
    configured = environment.get("SOL_LINKER")
    resolved = shutil.which(configured) if configured else (shutil.which("clang") or shutil.which("cc"))
    linker = Path(resolved) if resolved else Path("")
    if not resolved or not linker.is_file():
        fail("seed construction requires clang/cc or SOL_LINKER")
    result = invoke(linker, ["--version"], environment=environment, capture=True)
    lines = result.stdout.splitlines()
    return lines[0] if lines else linker.name


def file_manifest(root: Path) -> bytes:
    lines = []
    for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()):
        if path.is_file() and path.name != "SHA256SUMS":
            lines.append(f"{sha256(path)}  {path.relative_to(root).as_posix()}")
    return ("\n".join(lines) + "\n").encode("utf-8")


def package_tree(
    *,
    destination: Path,
    stage3: Path,
    target: str,
    version: str,
    archive_name: str,
    environment: dict[str, str],
) -> Path:
    root = destination / f"sol-bootstrap-{version}-{target}"
    root.mkdir(parents=True)
    if target.startswith("windows-"):
        for name in ("solc.bat", "solc.ps1", "sol.bat", "sol.ps1", "native-link.bat"):
            copy_file(SELFHOST / name, root / "bin" / name)
    else:
        copy_file(SELFHOST / "solc.sh", root / "bin" / "solc", executable=True)
        copy_file(SELFHOST / "sol.sh", root / "bin" / "sol", executable=True)
        copy_file(SELFHOST / "native-link.sh", root / "bin" / "native-link.sh", executable=True)
    copy_file(stage3, root / "libexec" / stage3.name, executable=True)
    copy_tree(REPOSITORY / "runtime-c", root / "runtime-c")
    copy_tree(SELFHOST / "stdlib", root / "stdlib")
    copy_file(SEED / "README.md", root / "README.md")
    copy_file(REPOSITORY / "LICENSE", root / "LICENSE")
    copy_file(REPOSITORY / "docs" / "bootstrap-seeds.md", root / "BOOTSTRAP.md")
    copy_file(stage3.parent / "source-inventory.json", root / "share" / "sol" / "source-inventory.json")
    copy_file(stage3.parent / "provenance.json", root / "share" / "sol" / "bootstrap-provenance.json")
    bootstrap = json.loads((stage3.parent / "provenance.json").read_text(encoding="utf-8"))
    seed_provenance = {
        "schema": "sol.bootstrap-seed-provenance.v1",
        "version": version,
        "target": target,
        "archive": archive_name,
        "source_revision": source_revision(),
        "source_inventory_sha256": bootstrap["source_inventory_sha256"],
        "stage3_core_sha256": sha256(stage3),
        "fixed_point": {
            "llvm_sha256": bootstrap["artifacts"]["llvm"]["sha256"],
            "literals_c_sha256": bootstrap["artifacts"]["literals_c"]["sha256"],
        },
        "native_link": {
            "mode": "reproducible",
            "toolchain": linker_identity(environment),
        },
    }
    (root / "share" / "sol" / "seed-provenance.json").write_bytes(canonical_json(seed_provenance))
    (root / "SHA256SUMS").write_bytes(file_manifest(root))
    return root


def archive_entries(root: Path) -> list[tuple[Path, str]]:
    entries = [(root, root.name)]
    entries.extend(
        (path, f"{root.name}/{path.relative_to(root).as_posix()}")
        for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix())
    )
    return entries


def archive_mode(path: Path, name: str) -> int:
    if path.is_dir():
        return stat.S_IFDIR | 0o755
    executable = "/bin/" in f"/{name}" or "/libexec/" in f"/{name}"
    return stat.S_IFREG | (0o755 if executable else 0o644)


def expected_archive_mode(name: str, directory: bool) -> int:
    if directory:
        return stat.S_IFDIR | 0o755
    executable = "/bin/" in f"/{name}" or "/libexec/" in f"/{name}"
    return stat.S_IFREG | (0o755 if executable else 0o644)


def build_tar(root: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, compresslevel=9, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode="w", format=tarfile.PAX_FORMAT) as archive:
                for path, name in archive_entries(root):
                    info = archive.gettarinfo(str(path), arcname=name)
                    info.uid = 0
                    info.gid = 0
                    info.uname = "root"
                    info.gname = "root"
                    info.mtime = 0
                    info.pax_headers = {}
                    if path.is_dir():
                        info.mode = archive_mode(path, name) & 0o7777
                        archive.addfile(info)
                    elif path.is_file():
                        info.mode = archive_mode(path, name) & 0o7777
                        with path.open("rb") as stream:
                            archive.addfile(info, stream)
                    else:
                        fail(f"archive input is not regular: {path}")


def build_zip(root: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path, name in archive_entries(root):
            directory = path.is_dir()
            entry_name = name + ("/" if directory else "")
            info = zipfile.ZipInfo(entry_name, ARCHIVE_EPOCH)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            mode = archive_mode(path, name)
            info.external_attr = mode << 16
            info.flag_bits = 0x800
            archive.writestr(info, b"" if directory else path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def build_archive(root: Path, destination: Path) -> None:
    if destination.suffix == ".zip":
        build_zip(root, destination)
    else:
        build_tar(root, destination)


def archive_members(path: Path) -> list[str]:
    if path.suffix == ".zip":
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            names = [info.filename for info in infos]
            for info in infos:
                if info.date_time != ARCHIVE_EPOCH:
                    fail(f"ZIP entry has an unstable timestamp: {info.filename}")
                if info.create_system != 3:
                    fail(f"ZIP entry lacks Unix permission metadata: {info.filename}")
                mode = (info.external_attr >> 16) & 0xFFFF
                if mode != expected_archive_mode(info.filename.rstrip("/"), info.is_dir()):
                    fail(f"ZIP entry has unstable permissions: {info.filename}")
            return names
    with path.open("rb") as stream:
        header = stream.read(8)
    if len(header) != 8 or struct.unpack("<I", header[4:8])[0] != 0:
        fail("gzip header contains a non-zero timestamp")
    with tarfile.open(path, "r:gz") as archive:
        members = archive.getmembers()
        for member in members:
            if member.mtime != 0 or member.uid != 0 or member.gid != 0 or member.uname != "root" or member.gname != "root":
                fail(f"tar entry has unstable ownership or timestamp metadata: {member.name}")
            expected_mode = expected_archive_mode(member.name, member.isdir()) & 0o7777
            if member.mode != expected_mode:
                fail(f"tar entry has unstable permissions: {member.name}")
        return [member.name + ("/" if member.isdir() else "") for member in members]


def validate_archive_metadata(path: Path) -> None:
    names = archive_members(path)
    if names != sorted(names):
        fail("archive entries are not in stable lexical order")
    for name in names:
        pure = PurePosixPath(name)
        if pure.is_absolute() or ".." in pure.parts or "\\" in name:
            fail(f"archive contains an unsafe or non-canonical path: {name}")


def extract_archive(archive: Path, destination: Path) -> Path:
    destination.mkdir(parents=True)
    if archive.suffix == ".zip":
        with zipfile.ZipFile(archive) as package:
            package.extractall(destination)
    else:
        with tarfile.open(archive, "r:gz") as package:
            package.extractall(destination, filter="data")
    roots = [path for path in destination.iterdir() if path.is_dir()]
    if len(roots) != 1:
        fail("seed archive must contain exactly one root directory")
    return roots[0]


def validate_file_manifest(root: Path) -> None:
    manifest = root / "SHA256SUMS"
    lines = manifest.read_text(encoding="utf-8").splitlines()
    expected = {
        path.relative_to(root).as_posix()
        for path in root.rglob("*")
        if path.is_file() and path != manifest
    }
    observed: set[str] = set()
    ordered: list[str] = []
    for line in lines:
        if len(line) < 67 or line[64:66] != "  ":
            fail(f"malformed embedded checksum line: {line!r}")
        digest, relative = line[:64], line[66:]
        path = root / PurePosixPath(relative)
        if relative in observed or relative not in expected or not path.is_file() or sha256(path) != digest:
            fail(f"embedded checksum mismatch: {relative}")
        observed.add(relative)
        ordered.append(relative)
    if ordered != sorted(ordered):
        fail("embedded SHA256SUMS is not sorted by path")
    if observed != expected:
        fail("embedded SHA256SUMS does not cover every seed file")


def validate_manifest_paths(root: Path) -> None:
    manifests = (
        root / "share" / "sol" / "source-inventory.json",
        root / "share" / "sol" / "bootstrap-provenance.json",
        root / "share" / "sol" / "seed-provenance.json",
    )

    def inspect(value: object, manifest: Path) -> None:
        if isinstance(value, dict):
            for nested in value.values():
                inspect(nested, manifest)
        elif isinstance(value, list):
            for nested in value:
                inspect(nested, manifest)
        elif isinstance(value, str) and (value.startswith(("/", "\\")) or re.match(r"^[A-Za-z]:[\\/]", value)):
            fail(f"manifest contains an absolute host path: {manifest.name}")

    for manifest in manifests:
        inspect(json.loads(manifest.read_text(encoding="utf-8")), manifest)


def no_java_environment(root: Path, base: dict[str, str]) -> tuple[dict[str, str], Path]:
    shim = root / "no-java" / "bin"
    shim.mkdir(parents=True)
    marker = root / "java-was-invoked"
    if IS_WINDOWS:
        (shim / "java.bat").write_text(f'@echo off\r\n>"{marker}" echo invoked\r\nexit /b 99\r\n', encoding="utf-8")
    else:
        script = shim / "java"
        script.write_text(f'#!/bin/sh\nprintf invoked >"{marker}"\nexit 99\n', encoding="utf-8")
        script.chmod(0o755)
    environment = dict(base)
    environment["JAVA_HOME"] = str(root / "no-java")
    environment["PATH"] = str(shim) + os.pathsep + environment.get("PATH", "")
    return environment, marker


def package_commands(root: Path) -> tuple[Path, Path, Path]:
    if IS_WINDOWS:
        return root / "bin" / "solc.bat", root / "bin" / "sol.bat", root / "libexec" / "solc-core.exe"
    return root / "bin" / "solc", root / "bin" / "sol", root / "libexec" / "solc-core"


def verify_extracted_seed(archive: Path, root: Path, seed_environment: dict[str, str], version: str) -> None:
    validate_archive_metadata(archive)
    extraction = root / "fresh extraction with spaces"
    package = extract_archive(archive, extraction)
    validate_file_manifest(package)
    validate_manifest_paths(package)
    forbidden = [path for path in package.rglob("*") if path.suffix.lower() in {".jar", ".class"}]
    if forbidden:
        fail(f"seed contains a Java compiler artifact: {forbidden[0]}")
    environment, marker = no_java_environment(root, seed_environment)
    environment["SOL_REPRODUCIBLE_LINK"] = "1"
    solc, sol, core = package_commands(package)
    for command, name in ((solc, "solc"), (sol, "sol")):
        result = invoke(command, ["--version"], environment=environment, capture=True)
        if result.stdout != f"Sol {version}\n":
            fail(f"extracted {name} version mismatch: {result.stdout!r}")
    source_root = root / "smoke sources with spaces"
    source_root.mkdir(parents=True)
    source = source_root / "main.sol"
    source.write_text("@init\nfn launch() -> int\n    return 23\nend\n", encoding="utf-8")
    output = source_root / "compiled program"
    invoke(solc, [str(source), "-o", str(output)], environment=environment)
    invoke(output_executable(output), [], environment=environment, expected=23)
    invoke(sol, ["run", str(source)], environment=environment, expected=23)
    rebuild = root / "native seed rebuild"
    rebuild_environment = dict(environment)
    rebuild_environment["SOLC"] = str(solc)
    rebuild_environment["SOL_REPEATED_BOOTSTRAP_BUILD"] = str(rebuild)
    invoke(
        Path(sys.executable),
        [str(SELFHOST / "repeated-bootstrap" / "run.py")],
        environment=rebuild_environment,
    )
    if marker.exists():
        fail("the extracted seed or its bootstrap descendants invoked Java")
    if sha256(core) != json.loads((package / "share" / "sol" / "seed-provenance.json").read_text(encoding="utf-8"))["stage3_core_sha256"]:
        fail("extracted compiler does not match seed provenance")


def build_clean_seed(
    *,
    name: str,
    build: Path,
    seed: Path,
    target: str,
    version: str,
    archive_name: str,
    environment: dict[str, str],
) -> tuple[Path, Path]:
    clean = build / name
    repeated = clean / "repeated bootstrap"
    run_environment = dict(environment)
    run_environment["SOLC"] = str(seed)
    run_environment["SOL_REPEATED_BOOTSTRAP_BUILD"] = str(repeated)
    run_environment["SOL_REPRODUCIBLE_LINK"] = "1"
    run_environment["SOURCE_DATE_EPOCH"] = "0"
    print(f"seed: starting independent {name} build", flush=True)
    invoke(Path(sys.executable), [str(SELFHOST / "repeated-bootstrap" / "run.py")], environment=run_environment)
    stage3 = output_executable(repeated / "stage3" / "solc-core")
    if not stage3.is_file():
        fail(f"{name} build did not produce stage3")
    package = package_tree(
        destination=clean / "package",
        stage3=stage3,
        target=target,
        version=version,
        archive_name=archive_name,
        environment=run_environment,
    )
    archive = clean / "dist" / archive_name
    build_archive(package, archive)
    validate_archive_metadata(archive)
    return stage3, archive


def main() -> int:
    version, targets = load_metadata()
    target = os.environ.get("SOL_SEED_TARGET", host_target())
    if target not in targets:
        fail(f"unsupported seed target: {target}")
    if target != host_target():
        fail(f"seed target {target} does not match native host {host_target()}")
    seed_value = os.environ.get("SOLC")
    if not seed_value:
        fail("SOLC must identify the trusted Sol 0.1.1 bootstrap compiler")
    seed = Path(seed_value).resolve()
    if not seed.is_file():
        fail(f"bootstrap compiler is missing: {seed}")
    environment = dict(os.environ)
    reported = invoke(seed, ["--version"], environment=environment, capture=True).stdout.strip()
    if reported != f"Sol {version}":
        fail(f"seed metadata version {version} does not match compiler output {reported!r}")
    build = Path(os.environ.get("SOL_SEED_BUILD", DEFAULT_BUILD)).resolve()
    if build.exists():
        shutil.rmtree(build)
    build.mkdir(parents=True)
    extension = ".zip" if target.startswith("windows-") else ".tar.gz"
    archive_name = f"sol-bootstrap-{version}-{target}{extension}"
    first_core, first_archive = build_clean_seed(
        name="clean build 1",
        build=build,
        seed=seed,
        target=target,
        version=version,
        archive_name=archive_name,
        environment=environment,
    )
    second_core, second_archive = build_clean_seed(
        name="clean build 2",
        build=build,
        seed=seed,
        target=target,
        version=version,
        archive_name=archive_name,
        environment=environment,
    )
    if first_core.read_bytes() != second_core.read_bytes():
        fail(f"independent stage3 compiler binaries differ: {sha256(first_core)} != {sha256(second_core)}")
    first_package = build / "clean build 1" / "package" / f"sol-bootstrap-{version}-{target}"
    second_package = build / "clean build 2" / "package" / f"sol-bootstrap-{version}-{target}"
    for relative in (
        "SHA256SUMS",
        "share/sol/source-inventory.json",
        "share/sol/bootstrap-provenance.json",
        "share/sol/seed-provenance.json",
    ):
        if (first_package / relative).read_bytes() != (second_package / relative).read_bytes():
            fail(f"independent seed manifests differ: {relative}")
    if first_archive.read_bytes() != second_archive.read_bytes():
        fail(f"independent seed archives differ: {sha256(first_archive)} != {sha256(second_archive)}")
    dist = build / "dist"
    dist.mkdir()
    published = dist / archive_name
    shutil.copyfile(first_archive, published)
    provenance_source = build / "clean build 1" / "package" / f"sol-bootstrap-{version}-{target}" / "share" / "sol" / "seed-provenance.json"
    provenance = dist / f"sol-bootstrap-{version}-{target}.provenance.json"
    shutil.copyfile(provenance_source, provenance)
    (dist / "SHA256SUMS").write_text(f"{sha256(published)}  {archive_name}\n", encoding="utf-8")
    checksum, checksum_name = (dist / "SHA256SUMS").read_text(encoding="utf-8").strip().split("  ", 1)
    if checksum_name != archive_name or checksum != sha256(published):
        fail("release-level SHA256SUMS does not verify the seed archive")
    verify_extracted_seed(published, build / "verification", environment, version)
    print(f"seed: reproducible {target} archive verified: {published} ({sha256(published)})", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SeedFailure as error:
        print(f"seed error: {error}", file=sys.stderr)
        raise SystemExit(1)
