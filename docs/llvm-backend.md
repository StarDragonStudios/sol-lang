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
| `void`      | `void`    |

`char` stores a Unicode code point.

`string` is deliberately unsupported by the current backend. Attempting to
lower it produces an explicit `LlvmBackendException`.

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
* unary instructions;
* binary instructions;
* local loads;
* value-returning calls.

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

* strings;
* LLVM IR optimization pipelines;
* debug information;
* runtime linking;
* executable output.

Runtime linking and executable production belong to later toolchain stages.
