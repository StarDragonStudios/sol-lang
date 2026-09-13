"""Native runtime tests; optionally link the compiler's generated failure fixture."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--llvm", type=Path)
    parser.add_argument("--literals", type=Path)
    args = parser.parse_args()
    if bool(args.llvm) != bool(args.literals):
        parser.error("--llvm and --literals must be supplied together")
    root = Path(__file__).resolve().parent
    driver = os.environ.get("SOL_LINKER", "clang")
    with tempfile.TemporaryDirectory(prefix="sol-object-lifetime-") as directory:
        executable = Path(directory) / "runtime-test.exe"
        subprocess.run([driver, "-std=c11", "-Wall", "-Wextra", "-Werror",
                        str(root / "object_lifetime_test.c"), "-o", str(executable)], check=True)
        subprocess.run([str(executable)], check=True)
        if args.llvm:
            subprocess.run([driver, "-std=c11", "-Wno-override-module",
                            "-DSOL_OBJECT_FAILURE_FIXTURE",
                            str(root / "object_lifetime_test.c"), str(args.llvm.resolve()),
                            "-I", str(root), str(args.literals.resolve()),
                            "-o", str(executable)], check=True)
            result = subprocess.run([str(executable)], input="object-allocation-failure\n",
                                    text=True, capture_output=True, check=True)
            if result.stdout or result.stderr:
                raise RuntimeError(f"Unexpected constructor/runtime output: {result.stdout!r} {result.stderr!r}")
    print("Object lifetime runtime tests passed.")


if __name__ == "__main__":
    main()
