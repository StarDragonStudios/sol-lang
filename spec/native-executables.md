# Native executable toolchain

## Pipeline

The Java bootstrap compiler produces host-native Sol executables through the
following pipeline:

```text
typed Sol IR
→ LLVM IR
→ host native object file
→ host linker driver
→ native executable
```

Java orchestrates compilation and linking. The generated executable contains
native machine code and does not require the JVM.

The current implementation does not translate Sol to Java, C or Rust.

## Executable programs

A typed Sol IR program is either:

* a library program without an entry point; or
* an executable program with exactly one `IrEntryPoint`.

The entry point corresponds to the Sol function selected by `@init`.

An entry function:

* must have a body;
* may use any valid Sol function name;
* may declare parameters;
* returns Sol `int`.

Entry-point parameters are valid at the language and Sol IR levels. The general
`IrEntryPoint` model must not reject a function merely because it declares
parameters.

## Native startup bridge

For an executable program, the LLVM backend generates the platform-visible
`main` symbol.

The currently supported startup signature is a parameterless Sol entry
function:

```sol
@init
fn launch() -> int
```

Conceptually, the generated LLVM wrapper is:

```llvm
define i32 @main() {
entry:
  %result = call i64 @sol.functionN.launch()
  %status = trunc i64 %result to i32
  ret i32 %status
}
```

Sol `int` is represented as LLVM `i64`. The native wrapper truncates the entry
result to the platform-compatible `i32` process exit status.

A parameterized entry function remains valid Sol IR, but the current LLVM
backend reports an actionable error because process-argument binding has not
yet been implemented.

The future startup ABI must define how native `argc` and `argv` values are
converted to Sol entry-point parameter values. That conversion must not be
invented implicitly by the general IR model.

Library programs do not receive a native `main` symbol.

## Linker discovery

The native toolchain discovers a linker driver in this order:

1. the executable path configured by `SOL_LINKER`;
2. the command name configured by `SOL_LINKER`, resolved through `PATH`;
3. `clang`, resolved through `PATH`;
4. `cc`, resolved through `PATH`.

On Windows, executable-name variants such as `clang.exe` and `cc.exe` are also
considered.

The compiler invokes a compiler driver rather than invoking `ld` directly.
The driver supplies the platform startup objects, system libraries and
platform-specific linker behavior required to produce an executable.

A configured linker must identify a regular executable file. Discovery
failures produce an actionable toolchain diagnostic.

## Link command

Link commands are represented as argument lists and are passed directly to
`ProcessBuilder`.

For example:

```text
clang program.sol-link.o -o program
```

is represented as distinct arguments rather than as a shell command string.

This provides deterministic argument ordering and supports paths containing
spaces without manual quoting or shell escaping.

The linker process:

* receives closed standard input;
* has standard output and standard error drained concurrently;
* preserves its exit code;
* preserves its original standard output and standard error;
* produces an actionable error when it fails.

## Output validation

A link operation is only successful when the requested output:

* exists;
* is a regular file;
* is non-empty.

A stale executable is removed before linking so that it cannot be mistaken for
new output.

Partial or empty executable files are deleted after failed link attempts.

## Output naming

On Unix-like hosts, the requested executable name is preserved.

On Windows, `.exe` is appended unless the requested name already ends with
that extension, case-insensitively.

The deterministic intermediate object name is:

```text
<executable>.sol-link.o
```

or on Windows:

```text
<executable>.sol-link.obj
```

## Temporary object policy

The native executable compiler supports two intermediate-object policies:

* `DELETE`: remove the object after successful or failed linking;
* `KEEP`: retain the object for debugging or inspection.

`DELETE` is the default.

Cleanup failures are either reported directly or attached to the original
compilation failure as suppressed exceptions.

## Command-line integration

The native executable pipeline is exposed through the bootstrap compiler CLI.

A command equivalent to:

```text
solc program.sol
```

performs:

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

The CLI supports:

```text
solc program.sol
solc program.sol -o output
solc program.sol --output output
solc program.sol --output=output
solc --keep-intermediates program.sol
```

Compilation only creates the executable. It does not execute it.

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

the declarations:

```sol
inject helper
inject utilities.math
```

resolve to:

```text
helper          → project/helper.sol
utilities.math  → project/utilities/math.sol
```

Injected modules are discovered recursively. Already discovered modules are not
loaded again, so cyclic function-level module dependencies are supported.

A missing injected source file remains unresolved during discovery. Semantic
analysis then reports the normal `SOL-S019` diagnostic at the corresponding
injection declaration.

The default intermediate-object policy is `DELETE`.
`--keep-intermediates` selects `KEEP`.

Frontend diagnostics preserve their source file, one-based line and column,
severity, diagnostic code and message.

Backend failures and native toolchain or linker failures remain distinguishable
at the CLI boundary.

The compiler process exit codes are:

|  Code | Meaning                               |
|------:|---------------------------------------|
|   `0` | successful compilation                |
|   `2` | invalid command-line arguments        |
|   `3` | source or filesystem input failure    |
|   `4` | lexical, parsing, or semantic failure |
|   `5` | Sol IR lowering failure               |
|   `6` | LLVM backend failure                  |
|   `7` | native toolchain or linker failure    |

## Current limitations

The current implementation:

* compiles only for the compiler host platform;
* supports only parameterless native entry-point binding;
* discovers source modules relative to the entry source file rather than
  through a package or project manifest;
* does not provide cross-compilation guarantees;
* does not require or link a separate general-purpose Sol runtime library.

Additional runtime support may be introduced when language features require
shared native implementations or operating-system services.
