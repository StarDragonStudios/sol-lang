# Sol 0.3.x safety and callable specification

Status: **proposed normative source contract, pending review in #207**.
This document specifies intended behavior; it does not announce implemented
features, a released version, or a memory-safety guarantee for Sol 0.2 programs.

Source of decisions: [agreed baseline #207](https://github.com/StarDragonStudios/sol-lang/issues/207).
Delivery program: [#201](https://github.com/StarDragonStudios/sol-lang/issues/201).
Project: [Sol Roadmap](https://github.com/users/StarDragonStudios/projects/1).

## Interpretation and compatibility

The declarative rules in these chapters are requirements on future conforming
implementations of their stated scope, not descriptions of today's compiler.
“Provisional”, “proposed”, and “design gate” qualify APIs that still require
review. A spelling in such a clause must not be treated as a finalized callable
signature, annotation grammar, allocation representation, or binary ABI.

The [Sol 0.2 specification](sol-0.2.md) and [0.1 specification](sol-0.1.md)
remain unchanged. In particular, 0.2 copyable structs and raw new/delete are not
retroactively assigned safe ownership semantics. The experimental mode and
legacy interoperability boundary must be designed in
[#208](https://github.com/StarDragonStudios/sol-lang/issues/208) and enforced in
[#209](https://github.com/StarDragonStudios/sol-lang/issues/209). Concrete option
spellings and the initial interoperability policy are isolated in the
[compatibility proposal](sol-0.3/compatibility.md) for explicit #208 review.

The immutable 0.1.1 bootstrap seed remains the trust root. The Sol-written
compiler need not use new source features as soon as it can compile them.
Changing the input seed requires a separate reviewed promotion with reproducible
evidence; changing the output version does not promote the seed.

Safety is required within the supported safe-language subset. Unsupported
constructs must be rejected explicitly, not accepted with silently omitted
ownership, borrowing, pinning or resolution checks. @unsafe marks a controlled
boundary with obligations; it is not a universal exemption from known loans,
pending results, pinning or resource dependencies.

## Chapters

| Chapter | Subject |
| --- | --- |
| [Syntax](sol-0.3/syntax.md) | Blocks, annotations, names and member access |
| [Ownership](sol-0.3/ownership.md) | Moves, copying, initialization and destruction |
| [Borrows](sol-0.3/borrows.md) | Shared/exclusive access, origins and dependencies |
| [Results](sol-0.3/results.md) | Alternatives, obligations, match and directed exits |
| [Resolution](sol-0.3/resolution.md) | User protocols and incremental collections |
| [Allocation](sol-0.3/allocation.md) | Exclusive ownership, allocators, raw memory and views |
| [Pinning](sol-0.3/pinning.md) | Permanent location guarantees and shared ownership |
| [Callables](sol-0.3/callables.md) | Captures, invocation capabilities, erasure and variadics |
| [Concurrency](sol-0.3/concurrency.md) | Scoped work, synchronization, cancellation and channels |

Each of the 111 numbered clauses corresponds to one baseline clause in #207.
Compound clauses intentionally keep their original context. Each links to an
owning issue and a [conformance family](sol-0.3/conformance.md); every individual
requirement within a compound clause needs a witness, not merely one test per
heading. Stable IDs must not be reused for a different rule.

The [decision register](sol-0.3/decisions.md) records superseded alternatives and
open gates. [Complete examples](sol-0.3/examples.md) distinguish accepted and
rejected programs under this proposed contract.

The [experimental-mode compatibility proposal](sol-0.3/compatibility.md)
addresses #208: explicit CLI selection, whole-graph propagation, legacy
preservation, bootstrap protocol, metadata and mixed-mode rejection. It requires
review before #209 implementation and does not enable new CLI options by itself.

## Delivery and availability

| Version | Cumulative scope | Tracker | Stabilization / release |
| --- | --- | --- | --- |
| 0.3.0 | Ownership, local borrowing, cleanup, raw boundary, optional/result and match | [#202](https://github.com/StarDragonStudios/sol-lang/issues/202) | [#252](https://github.com/StarDragonStudios/sol-lang/issues/252) / [#253](https://github.com/StarDragonStudios/sol-lang/issues/253) |
| 0.3.1 | Stored lifetimes, resolution protocols, allocator dependencies and collection views | [#203](https://github.com/StarDragonStudios/sol-lang/issues/203) | [#254](https://github.com/StarDragonStudios/sol-lang/issues/254) / [#255](https://github.com/StarDragonStudios/sol-lang/issues/255) |
| 0.3.2 | Pinning, shared/weak ownership and atomic-count qualifiers | [#204](https://github.com/StarDragonStudios/sol-lang/issues/204) | [#256](https://github.com/StarDragonStudios/sol-lang/issues/256) / [#257](https://github.com/StarDragonStudios/sol-lang/issues/257) |
| 0.3.3 | First-class functions, explicit captures, callable ownership and variadics | [#205](https://github.com/StarDragonStudios/sol-lang/issues/205) | [#258](https://github.com/StarDragonStudios/sol-lang/issues/258) / [#259](https://github.com/StarDragonStudios/sol-lang/issues/259) |
| 0.3.4 | Structured threads, mutexes, cooperative cancellation and bounded MPSC channels | [#206](https://github.com/StarDragonStudios/sol-lang/issues/206) | [#260](https://github.com/StarDragonStudios/sol-lang/issues/260) / [#261](https://github.com/StarDragonStudios/sol-lang/issues/261) |

A chapter can span deliveries: the issue linked from the clause owns the
implementation, while cross-cutting limitations apply from the first affected
feature. A later clause cannot be pulled into an earlier release merely because
it appears in the same chapter. In particular, arbitrary stored/returned lifetime
contracts are not implied by 0.3.0 local borrowing.

Each delivery progresses through alpha.N, beta.N, rc.N and final, with no fixed
number of candidates or invented dates. Alpha declares its tested subset; beta
requires that delivery's scope complete; RC/final require its release gate.
Using 0.3.x for additive increments is an explicit pre-1.0 development policy,
not a promise of post-1.0 patch compatibility. See
[#251](https://github.com/StarDragonStudios/sol-lang/issues/251).

## Required 0.3.0 vertical slice

The first delivery must demonstrate one end-to-end path through parsing,
semantic analysis, typed IR, native lowering and runtime cleanup:

1. Select the explicitly designed experimental mode without changing legacy
   rebuild semantics (#208–#210).
2. Create initialized move-only ownership, transfer it explicitly, track branch
   and loop initialization, and reject subsequent source use (#211–#212).
3. Form local shared/exclusive loans, account for all derived uses, and reject
   overlapping incompatible access or moving/destroying a borrowed owner (#213).
4. Emit exactly-once deterministic cleanup for initialized ownership on normal
   exits; preserve obligations at raw operations and failure paths (#214–#215).
5. Construct optional/result alternatives, exhaustively match them, and resolve
   or transfer results on every normal exit including @exit (#216–#218).
6. Exercise exclusive allocation/cleanup and deterministic allocation failure
   according to the reviewed factory contract (#219).
7. Validate acceptance, rejection, diagnostics and native effects together,
   including repeated bootstrap and legacy compatibility (#220).

Stored dependency syntax, user-defined resolution, dynamic collection proofs,
pinning, shared ownership, general lambdas and threading are not fallback escape
hatches for this slice. Implementations must reject unsupported uses rather than
infer unsound lifetimes, silently leak unresolved results, or skip cleanup.

## Validation and non-goals

The [conformance plan](sol-0.3/conformance.md) defines future positive/reject,
runtime and design-review witnesses. This documentation PR does not add runnable
0.3 fixtures to the current compiler suite or claim those witnesses pass.
Technical PRs must provide their fixture paths, diagnostic decisions and results.
Release validation must cover all six supported target archives, checksums,
extracted-package smoke tests and bootstrap provenance; a host-only result is
not all-target publication evidence.

There is no mandatory GC or OS scheduler, safe self-reference, stable
cross-compiler ABI, forced cancellation, detached local borrows, general
async/await, arbitrary raw-pointer escape, or automatic deadlock-freedom claim.
Additional pointer families and generalized recoverable constructor failures
remain outside this contract unless a later reviewed decision adds them.
