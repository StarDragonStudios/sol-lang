# Ownership, copies and destruction

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## OWNERSHIP-01

Safe by default within the new mode. One exclusive owner; explicit move(value); moved source is invalid until valid @mut reinitialization.

Agreed constraint. Owning task: [#212](https://github.com/StarDragonStudios/sol-lang/issues/212); planned delivery: 0.3.0. Conformance: [C-moves](conformance.md#c-moves).

## OWNERSHIP-02

@mut permits rebinding; it is not required merely to obtain mut_borrow and it is not a method modifier.

Agreed constraint. Owning task: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211); planned delivery: 0.3.0. Conformance: [C-mutability](conformance.md#c-mutability).

## OWNERSHIP-03

Experimental structs are move-only unless @copy is valid; every field must be copyable and no destructor may exist. Primitive copy and borrow copy do not invoke custom duplication.

Agreed constraint. Owning task: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211); planned delivery: 0.3.0. Conformance: [C-copy](conformance.md#c-copy).

## OWNERSHIP-04

Classes retain identity and are not implicitly copied/moved; do not authorize whole-class replacement via store/replace or through base views.

Agreed constraint. Owning task: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211); planned delivery: 0.3.0. Conformance: [C-class-identity](conformance.md#c-class-identity).

## OWNERSHIP-05

Track moves path-sensitively, including joins, backedges and normal exits. Destroy only initialized owned values, in reverse local initialization order. No partial field moves leaving an invalid aggregate.

Agreed constraint. Owning task: [#212](https://github.com/StarDragonStudios/sol-lang/issues/212); planned delivery: 0.3.0. Conformance: [C-flow](conformance.md#c-flow).

## OWNERSHIP-06

Ordinary assignment prepares the replacement before destroying the old value. take empties optional; replace installs and returns old content; store installs and destroys old content.

Agreed constraint. Owning task: [#214](https://github.com/StarDragonStudios/sol-lang/issues/214); planned delivery: 0.3.0. Conformance: [C-replacement](conformance.md#c-replacement).

## OWNERSHIP-07

@destructor: arbitrary function name, one per type, no explicit args, void result, not directly called. Body runs while fields/base are present, then own fields in reverse declaration order, then base.

Agreed constraint. Owning task: [#214](https://github.com/StarDragonStudios/sol-lang/issues/214); planned delivery: 0.3.0. Conformance: [C-cleanup](conformance.md#c-cleanup).

## OWNERSHIP-08

Destructors cannot extract owned fields via move, take or replace, or propagate recoverable errors. Fallible close/flush is a separate explicit API.

Agreed constraint. Owning task: [#214](https://github.com/StarDragonStudios/sol-lang/issues/214); planned delivery: 0.3.0. Conformance: [C-destructor](conformance.md#c-destructor).

## OWNERSHIP-09

drop(move(value)) destroys transferable owned values early. Fatal failure does not guarantee stack unwinding or destruction; environment fatal mechanism cannot return normally.

Agreed constraint. Owning task: [#214](https://github.com/StarDragonStudios/sol-lang/issues/214); planned delivery: 0.3.0. Conformance: [C-drop](conformance.md#c-drop).

## OWNERSHIP-10

Raw pointers do not automatically own pointees.

Agreed constraint. Owning task: [#215](https://github.com/StarDragonStudios/sol-lang/issues/215); planned delivery: 0.3.0. Conformance: [C-raw](conformance.md#c-raw).
