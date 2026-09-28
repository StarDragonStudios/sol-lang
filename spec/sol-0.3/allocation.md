# Allocation, raw memory and views

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## ALLOCATION-01

`unique_pointer<T>` owns a nonnull initialized allocation. Explicit move; safe loans; moving handle does not relocate pointee.

Agreed constraint. Owning task: [#219](https://github.com/StarDragonStudios/sol-lang/issues/219); planned delivery: 0.3.0. Conformance: [C-owner](conformance.md#c-owner).

## ALLOCATION-02

Proposed new returns optional unique ownership; legacy 0.2 new/null/delete remains isolated during migration. Arguments evaluate left-to-right before allocation; moves consumed even on failure. Ordinary allocation failure does not silently restore moved inputs.

Provisional API; semantic constraints retained. Owning task: [#219](https://github.com/StarDragonStudios/sol-lang/issues/219); planned delivery: 0.3.0. Conformance: [C-allocation-failure](conformance.md#c-allocation-failure).

## ALLOCATION-03

new(allocator_instance) selects an instance, not a raw/pool/arena enum mode. Default allocator optional by environment. Correct allocator outlives all dependent allocations/control blocks.

Agreed constraint. Owning task: [#224](https://github.com/StarDragonStudios/sol-lang/issues/224); planned delivery: 0.3.1. Conformance: [C-allocator](conformance.md#c-allocator).

## ALLOCATION-04

Arena/pool reset must respect outstanding objects; deferred physical reclamation does not suppress destructors.

Agreed constraint. Owning task: [#224](https://github.com/StarDragonStudios/sol-lang/issues/224); planned delivery: 0.3.1. Conformance: [C-allocator](conformance.md#c-allocator).

## ALLOCATION-05

`pointer<T>` and `pointer<const T>` are nullable raw addresses; const restricts writes through that pointer, not global immutability. Copies are address copies, not safe ownership.

Agreed constraint. Owning task: [#215](https://github.com/StarDragonStudios/sol-lang/issues/215); planned delivery: 0.3.0. Conformance: [C-raw](conformance.md#c-raw).

## ALLOCATION-06

raw(borrow(...)) gives readonly pointer; raw(mut_borrow(...)) gives mutable pointer in explicit unsafe scope.

Agreed constraint. Owning task: [#215](https://github.com/StarDragonStudios/sol-lang/issues/215); planned delivery: 0.3.0. Conformance: [C-raw](conformance.md#c-raw).

## ALLOCATION-07

ref/mut_ref are unsafe conversions to borrows, not separate reference types. Caller guarantees initialized/aligned/live memory and alias permissions.

Agreed constraint. Owning task: [#215](https://github.com/StarDragonStudios/sol-lang/issues/215); planned delivery: 0.3.0. Conformance: [C-raw](conformance.md#c-raw).

## ALLOCATION-08

Raw-derived pointers/loans and slice_ref/mut_slice_ref views cannot escape their unsafe block initially. (null,0) can form an empty raw view; invalid positive-null/negative ranges violate contract.

Agreed constraint. Owning task: [#215](https://github.com/StarDragonStudios/sol-lang/issues/215); planned delivery: 0.3.0. Conformance: [C-raw-escape](conformance.md#c-raw-escape).

## ALLOCATION-09

slice/mut_slice are non-owning contiguous views; shared is copyable, exclusive is not. get/get_mut and subslices are checked optional results; [] bounds failure is fatal, not UB.

Agreed constraint. Owning task: [#225](https://github.com/StarDragonStudios/sol-lang/issues/225); planned delivery: 0.3.1. Conformance: [C-slices](conformance.md#c-slices).

## ALLOCATION-10

split_at_mut returns disjoint views with shared origin dependency. vector structural changes are blocked under views, even if no realloc is needed.

Agreed constraint. Owning task: [#225](https://github.com/StarDragonStudios/sol-lang/issues/225); planned delivery: 0.3.1. Conformance: [C-slices](conformance.md#c-slices).

## ALLOCATION-11

borrow_cell owns content and checks shared-versus-exclusive access at runtime. try operations return optional move-only guards. Guard outlives loans, cell outlives guard; no implied thread safety.

Agreed constraint. Owning task: [#226](https://github.com/StarDragonStudios/sol-lang/issues/226); planned delivery: 0.3.1. Conformance: [C-borrow-cell](conformance.md#c-borrow-cell).
