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


def assert_source_diagnostic(test, actual, source, code, message):
    # Compare physical file identity, not spelling: Windows expands 8.3 temp
    # paths and discovery can join native roots with POSIX separators. Keep the
    # complete location, severity, code and message comparison exact.
    path, separator, diagnostic = actual.strip().partition(":1:1: ")
    test.assertEqual(separator, ":1:1: ", actual)
    test.assertEqual(diagnostic, f"error [{code}]: {message}")
    test.assertTrue(Path(path).samefile(source), f"wrong diagnostic source: {path}")


class DiagnosticPathTests(unittest.TestCase):
    def test_equivalent_path_and_exact_diagnostic(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "main.sol"
            source.touch()
            (root / "nested").mkdir()
            alias = root / "nested" / ".." / "main.sol"
            assert_source_diagnostic(self, f"{alias.as_posix()}:1:1: error [SOL-M001]: {SUBSET}", source, "SOL-M001", SUBSET)

    def test_wrong_file_position_code_message_or_extra_output_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "main.sol"
            other = Path(directory) / "other.sol"
            source.touch()
            other.touch()
            valid = f"{source}:1:1: error [SOL-M001]: {SUBSET}"
            for actual in (
                valid.replace(str(source), str(other)),
                valid.replace(":1:1: ", ":2:1: "),
                valid.replace(":1:1: ", ":1:2: "),
                valid.replace("SOL-M001", "SOL-M002"),
                valid.replace("error [", "warning ["),
                valid + " changed",
                valid + "\nextra diagnostic",
            ):
                with self.subTest(actual=actual), self.assertRaises(AssertionError):
                    assert_source_diagnostic(self, actual, source, "SOL-M001", SUBSET)


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
        assert_source_diagnostic(self, result.stderr, helper, "SOL-M001", SUBSET)
        self.assertFalse(self.output.exists())
        self.assertFalse(Path(str(self.output) + ".sol-selfhost.ll").exists())
        self.compile("legacy")

    def test_cyclic_sources_checked_in_same_mode(self):
        self.source.write_text("inject helper\n" + MINIMAL, encoding="utf-8")
        (self.root / "helper.sol").write_text("inject main\nfn helper() -> void\n    return\nend\n", encoding="utf-8")
        self.compile("safe-experimental")

    def test_compact_init_and_annotation_grammar_boundary(self):
        self.source.write_text("@init fn launch() -> int\nreturn 17\nend\n", encoding="utf-8")
        self.compile("safe-experimental")
        self.assertEqual(subprocess.run([self.output]).returncode, 17)
        # A rejected compilation must leave an existing user output intact.
        original = self.output.read_bytes()
        result = self.compile("legacy", expected=4)
        self.assertIn("error [SOL-P002]:", result.stderr)
        self.assertEqual(self.output.read_bytes(), original)
        self.source.write_text("@mystery fn launch() -> int\nreturn 17\nend\n", encoding="utf-8")
        result = self.compile("safe-experimental", expected=4)
        self.assertIn(":1:2: error [SOL-P011]: Unknown experimental annotation '@mystery'.", result.stderr)

    def test_parsed_safety_annotations_do_not_enable_unchecked_semantics(self):
        programs = [
            "@copy struct Data\nvalue: int\nend\n" + MINIMAL,
            "@init fn launch() -> int\n@unsafe begin\nreturn 17\nend\nend\n",
            "@init fn launch() -> int\n@exit(launch) return 17\nend\n",
            "@init fn launch() -> int\n@mut @pinned let value: int = 17\nreturn 17\nend\n",
            "@returns(once) fn future() -> void\nend\n" + MINIMAL,
        ]
        for program in programs:
            with self.subTest(program=program):
                self.source.write_text(program, encoding="utf-8")
                result = self.compile("safe-experimental", expected=4)
                self.assertIn("error [SOL-M001]: " + SUBSET, result.stderr)
                self.assertFalse(self.output.exists())

    def test_no_legacy_stdlib_fallback_or_impersonation(self):
        self.env["SOL_SELFHOST_STDLIB"] = str(COMPILER / "stdlib")
        (self.root / "std").mkdir()
        (self.root / "std/custom.sol").write_text("fn stub() -> void\nend\n", encoding="utf-8")
        for module in ("std.memory", "std.custom"):
            with self.subTest(module=module):
                self.source.write_text(f"inject {module}\n" + MINIMAL, encoding="utf-8")
                result = self.compile("safe-experimental", expected=4)
                assert_source_diagnostic(self, result.stderr, self.source, "SOL-M002", "Standard library imports are unavailable in the safe-experimental subset; legacy fallback is forbidden.")
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
