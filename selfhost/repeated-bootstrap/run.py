#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
SELFHOST = REPOSITORY / "selfhost"
BUILD = Path(os.environ.get("SOL_REPEATED_BOOTSTRAP_BUILD", SELFHOST / "build" / "repeated bootstrap")).resolve()
SOURCE = SELFHOST / "src" / "main.sol"
STDLIB = SELFHOST / "stdlib"
IS_WINDOWS = os.name == "nt"


class BootstrapFailure(RuntimeError):
    pass


def fail(message: str) -> None:
    raise BootstrapFailure(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def canonical_json(document: object) -> bytes:
    return (json.dumps(document, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def executable_command(executable: Path, arguments: list[str]) -> list[str]:
    if IS_WINDOWS and executable.suffix.lower() in {".bat", ".cmd"}:
        return [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", "call", str(executable), *arguments]
    return [str(executable), *arguments]


def run(
    executable: Path,
    arguments: list[str],
    *,
    environment: dict[str, str],
    label: str,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        executable_command(executable, arguments),
        cwd=REPOSITORY,
        env=environment,
        text=True,
        encoding="utf-8",
        errors="strict",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        fail(
            f"{label} failed with status {result.returncode}\n"
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )
    return result


def output_executable(path: Path) -> Path:
    if IS_WINDOWS and path.suffix.lower() != ".exe":
        return Path(str(path) + ".exe")
    return path


def inventory_paths() -> list[Path]:
    paths = list((SELFHOST / "src").rglob("*.sol"))
    paths.extend(STDLIB.rglob("*.sol"))
    paths.extend(
        (
            REPOSITORY / "runtime-c" / "selfhost.c",
            REPOSITORY / "runtime-c" / "selfhost.h",
            SELFHOST / "solc.sh",
            SELFHOST / "solc.bat",
            SELFHOST / "solc.ps1",
            SELFHOST / "native-link.sh",
            SELFHOST / "native-link.bat",
        )
    )
    missing = [path for path in paths if not path.is_file()]
    if missing:
        fail(f"bootstrap inventory input is missing: {missing[0]}")
    return sorted(set(paths), key=lambda path: path.relative_to(REPOSITORY).as_posix())


def source_inventory() -> bytes:
    entries = []
    for path in inventory_paths():
        entries.append(
            {
                "path": path.relative_to(REPOSITORY).as_posix(),
                "sha256": sha256(path),
                "size": path.stat().st_size,
            }
        )
    return canonical_json({"schema": "sol.bootstrap-source-inventory.v1", "files": entries})


def write_provenance(
    stage: Path,
    *,
    stage_name: str,
    compiler_kind: str,
    compiler: Path,
    core: Path,
    inventory_digest: str,
) -> None:
    artifacts: dict[str, dict[str, object]] = {
        "core": {"name": core.name, "sha256": sha256(core), "size": core.stat().st_size}
    }
    for suffix, name in ((".sol-selfhost.ll", "llvm"), (".sol-selfhost-literals.c", "literals_c")):
        artifact = Path(str(core) + suffix)
        if artifact.is_file():
            artifacts[name] = {
                "name": artifact.name,
                "sha256": sha256(artifact),
                "size": artifact.stat().st_size,
            }
    document = {
        "schema": "sol.bootstrap-provenance.v1",
        "stage": stage_name,
        "compiler": {
            "kind": compiler_kind,
            "name": compiler.name,
            "sha256": sha256(compiler),
        },
        "source_inventory_sha256": inventory_digest,
        "artifacts": artifacts,
    }
    (stage / "provenance.json").write_bytes(canonical_json(document))


def compile_stage(
    *,
    stage_name: str,
    compiler: Path,
    compiler_kind: str,
    seed: Path,
    inventory: bytes,
) -> Path:
    stage = BUILD / stage_name
    stage.mkdir(parents=True)
    inventory_path = stage / "source-inventory.json"
    inventory_path.write_bytes(inventory)
    output = stage / "solc-core"
    environment = dict(os.environ)
    environment["SOLC"] = str(seed)
    if compiler_kind == "self-hosted":
        environment["SOL_SELFHOST_CORE"] = str(compiler)
        environment["SOL_SELFHOST_STDLIB"] = str(STDLIB)
        arguments = ["--keep-intermediates", str(SOURCE), "-o", str(output)]
    else:
        arguments = [str(SOURCE), "-o", str(output)]
    print(f"repeated bootstrap: compiling {stage_name} with {compiler_kind}", flush=True)
    run(
        SELFHOST / ("solc.bat" if IS_WINDOWS else "solc.sh") if compiler_kind == "self-hosted" else compiler,
        arguments,
        environment=environment,
        label=f"{stage_name} compilation",
    )
    core = output_executable(output)
    if not core.is_file() or core.stat().st_size == 0:
        fail(f"{stage_name} did not produce a native compiler core")
    write_provenance(
        stage,
        stage_name=stage_name,
        compiler_kind=compiler_kind,
        compiler=compiler,
        core=core,
        inventory_digest=hashlib.sha256(inventory).hexdigest(),
    )
    return core


def require_equal(left: Path, right: Path, label: str) -> None:
    if left.read_bytes() != right.read_bytes():
        fail(
            f"fixed-point mismatch for {label}: "
            f"{left} ({sha256(left)}) != {right} ({sha256(right)})"
        )


def validate_fixed_point(stage2: Path, stage3: Path) -> None:
    require_equal(
        Path(str(stage2) + ".sol-selfhost.ll"),
        Path(str(stage3) + ".sol-selfhost.ll"),
        "generated LLVM",
    )
    require_equal(
        Path(str(stage2) + ".sol-selfhost-literals.c"),
        Path(str(stage3) + ".sol-selfhost-literals.c"),
        "generated literal C",
    )
    require_equal(
        stage2.parent / "source-inventory.json",
        stage3.parent / "source-inventory.json",
        "source inventory",
    )
    print("repeated bootstrap: stage2 and stage3 reached a deterministic content fixed point", flush=True)


def validate_conformance(seed: Path, core: Path, stage_name: str) -> None:
    environment = dict(os.environ)
    environment["SOLC"] = str(seed)
    environment["SOL_SELFHOST_CORE"] = str(core)
    print(f"repeated bootstrap: validating {stage_name} conformance", flush=True)
    run(
        Path(sys.executable),
        [str(SELFHOST / "conformance" / "run.py")],
        environment=environment,
        label=f"{stage_name} conformance",
    )


def main() -> int:
    seed_value = os.environ.get("SOLC")
    if not seed_value:
        fail("SOLC must identify the released Sol 0.1.1 compiler")
    seed = Path(seed_value).resolve()
    if not seed.is_file():
        fail(f"released seed compiler is missing: {seed}")
    version = run(seed, ["--version"], environment=dict(os.environ), label="seed version").stdout.strip()
    if version != "Sol 0.1.1":
        fail(f"expected Sol 0.1.1 seed compiler, got {version!r}")
    if BUILD.exists():
        shutil.rmtree(BUILD)
    BUILD.mkdir(parents=True)
    inventory = source_inventory()
    stage1 = compile_stage(
        stage_name="stage1",
        compiler=seed,
        compiler_kind="released-seed",
        seed=seed,
        inventory=inventory,
    )
    stage2 = compile_stage(
        stage_name="stage2",
        compiler=stage1,
        compiler_kind="self-hosted",
        seed=seed,
        inventory=inventory,
    )
    stage3 = compile_stage(
        stage_name="stage3",
        compiler=stage2,
        compiler_kind="self-hosted",
        seed=seed,
        inventory=inventory,
    )
    validate_fixed_point(stage2, stage3)
    validate_conformance(seed, stage2, "stage2")
    validate_conformance(seed, stage3, "stage3")
    print(f"repeated bootstrap: complete; provenance is in {BUILD}", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BootstrapFailure as error:
        print(f"repeated bootstrap error: {error}", file=sys.stderr)
        raise SystemExit(1)
