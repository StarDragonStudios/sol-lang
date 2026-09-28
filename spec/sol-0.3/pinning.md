# Pinning and shared ownership

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## PINNING-01

@pinned is a tracked guarantee, not a `pinned<P>` wrapper. pin(move(unique_owner)) requires ended prior loans; direct locals construct in final storage.

Agreed constraint. Owning task: [#230](https://github.com/StarDragonStudios/sol-lang/issues/230); planned delivery: 0.3.2. Conformance: [C-pin](conformance.md#c-pin).

## PINNING-02

Pin lasts until in-place destruction; no safe unpin. No whole pinned value copy, move, extraction or replacement. Owner handles may move.

Agreed constraint. Owning task: [#230](https://github.com/StarDragonStudios/sol-lang/issues/230); planned delivery: 0.3.2. Conformance: [C-pin](conformance.md#c-pin).

## PINNING-03

Pin activates after successful construction; constructors cannot publish pinned loans to their unfinished direct storage.

Agreed constraint. Owning task: [#230](https://github.com/StarDragonStudios/sol-lang/issues/230); planned delivery: 0.3.2. Conformance: [C-pin-init](conformance.md#c-pin-init).

## PINNING-04

Only annotated direct fields become structurally pinned with their container. Intermediate aggregates cannot move/replace; ordinary siblings remain mutable.

Agreed constraint. Owning task: [#231](https://github.com/StarDragonStudios/sol-lang/issues/231); planned delivery: 0.3.2. Conformance: [C-projection](conformance.md#c-projection).

## PINNING-05

Compatible exclusive methods use @pinned; @returns(pinned) on direct storage requires a pinned origin. Independent owned pinned allocations need not force their container pinned.

Agreed constraint. Owning task: [#231](https://github.com/StarDragonStudios/sol-lang/issues/231); planned delivery: 0.3.2. Conformance: [C-projection](conformance.md#c-projection).

## PINNING-06

Destructors obey pinned-field restrictions even for an unpinned instance; pin survives cleanup. Pinned mutable local drop(value) permits later fresh in-place construction only with @mut; old loans never revive.

Agreed constraint. Owning task: [#231](https://github.com/StarDragonStudios/sol-lang/issues/231); planned delivery: 0.3.2. Conformance: [C-pin-drop](conformance.md#c-pin-drop).

## PINNING-07

Pinning vector/borrow_cell does not pin managed contents. No directly pinned contiguous elements initially; collections can move handles to independently pinned allocations.

Agreed constraint. Owning task: [#231](https://github.com/StarDragonStudios/sol-lang/issues/231); planned delivery: 0.3.2. Conformance: [C-pin-container](conformance.md#c-pin-container).

## PINNING-08

Shared/weak handles are explicit-copy only: share/downgrade/upgrade/clone_weak/move. Last strong destroys object; weak retains control block; cycles not collected automatically.

Agreed constraint. Owning task: [#232](https://github.com/StarDragonStudios/sol-lang/issues/232); planned delivery: 0.3.2. Conformance: [C-shared](conformance.md#c-shared).

## PINNING-09

into_shared / into_atomic_shared consume unique ownership without relocating object; control-block failure consumes/destroys discardable object under agreed contract. Pending ownership is excluded rather than silently destroyed.

Agreed constraint. Owning task: [#232](https://github.com/StarDragonStudios/sol-lang/issues/232); planned delivery: 0.3.2. Conformance: [C-shared-failure](conformance.md#c-shared-failure).

## PINNING-10

@atomic denotes counter behavior, not content thread safety. No implicit conversion between atomic/non-atomic families.

Agreed constraint. Owning task: [#233](https://github.com/StarDragonStudios/sol-lang/issues/233); planned delivery: 0.3.2. Conformance: [C-atomic](conformance.md#c-atomic).

## PINNING-11

Pin before sharing; all strong/weak operations and upcasts preserve pin and concrete destruction/allocator identity.

Agreed constraint. Owning task: [#234](https://github.com/StarDragonStudios/sol-lang/issues/234); planned delivery: 0.3.2. Conformance: [C-qualifiers](conformance.md#c-qualifiers).

## PINNING-12

Annotated generic arguments preserve guarantees; optional is transparent for payload qualifiers, arbitrary containers are not. Mutable containers invariant. Qualifiers do not distinguish overloads.

Agreed constraint. Owning task: [#234](https://github.com/StarDragonStudios/sol-lang/issues/234); planned delivery: 0.3.2. Conformance: [C-qualifiers](conformance.md#c-qualifiers).

## PINNING-13

Unsafe raw access cannot cancel pin; known-origin mut_ref must preserve it, external origins impose explicit obligations.

Agreed constraint. Owning task: [#235](https://github.com/StarDragonStudios/sol-lang/issues/235); planned delivery: 0.3.2. Conformance: [C-pin-raw](conformance.md#c-pin-raw).
