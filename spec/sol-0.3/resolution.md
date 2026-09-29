# Must-resolve protocols and collections

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## RESOLUTION-01

@must_resolve replaces the earlier suggested @must_consume. Reading or moving is not resolution; moving transfers the obligation.

Agreed constraint. Owning task: [#222](https://github.com/StarDragonStudios/sol-lang/issues/222); planned delivery: 0.3.1. Conformance: [C-resolution](conformance.md#c-resolution).

## RESOLUTION-02

Container storage retains obligations; shared owning pointers cannot own pending content in the first model.

Agreed constraint. Owning task: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228); planned delivery: 0.3.1. Conformance: [C-container-pending](conformance.md#c-container-pending).

## RESOLUTION-03

@resolves(parameter) authorizes a defining-module operation on an owned parameter. It is not automatic discharge on entry.

Agreed constraint. Owning task: [#222](https://github.com/StarDragonStudios/sol-lang/issues/222); planned delivery: 0.3.1. Conformance: [C-resolution](conformance.md#c-resolution).

## RESOLUTION-04

Inherited field obligations survive complete destructuring; explicit type obligations cannot be bypassed by external public-field destructuring.

Agreed constraint. Owning task: [#222](https://github.com/StarDragonStudios/sol-lang/issues/222); planned delivery: 0.3.1. Conformance: [C-patterns](conformance.md#c-patterns).

## RESOLUTION-05

Initial user resolution uses authorized complete destructuring of structs without a custom destructor. Classes/pinned in-place resolution remains deferred.

Agreed constraint. Owning task: [#223](https://github.com/StarDragonStudios/sol-lang/issues/223); planned delivery: 0.3.1. Conformance: [C-patterns](conformance.md#c-patterns).

## RESOLUTION-06

Results may stay pending in a collection and be processed incrementally. Stop early by retaining/transferring the remainder; do not force full traversal at every use.

Agreed constraint. Owning task: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228); planned delivery: 0.3.1. Conformance: [C-incremental](conformance.md#c-incremental).

## RESOLUTION-07

Finalization consumes the collection and exclusively borrows a reusable void action. Sequential traversal; no retained callback or hidden parallelism. Zero elements means zero calls, not capture destruction.

Agreed constraint. Owning task: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228); planned delivery: 0.3.1. Conformance: [C-consumer](conformance.md#c-consumer).

## RESOLUTION-08

Handlers may resolve or transfer each owned argument but cannot leave a pending return ignored. A void action cannot magically propagate an error through its caller; use an explicit destination or a different fallible API.

Agreed constraint. Owning task: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228); planned delivery: 0.3.1. Conformance: [C-consumer](conformance.md#c-consumer).

## RESOLUTION-09

Checked empty finalization frees only empty storage and returns an intact nonempty collection on failure. Static ownership plus runtime empty proof is allowed. Merely resetting length cannot erase stored obligations.

Agreed constraint. Owning task: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228); planned delivery: 0.3.1. Conformance: [C-empty-proof](conformance.md#c-empty-proof).

## RESOLUTION-10

The extraction/finalization trusted base and named-action bridge before general lambdas require reviewed implementation contracts; no arbitrary-loop correctness claim.

Agreed constraint. Owning task: [#227](https://github.com/StarDragonStudios/sol-lang/issues/227); planned delivery: 0.3.1. Conformance: [C-empty-proof](conformance.md#c-empty-proof).
