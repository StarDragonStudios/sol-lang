import unittest

from run import CONFORMANCE, ConformanceFailure, load_catalog, make_seed_environment
from pathlib import Path
import json
import tempfile


class SeedEnvironmentTests(unittest.TestCase):
    def test_candidate_overrides_do_not_reach_native_seed(self):
        environment = {
            "SOL_SELFHOST_CORE": "/candidate/solc-core",
            "SOL_SELFHOST_STDLIB": "/candidate/stdlib",
            "SOL_SELFHOST_NATIVE_LINK": "/candidate/native-link.sh",
            "SOLC": "/seed/bin/solc",
            "SOL_LINKER": "/toolchain/clang",
            "PATH": "/toolchain/bin",
        }
        original = dict(environment)
        self.assertEqual(make_seed_environment(environment), {
            "SOLC": "/seed/bin/solc",
            "SOL_LINKER": "/toolchain/clang",
            "PATH": "/toolchain/bin",
        })
        self.assertEqual(environment, original)

    def test_windows_environment_keys_are_case_insensitive(self):
        self.assertEqual(make_seed_environment({
            "Sol_Selfhost_Core": "candidate.exe", "Path": "tools"
        }), {"Path": "tools"})

    def test_environment_without_overrides_is_preserved(self):
        self.assertEqual(make_seed_environment({"PATH": "tools"}), {"PATH": "tools"})


class ObjectCatalogTests(unittest.TestCase):
    def test_catalogs_are_valid_and_separate(self):
        baseline = {case["id"] for case in load_catalog()}
        objects = load_catalog(CONFORMANCE / "objects.json")
        self.assertFalse(baseline & {case["id"] for case in objects})
        self.assertEqual({case["kind"] for case in objects}, {"run", "reject"})

    def test_duplicate_object_case_is_rejected(self):
        case = load_catalog(CONFORMANCE / "objects.json")[0]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.json"
            path.write_text(json.dumps({"version": 1, "cases": [case, case]}))
            with self.assertRaises(ConformanceFailure):
                load_catalog(path)


if __name__ == "__main__":
    unittest.main()
