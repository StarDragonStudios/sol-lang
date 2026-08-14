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

The self-host now contains its source model, token representation, lexical
scanner, uniform syntax-tree representation and parser foundations. Complete
grammar coverage, semantic analysis, typed Sol IR, LLVM generation, native
object emission, linking, and the final compiler CLI will be implemented
incrementally in later issues.

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

5. executes the generated program to verify that the native bootstrap artifact is runnable;
6. compiles and runs the self-host lexical-analysis suite;
7. compiles and runs the self-host syntax-tree and parser-foundation suite.

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

## Source model and lexer

The frontend implementation under `selfhost/src/frontend/` provides:

* zero-based Unicode-scalar offsets with one-based lines and columns;
* half-open source spans `[start, end)`;
* value-type tokens and stable integer token kinds;
* keywords, identifiers, numeric, string and character literals;
* punctuation, arithmetic, logical, comparison and pointer-arrow operators;
* LF, CRLF and CR newline handling;
* line and block comments;
* explicit lexical results with stable `SOL-L001` through `SOL-L005`
  diagnostics.

Sol strings already guarantee valid UTF-8, so malformed input is rejected by
the text-input boundary before lexical analysis. Lexer offsets therefore count
Unicode scalar values, matching the indexing semantics of Sol strings.

`selfhost/src/lexer_test.sol` is a separate native test entry point. Both
bootstrap scripts compile and execute it with the frozen Sol 0.1.1 seed.

## Syntax tree and parser foundations

Sol 0.1.1 does not yet provide enums, sealed hierarchies or tagged unions. The
self-host syntax tree therefore uses one uniform `SyntaxNode` value containing:

* a stable integer node kind;
* a kind-specific variant for operators, literals and declaration forms;
* optional textual payload;
* a source span;
* an ordered `Vector<pointer<SyntaxNode>>` of owned children.

The catalog covers every declaration, statement and expression shape in the
current Java syntax model. Each node has one owner, and recursive destruction
releases the complete tree without casts or untyped pointers.

The parser foundation owns only its temporary cursor state and borrows the
lexer's token vector. `ParseResult` owns the resulting syntax tree. It validates
that token streams are non-empty and contain exactly one terminal EOF, skips
top-level newlines, constructs empty or newline-only compilation units, and
reports stable `SOL-P000` and `SOL-P001` diagnostics. `SOL-P002` expectation
support and cursor primitives are ready for the complete grammar implementation.

`selfhost/src/parser_test.sol` validates tree construction, navigation and
recursive destruction, compilation-unit spans, newline consumption, unexpected
top-level tokens and malformed token streams. Full Sol 0.1.x grammar parsing is
the next self-host milestone and is intentionally outside these foundations.
