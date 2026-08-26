#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import shutil
import stat
import subprocess
import sys
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
COMPILER = REPOSITORY / "compiler"
CONFORMANCE = COMPILER / "conformance"
FIXTURES = CONFORMANCE / "fixtures"
BUILD = COMPILER / "build" / "conformance suite"
CATALOG = CONFORMANCE / "catalog.json"
IS_WINDOWS = os.name == "nt"


class ConformanceFailure(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ConformanceFailure(message)


def load_catalog() -> list[dict[str, object]]:
    document = json.loads(CATALOG.read_text(encoding="utf-8"))
    if document.get("version") != 1 or not isinstance(document.get("cases"), list):
        fail("catalog must use version 1 and contain a cases array")
    cases = document["cases"]
    identifiers: set[str] = set()
    for case in cases:
        if not isinstance(case, dict):
            fail("every catalog case must be an object")
        identifier = case.get("id")
        kind = case.get("kind")
        source = case.get("source")
        if not isinstance(identifier, str) or not identifier or identifier in identifiers:
            fail(f"catalog case has a missing or duplicate id: {identifier!r}")
        if kind not in {"run", "reject", "runtime-failure"}:
            fail(f"catalog case {identifier!r} has unsupported kind {kind!r}")
        if not isinstance(source, str) or not source or not (FIXTURES / source).is_file():
            fail(f"catalog case {identifier!r} has no regular source file")
        if kind == "reject":
            if not isinstance(case.get("compile_status"), int) or not isinstance(case.get("diagnostic"), str):
                fail(f"catalog rejection {identifier!r} lacks compile_status or diagnostic")
        else:
            if not isinstance(case.get("run_status"), int):
                fail(f"catalog runtime case {identifier!r} lacks run_status")
            if not isinstance(case.get("stdout"), str) or not isinstance(case.get("stderr"), str):
                fail(f"catalog runtime case {identifier!r} lacks stdout or stderr")
            if kind == "run" and (not isinstance(case.get("stdin"), str) or not isinstance(case.get("files"), dict)):
                fail(f"catalog run case {identifier!r} lacks stdin or files")
        identifiers.add(identifier)
    return cases


def executable_command(executable: Path, arguments: list[str]) -> list[str]:
    if IS_WINDOWS and executable.suffix.lower() in {".bat", ".cmd"}:
        command_processor = os.environ.get("COMSPEC", "cmd.exe")
        return [command_processor, "/d", "/c", "call", str(executable), *arguments]
    return [str(executable), *arguments]


def invoke(
    executable: Path,
    arguments: list[str],
    *,
    cwd: Path,
    environment: dict[str, str],
    input_text: str = "",
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        executable_command(executable, arguments),
        cwd=cwd,
        env=environment,
        input=input_text,
        text=True,
        encoding="utf-8",
        errors="strict",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def output_executable(path: Path) -> Path:
    if IS_WINDOWS and path.suffix.lower() != ".exe":
        return Path(str(path) + ".exe")
    return path


def make_no_java_environment(base: dict[str, str]) -> tuple[dict[str, str], Path]:
    shim = BUILD / "no-java" / "bin"
    shim.mkdir(parents=True, exist_ok=True)
    marker = BUILD / "java-was-invoked"
    if IS_WINDOWS:
        script = shim / "java.bat"
        script.write_text(f'@echo off\r\n>"{marker}" echo invoked\r\nexit /b 99\r\n', encoding="utf-8")
    else:
        script = shim / "java"
        script.write_text(f'#!/bin/sh\nprintf invoked >"{marker}"\nexit 99\n', encoding="utf-8")
        script.chmod(script.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    environment = dict(base)
    environment["JAVA_HOME"] = str(BUILD / "no-java")
    environment["PATH"] = str(shim) + os.pathsep + environment.get("PATH", "")
    return environment, marker


def copy_case_source(case: dict[str, object], case_root: Path) -> Path:
    relative = Path(str(case["source"]))
    source = FIXTURES / relative
    destination = case_root / "sources with spaces"
    shutil.copytree(source.parent, destination)
    return destination / source.name


def compile_source(
    compiler: Path,
    source: Path,
    output: Path,
    *,
    cwd: Path,
    environment: dict[str, str],
    extra: list[str] | None = None,
) -> subprocess.CompletedProcess[str]:
    arguments = list(extra or []) + [str(source), "-o", str(output)]
    return invoke(compiler, arguments, cwd=cwd, environment=environment)


def assert_result(result: subprocess.CompletedProcess[str], status: int, context: str) -> None:
    if result.returncode != status:
        fail(
            f"{context}: expected status {status}, got {result.returncode}\n"
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )


def assert_program_observation(
    executable: Path,
    case: dict[str, object],
    *,
    runtime_root: Path,
    environment: dict[str, str],
    label: str,
) -> None:
    runtime_root.mkdir(parents=True)
    result = invoke(
        executable,
        [],
        cwd=runtime_root,
        environment=environment,
        input_text=str(case.get("stdin", "")),
    )
    assert_result(result, int(case["run_status"]), f"{case['id']} ({label} run)")
    if result.stdout != case.get("stdout", ""):
        fail(f"{case['id']} ({label}): stdout mismatch: {result.stdout!r}")
    if result.stderr != case.get("stderr", ""):
        fail(f"{case['id']} ({label}): stderr mismatch: {result.stderr!r}")
    effects = case.get("files", {})
    if not isinstance(effects, dict):
        fail(f"{case['id']}: files expectation must be an object")
    for relative, expected in effects.items():
        effect = runtime_root / relative
        if not effect.is_file() or effect.read_text(encoding="utf-8") != expected:
            fail(f"{case['id']} ({label}): filesystem effect mismatch for {relative}")
    actual_effects = {
        str(path.relative_to(runtime_root)).replace(os.sep, "/")
        for path in runtime_root.rglob("*")
        if path.is_file()
    }
    if actual_effects != set(effects):
        fail(
            f"{case['id']} ({label}): unexpected filesystem effects; "
            f"expected {sorted(effects)}, got {sorted(actual_effects)}"
        )


def run_catalog_case(
    case: dict[str, object],
    seed: Path,
    selfhost_solc: Path,
    seed_environment: dict[str, str],
    selfhost_environment: dict[str, str],
) -> None:
    identifier = str(case["id"])
    case_root = BUILD / "cases" / identifier
    case_root.mkdir(parents=True)
    source = copy_case_source(case, case_root)
    seed_output = case_root / "seed output"
    selfhost_output = case_root / "selfhost output"
    seed_compile = compile_source(seed, source, seed_output, cwd=case_root, environment=seed_environment)
    selfhost_compile = compile_source(
        selfhost_solc, source, selfhost_output, cwd=case_root, environment=selfhost_environment
    )
    kind = case["kind"]
    if kind == "reject":
        status = int(case["compile_status"])
        assert_result(seed_compile, status, f"{identifier} (seed compile)")
        assert_result(selfhost_compile, status, f"{identifier} (self-host compile)")
        expected = str(case["diagnostic"])
        for label, result in (("seed", seed_compile), ("self-host", selfhost_compile)):
            first = result.stderr.splitlines()[0] if result.stderr.splitlines() else ""
            if not first.endswith(expected):
                fail(f"{identifier} ({label}): diagnostic mismatch: {first!r}")
        return
    assert_result(seed_compile, 0, f"{identifier} (seed compile)")
    assert_result(selfhost_compile, 0, f"{identifier} (self-host compile)")
    seed_executable = output_executable(seed_output)
    selfhost_executable = output_executable(selfhost_output)
    if not seed_executable.is_file() or not selfhost_executable.is_file():
        fail(f"{identifier}: a compiler did not produce its native executable")
    assert_program_observation(
        seed_executable,
        case,
        runtime_root=case_root / "seed runtime",
        environment=seed_environment,
        label="seed",
    )
    assert_program_observation(
        selfhost_executable,
        case,
        runtime_root=case_root / "selfhost runtime",
        environment=selfhost_environment,
        label="self-host",
    )


def validate_selfhost_cli(
    selfhost_solc: Path,
    selfhost_sol: Path,
    environment: dict[str, str],
) -> None:
    cli_root = BUILD / "CLI paths with spaces"
    cli_root.mkdir(parents=True)
    version = invoke(selfhost_solc, ["--version"], cwd=cli_root, environment=environment)
    assert_result(version, 0, "solc --version")
    if version.stdout != "Sol 0.1.1\n":
        fail(f"solc --version output mismatch: {version.stdout!r}")
    assert_result(invoke(selfhost_solc, [], cwd=cli_root, environment=environment), 2, "solc missing source")
    assert_result(
        invoke(selfhost_solc, ["--unknown"], cwd=cli_root, environment=environment), 2, "solc unknown option"
    )
    assert_result(
        invoke(selfhost_solc, [str(cli_root / "missing.sol")], cwd=cli_root, environment=environment),
        3,
        "solc missing input",
    )

    source = cli_root / "-program.sol"
    source.write_text("@init\nfn launch() -> int\n    return 23\nend\n", encoding="utf-8")
    retained = cli_root / "retained output"
    compiled = invoke(
        selfhost_solc,
        ["--keep-intermediates", f"--output={retained}", "--", str(source)],
        cwd=cli_root,
        environment=environment,
    )
    assert_result(compiled, 0, "solc retained output")
    retained_executable = output_executable(retained)
    for artifact in (
        retained_executable,
        Path(str(retained_executable) + ".sol-selfhost.ll"),
        Path(str(retained_executable) + ".sol-selfhost-literals.c"),
        Path(str(retained_executable) + (".sol-link.obj" if IS_WINDOWS else ".sol-link.o")),
        Path(str(retained_executable) + (".sol-runtime.obj" if IS_WINDOWS else ".sol-runtime.o")),
        Path(str(retained_executable) + (".sol-literals.obj" if IS_WINDOWS else ".sol-literals.o")),
    ):
        if not artifact.is_file() or artifact.stat().st_size == 0:
            fail(f"solc --keep-intermediates did not retain {artifact}")

    stdin_source = FIXTURES / "positive" / "stdin" / "main.sol"
    run = invoke(selfhost_sol, ["run", str(stdin_source)], cwd=cli_root, environment=environment, input_text="input\n")
    assert_result(run, 29, "sol run status")
    if run.stdout != "input\n" or run.stderr:
        fail(f"sol run stream mismatch: stdout={run.stdout!r}, stderr={run.stderr!r}")


def validate_toolchain_failure(selfhost_solc: Path, environment: dict[str, str]) -> None:
    root = BUILD / "toolchain failure"
    root.mkdir(parents=True)
    if IS_WINDOWS:
        driver = root / "driver.bat"
        driver.write_text('@echo off\r\nif "%~1"=="--version" exit /b 0\r\nexit /b 42\r\n', encoding="utf-8")
    else:
        driver = root / "driver"
        driver.write_text('#!/bin/sh\n[ "${1:-}" = "--version" ] && exit 0\nexit 42\n', encoding="utf-8")
        driver.chmod(driver.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    failed_environment = dict(environment)
    failed_environment["SOL_LINKER"] = str(driver)
    source = FIXTURES / "positive" / "stdin" / "main.sol"
    output = root / "failed output"
    result = compile_source(selfhost_solc, source, output, cwd=root, environment=failed_environment)
    assert_result(result, 7, "self-host toolchain failure")
    if "toolchain error: native compiler driver failed with exit code 42." not in result.stderr:
        fail(f"toolchain failure diagnostic mismatch: {result.stderr!r}")
    if output_executable(output).exists():
        fail("failed self-host toolchain left an executable")


def main() -> int:
    seed_value = os.environ.get("SOLC")
    if not seed_value:
        fail("SOLC must identify the released Sol 0.1.1 compiler")
    seed = Path(seed_value).resolve()
    core = Path(os.environ.get("SOL_SELFHOST_CORE", COMPILER / "build" / "stage1" / ("solc-core.exe" if IS_WINDOWS else "solc-core"))).resolve()
    selfhost_solc = COMPILER / ("solc.bat" if IS_WINDOWS else "solc.sh")
    selfhost_sol = COMPILER / ("sol.bat" if IS_WINDOWS else "sol.sh")
    if not seed.exists() or not core.is_file() or not selfhost_solc.is_file() or not selfhost_sol.is_file():
        fail("seed, self-host core, solc or sol command is missing")

    if BUILD.exists():
        shutil.rmtree(BUILD)
    BUILD.mkdir(parents=True)
    seed_environment = dict(os.environ)
    selfhost_environment, java_marker = make_no_java_environment(seed_environment)
    selfhost_environment["SOL_SELFHOST_CORE"] = str(core)

    cases = load_catalog()
    for case in cases:
        print(f"conformance: {case['id']}")
        run_catalog_case(case, seed, selfhost_solc, seed_environment, selfhost_environment)
    print("conformance: public CLI")
    validate_selfhost_cli(selfhost_solc, selfhost_sol, selfhost_environment)
    print("conformance: toolchain failure")
    validate_toolchain_failure(selfhost_solc, selfhost_environment)
    if java_marker.exists():
        fail("the self-host compiler or one of its native outputs invoked Java")
    print(f"conformance: {len(cases)} catalog cases and CLI contracts passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ConformanceFailure as error:
        print(f"conformance error: {error}", file=sys.stderr)
        raise SystemExit(1)
