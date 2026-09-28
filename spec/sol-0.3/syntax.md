# Source syntax and naming

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## SYNTAX-01

Every let has an explicit type. Pattern bindings may inherit field types during complete destructuring; ordinary match payload examples use explicit types.

Agreed constraint. Owning task: [#210](https://github.com/StarDragonStudios/sol-lang/issues/210); planned delivery: 0.3.0. Conformance: [C-syntax](conformance.md#c-syntax).

## SYNTAX-02

fn and struct open their own bodies and end with end; generic blocks use begin ... end. An unsafe block is @unsafe begin ... end.

Agreed constraint. Owning task: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211); planned delivery: 0.3.0. Conformance: [C-syntax](conformance.md#c-syntax).

## SYNTAX-03

Annotations use lower_snake_case. Consecutive annotations may share a line; no required newline before the declaration. Prefer compact signatures/annotations and blank lines between logical body steps.

Agreed constraint. Owning task: [#210](https://github.com/StarDragonStudios/sol-lang/issues/210); planned delivery: 0.3.0. Conformance: [C-syntax](conformance.md#c-syntax).

## SYNTAX-04

Values and borrows access members with a dot; pointers use ->. No unary * or &.

Agreed constraint. Owning task: [#215](https://github.com/StarDragonStudios/sol-lang/issues/215); planned delivery: 0.3.0. Conformance: [C-syntax](conformance.md#c-syntax).

## SYNTAX-05

Non-object concepts use snake_case. Retain the agreed callable names Function and Action pending a deliberate reviewed naming change, not an automatic renaming.

Agreed constraint. Owning task: [#236](https://github.com/StarDragonStudios/sol-lang/issues/236); planned delivery: 0.3.3. Conformance: [C-naming](conformance.md#c-naming).

## SYNTAX-06

List is node-linked; vector is the contiguous dynamic buffer. Do not reinstate Map/Set/tree work as a safety-release gate.

Agreed constraint. Owning task: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228); planned delivery: 0.3.1. Conformance: [C-naming](conformance.md#c-naming).
