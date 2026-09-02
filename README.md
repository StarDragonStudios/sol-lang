# Sol

Sol is a compiled programming language designed for clarity, strong typing,
memory safety and systems programming.

## Current status

Sol is in early self-hosted development.

The Sol 0.1 milestone focuses on establishing the initial procedural language
and its native compilation pipeline:

* static typing;
* functions;
* variables and constants;
* conditions and loops;
* value-type structs;
* minimal monomorphized generics;
* typed raw pointers and explicit manual allocation;
* an official compiler implemented in Sol;
* a typed, compiler-independent Sol IR;
* LLVM IR generation;
* native object-file emission;
* host-native executable linking;
* bundled native console, file and raw-memory standard-library modules.

The compiler core is implemented in Sol and distributed as a native executable.
Neither the compiler nor generated Sol programs require a JVM.

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

The [Sol 0.2 object-model design](spec/sol-0.2-object-model.md) records the
approved source model and provisional manual-memory contract. It is not yet
implemented compiler functionality.

## Installation

The official Sol 0.1.1 compiler is distributed through the portable native
`sol-bootstrap-0.1.1-<platform>` archives. These are the verified stage-3
toolchains produced by repeated self-compilation.

### Requirements

To use the Sol compiler, the host system currently requires:

* a native linker driver available through `PATH`, either `clang` or `cc`.

The linker can also be selected explicitly with the `SOL_LINKER` environment
variable.

Download the Sol 0.1.1 archive matching your operating system and architecture
from the GitHub release and extract it to a directory of your choice.

A native distribution has the following layout:

```text
sol-bootstrap-0.1.1-<platform>/
├── bin/
│   ├── sol
│   └── solc
├── libexec/
│   └── solc-core
├── runtime-c/
├── stdlib/
├── share/sol/
├── SHA256SUMS
├── LICENSE
└── README.md
```

### macOS and Linux

Add the extracted `bin` directory to `PATH`.

For example:

```bash
export PATH="/path/to/sol-bootstrap-0.1.1-<platform>/bin:$PATH"
```

Verify the installation with:

```bash
sol --version
solc --version
```

Both commands should report:

```text
Sol 0.1.1
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
Sol 0.1.1
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

The generated executable can then be run directly.

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

`std.console` provides UTF-8 output through `print(string)` and
`print_line(string)`, plus input through `read_line() -> string`. `read_line`
removes LF or CRLF line terminators and returns `""` for a valid empty line.
EOF before any line and malformed UTF-8 are deterministic runtime failures
with status `70`. The `csl` name above is the conventional namespace alias.

`std.file` provides the initial procedural filesystem API through
`exists(string)`, `read_text(string)`, `write_text(string, string)` and
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

`read_text` returns the complete file unchanged, including line terminators.
Empty files produce `""`. Missing or inaccessible files, read or close
failures, and malformed UTF-8 produce a deterministic diagnostic and status
`70`.

Strings are immutable UTF-8 values. Public indices count Unicode scalar values,
so multibyte text is never split in the middle of an encoding sequence.
Indexing, concatenation and exact content equality are built into the language;
`std.string` supplies length and slicing operations:

```sol
inject namespace std.string as strings

let text: string = "Aé🐉Z"
let scalar: char = text[2]
let middle: string = strings::slice(text, 1, 3)
let same: string = strings::substring(text, 1, 2)
let message: string = "Sol " + "🐉"
```

`strings::length(text)` returns `4` in this example. `slice` uses the
end-exclusive scalar range `[start, end)`, while `substring` accepts a scalar
start and count. Invalid indices and ranges produce a deterministic runtime
diagnostic and terminate the process with status `70`.

`std.memory` provides the deliberately unsafe bootstrap allocator through
generic `allocate<T>`, `reallocate<T>` and `free<T>` operations. Raw storage is
read and written explicitly with `load<T>`, `store<T>`, `load_at<T>` and
`store_at<T>`; `[]` is reserved for string scalar indexing:

```sol
inject namespace std.memory as memory

@init
fn launch() -> int
    let values: pointer<int> = memory::allocate<int>(2)

    if values == null then
        return 1
    end

    memory::store<int>(values, 19)
    memory::store_at<int>(values, 1, 23)

    let result: int = memory::load<int>(values) + memory::load_at<int>(values, 1)
    memory::free<int>(values)
    return result
end
```

Pointers to structs use `->` for field reads, writes and chains:

```sol
node->value = 42
let next_value: int = node->next->value
```

Sol deliberately has no source-level `*pointer`, `(*pointer).field`,
`pointer[index]` or `&value` syntax in this bootstrap memory model.

This 0.1.1 facility is manual raw memory, not the future Sol ownership model.
Bounds, liveness, aliasing, double-free and use-after-free remain programmer
responsibilities; `ref<T>`, `borrow<T>`, lifetimes and borrow checking are not
part of this release.

`std.collections.vector` builds a generic growable contiguous collection on
that raw-memory model. `Vector<T>` is distinct from the node-linked `List<T>`
reserved for a later collections module:

```sol
inject std.collections.vector

let values: pointer<Vector<int>> = create_vector<int>()
vector_push<int>(values, 19)
vector_push<int>(values, 23)

let result: int = vector_get<int>(values, 0) + vector_pop<int>(values)
destroy_vector<int>(values)
```

The module provides `create_vector`, `destroy_vector`, `vector_length`,
`vector_capacity`, `vector_get`, `vector_set`, `vector_reserve`, `vector_push`,
`vector_pop` and `vector_clear`. Capacity starts at zero, the first automatic
growth reserves eight elements, and later automatic growth doubles capacity.
Invalid indices, empty pops, invalid or overflowing capacities, and allocation
failures emit deterministic diagnostics and terminate with status `70`.

Elements have value-copy semantics. Destroying a vector frees its contiguous
buffer and header, but does not recursively release resources referenced by its
elements; those remain the caller's responsibility under the manual-memory
model. `clear` sets length to zero while retaining capacity and storage.

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

* `compiler/`: compiler written in Sol, canonical standard library, conformance
  suite, bootstrap tooling and native command launchers.
* `runtime-c/`: portable host runtime used by native compiler output.
* `spec/`: language specifications and compiler design decisions.
* `examples/`: example Sol programs.

## Development

Build the compiler and run its focused, CLI and conformance suites with a
verified Sol 0.1.1 native seed selected through `SOLC`:

```bash
SOLC=/path/to/sol-bootstrap-0.1.1-<platform>/bin/solc \
  ./compiler/bootstrap.sh
```

Prove repeated self-compilation independently with:

```bash
SOLC=/path/to/sol-bootstrap-0.1.1-<platform>/bin/solc \
  ./compiler/repeated-bootstrap/run.sh
```

Bootstrap archive orchestration tests run with
`python compiler/seed/test_seed.py`. The six-target GitHub Actions matrix builds
each native archive twice, requires byte-identical results, verifies its
embedded manifest, smoke-tests `solc` and `sol`, and rebuilds the compiler with
Java unavailable.

Repository changes should also pass:

```bash
git diff --check
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for additional contribution
information.
