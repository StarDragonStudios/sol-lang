# Sol self-hosted compiler

This directory contains the new Sol compiler implementation written in Sol.

The self-hosted compiler is being built from scratch against the released Sol 0.1.x language and the current compiler architecture. It does not reuse or adapt obsolete pre-0.1 self-host sources or previous Rust compiler implementations.

## Bootstrap

The self-host bootstrap is frozen to the released Sol 0.1.1 compiler as its seed until the frontend roadmap is complete:

```text
Sol 0.1.1 seed compiler
        ↓
selfhost/src/main.sol
        ↓
stage 1 native compiler executable
```

At this stage, the self-hosted compiler is only a minimal executable skeleton. Frontend, semantic analysis, typed Sol IR, LLVM generation, native object emission, linking, and the final compiler CLI will be implemented incrementally in later issues.

The full compiler architecture remains:

```text
Sol source
→ frontend
→ typed Sol IR
→ LLVM IR
→ native object
→ host linker
→ native executable
```

## Building stage 1

On macOS, run:

```bash
./selfhost/bootstrap.sh
```

The bootstrap script:

1. locates the Sol 0.1.1 seed compiler;
2. verifies its version;
3. compiles `selfhost/src/main.sol`;
4. writes the stage 1 executable to:
```text
selfhost/build/stage1/solc
```

5. executes the generated program to verify that the native bootstrap artifact is runnable.

The seed compiler can be selected explicitly with the `SOLC` environment variable:

```bash
SOLC=/path/to/sol-0.1.1/bin/solc ./selfhost/bootstrap.sh
```

Generated bootstrap artifacts under `selfhost/build/` are not committed to the repository.

### Windows

Run:

```bat
selfhost\bootstrap.bat
```

An explicit seed compiler can be selected with:

```bat
set SOLC=C:\path\to\sol-0.1.1\bin\solc.bat
selfhost\bootstrap.bat
```

The generated stage 1 executable is written to:

```text
selfhost\build\stage1\solc.exe
```

## Current scope

The current bootstrap establishes only:

* the fresh self-host source tree;
* the compiler entry point;
* the stage 0 → stage 1 build flow;
* validation that the frozen Sol 0.1.1 seed can compile the new compiler skeleton.

Lexer, parser, semantic analysis, typed Sol IR, LLVM backend implementation, native toolchain orchestration, and command-line compatibility are intentionally outside the scope of this initial bootstrap.
