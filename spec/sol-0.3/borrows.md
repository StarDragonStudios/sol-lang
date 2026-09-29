# Borrows and lifetime dependencies

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## BORROWS-01

`borrow<T>` is nonnull shared read-only and copyable independently of T. `mut_borrow<T>` is nonnull exclusive and noncopyable.

Agreed constraint. Owning task: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211); planned delivery: 0.3.0. Conformance: [C-loans](conformance.md#c-loans).

## BORROWS-02

Explicit borrow/mut_borrow, temporary reborrows, no conflicting access, no owner move/destruction under active loans. Last-use analysis includes derived/returned/stored loans, not textual heuristics.

Agreed constraint. Owning task: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213); planned delivery: 0.3.0. Conformance: [C-loans](conformance.md#c-loans).

## BORROWS-03

Reborrowing shared access from an exclusive loan suspends incompatible mutation; exclusive reborrow suspends original exclusive access.

Agreed constraint. Owning task: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213); planned delivery: 0.3.0. Conformance: [C-reborrow](conformance.md#c-reborrow).

## BORROWS-04

Explicit argument reborrow or move for noncopyable exclusive loans; methods may autoborrow receivers but never automove them.

Agreed constraint. Owning task: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213); planned delivery: 0.3.0. Conformance: [C-arguments](conformance.md#c-arguments).

## BORROWS-05

Disjoint direct fields can be independently borrowed if proved; arbitrary indices/raw pointees are not assumed disjoint.

Agreed constraint. Owning task: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213); planned delivery: 0.3.0. Conformance: [C-disjoint](conformance.md#c-disjoint).

## BORROWS-06

@lifetime names aggregate dependencies; @from marks dependent fields or borrowed returns. Multiple origins and named mappings are supported by the stored-lifetime milestone. Only dependent fields need annotations.

Agreed constraint. Owning task: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221); planned delivery: 0.3.1. Conformance: [C-origins](conformance.md#c-origins).

## BORROWS-07

Limited single-origin @from inference includes this; multiple possible origins require explicit contracts. Common duration does not mean same owner.

Agreed constraint. Owning task: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221); planned delivery: 0.3.1. Conformance: [C-origins](conformance.md#c-origins).

## BORROWS-08

@depends describes allocator/resource dependence, distinct from @from. Dependencies remain through moves, generic substitution and output values.

Agreed constraint. Owning task: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221); planned delivery: 0.3.1. Conformance: [C-dependencies](conformance.md#c-dependencies).

## BORROWS-09

Stored loans in types with destructors conservatively remain live until destruction.

Agreed constraint. Owning task: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221); planned delivery: 0.3.1. Conformance: [C-stored-drop](conformance.md#c-stored-drop).

## BORROWS-10

No safe self-referential aggregates in this first overall model, even with pinning.

Agreed constraint. Owning task: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221); planned delivery: 0.3.1. Conformance: [C-self-reference](conformance.md#c-self-reference).

## BORROWS-11

Shared/exclusive receiver access is inferred from implemented bodies or fixed explicitly; bodyless abstract/interface contracts require it. These are receiver permissions, not purity.

Agreed constraint. Owning task: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213); planned delivery: 0.3.0. Conformance: [C-receivers](conformance.md#c-receivers).

## BORROWS-12

Shared overrides cannot require exclusive access. Upcasts never permit replacing the concrete object through its base part.

Agreed constraint. Owning task: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213); planned delivery: 0.3.0. Conformance: [C-receivers](conformance.md#c-receivers).

## BORROWS-13

load copies only copyable values; store/replace need compatible exclusive places.

Agreed constraint. Owning task: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213); planned delivery: 0.3.0. Conformance: [C-places](conformance.md#c-places).
