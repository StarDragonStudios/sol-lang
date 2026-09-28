# Optional, result and control flow

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## RESULTS-01

`optional<T>` is one non-allocating-by-requirement value family, not a separate Optional object wrapper. some/none; safe owners/borrows are nonnull.

Agreed constraint. Owning task: [#216](https://github.com/StarDragonStudios/sol-lang/issues/216); planned delivery: 0.3.0. Conformance: [C-alternatives](conformance.md#c-alternatives).

## RESULTS-02

`result<T,E>` is success/error, no required heap and always noncopyable. ok()/`result<void,E>` is an explicit no-payload case needing compiler support.

Agreed constraint. Owning task: [#216](https://github.com/StarDragonStudios/sol-lang/issues/216); planned delivery: 0.3.0. Conformance: [C-alternatives](conformance.md#c-alternatives).

## RESULTS-03

Results cannot be ignored, overwritten, generically dropped or lost on normal return, @exit, break/continue or scope exit. Borrowed inspection does not discharge. Transfer returns responsibility.

Agreed constraint. Owning task: [#218](https://github.com/StarDragonStudios/sol-lang/issues/218); planned delivery: 0.3.0. Conformance: [C-pending](conformance.md#c-pending).

## RESULTS-04

Exhaustive match consumes owned result payloads or inspects borrowed ones. Nested results are not flattened or automatically resolved.

Agreed constraint. Owning task: [#217](https://github.com/StarDragonStudios/sol-lang/issues/217); planned delivery: 0.3.0. Conformance: [C-match](conformance.md#c-match).

## RESULTS-05

case pattern => expression is equivalent to case pattern => begin return expression end.

Agreed constraint. Owning task: [#217](https://github.com/StarDragonStudios/sol-lang/issues/217); planned delivery: 0.3.0. Conformance: [C-match](conformance.md#c-match).

## RESULTS-06

A return in a match-expression branch supplies its value; @exit(enclosing_function) return targets the named containing function. => @exit(name) expression annotates the implicit return. No exit across lambda boundaries. Generic begin/end alone adds no return context.

Agreed constraint. Owning task: [#217](https://github.com/StarDragonStudios/sol-lang/issues/217); planned delivery: 0.3.0. Conformance: [C-exit](conformance.md#c-exit).

## RESULTS-07

A match expression's live branches must yield compatible types. err(E) is not implicitly a T. Early-exit branches need not initialize the expression destination.

Agreed constraint. Owning task: [#217](https://github.com/StarDragonStudios/sol-lang/issues/217); planned delivery: 0.3.0. Conformance: [C-branch-types](conformance.md#c-branch-types).

## RESULTS-08

Complete struct patterns use { field, other as local_name }, by name rather than position; all fields are accounted for initially. Respect visibility, no classes/pinned direct values/custom-destructor structs in the initial consuming destructure mechanism.

Agreed constraint. Owning task: [#223](https://github.com/StarDragonStudios/sol-lang/issues/223); planned delivery: 0.3.1. Conformance: [C-patterns](conformance.md#c-patterns).

## RESULTS-09

Error policies remain explicit: propagation via result; discard that removes success but returns error; discard_or_fail that invokes fatal policy; or a report-and-continue handler. Names/signatures other than those explicitly fixed remain provisional.

Provisional API; semantic constraints retained. Owning task: [#227](https://github.com/StarDragonStudios/sol-lang/issues/227); planned delivery: 0.3.1. Conformance: [C-error-policy](conformance.md#c-error-policy).

## RESULTS-10

Do not assign universal inherent error severity. Reporting is an application decision; compiler cannot prove useful recovery.

Agreed constraint. Owning task: [#227](https://github.com/StarDragonStudios/sol-lang/issues/227); planned delivery: 0.3.1. Conformance: [C-error-policy](conformance.md#c-error-policy).

## RESULTS-11

New resource factories return result when reasons matter. If rollback itself fails recoverably, retain original error as primary and attach cleanup failure. Fatal cleanup invokes environment fatal policy. Uncertain external completion needs a distinct reconciliation protocol, not a false retry guarantee.

Agreed constraint. Owning task: [#224](https://github.com/StarDragonStudios/sol-lang/issues/224); planned delivery: 0.3.1. Conformance: [C-rollback](conformance.md#c-rollback).
