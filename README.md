# Sol

Sol is a compiled programming language designed for clarity, strong typing,
memory safety and systems programming.

## Current status

Sol is in early bootstrap development.

The Sol 0.1 milestone focuses on establishing the initial procedural language
and its native compilation pipeline:

* static typing;
* functions;
* variables and constants;
* conditions and loops;
* a Java bootstrap compiler;
* a typed, compiler-independent Sol IR;
* LLVM IR generation;
* native object-file emission;
* host-native executable linking.
* a bundled native console standard-library module.

The compiler is implemented in Java, but generated Sol executables are native
programs and do not depend on the JVM.

The current native compiler pipeline is:

```text
Sol source
→ lexer
→ parser
→ semantic analysis
→ typed Sol IR
→ LLVM IR
→ native object file
→ host linker
→ native executable
```

The complete host-native compilation pipeline is exposed through the compiler
command-line interface.

## Command-line compilation

Compile an executable Sol program with:

```text
solc program.sol
```

The compiler performs:

```text
source discovery
→ lexical analysis
→ parsing
→ semantic analysis
→ typed Sol IR lowering
→ LLVM IR generation
→ native object emission
→ host linking
→ native executable
```

By default, an input such as:

```text
program.sol
```

produces:

```text
program
```

on Unix-like hosts and:

```text
program.exe
```

on Windows.

Specify an explicit output path with:

```text
solc program.sol -o build/program
```

or:

```text
solc program.sol --output build/program
```

The equivalent inline form is also supported:

```text
solc program.sol --output=build/program
```

Retain the native intermediate object for debugging with:

```text
solc --keep-intermediates program.sol
```

The intermediate object is deleted by default.

During bootstrap development, the Gradle application can be invoked directly:

```bash
cd compiler
./gradlew run --args="/path/to/program.sol"
```

Compilation does not execute the generated program automatically.

### Source discovery

The directory containing the explicitly supplied source file is currently used
as the filesystem module root.

For example:

```text
project/
├── main.sol
├── helper.sol
└── utilities/
    └── math.sol
```

From `main.sol`:

```sol
inject helper
inject utilities.math
```

resolves to:

```text
helper          → project/helper.sol
utilities.math  → project/utilities/math.sol
```

Compiler-provided standard-library modules under `std` are resolved before
filesystem modules and do not require project-local source files.

For example:

```sol
inject namespace std.console as csl

@init
fn launch() -> int
    csl::print("Hello ")
    csl::print_line("Sol")
    return 0
end
```

`std.console` currently provides UTF-8 standard-output operations through
`print(string)` and `print_line(string)`. The `csl` name above is the
conventional Sol 0.1 namespace alias for console access.


Injected modules are discovered recursively.

Cyclic function-level module dependencies are supported. A module already
discovered is not loaded again.

If an injected module cannot be found, source discovery leaves it unresolved so
semantic analysis can emit the normal `SOL-S019` diagnostic at the injection
site.

This filesystem convention is part of the current bootstrap compiler. A future
project or package model may define module roots differently.

### Compiler exit codes

The compiler uses distinct process exit codes for compilation stages:

|  Code | Meaning                               |
|------:|---------------------------------------|
|   `0` | successful compilation                |
|   `2` | invalid command-line arguments        |
|   `3` | source or filesystem input failure    |
|   `4` | lexical, parsing, or semantic failure |
|   `5` | Sol IR lowering failure               |
|   `6` | LLVM backend failure                  |
|   `7` | native toolchain or linker failure    |

Frontend diagnostics include the source path, one-based line and column,
severity, diagnostic code and message.

For example:

```text
program.sol:3:8: error [SOL-S002]: Unresolved name 'value'.
```

## Native executables

Executable Sol programs designate one function with `@init`.

The language and typed IR permit entry-point parameters. The current native
startup bridge can execute parameterless entry functions returning Sol `int`.
Binding process command-line arguments to entry-point parameters will be added
separately.

The compiler currently targets the host platform. It discovers a linker driver
through `SOL_LINKER` or the host `PATH`, preferring `clang` and then `cc`.

See [Native executable toolchain](spec/native-executables.md) for the current
entry-point convention, linking process and limitations.

## Repository layout

* `compiler/`: Java bootstrap compiler, typed Sol IR, LLVM backend and native
  toolchain.
* `spec/`: language specifications and compiler design decisions.
* `examples/`: example Sol programs.

## Development

The compiler test suite can be run with:

```bash
cd compiler
./gradlew clean test
```

Repository changes should also pass:

```bash
git diff --check
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for additional contribution
information.
