# LLVM backend

The LLVM backend transforms validated typed Sol IR into verified LLVM IR.

It can also configure the generated module for a native target and emit a
native object file.

It is implemented under:

```text
io.github.stardragonstudios.sol.backend.llvm
```

## Pipeline position

```text
typed Sol IR
→ target-independent LLVM generation
→ LLVM verification
→ native object emission
→ native linker
→ executable
```

This backend performs every step through native object emission.

Native linking and executable production remain toolchain responsibilities.

It does not:

* consume syntax or semantic AST nodes;
* generate C source;
* invoke GCC, Clang or another C compiler;
* invoke a linker;
* link the Sol runtime.

Native target selection and object generation are implemented by the
object-emission layer built on top of `LlvmBackend`.

The linker and runtime integration remain outside the LLVM backend.

## Dependency

The compiler uses JavaCPP Presets for LLVM:

```text
org.bytedeco:llvm-platform
```

The platform artifact provides the Java bindings and the corresponding native
LLVM libraries.

The JVM must allow native access for unnamed modules:

```text
--enable-native-access=ALL-UNNAMED
```

Gradle tests and application executions configure this option explicitly.

## Native target configuration

Native target support is initialized lazily and at most once per JVM process.

Initialization:

* loads the LLVM native library;
* initializes the native target;
* initializes the native assembly printer.

`LlvmTargetConfiguration.host()` obtains the host:

* default target triple;
* CPU name;
* CPU feature string.

The default host configuration uses LLVM's default optimization level,
relocation model and code model.

An explicit `LlvmTargetConfiguration` can instead provide:

* a target triple;
* a CPU name;
* a CPU feature string;
* a code-generation optimization level;
* a relocation model;
* a code model.

Configuration values are normalized and validated before a target machine is
created.

## Target machines

`LlvmTargetMachine.create` resolves the configured target triple and verifies
that the resolved LLVM target provides:

* a target-machine implementation;
* an assembly backend capable of native object emission.

The target machine exposes its resolved target name, configuration and data
layout.

Before object emission, it configures the LLVM module with the exact target
triple and target data layout used by native code generation.

`LlvmTargetMachine` implements `AutoCloseable`. Closing it is idempotent, and
using its native handle after closure produces an explicit
`LlvmBackendException`.

## Native object emission

`LlvmObjectBackend.emitHostObject` generates an LLVM module and emits an object
file using the detected host configuration.

`LlvmObjectBackend.emitObject` performs the same operation using an explicit
target configuration.

Object emission:

1. normalizes the destination path;
2. creates missing parent directories;
3. rejects a destination that identifies a directory;
4. applies the target triple and data layout to the module;
5. verifies the configured module;
6. emits an LLVM native object file;
7. verifies that the result is a non-empty regular file.

If emission fails after creating a new destination file, the incomplete file
is removed. A pre-existing destination is not deleted during failure cleanup.

## Primitive type mapping

The initial Sol IR primitive representations are:

| Sol IR type | LLVM type |
| ----------- | --------- |
| `int`       | `i64`     |
| `float`     | `double`  |
| `boolean`   | `i1`      |
| `char`      | `i32`     |
| `string`    | `{ ptr, i64, i64 }` |
| `void`      | `void`    |
| `pointer<T>` | opaque `ptr` |

Sol IR structs lower to LLVM aggregate struct types whose element order matches
the canonical declaration order. Structs are passed to functions and returned
by value. Nested structs lower recursively; semantic analysis rejects recursive
by-value layouts before backend generation. Pointer fields lower to opaque
`ptr`, so a pointer may break an otherwise recursive struct layout without
requiring a recursive LLVM aggregate.

Source generics require no LLVM runtime mechanism. Sol IR contains only the
concrete function and struct specializations produced by monomorphization, so
the backend lowers them through the ordinary function and aggregate paths.

`char` stores a Unicode code point.

`string` stores a pointer to valid UTF-8 bytes, the byte length and the Unicode
scalar length. Neither length includes an implementation NUL terminator.
Static literals have module lifetime; runtime concatenations use
process-lifetime storage in the 0.1.1 bootstrap.

## Program generation

One `IrProgram` is lowered into one LLVM module.

Generation occurs in two phases.

### Function predeclaration

Every Sol IR function is first registered as an LLVM function declaration.

This guarantees that the body-lowering phase can resolve:

* forward calls;
* recursive calls;
* calls between Sol modules;
* bodyless external declarations.

Functions are resolved through their global `IrFunctionId`.

Diagnostic source names do not define backend identity.

LLVM symbols currently use the deterministic form:

```text
sol.function<index>.<diagnostic-name>
```

### Function-body lowering

After all declarations exist, defined functions receive their bodies.

For every function, the backend:

1. registers LLVM parameters;
2. predeclares every basic block;
3. allocates every local-storage slot;
4. lowers instructions in Sol IR order;
5. lowers each block terminator.

Bodyless functions remain LLVM declarations and do not receive invented
implementations.

## Values

LLVM values are registered against their canonical Sol IR object instances.

The backend supports:

* parameters;
* integer constants;
* floating-point constants;
* boolean constants;
* character constants;
* UTF-8 string constants;
* typed null pointer constants;
* unary instructions;
* binary instructions;
* local loads;
* value-returning calls;
* struct construction with `insertvalue`;
* field reads with `extractvalue`;
* direct and nested field updates by rebuilding the affected aggregate path and
  storing the complete updated value;
* pointer loads and stores;
* indexed pointer address calculation with `getelementptr` followed by a typed
  load or store.
* Unicode-scalar string indexing through the shared string runtime.

Unary positive reuses the operand's LLVM value and does not emit a redundant
instruction.

## Operations

Integer arithmetic uses signed LLVM operations where signedness matters:

* `sdiv`;
* `srem`;
* signed relational comparisons.

Floating-point arithmetic uses the corresponding LLVM floating-point
instructions.

Floating-point equality uses ordered equality.

Floating-point inequality uses unordered-or-not-equal semantics, so NaN remains
different from every value, including itself.

Boolean conjunction and disjunction operate directly on `i1`.

String concatenation adds checked byte and scalar lengths, allocates bounded
UTF-8 storage, copies both byte sequences and stores a trailing interoperability
NUL outside the Sol byte length. String equality rejects unequal byte lengths
before calling `memcmp`; valid UTF-8 makes exact byte equality equivalent to
exact scalar-sequence equality. Inequality negates that content result and
never compares data pointers.

String indexing first checks the scalar index and then walks UTF-8 leading-byte
widths to the selected scalar. The decoder returns the complete Unicode code
point as `i32`, including four-byte supplementary values.

## Local storage

Every canonical `IrLocal` receives one LLVM `alloca`.

All allocations are emitted in the function entry block, independently of the
Sol IR block containing the initialization instruction.

Local instructions map as follows:

| Sol IR instruction | LLVM representation |
| ------------------ | ------------------- |
| initialization     | `store`             |
| load               | `load`              |
| mutable update     | `store`             |

Sol IR validates mutability and type equality before backend generation.

The backend resolves locals by canonical object identity rather than by source
name or equivalent copied records.

## Control flow

All LLVM basic blocks are created before terminators are lowered.

This permits:

* forward branches;
* merge blocks;
* loop back-edges;
* cyclic control-flow graphs.

Terminator mappings are:

| Sol IR terminator  | LLVM instruction |
| ------------------ | ---------------- |
| bare return        | `ret void`       |
| value return       | `ret`            |
| branch             | `br label`       |
| conditional branch | `br i1`          |

Branch destinations are resolved through canonical `IrBlockTarget` instances.

## Calls

Both value-producing and `void` calls use the function declaration registered
during predeclaration.

Call arguments retain their exact Sol IR order.

The backend does not:

* search by function spelling;
* re-resolve overloads;
* reconstruct signatures;
* infer argument conversions.

Those properties have already been established and validated by Sol IR.

## Standard string lowering

Bodyless declarations from `std.string` receive compiler-supplied LLVM bodies:

* `length` extracts the cached Unicode scalar count;
* `slice` maps end-exclusive scalar boundaries to UTF-8 byte offsets and
  returns an immutable view;
* `substring` validates its scalar start and count before delegating to the
  same slice operation.

The backend emits shared internal helpers only when a program uses the
corresponding operation. They centralize UTF-8 offset walking, scalar decoding,
content comparison, concatenation and deterministic failure handling. Negative,
reversed, overflowing or out-of-bounds requests print a stable runtime
diagnostic and call the portable host `exit(70)` boundary.

The runtime invariant is that every native Sol string contains valid UTF-8.
Source decoding enforces it for literals, concatenation preserves it by joining
complete strings, and slicing computes only scalar boundaries.

`std.file.read_text` and `std.console.read_line` share a native text-input
runtime. It grows process-lifetime storage with `malloc`/`realloc`, validates
RFC 3629 UTF-8 (including overlong encodings, surrogates and the U+10FFFF
limit), counts Unicode scalars and only then constructs `{ ptr, i64, i64 }`.
File bytes are read in binary mode without newline rewriting. Console input
recognizes LF and CRLF, distinguishes an empty line from EOF, and accepts an
unterminated non-empty final line. Stable failures call `exit(70)`.

## Standard raw-memory lowering

Concrete bodyless specializations from `std.memory` receive compiler-supplied
LLVM bodies after function predeclaration. They call the platform C allocator
through these 64-bit supported-target signatures:

```llvm
declare ptr @malloc(i64)
declare ptr @realloc(ptr, i64)
declare void @free(ptr)
```

The element size comes from LLVM's target-aware `sizeof` constant expression.
`allocate<T>` and `reallocate<T>` guard signed non-positive counts and check
`count * sizeof(T)` against the positive 64-bit byte-count range before calling
the host allocator. Zero-sized pointee layouts return null without requesting
host storage. `reallocate<T>(pointer, 0)` routes through `free` and returns an
opaque null pointer; negative and overflowing requests return null without
releasing the original allocation.

The native allocator supplies alignment suitable for every Sol value layout
currently emitted on the supported x86-64 and arm64 targets. Indexed access
uses the exact pointee LLVM type with `getelementptr`, so element scaling and
alignment are derived from the target data layout rather than hard-coded byte
sizes.

`load<T>` and `store<T>` lower to typed LLVM loads and stores at the supplied
address. `load_at<T>` and `store_at<T>` first compute the indexed address with
typed `getelementptr`. Source-level `pointer->field` uses a struct GEP followed
by a typed load or store; chained arrows repeat that operation using each
field's pointer type.

## Native ownership

`LlvmModule` owns:

* one `LLVMContextRef`;
* one `LLVMModuleRef`.

The LLVM module is destroyed before its context.

`LlvmFunctionLoweringContext` owns one temporary `LLVMBuilderRef` for the
function currently being generated.

Builders are destroyed immediately after lowering that function.

LLVM types, values, functions and basic blocks are owned transitively by their
context or module and are not destroyed independently.

Strings allocated by LLVM printing and verification APIs are released with
`LLVMDisposeMessage`.

`LlvmModule` implements `AutoCloseable` and closing it is idempotent.

Calling module operations after closure produces an explicit
`LlvmBackendException`.

`LlvmTargetMachine` owns one `LLVMTargetMachineRef`.

Target-data values created to obtain or apply a data layout are temporary and
are disposed immediately after use.

## Verification

Every module produced by `LlvmBackend.generate` is verified before it is
returned.

The object-emission layer verifies the module again after applying the target
triple and data layout, ensuring that the exact module passed to native code
generation is valid.

Verification uses status-returning behavior rather than aborting the compiler
process.

A verification failure becomes an `LlvmBackendException` with:

* a deterministic Sol compiler prefix;
* normalized LLVM verification details.

Generation failures close the partially created native module before
propagating the error.

## Invocation isolation

Every invocation of `LlvmBackend.generate` creates a new LLVM context and
module.

No mutable LLVM state is shared between compilations.

This prevents functions, values, blocks or diagnostics from leaking from one
compiler invocation into another.

## Current exclusions

The current backend does not support:

* LLVM IR optimization pipelines;
* debug information;
* sanitizer instrumentation as a first-class compiler option.

Runtime linking and executable production belong to the surrounding native
toolchain layer rather than this LLVM generation component.
