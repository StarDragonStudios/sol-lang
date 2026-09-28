# Structured concurrency

Status: proposed normative contract for review under [#207](https://github.com/StarDragonStudios/sol-lang/issues/207), **not implemented availability**.

Read the [version and interpretation rules](../sol-0.3.md) first.
Each clause below preserves one agreed baseline clause. Its owning issue defines
implementation scope; linked conformance families are planned, not passing tests.

## CONCURRENCY-01

Distinguish transferable and shareable capabilities; infer structurally, including allocator/thread-affinity constraints. Raw/synchronization exceptions require audited explicit contracts.

Agreed constraint. Owning task: [#243](https://github.com/StarDragonStudios/sol-lang/issues/243); planned delivery: 0.3.4. Conformance: [C-transfer](conformance.md#c-transfer).

## CONCURRENCY-02

Shared borrows cross threads only if content shareable; exclusive borrows require transferable content. Lifetime safety is independent.

Agreed constraint. Owning task: [#243](https://github.com/StarDragonStudios/sol-lang/issues/243); planned delivery: 0.3.4. Conformance: [C-transfer](conformance.md#c-transfer).

## CONCURRENCY-03

Scoped threads cannot outlive scope; all normal exits wait before dependent locals die. Waiting may block indefinitely; no deadlock-freedom/fairness promise.

Agreed constraint. Owning task: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244); planned delivery: 0.3.4. Conformance: [C-scope](conformance.md#c-scope).

## CONCURRENCY-04

Handles are noncopyable, movable only within creator thread/scope. Discarding ordinary handle does not detach/cancel/release loans.

Agreed constraint. Owning task: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244); planned delivery: 0.3.4. Conformance: [C-scope](conformance.md#c-scope).

## CONCURRENCY-05

Creation success accepts work once; failure returns intact never-started work for all captures. Failures after execution begins are not recoverable creation failure.

Agreed constraint. Owning task: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244); planned delivery: 0.3.4. Conformance: [C-launch](conformance.md#c-launch).

## CONCURRENCY-06

Start publishes prior initialization; join observes completed legal writes. Results can retain dependencies; creator does not regain conflicting access until completion is known.

Agreed constraint. Owning task: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244); planned delivery: 0.3.4. Conformance: [C-join](conformance.md#c-join).

## CONCURRENCY-07

Uncollected discardable results destroyed in creator during automatic wait. Pending results require explicit collection; wait alone is not handling.

Agreed constraint. Owning task: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244); planned delivery: 0.3.4. Conformance: [C-join-pending](conformance.md#c-join-pending).

## CONCURRENCY-08

mutex owns T; fallible explicit creation, no required global allocator. lock/try_lock yield move-only nontransferable guards; non-reentrant.

Agreed constraint. Owning task: [#245](https://github.com/StarDragonStudios/sol-lang/issues/245); planned delivery: 0.3.4. Conformance: [C-mutex](conformance.md#c-mutex).

## CONCURRENCY-09

mutex outlives guard; guard outlives loans. get_mut with static exclusivity avoids lock; inner(move(mutex)) recovers content with no active uses.

Agreed constraint. Owning task: [#245](https://github.com/StarDragonStudios/sol-lang/issues/245); planned delivery: 0.3.4. Conformance: [C-mutex](conformance.md#c-mutex).

## CONCURRENCY-10

Guard unlock provides synchronization to subsequent acquisition. T need only be transferable for mutex sharing; no content atomic annotation required.

Agreed constraint. Owning task: [#245](https://github.com/StarDragonStudios/sol-lang/issues/245); planned delivery: 0.3.4. Conformance: [C-mutex](conformance.md#c-mutex).

## CONCURRENCY-11

Mutex moves only with no active users and a valid native representation. Fatal failure does not promise unlock/recovery; no automatic ISR suitability.

Agreed constraint. Owning task: [#245](https://github.com/StarDragonStudios/sol-lang/issues/245); planned delivery: 0.3.4. Conformance: [C-mutex](conformance.md#c-mutex).

## CONCURRENCY-12

Cancellation source requests, token observes. Explicit synchronized state, irreversible/idempotent request, source destruction not cancellation, token state remains valid.

Agreed constraint. Owning task: [#246](https://github.com/StarDragonStudios/sol-lang/issues/246); planned delivery: 0.3.4. Conformance: [C-cancel](conformance.md#c-cancel).

## CONCURRENCY-13

Cooperative checks do not rollback effects or interrupt arbitrary I/O/mutex waits. Work reports actual result; cancellation request may race successful completion.

Agreed constraint. Owning task: [#246](https://github.com/StarDragonStudios/sol-lang/issues/246); planned delivery: 0.3.4. Conformance: [C-cancel](conformance.md#c-cancel).

## CONCURRENCY-14

Bounded MPSC channel, per-sender ordering, one receiver, explicit sender duplication. Send either accepts message or returns it intact.

Agreed constraint. Owning task: [#247](https://github.com/StarDragonStudios/sol-lang/issues/247); planned delivery: 0.3.4. Conformance: [C-channel](conformance.md#c-channel).

## CONCURRENCY-15

Close prevents admission, retains queue and wakes blocked senders; last sender closes input. Blocking receive: message/end; nonblocking: message/empty/end.

Agreed constraint. Owning task: [#247](https://github.com/StarDragonStudios/sol-lang/issues/247); planned delivery: 0.3.4. Conformance: [C-channel](conformance.md#c-channel).

## CONCURRENCY-16

Receiver owns obligations of accepted messages. Empty open queue not proof of finalized state. Shutdown closes, drains and frees under explicit handling policy.

Agreed constraint. Owning task: [#248](https://github.com/StarDragonStudios/sol-lang/issues/248); planned delivery: 0.3.4. Conformance: [C-channel-pending](conformance.md#c-channel-pending).

## CONCURRENCY-17

Cancelable send has definite acceptance or canceled-with-message outcome; cancelable receive never both consumes a message and reports cancellation.

Agreed constraint. Owning task: [#249](https://github.com/StarDragonStudios/sol-lang/issues/249); planned delivery: 0.3.4. Conformance: [C-cancel-waits](conformance.md#c-cancel-waits).

## CONCURRENCY-18

Example shutdown: request cancel, close admission, drain accepted messages, handle rejected messages in producers, collect worker results, then return. Joining producers first with an unconsumed full queue can deadlock.

Agreed constraint. Owning task: [#250](https://github.com/StarDragonStudios/sol-lang/issues/250); planned delivery: 0.3.4. Conformance: [C-shutdown](conformance.md#c-shutdown).

## CONCURRENCY-19

Environment capability availability is explicit; no mandatory OS scheduler/heap for all programs.

Agreed constraint. Owning task: [#250](https://github.com/StarDragonStudios/sol-lang/issues/250); planned delivery: 0.3.4. Conformance: [C-environment](conformance.md#c-environment).

## CONCURRENCY-20

Deferred: forced cancellation, detached local borrows, deadlines, general async/await, unbounded/MPMC/broadcast channels, mandatory GC, safe self-reference.

Agreed constraint. Owning task: [#250](https://github.com/StarDragonStudios/sol-lang/issues/250); planned delivery: 0.3.4. Conformance: [C-deferred](conformance.md#c-deferred).
