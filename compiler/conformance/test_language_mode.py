"""Candidate-only executable mode gate; never pass these options to the seed."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

COMPILER = Path(__file__).resolve().parents[1]
WINDOWS = os.name == "nt"
SUFFIX = ".bat" if WINDOWS else ".sh"
CORE = Path(os.environ.get("SOL_SELFHOST_CORE", str(COMPILER / "build/stage1" / ("solc-core.exe" if WINDOWS else "solc-core"))))
MINIMAL = "@init\nfn launch() -> int\n    return 17\nend\n"
SUBSET = "Construct is not supported by the safe-experimental literal-return subset."


class LanguageModeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="sol mode fixtures ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "main.sol"
        self.source.write_text(MINIMAL, encoding="utf-8")
        self.output = self.root / ("program.exe" if WINDOWS else "program")
        self.env = dict(os.environ, SOL_SELFHOST_CORE=str(CORE))

    def invoke(self, tool, args, expected, stdin=""):
        command = [str(COMPILER / (tool + SUFFIX)), *map(str, args)]
        if WINDOWS:
            command = ["cmd", "/d", "/c", "call", *command]
        result = subprocess.run(command, cwd=self.root, env=self.env, input=stdin,
                                text=True, capture_output=True, timeout=120)
        self.assertEqual(result.returncode, expected, result.stdout + result.stderr)
        return result

    def compile(self, mode=None, expected=0, extra=()):
        args = [] if mode is None else ["--language-mode=" + mode]
        return self.invoke("solc", [*args, *extra, self.source, "-o", self.output], expected)

    def test_legacy_default_and_explicit_are_identical(self):
        self.compile(extra=["--keep-intermediates"])
        llvm = Path(str(self.output) + ".sol-selfhost.ll").read_bytes()
        self.compile("legacy", extra=["--keep-intermediates"])
        self.assertEqual(llvm, Path(str(self.output) + ".sol-selfhost.ll").read_bytes())
        self.assertEqual(subprocess.run([self.output]).returncode, 17)

    def test_safe_compile_run_metadata_and_no_mode_leak(self):
        self.compile("safe-experimental", extra=["--keep-intermediates"])
        for suffix in (".sol-selfhost.ll", ".sol-selfhost-literals.c"):
            text = Path(str(self.output) + suffix).read_text()
            self.assertIn("sol-language-mode: safe-experimental; contract: literal-return-1; reusable-module: no", text)
        self.invoke("sol", ["run", "--language-mode", "safe-experimental", "--", self.source], 17, stdin="untouched input\n")
        self.compile("legacy", extra=["--keep-intermediates"])
        self.assertNotIn("safe-experimental", Path(str(self.output) + ".sol-selfhost.ll").read_text())

    def test_options_reject_exactly_before_compilation(self):
        cases = [
            (["--language-mode"], "Option '--language-mode' requires a value."),
            (["--language-mode="], "Unknown language mode ''."),
            (["--language-mode=SAFE-EXPERIMENTAL"], "Unknown language mode 'SAFE-EXPERIMENTAL'."),
            (["--language-mode=other"], "Unknown language mode 'other'."),
            (["--language-mode=legacy", "--language-mode=legacy"], "Language mode may only be specified once."),
            (["--language-mode=legacy", "--language-mode=safe-experimental"], "Language mode may only be specified once."),
        ]
        for tool, prefix in (("solc", []), ("sol", ["run"])):
            for args, message in cases:
                with self.subTest(tool=tool, args=args):
                    result = self.invoke(tool, [*prefix, *args], 2)
                    self.assertEqual(result.stderr.strip(), "command-line error: " + message)
        self.assertFalse(self.output.exists())

    def test_split_option_and_terminator(self):
        self.invoke("solc", ["--language-mode", "safe-experimental", "-o", self.output, "--", self.source], 0)
        odd = self.root / "--language-mode=legacy.sol"
        odd.write_text(MINIMAL, encoding="utf-8")
        self.invoke("solc", ["-o", self.output, "--", odd.name], 0)
        self.invoke("sol", ["run", "--", odd.name], 17)
        result = self.invoke("sol", ["run", self.source, "--language-mode=legacy"], 2)
        self.assertEqual(result.stderr.strip(), "command-line error: Language mode must precede the run source.")

    def test_transitive_legacy_construct_rejected(self):
        self.source.write_text("inject helper\n" + MINIMAL, encoding="utf-8")
        helper = self.root / "helper.sol"
        helper.write_text("struct Data\n    value: int\nend\n", encoding="utf-8")
        result = self.compile("safe-experimental", expected=4)
        self.assertEqual(result.stderr.strip(), f"{helper}:1:1: error [SOL-M001]: {SUBSET}")
        self.assertFalse(self.output.exists())
        self.assertFalse(Path(str(self.output) + ".sol-selfhost.ll").exists())
        self.compile("legacy")

    def test_cyclic_sources_checked_in_same_mode(self):
        self.source.write_text("inject helper\n" + MINIMAL, encoding="utf-8")
        (self.root / "helper.sol").write_text("inject main\nfn helper() -> void\n    return\nend\n", encoding="utf-8")
        self.compile("safe-experimental")

    def test_no_legacy_stdlib_fallback_or_impersonation(self):
        self.env["SOL_SELFHOST_STDLIB"] = str(COMPILER / "stdlib")
        (self.root / "std").mkdir()
        (self.root / "std/custom.sol").write_text("fn stub() -> void\nend\n", encoding="utf-8")
        for module in ("std.memory", "std.custom"):
            with self.subTest(module=module):
                self.source.write_text(f"inject {module}\n" + MINIMAL, encoding="utf-8")
                result = self.compile("safe-experimental", expected=4)
                self.assertEqual(result.stderr.strip(), f"{self.source}:1:1: error [SOL-M002]: Standard library imports are unavailable in the safe-experimental subset; legacy fallback is forbidden.")
        self.assertFalse(self.output.exists())

    def test_unsupported_raw_and_unchecked_features_fail_closed(self):
        programs = [
            "fn raw() -> pointer<int>\n    return null\nend\n",
            "fn generic<T>() -> void\nend\n",
            "fn param(value: int) -> int\n    return value\nend\n",
            "fn arithmetic() -> int\n    return 1 + 2\nend\n",
            "@fn external() -> int\n",
        ]
        for program in programs:
            with self.subTest(program=program):
                self.source.write_text(program + MINIMAL, encoding="utf-8")
                result = self.compile("safe-experimental", expected=4)
                self.assertIn("error [SOL-M001]: " + SUBSET, result.stderr)
                self.assertFalse(self.output.exists())

    def test_v1_v2_core_protocol_and_malformed_requests(self):
        tail = [str(self.source), str(self.root), "main", str(COMPILER / "stdlib-safe"), str(self.root / "out.ll"), str(self.root / "out.c")]
        for header in (["SOL-SELFHOST-REQUEST-1"], ["SOL-SELFHOST-REQUEST-2", "safe-experimental"]):
            request = self.root / "request.txt"
            request.write_bytes(("\r\n".join([*header, *tail]) + "\r\n").encode())
            result = subprocess.run([CORE], input=str(request) + "\n", text=True, capture_output=True, timeout=120)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for fields in (
            ["SOL-SELFHOST-REQUEST-2", "legacy", *tail],
            ["SOL-SELFHOST-REQUEST-2", "", *tail],
            ["SOL-SELFHOST-REQUEST-2", "safe-experimental", *tail[:-1]],
            ["SOL-SELFHOST-REQUEST-1", "safe-experimental", *tail],
            ["SOL-SELFHOST-REQUEST-3", *tail],
        ):
            request.write_text("\n".join(fields) + "\n", encoding="utf-8")
            result = subprocess.run([CORE], input=str(request) + "\n", text=True, capture_output=True, timeout=120)
            self.assertEqual(result.returncode, 2, repr(fields) + result.stdout + result.stderr)
            self.assertEqual(result.stdout.strip(), "command-line error: malformed self-host request")


if __name__ == "__main__":
    if not CORE.is_file():
        raise SystemExit("Build the candidate core before running the mode gate")
    unittest.main()
