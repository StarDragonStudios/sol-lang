import unittest

from run import make_seed_environment


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


if __name__ == "__main__":
    unittest.main()
