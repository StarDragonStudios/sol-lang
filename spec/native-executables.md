# Native executable toolchain

## Pipeline

The official compiler produces host-native Sol executables through the
following pipeline:

```text
typed Sol IR
→ LLVM IR
→ host native object file
→ host linker driver
→ native executable
```

The compiler core emits deterministic LLVM IR and a C literal registry. Small
platform launchers invoke the native driver and manage intermediate files. The
compiler and generated executable both contain native machine code and do not
require a JVM.

The current implementation does not translate Sol to Java or Rust. C is used
only for the portable runtime boundary and generated literal registry.

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

Link commands are assembled by the platform launcher and passed to the selected
driver as distinct arguments.

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

The native executable pipeline is exposed through the official `solc` and
`sol` commands.

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

The public `sol` command also supports immediate native compilation and
execution:

```text
sol run program.sol
```

`sol run` performs the normal source-discovery, frontend, Sol IR, LLVM, native
object and linking pipeline, but places the generated native artifacts in
temporary storage.

After successful compilation, the resulting executable is launched directly
without a shell.

The child process inherits:

* standard input;
* standard output;
* standard error;
* the current working directory of the `sol` process.

This allows interactive native programs and preserves the meaning of relative
filesystem paths.

Temporary native artifacts are removed after execution.

If compilation fails, the program is not launched.

The Sol 0.1 `run` command accepts exactly one source file. Program command-line
arguments are not yet supported.

Persistent-output options such as `-o`, `--output` and
`--keep-intermediates` are not accepted by `sol run`, because its generated
artifacts are temporary.

Once a compiled program has been launched successfully, its process exit code
is returned unchanged by `sol run`.

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

The compiler and command infrastructure use the following exit codes:

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

For `sol run`, these codes describe command failures that occur before the
compiled program successfully takes control.

After a successful launch, the child's own process status is returned
unchanged. A valid Sol program may therefore deliberately cause `sol run` to
exit with values such as `2`, `4`, `7` or `8`; those values are then program
results rather than compiler failures.

## Current limitations

The current implementation:

* compiles only for the compiler host platform;
* supports only parameterless native entry-point binding and does not yet bind
  process command-line arguments to Sol entry-point parameters;
* discovers source modules relative to the entry source file rather than
  through a package or project manifest;
* does not provide cross-compilation guarantees;
* does not require or link a separate general-purpose Sol runtime library.

Additional runtime support may be introduced when language features require
shared native implementations or operating-system services.
