# Self-host textual LLVM backend

The self-hosted compiler lowers a sealed, target-independent `IrProgram` to
deterministic textual LLVM IR. The public entry point is:

```sol
fn generate_llvm_ir(program: pointer<IrProgram>, module_name: string) -> LlvmGenerationResult
```

Generation is deliberately independent of the lexer, parser, semantic model
and host target. It consumes only validated Sol IR and returns either the
complete module text or one stable error message. It does not initialize an
LLVM target or choose a data layout. Native artifact generation and the host
driver are described in
[`selfhost-native-toolchain.md`](selfhost-native-toolchain.md).

## Type and identity mapping

The bootstrap ABI maps Sol types as follows:

| Sol IR | LLVM IR |
| --- | --- |
| `int` | `i64` |
| `float` | `double` |
| `boolean` | `i1` |
| `char` | `i32` Unicode scalar value |
| `string` | `%sol.string = type { ptr, i64, i64 }` |
| `pointer<T>` | opaque `ptr` |
| struct value | deterministic named `%sol.typeN` |
| `void` | `void` |

The two integer fields in `%sol.string` are the UTF-8 byte length and Unicode
scalar length. Struct names and function symbols use catalog identities rather
than source spelling, so punctuation in a generic display name cannot change
LLVM parsing. Source module, function and struct names are retained only in
comments for diagnostics.

Function, block, parameter, instruction and local identities are mapped to
`@sol.functionN`, `blockN`, `%valueN` and `%localN`. Temporary addresses and
aggregate updates include their block and instruction positions. Traversal is
always in source-module, declaration, block and instruction order, making two
generations of the same sealed IR byte-for-byte identical.

Backend allocations are invocation-local and owned by the generation context.
The context borrows the sealed `IrProgram`, never mutates it, and releases its
type catalog before returning. The returned strings are ordinary immutable Sol
values. A failed generation returns an empty `text` field, so callers cannot
accidentally consume a partial module.

## Supported Sol IR

The emitter covers all instructions currently produced by semantic lowering:

- integer and floating arithmetic, comparisons and boolean operations;
- locals, parameters, calls and `void` calls;
- struct construction, extraction and nested field mutation;
- raw pointer loads, stores, indexing and struct-field access;
- direct, conditional and return terminators;
- the native `main` adapter for a canonical Sol entry point;
- string concatenation, equality and scalar indexing.

All functions and struct layouts are emitted before bodies can refer to them.
The native adapter calls the zero-parameter Sol entry function, truncates its
`i64` status to the platform C `int`, and returns it.

## Runtime boundary

String operations are emitted as calls to stable C-compatible
`sol_runtime_string_*`
declarations. Unicode `char` and `string` constants are materialized through
runtime declarations keyed by the globally unique function id and the
function-local IR value id. Their exact decoded value is also preserved in a
deterministic LLVM comment.

Canonical bodyless standard-library functions receive typed LLVM adapters.
Memory operations lower directly to allocation, typed load/store and typed
indexing instructions. Console, file, string and vector-failure adapters cross
the C boundary using only pointers and scalar fields; `%sol.string` aggregate
values never depend on a platform-specific C aggregate calling convention.

This boundary is intentional. Sol 0.1.1 exposes string length in Unicode
scalars, but it does not expose raw UTF-8 bytes or a numeric `char` conversion
to self-hosted code. The native artifact pass therefore emits a deterministic C
literal registry with exact UTF-8 byte and scalar lengths. The separately
compiled runtime validates UTF-8 at host input boundaries.

## Verification

`selfhost/src/llvm_generation_test.sol` checks input rejection, deterministic
names, layouts, functions, operations and the native entry adapter. The
existing lowering suite also passes representative complete programs through
LLVM generation.

Both bootstrap scripts compile `selfhost/src/llvm_fixture.sol` with the frozen
Sol 0.1.1 seed, run it to generate a non-trivial `.ll` module, and ask the host
Clang to parse and re-emit that module:

```text
clang -x ir -S -emit-llvm generated.ll -o <discard>
```

Set `SOL_CLANG` when Clang is not available as `clang` on `PATH`. This is the
authoritative syntax and verifier gate on both Linux and Windows. The bootstrap
then compiles the generated LLVM module, runtime and literal registry to
objects, links them, and executes the result. Target archives and release smoke
tests remain part of the 0.1.1 release gate.
