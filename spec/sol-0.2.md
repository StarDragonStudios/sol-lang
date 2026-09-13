# Sol 0.2 Language Specification

Status: normative source-language contract for Sol 0.2; release publication is
tracked separately in #145. The compiler implementation and conformance checks
do not by themselves constitute a published release.

## Relationship to Sol 0.1

Sol 0.2 consists of the [Sol 0.1 specification](sol-0.1.md) plus the
[object-model specification](sol-0.2-object-model.md). Unchanged procedural,
module, primitive, string, struct, generic and raw-memory behavior remains
normative. Where the object-model document explicitly extends a rule for class
or interface types, that extension takes precedence for those types only.

In particular, structs remain copyable value types. Classes are nominal,
noncopyable identity-bearing storage; they do not replace structs. Generic
functions and structs retain monomorphization. Instance methods may use method
type parameters, but generic classes and interfaces are outside this version.

## Object contract

The object-model document normatively defines:

- concrete/abstract classes and interfaces, their headers and visibility;
- explicit this/base receivers, mutable fields and exact overload matching;
- annotated constructors, delegation and definite initialization;
- single class inheritance, interface contracts and dynamic method dispatch;
- destination construction and reconstruction without instance copy/move;
- raw pointer construction, deletion and identity-preserving upcast views.

Visibility omission means public; explicitly annotating all visibility is a
style convention, not a compilation requirement. Constructors use @constructor
regardless of their function name. Fields are mutable state without @mut on
methods; @mut on a variable permits rebinding/reconstruction.

## Safety boundary

Sol 0.2 does not define safe references, borrowing, lifetimes, ownership
tracking, destructors or GC. ref<T> and borrow<T> are future concepts, not
alternative spellings for pointer<T>. Raw aliases carry no automatic ownership
transfer or cleanup responsibility. No unary * or & syntax is introduced.

Allocation failure returns null and skips construction. Null deletion is a
no-op. Valid deletion requires the original concrete allocation view exactly
once. The type checker rejects non-class operands, but does not prove allocation
provenance: invalid class-pointer deletion, dangling aliases and use-after-free
remain undefined behavior, not guaranteed compile-time or runtime diagnostics.
Deleting an object, leaving direct storage or reconstructing a destination does
not recursively release resources held through its fields.

## Representation and conformance

Object layout, table slots, allocation helpers and receiver ABI are internal
compiler/runtime details, not a portable binary interface guaranteed by the
source specification. See [LLVM backend](../docs/compiler-llvm-backend.md) and
[typed IR](../docs/sol-ir.md) for the current implementation.

The [conformance gate](../docs/compiler-conformance.md), focused frontend/IR
suites and native allocation-failure tests collectively validate the contract.
The immutable Sol 0.1.1 seed remains the bootstrap trust root; it is not expected
to accept Sol 0.2 object syntax. Publication, archive verification and target
validation are separate release gates.
