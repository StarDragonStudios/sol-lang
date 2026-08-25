#!/usr/bin/env python3
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import build as seed
import download as trusted_seed


class SeedArchiveTest(unittest.TestCase):
    def test_released_archive_paths_must_be_canonical(self) -> None:
        self.assertTrue(trusted_seed.safe_member("sol-0.1.1/bin/solc"))
        self.assertFalse(trusted_seed.safe_member("../solc"))
        self.assertFalse(trusted_seed.safe_member("/absolute/solc"))
        self.assertFalse(trusted_seed.safe_member("windows\\solc.bat"))

    def test_declared_target_matrix_is_complete_and_sorted(self) -> None:
        version, targets = seed.load_metadata()
        self.assertEqual("0.1.1", version)
        self.assertEqual(
            [
                "linux-arm64",
                "linux-x86_64",
                "macosx-arm64",
                "macosx-x86_64",
                "windows-arm64",
                "windows-x86_64",
            ],
            targets,
        )

    def test_tar_and_zip_are_byte_reproducible_and_canonical(self) -> None:
        with tempfile.TemporaryDirectory(prefix="sol seed archive ") as temporary:
            root = Path(temporary) / "sol-bootstrap-0.1.1-test"
            (root / "bin").mkdir(parents=True)
            executable = root / "bin" / "solc"
            executable.write_bytes(b"#!/bin/sh\nexit 0\n")
            executable.chmod(0o755)
            (root / "README.md").write_text("Sol 🐉\n", encoding="utf-8")
            (root / "SHA256SUMS").write_bytes(seed.file_manifest(root))

            for extension in ("tar.gz", "zip"):
                first = Path(temporary) / f"first.{extension}"
                second = Path(temporary) / f"second.{extension}"
                seed.build_archive(root, first)
                seed.build_archive(root, second)
                self.assertEqual(first.read_bytes(), second.read_bytes())
                seed.validate_archive_metadata(first)
                extracted = seed.extract_archive(first, Path(temporary) / f"extract {extension}")
                seed.validate_file_manifest(extracted)
                readme = extracted / "README.md"
                original = readme.read_bytes()
                readme.write_bytes(original + b"altered")
                with self.assertRaises(seed.SeedFailure):
                    seed.validate_file_manifest(extracted)
                readme.write_bytes(original)
                (extracted / "bin" / "solc").unlink()
                with self.assertRaises(seed.SeedFailure):
                    seed.validate_file_manifest(extracted)


if __name__ == "__main__":
    unittest.main()
