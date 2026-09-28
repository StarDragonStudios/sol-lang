# First-class functions, lambdas and modules

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## CALLABLES-01

`Function<Args,R>` values can be passed, stored, returned and composed. `Action<Args>` aliases `Function<Args,void>`; [] is the empty parameter list.

Agreed constraint. Owning task: [#237](https://github.com/StarDragonStudios/sol-lang/issues/237); planned delivery: 0.3.3. Conformance: [C-signature](conformance.md#c-signature).

## CALLABLES-02

Lambdas use (...) => expression or (...) => begin ... return ... end. @captures(alias = expression) captures explicitly once, left-to-right in outer scope; aliases available only in body.

Agreed constraint. Owning task: [#237](https://github.com/StarDragonStudios/sol-lang/issues/237); planned delivery: 0.3.3. Conformance: [C-capture](conformance.md#c-capture).

## CALLABLES-03

Capture by allowed copy/move/borrow/mut_borrow. No implicit local capture. Parameter passing is distinct from capture.

Agreed constraint. Owning task: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238); planned delivery: 0.3.3. Conformance: [C-capture](conformance.md#c-capture).

## CALLABLES-04

@shared reads captures; @exclusive mutates with reusable valid state; @once permits consuming invocation. These are not purity/concurrency claims.

Agreed constraint. Owning task: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238); planned delivery: 0.3.3. Conformance: [C-capability](conformance.md#c-capability).

## CALLABLES-05

move(work)() consumes a direct callable or exclusive callable owner. Function is noncopyable by default. A move capture can still support shared invocation.

Agreed constraint. Owning task: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238); planned delivery: 0.3.3. Conformance: [C-capability](conformance.md#c-capability).

## CALLABLES-06

Reusable actions cannot consume a capture repeatedly unless represented with a valid state transition (e.g. optional/take).

Agreed constraint. Owning task: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238); planned delivery: 0.3.3. Conformance: [C-capability](conformance.md#c-capability).

## CALLABLES-07

@once does not itself discharge @must_resolve; consuming adapters must not abandon captures. Borrowing pending data leaves responsibility outside.

Agreed constraint. Owning task: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238); planned delivery: 0.3.3. Conformance: [C-callable-pending](conformance.md#c-callable-pending).

## CALLABLES-08

Captured loans and returned loans preserve @from/@depends. No returning a loan to an owned capture destroyed by consuming invocation.

Agreed constraint. Owning task: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238); planned delivery: 0.3.3. Conformance: [C-capture-lifetime](conformance.md#c-capture-lifetime).

## CALLABLES-09

Same lambda expression and capture types define the concrete identity; captured values do not. Separate lambda expressions remain distinct even with equal sizes/signatures.

Agreed constraint. Owning task: [#236](https://github.com/StarDragonStudios/sol-lang/issues/236); planned delivery: 0.3.3. Conformance: [C-identity](conformance.md#c-identity).

## CALLABLES-10

Direct factories return one concrete representation across branches; by-value consumers specialize. @mut cannot silently change layout or allocate.

Agreed constraint. Owning task: [#239](https://github.com/StarDragonStudios/sol-lang/issues/239); planned delivery: 0.3.3. Conformance: [C-specialization](conformance.md#c-specialization).

## CALLABLES-11

Borrowed Function views support compatible implementations without owning/allocating captures. They may use indirect calls.

Agreed constraint. Owning task: [#239](https://github.com/StarDragonStudios/sol-lang/issues/239); planned delivery: 0.3.3. Conformance: [C-views](conformance.md#c-views).

## CALLABLES-12

Uniform owned callables use explicit fallible allocator-backed unique_pointer. Failure returns original uninvoked callable; this is not ordinary new semantics.

Agreed constraint. Owning task: [#240](https://github.com/StarDragonStudios/sol-lang/issues/240); planned delivery: 0.3.3. Conformance: [C-erasure](conformance.md#c-erasure).

## CALLABLES-13

Uniform erasure preserves concrete destruction, allocator provenance, pin/capabilities/lifetimes/obligations. No recovery/downcast to concrete representation initially.

Agreed constraint. Owning task: [#240](https://github.com/StarDragonStudios/sol-lang/issues/240); planned delivery: 0.3.3. Conformance: [C-erasure](conformance.md#c-erasure).

## CALLABLES-14

Compiled interfaces expose contracts/layout metadata to compiler, not capture names to users. No stable cross-compiler ABI; reject incompatible interfaces. FFI is explicit.

Agreed constraint. Owning task: [#236](https://github.com/StarDragonStudios/sol-lang/issues/236); planned delivery: 0.3.3. Conformance: [C-interfaces](conformance.md#c-interfaces).

## CALLABLES-15

Typed variadics: [string...], parameters parts: string..., read-only slice-like body view, zero-or-more homogeneous args, at most one last variadic.

Agreed constraint. Owning task: [#241](https://github.com/StarDragonStudios/sol-lang/issues/241); planned delivery: 0.3.3. Conformance: [C-variadic](conformance.md#c-variadic).

## CALLABLES-16

One trailing values... call expansion. No hidden noncopyable copy/move-out. Exact fixed overload precedes compatible variadic for ordinary calls; expanded calls use variadic signature.

Agreed constraint. Owning task: [#241](https://github.com/StarDragonStudios/sol-lang/issues/241); planned delivery: 0.3.3. Conformance: [C-variadic](conformance.md#c-variadic).

## CALLABLES-17

Named callable overload resolves once against expected exact signature, not at every invocation.

Agreed constraint. Owning task: [#237](https://github.com/StarDragonStudios/sol-lang/issues/237); planned delivery: 0.3.3. Conformance: [C-overload](conformance.md#c-overload).
