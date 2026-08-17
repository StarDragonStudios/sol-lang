# Self-host native toolchain

The self-host bootstrap turns a sealed `IrProgram` into a host-native
executable without adding a process API to the Sol 0.1.1 language:

```text
sealed Sol IR
→ deterministic textual LLVM IR
→ generated C literal registry
→ LLVM, runtime and literal object files
→ host compiler-driver link
→ native executable
```

`generate_native_artifacts` owns the target-independent part. It rejects an
invalid program without returning partial output, delegates LLVM generation to
the textual backend, collects character and string constants in stable program
order, and emits a C registry keyed by function and IR-value identity. Repeated
generation of the same program and module name is byte-for-byte identical.

The registry stores literal bytes as C `unsigned char` arrays. Byte lengths use
`sizeof(array) - 1`; Unicode scalar lengths are computed by Sol while the source
value is available. This avoids exposing raw UTF-8 internals as a language
intrinsic.

## Runtime ABI

`runtime-c/selfhost.h` is the complete cross-language contract. A string is:

```c
typedef struct {
    const unsigned char *data;
    int64_t byte_length;
    int64_t scalar_length;
} SolString;
```

External calls flatten string inputs to pointer and integer fields and return
strings through an output pointer. LLVM adapters rebuild the Sol aggregate on
their side. This deliberately avoids relying on platform-specific rules for
passing C structs by value.

The runtime implements UTF-8 string operations, console and file boundaries,
allocation and vector failure diagnostics. Host text input is strictly
validated UTF-8. Runtime contract violations print a stable diagnostic and
exit with status 70.

`std.memory` specializations are emitted directly as typed LLVM. Element sizes
come from `getelementptr`, so allocation, reallocation, loading, storing and
indexing use the concrete monomorphized type without hard-coded target sizes.

## Host driver

Use the platform driver directly when native artifacts already exist:

```text
selfhost/native-link.sh module.ll literals.c output
selfhost\native-link.bat module.ll literals.c output.exe
```

The driver selects `SOL_LINKER` when configured, then `clang`, then `cc`. It
passes every path as a distinct argument, compiles three deterministic
intermediate objects, links them in a stable order and verifies that the output
is a non-empty regular file. A stale or partial output is removed on failure.

Intermediate names are derived from the requested output:

```text
<output>.sol-link.o
<output>.sol-runtime.o
<output>.sol-literals.o
```

Windows uses `.obj`. They are removed by default; set
`SOL_KEEP_INTERMEDIATES=1` to retain them.

## Bootstrap gate

Both bootstrap scripts compile and run `native_artifact_fixture.sol`, invoke
the platform driver, then execute the linked program. The fixture exercises:

- typed manual memory allocation, load/store and release;
- UTF-8 literals, concatenation, equality, scalar length and slicing;
- console output and line input;
- file existence, write, append and read;
- the stable vector-failure diagnostic and exit status;
- deterministic artifact generation and stable invalid-input rejection.

This proves the stage-1 backend can cross the native boundary on every CI host.
The public orchestration is documented in
[`selfhost-cli.md`](selfhost-cli.md).
