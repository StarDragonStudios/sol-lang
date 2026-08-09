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
* bundled native console and file standard-library modules.

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

## Installation

Sol 0.1.0 is distributed as a portable, platform-specific archive. The
compiler itself runs on the JVM, while executables produced from Sol source are
native programs and do not require Java at runtime.

### Requirements

To use the Sol compiler, the host system currently requires:

* Java 25 or newer;
* a native linker driver available through `PATH`, either `clang` or `cc`.

The linker can also be selected explicitly with the `SOL_LINKER` environment
variable.

Download the Sol 0.1.0 archive matching your operating system and architecture
from the GitHub release and extract it to a directory of your choice.

A distribution has the following layout:

```text
sol-0.1.0-<platform>/
├── bin/
│   ├── sol
│   ├── solc
│   ├── sol.bat
│   └── solc.bat
├── lib/
├── LICENSE
└── README.md
```

### macOS and Linux

Add the extracted `bin` directory to `PATH`.

For example:

```bash
export PATH="/path/to/sol-0.1.0-<platform>/bin:$PATH"
```

Verify the installation with:

```bash
sol --version
solc --version
```

Both commands should report:

```text
Sol 0.1.0
```

### Windows

Add the extracted `bin` directory to the user or system `PATH`, then open a new
terminal and verify the installation with:

```text
sol --version
solc --version
```

Both commands should report:

```text
Sol 0.1.0
```

### Compile a program

Given a file named `hello.sol`:

```sol
inject namespace std.console as csl

@init
fn launch() -> int
    csl::print_line("Hello, Sol!")
    return 0
end
```

compile it with:

```text
solc hello.sol
```

This produces a native executable for the host platform:

```text
hello
```

on Unix-like systems, or:

```text
hello.exe
```

on Windows.

The generated executable can then be run directly and does not require the JVM.

Alternatively, compile and execute the source in one step with:

```text
sol run hello.sol
```

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

Compilation does not execute the generated program automatically.

## Command-line execution

Compile and immediately run a Sol program with:

```text
sol run program.sol
```

The `run` command performs the normal native compilation pipeline, writes the
generated executable and intermediate native artifacts to temporary storage,
executes the resulting program, and removes the temporary files afterwards.

The executed program inherits standard input, standard output and standard
error from the `sol` process, allowing normal interactive terminal programs.

The program also inherits the current working directory of the `sol` process.
For example, relative paths passed to `std.file` are resolved from the directory
where `sol run` was invoked rather than from the temporary directory containing
the generated executable.

A successfully launched program controls the final process exit code. For
example, if the Sol entry point returns `23`, `sol run` also exits with `23`.

The Sol 0.1 bootstrap `run` command currently accepts exactly one source file.
Program command-line arguments are not yet supported. Compiler output options
such as `-o`, `--output` and `--keep-intermediates` are intentionally not
available for `run`, because its native artifacts are temporary.

During bootstrap development, the command can be invoked through Gradle with:

```bash
cd compiler
./gradlew run --args="run /path/to/program.sol"
```

`solc` remains the direct compile-only command and continues to produce a
persistent native executable.

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

`std.file` provides the initial procedural filesystem API through
`exists(string)`, `write_text(string, string)` and
`append_text(string, string)`.

The conventional Sol 0.1 namespace alias is `file`:

```sol
inject namespace std.file as file

@init
fn launch() -> int
    if file::write_text("output.txt", "Hello") then
        file::append_text("output.txt", " Sol")
        return 0
    else
        return 1
    end
end
```

File paths are resolved by the generated native process relative to its current
working directory unless an absolute path is supplied.

Injected modules are discovered recursively.

Cyclic function-level module dependencies are supported. A module already
discovered is not loaded again.

If an injected module cannot be found, source discovery leaves it unresolved so
semantic analysis can emit the normal `SOL-S019` diagnostic at the injection
site.

This filesystem convention is part of the current bootstrap compiler. A future
project or package model may define module roots differently.

### CLI exit codes

Compilation and command infrastructure failures use distinct exit codes:

| Code | Meaning                                      |
| ---: | -------------------------------------------- |
|  `0` | successful compile-only command              |
|  `2` | invalid command-line arguments               |
|  `3` | source or filesystem input failure           |
|  `4` | lexical, parsing, or semantic failure        |
|  `5` | Sol IR lowering failure                      |
|  `6` | LLVM backend failure                         |
|  `7` | native toolchain or linker failure           |
|  `8` | failed to start or manage a compiled program |

For `sol run`, these codes describe failures that happen before the compiled
program successfully takes control. Once the program has been launched
successfully, its own process exit code is returned unchanged.

Consequently, a successful `sol run` may also return values such as `2`, `4` or
`8` when those values were deliberately returned by the Sol program itself.

When the corresponding source file is available, the compiler also renders the
affected source lines and marks the diagnostic span with carets:

```text
program.sol:3:12: error [SOL-S002]: Unresolved name 'missing'.
  |
3 |     return missing
  |            ^^^^^^^
```

Multiline spans render every affected source line with its corresponding marked
range. Empty spans render at least one caret at the diagnostic position.

Source rendering is best-effort. If the source file cannot be read or the
diagnostic span no longer fits the available source text, the compiler still
emits the diagnostic header without source context.

Diagnostic rendering does not currently use ANSI colors, keeping compiler
output deterministic for terminals, CI systems and other tools.

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

Release distribution changes should additionally pass:

```bash
cd compiler
./gradlew clean test distributionSmokeTest assembleDist
```

This builds the platform-specific distribution and verifies that the installed
`sol` and `solc` launchers can report their version, compile a native Sol
program, execute the resulting binary and run the same program through
`sol run`.

Repository changes should also pass:

```bash
git diff --check
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for additional contribution
information.
