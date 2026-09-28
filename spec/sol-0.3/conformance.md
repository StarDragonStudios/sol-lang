# Sol 0.3.x conformance plan

This is a specification test plan, not an executable compiler catalog. No test
here is claimed to pass on Sol 0.2 or the immutable 0.1.1 seed. Owning technical
PRs must add candidate-only fixtures and link their paths back to these stable
IDs. Never feed new syntax into the seed comparison catalog as if it were legacy.

Each C-* family requires separate positive and negative witnesses, subdivided
for every linked clause (including each semicolon-separated requirement).
Parser/semantic rejection must identify the offending source span and leave no
executable; exact diagnostic codes are assigned by the implementation PR, not
invented here. Native cases assert observable events/outputs; failure injection
must be deterministic. Fatal cases run in child processes. Unsafe UB is reviewed
as a contract/proof obligation, never intentionally executed. Design/scope rules
use specification/metadata checks rather than fabricated runtime tests.

The [examples](examples.md) provide full-function starting witnesses for the
first delivery; they are not compiled by existing CI. Cross-target execution and
packaging belong to the version's stabilization/release gates.

## C-syntax

Rules: [SYNTAX-01](syntax.md#syntax-01), [SYNTAX-02](syntax.md#syntax-02), [SYNTAX-03](syntax.md#syntax-03), [SYNTAX-04](syntax.md#syntax-04).

Owners: [#210](https://github.com/StarDragonStudios/sol-lang/issues/210), [#211](https://github.com/StarDragonStudios/sol-lang/issues/211), [#215](https://github.com/StarDragonStudios/sol-lang/issues/215).

- **Positive witness:** Parse compact and multiline annotations, typed bindings and complete fn/struct bodies; dot on values/borrows and arrow on pointers.
- **Negative/boundary witness:** Reject missing ordinary let types, stray begin in declaration headers, unary * or &, and pointer/member-operator misuse.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-naming

Rules: [SYNTAX-05](syntax.md#syntax-05), [SYNTAX-06](syntax.md#syntax-06).

Owners: [#236](https://github.com/StarDragonStudios/sol-lang/issues/236), [#228](https://github.com/StarDragonStudios/sol-lang/issues/228).

- **Positive witness:** Review Function/Action spellings, linked List versus contiguous vector, and lower_snake_case non-object names against the agreed glossary.
- **Negative/boundary witness:** Reject an implementation/spec change that silently renames the approved callable aliases or adds unrelated collection release gates.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-moves

Rules: [OWNERSHIP-01](ownership.md#ownership-01).

Owners: [#212](https://github.com/StarDragonStudios/sol-lang/issues/212).

- **Positive witness:** Transfer a value and use the destination; reinitialize a moved @mut binding before its next use.
- **Negative/boundary witness:** Reject source use or a second move before reinitialization.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-mutability

Rules: [OWNERSHIP-02](ownership.md#ownership-02).

Owners: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211).

- **Positive witness:** Rebind an @mut variable and obtain a valid exclusive loan without requiring rebinding.
- **Negative/boundary witness:** Reject rebinding without @mut; do not reject an otherwise valid mut_borrow merely because @mut is absent.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-copy

Rules: [OWNERSHIP-03](ownership.md#ownership-03).

Owners: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211).

- **Positive witness:** Copy primitives, shared loans and a valid @copy aggregate without invoking duplication.
- **Negative/boundary witness:** Reject implicit copies of move-only structs and @copy with a noncopyable field or destructor.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-class-identity

Rules: [OWNERSHIP-04](ownership.md#ownership-04).

Owners: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211).

- **Positive witness:** Pass identity-preserving object views without moving or copying the instance.
- **Negative/boundary witness:** Reject whole-instance copy, move or replacement through a base view.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-flow

Rules: [OWNERSHIP-05](ownership.md#ownership-05).

Owners: [#212](https://github.com/StarDragonStudios/sol-lang/issues/212).

- **Positive witness:** Exercise move/reinitialization on both branches, loop backedges and all normal exits.
- **Negative/boundary witness:** Reject a use reachable from an uninitialized/moved path and partial field extraction leaving an invalid aggregate.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-replacement

Rules: [OWNERSHIP-06](ownership.md#ownership-06).

Owners: [#214](https://github.com/StarDragonStudios/sol-lang/issues/214).

- **Positive witness:** Record replacement preparation before old-value destruction; observe take emptying, replace returning old content, and store destroying it.
- **Negative/boundary witness:** Reject replacement that loses pending obligations or uses a conflicting loan; ensure preparation failure does not destroy the old value prematurely.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-cleanup

Rules: [OWNERSHIP-07](ownership.md#ownership-07).

Owners: [#214](https://github.com/StarDragonStudios/sol-lang/issues/214).

- **Positive witness:** Use a native event log to assert reverse local order and destructor-body, reverse own-field, then base order; moved-out locals are skipped.
- **Negative/boundary witness:** Reject duplicate/parameterized/non-void/directly called destructors; detect missing or double cleanup on exits.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-destructor

Rules: [OWNERSHIP-08](ownership.md#ownership-08).

Owners: [#214](https://github.com/StarDragonStudios/sol-lang/issues/214).

- **Positive witness:** Borrow live fields while the destructor runs; perform fallible close separately before destruction.
- **Negative/boundary witness:** Reject owned-field extraction and propagation of recoverable errors from a destructor.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-drop

Rules: [OWNERSHIP-09](ownership.md#ownership-09).

Owners: [#214](https://github.com/StarDragonStudios/sol-lang/issues/214).

- **Positive witness:** Early-drop transferable discardable ownership exactly once, then end scope; run fatal-policy fixtures in child processes.
- **Negative/boundary witness:** Reject a second use/drop and generic dropping of pending results; do not require cleanup after fatal termination.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-raw

Rules: [OWNERSHIP-10](ownership.md#ownership-10), [ALLOCATION-05](allocation.md#allocation-05), [ALLOCATION-06](allocation.md#allocation-06), [ALLOCATION-07](allocation.md#allocation-07).

Owners: [#215](https://github.com/StarDragonStudios/sol-lang/issues/215).

- **Positive witness:** Within @unsafe, exercise valid mutable/readonly conversions, nullable raw addresses and operations on live aligned initialized storage.
- **Negative/boundary witness:** Reject const writes, unsafe operations outside the boundary, implicit raw ownership, and treating ref/mut_ref as new types; never execute UB to test rejection.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-loans

Rules: [BORROWS-01](borrows.md#borrows-01), [BORROWS-02](borrows.md#borrows-02).

Owners: [#211](https://github.com/StarDragonStudios/sol-lang/issues/211), [#213](https://github.com/StarDragonStudios/sol-lang/issues/213).

- **Positive witness:** Copy shared loans independently of pointee copyability; resume owner access after all derived loans end.
- **Negative/boundary witness:** Reject null safe loans, copying exclusive loans, alias conflicts and owner move/drop while a loan is live.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-reborrow

Rules: [BORROWS-03](borrows.md#borrows-03).

Owners: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213).

- **Positive witness:** Suspend the source exclusive loan during a nested loan and resume it after the nested loan ends.
- **Negative/boundary witness:** Reject incompatible use of the suspended source, including through returned/stored derived loans.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-arguments

Rules: [BORROWS-04](borrows.md#borrows-04).

Owners: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213).

- **Positive witness:** Explicitly reborrow or move exclusive arguments; autoborrow a compatible method receiver.
- **Negative/boundary witness:** Reject implicit exclusive-loan duplication or receiver automove.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-disjoint

Rules: [BORROWS-05](borrows.md#borrows-05).

Owners: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213).

- **Positive witness:** Borrow independently proven distinct direct fields.
- **Negative/boundary witness:** Reject assumed disjointness for arbitrary indices or raw addresses without a proof.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-origins

Rules: [BORROWS-06](borrows.md#borrows-06), [BORROWS-07](borrows.md#borrows-07).

Owners: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221).

- **Positive witness:** Propagate declared field/return origins and supported single-origin inference including this; exercise named multi-origin mappings.
- **Negative/boundary witness:** Reject ambiguous missing origins and a returned/stored loan outliving any required owner; common duration is not owner identity.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-dependencies

Rules: [BORROWS-08](borrows.md#borrows-08).

Owners: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221).

- **Positive witness:** Move and generic-substitute a value while preserving its allocator/resource dependencies.
- **Negative/boundary witness:** Reject escape beyond a depended-on allocator/resource and substitution that erases dependencies.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-stored-drop

Rules: [BORROWS-09](borrows.md#borrows-09).

Owners: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221).

- **Positive witness:** Keep a stored loan live through the containing destructor, then release it.
- **Negative/boundary witness:** Reject owner destruction before the container/destructor's final dependent use.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-self-reference

Rules: [BORROWS-10](borrows.md#borrows-10).

Owners: [#221](https://github.com/StarDragonStudios/sol-lang/issues/221).

- **Positive witness:** Use independent owners with externally valid loans.
- **Negative/boundary witness:** Reject a safe self-referential aggregate, including a pinned attempt; pinning alone is no proof.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-receivers

Rules: [BORROWS-11](borrows.md#borrows-11), [BORROWS-12](borrows.md#borrows-12).

Owners: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213).

- **Positive witness:** Infer compatible implemented receiver access and preserve explicit abstract/interface access through overrides.
- **Negative/boundary witness:** Reject missing bodyless access contracts, stronger exclusive overrides of shared access, and whole-object replacement via a base receiver.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-places

Rules: [BORROWS-13](borrows.md#borrows-13).

Owners: [#213](https://github.com/StarDragonStudios/sol-lang/issues/213).

- **Positive witness:** Copy-load a copyable value and store/replace through a valid exclusive place.
- **Negative/boundary witness:** Reject copy-load of move-only content and mutation through shared/conflicting access.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-alternatives

Rules: [RESULTS-01](results.md#results-01), [RESULTS-02](results.md#results-02).

Owners: [#216](https://github.com/StarDragonStudios/sol-lang/issues/216).

- **Positive witness:** Construct and consume some/none and ok/err, including result<void,E> with ok(); inspect representation for absence of mandatory allocation.
- **Negative/boundary witness:** Reject result copies, raw null used as a safe owner/borrow, and invalid alternative payloads.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-pending

Rules: [RESULTS-03](results.md#results-03).

Owners: [#218](https://github.com/StarDragonStudios/sol-lang/issues/218).

- **Positive witness:** Resolve or transfer every result on branches, loops, break/continue, ordinary return and directed exit.
- **Negative/boundary witness:** Reject ignored/overwritten/dropped results, borrowed inspection presented as resolution, and abandoned nested obligations.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-match

Rules: [RESULTS-04](results.md#results-04), [RESULTS-05](results.md#results-05).

Owners: [#217](https://github.com/StarDragonStudios/sol-lang/issues/217).

- **Positive witness:** Consume owned or inspect borrowed alternatives exhaustively; compare short-arm and explicit branch-return behavior.
- **Negative/boundary witness:** Reject nonexhaustive cases and treating nested results as automatically resolved.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-exit

Rules: [RESULTS-06](results.md#results-06).

Owners: [#217](https://github.com/StarDragonStudios/sol-lang/issues/217).

- **Positive witness:** Run complete functions with a branch-local return, explicit @exit(function) return, and short-arm @exit(function); generic blocks add no context.
- **Negative/boundary witness:** Reject an unknown target or crossing a lambda boundary, and an exit that abandons another pending value.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-branch-types

Rules: [RESULTS-07](results.md#results-07).

Owners: [#217](https://github.com/StarDragonStudios/sol-lang/issues/217).

- **Positive witness:** Unify live arm values, excluding arms that exit the containing function.
- **Negative/boundary witness:** Reject err(E) as an arbitrary T, incompatible live-arm types and a continuing arm with no required value.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-patterns

Rules: [RESULTS-08](results.md#results-08), [RESOLUTION-04](resolution.md#resolution-04), [RESOLUTION-05](resolution.md#resolution-05).

Owners: [#223](https://github.com/StarDragonStudios/sol-lang/issues/223), [#222](https://github.com/StarDragonStudios/sol-lang/issues/222).

- **Positive witness:** Completely destructure named fields, optionally rename with as, and preserve transferred field obligations.
- **Negative/boundary witness:** Reject missing/inaccessible fields, positional renaming, unsupported class/pinned/destructor destructuring, and external bypass of explicit resolution obligations.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-error-policy

Rules: [RESULTS-09](results.md#results-09), [RESULTS-10](results.md#results-10).

Owners: [#227](https://github.com/StarDragonStudios/sol-lang/issues/227).

- **Positive witness:** Review explicit propagation, report-and-continue and fatal/discard policy witnesses against the selected API design before executable fixtures are added.
- **Negative/boundary witness:** Reject a claimed universal severity or automatic caller propagation from a void handler; no provisional spelling is an approved API.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-rollback

Rules: [RESULTS-11](results.md#results-11).

Owners: [#224](https://github.com/StarDragonStudios/sol-lang/issues/224).

- **Positive witness:** Inject primary factory failure and secondary recoverable cleanup failure; retain the primary and attach cleanup information.
- **Negative/boundary witness:** Reject loss of the primary error or a retry guarantee after uncertain external completion; fatal cleanup must not return normally.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-resolution

Rules: [RESOLUTION-01](resolution.md#resolution-01), [RESOLUTION-03](resolution.md#resolution-03).

Owners: [#222](https://github.com/StarDragonStudios/sol-lang/issues/222).

- **Positive witness:** Resolve through an authorized defining-module operation and verify all owned parameter obligations on every exit.
- **Negative/boundary witness:** Reject reading/moving as discharge, automatic discharge at entry, or unauthorized external resolution.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-container-pending

Rules: [RESOLUTION-02](resolution.md#resolution-02).

Owners: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228).

- **Positive witness:** Store and transfer pending content without erasing its obligation.
- **Negative/boundary witness:** Reject sharing ownership of pending content and dropping/clearing a container with unresolved contents.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-incremental

Rules: [RESOLUTION-06](resolution.md#resolution-06).

Owners: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228).

- **Positive witness:** Process only selected elements and retain or transfer the remainder for later handling.
- **Negative/boundary witness:** Reject abandoning the remainder; do not impose complete traversal merely to access one element.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-consumer

Rules: [RESOLUTION-07](resolution.md#resolution-07), [RESOLUTION-08](resolution.md#resolution-08).

Owners: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228).

- **Positive witness:** Use a reusable exclusively borrowed void action sequentially; an empty collection invokes it zero times and leaves captures intact.
- **Negative/boundary witness:** Reject retained/parallel invocation, ignored pending handler results and consumed captures in a supposedly reusable handler.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-empty-proof

Rules: [RESOLUTION-09](resolution.md#resolution-09), [RESOLUTION-10](resolution.md#resolution-10).

Owners: [#228](https://github.com/StarDragonStudios/sol-lang/issues/228), [#227](https://github.com/StarDragonStudios/sol-lang/issues/227).

- **Positive witness:** Finalize proven-empty storage and return intact nonempty storage on failed checked finalization; review trusted extraction proofs.
- **Negative/boundary witness:** Reject resetting length as resolution, loss/reordering of pending contents on failure, or unaudited proof primitives.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-owner

Rules: [ALLOCATION-01](allocation.md#allocation-01).

Owners: [#219](https://github.com/StarDragonStudios/sol-lang/issues/219).

- **Positive witness:** Move an exclusive nonnull initialized allocation handle, borrow its stable pointee, then destroy once.
- **Negative/boundary witness:** Reject null/uninitialized safe ownership, duplicated ownership and relocation of the pointee solely because the handle moved.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-allocation-failure

Rules: [ALLOCATION-02](allocation.md#allocation-02).

Owners: [#219](https://github.com/StarDragonStudios/sol-lang/issues/219).

- **Positive witness:** Inject ordinary new failure after left-to-right argument evaluation and verify consumed moves remain consumed, following the reviewed optional-owner migration contract.
- **Negative/boundary witness:** Reject silently restoring moved arguments or treating legacy raw new as the safe API; exact syntax remains gated by #208/#219.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-allocator

Rules: [ALLOCATION-03](allocation.md#allocation-03), [ALLOCATION-04](allocation.md#allocation-04).

Owners: [#224](https://github.com/StarDragonStudios/sol-lang/issues/224).

- **Positive witness:** Select a live allocator instance and preserve destruction despite deferred arena/pool reclamation.
- **Negative/boundary witness:** Reject reset while dependents remain, allocator-kind enum substitution and a mandatory global allocator requirement.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-raw-escape

Rules: [ALLOCATION-08](allocation.md#allocation-08).

Owners: [#215](https://github.com/StarDragonStudios/sol-lang/issues/215).

- **Positive witness:** Create and use raw-derived pointers/loans/views entirely within the unsafe block; (null,0) is empty.
- **Negative/boundary witness:** Reject their initial-model escape; invalid positive-null, alignment, liveness or negative-range fixtures are contract/proof cases, not executed UB.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-slices

Rules: [ALLOCATION-09](allocation.md#allocation-09), [ALLOCATION-10](allocation.md#allocation-10).

Owners: [#225](https://github.com/StarDragonStudios/sol-lang/issues/225).

- **Positive witness:** Use checked optional get/subslice and disjoint split views; bounds-failure [] runs in a child process.
- **Negative/boundary witness:** Reject exclusive-view copying, mutation invalidating live views even without realloc, and assumed direct-content pinning.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-borrow-cell

Rules: [ALLOCATION-11](allocation.md#allocation-11).

Owners: [#226](https://github.com/StarDragonStudios/sol-lang/issues/226).

- **Positive witness:** Acquire valid dynamic shared/exclusive guards and observe failed conflicting try operations.
- **Negative/boundary witness:** Reject guard/cell lifetime escape and claims of implicit cross-thread safety.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-pin

Rules: [PINNING-01](pinning.md#pinning-01), [PINNING-02](pinning.md#pinning-02).

Owners: [#230](https://github.com/StarDragonStudios/sol-lang/issues/230).

- **Positive witness:** Pin only after existing loans end, move its owner handle without relocating the pointee, and destroy in place.
- **Negative/boundary witness:** Reject safe unpin and whole-pointee copy/move/extraction/replacement.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-pin-init

Rules: [PINNING-03](pinning.md#pinning-03).

Owners: [#230](https://github.com/StarDragonStudios/sol-lang/issues/230).

- **Positive witness:** Construct in final storage and activate pin after successful construction.
- **Negative/boundary witness:** Reject publication of pinned loans to unfinished direct construction storage.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-projection

Rules: [PINNING-04](pinning.md#pinning-04), [PINNING-05](pinning.md#pinning-05).

Owners: [#231](https://github.com/StarDragonStudios/sol-lang/issues/231).

- **Positive witness:** Project declared pinned direct fields, preserving intermediate aggregates, while mutating ordinary siblings.
- **Negative/boundary witness:** Reject moving pinned projections/intermediates and a pinned direct-storage return without a pinned origin.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-pin-drop

Rules: [PINNING-06](pinning.md#pinning-06).

Owners: [#231](https://github.com/StarDragonStudios/sol-lang/issues/231).

- **Positive witness:** Observe in-place destruction and @mut fresh construction after drop without reviving old loans.
- **Negative/boundary witness:** Reject destructor movement of structurally pinned fields even when the particular instance was not pinned.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-pin-container

Rules: [PINNING-07](pinning.md#pinning-07).

Owners: [#231](https://github.com/StarDragonStudios/sol-lang/issues/231).

- **Positive witness:** Move handles to independently pinned allocations inside a container.
- **Negative/boundary witness:** Reject assuming pin(vector/borrow_cell) pins contents or admitting initially unsupported directly pinned contiguous elements.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-shared

Rules: [PINNING-08](pinning.md#pinning-08).

Owners: [#232](https://github.com/StarDragonStudios/sol-lang/issues/232).

- **Positive witness:** Explicitly duplicate/downgrade/upgrade handles; last strong destroys concrete content and last weak frees control storage.
- **Negative/boundary witness:** Reject implicit copies, pending contents and an automatic cycle-collection guarantee.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-shared-failure

Rules: [PINNING-09](pinning.md#pinning-09).

Owners: [#232](https://github.com/StarDragonStudios/sol-lang/issues/232).

- **Positive witness:** Convert unique to shared without pointee relocation; inject control-block failure and observe destruction of discardable input.
- **Negative/boundary witness:** Reject a guarantee that failed conversion restores the input or permission to destroy pending input.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-atomic

Rules: [PINNING-10](pinning.md#pinning-10).

Owners: [#233](https://github.com/StarDragonStudios/sol-lang/issues/233).

- **Positive witness:** Use atomic counting without changing pointee capabilities.
- **Negative/boundary witness:** Reject implicit atomic/non-atomic conversion and equating atomic counters with safe shared mutable contents.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-qualifiers

Rules: [PINNING-11](pinning.md#pinning-11), [PINNING-12](pinning.md#pinning-12).

Owners: [#234](https://github.com/StarDragonStudios/sol-lang/issues/234).

- **Positive witness:** Preserve pin, concrete destruction and allocator identity through sharing/upcasts and annotated generic arguments; optional forwards payload qualifiers.
- **Negative/boundary witness:** Reject qualifier-erasing substitution, mutable covariance, implicit container transparency and qualifier-only overload distinctions.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-pin-raw

Rules: [PINNING-13](pinning.md#pinning-13).

Owners: [#235](https://github.com/StarDragonStudios/sol-lang/issues/235).

- **Positive witness:** Preserve known pin provenance through unsafe conversions and explicitly review obligations for external origins.
- **Negative/boundary witness:** Reject treating @unsafe or mut_ref as permission to cancel a known pin.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-signature

Rules: [CALLABLES-01](callables.md#callables-01).

Owners: [#237](https://github.com/StarDragonStudios/sol-lang/issues/237).

- **Positive witness:** Pass/store/return Function values and Action aliases with [] for no arguments; parse expression and begin/end lambdas.
- **Negative/boundary witness:** Reject incompatible signatures and obsolete block forms.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-capture

Rules: [CALLABLES-02](callables.md#callables-02), [CALLABLES-03](callables.md#callables-03).

Owners: [#237](https://github.com/StarDragonStudios/sol-lang/issues/237), [#238](https://github.com/StarDragonStudios/sol-lang/issues/238).

- **Positive witness:** Evaluate explicit capture initializers once, left-to-right, in the outer scope, then expose aliases only in the lambda.
- **Negative/boundary witness:** Reject implicit local capture, unavailable aliases and illegal copy/move/loan captures.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-capability

Rules: [CALLABLES-04](callables.md#callables-04), [CALLABLES-05](callables.md#callables-05), [CALLABLES-06](callables.md#callables-06).

Owners: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238).

- **Positive witness:** Exercise shared/exclusive reusable calls and consuming move(work)(); test a move capture that only needs shared invocation and optional/take state transitions.
- **Negative/boundary witness:** Reject consuming a capture repeatedly, ordinary callable copying and interpreting invocation permissions as purity/thread safety.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-callable-pending

Rules: [CALLABLES-07](callables.md#callables-07).

Owners: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238).

- **Positive witness:** Consume a once must-resolve action and trace resolution/transfer of its owned pending capture.
- **Negative/boundary witness:** Reject treating @once or destruction alone as resolution; borrowed capture responsibility stays with its owner.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-capture-lifetime

Rules: [CALLABLES-08](callables.md#callables-08).

Owners: [#238](https://github.com/StarDragonStudios/sol-lang/issues/238).

- **Positive witness:** Carry capture/output dependencies through calls and returned views.
- **Negative/boundary witness:** Reject returning a loan to a capture destroyed by the consuming call.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-identity

Rules: [CALLABLES-09](callables.md#callables-09).

Owners: [#236](https://github.com/StarDragonStudios/sol-lang/issues/236).

- **Positive witness:** Compare repeated instantiations of the same lambda expression/capture types versus distinct expressions with identical sizes.
- **Negative/boundary witness:** Reject using runtime captured values or equal layout as concrete callable identity.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-specialization

Rules: [CALLABLES-10](callables.md#callables-10).

Owners: [#239](https://github.com/StarDragonStudios/sol-lang/issues/239).

- **Positive witness:** Specialize by-value consumers and return one concrete callable representation on all factory branches.
- **Negative/boundary witness:** Reject mixed direct return representations and @mut rebinding that secretly changes layout or allocates.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-views

Rules: [CALLABLES-11](callables.md#callables-11).

Owners: [#239](https://github.com/StarDragonStudios/sol-lang/issues/239).

- **Positive witness:** Invoke compatible concrete implementations through non-owning borrowed callable views, including indirect calls.
- **Negative/boundary witness:** Reject expired capture loans and hidden ownership/allocation in a borrowed view.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-erasure

Rules: [CALLABLES-12](callables.md#callables-12), [CALLABLES-13](callables.md#callables-13).

Owners: [#240](https://github.com/StarDragonStudios/sol-lang/issues/240).

- **Positive witness:** Inject failure in explicit uniform callable ownership: return original never-invoked callable; success preserves destructor, allocator, pin, lifetime and capability metadata.
- **Negative/boundary witness:** Reject lost captures, hidden fallible allocation on direct binding and unsupported concrete recovery/downcasts.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-interfaces

Rules: [CALLABLES-14](callables.md#callables-14).

Owners: [#236](https://github.com/StarDragonStudios/sol-lang/issues/236).

- **Positive witness:** Rebuild modules with compatible contract/layout metadata while keeping capture names non-public.
- **Negative/boundary witness:** Reject incompatible compiled interfaces and an unsupported stable cross-compiler ABI/implicit FFI promise.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-variadic

Rules: [CALLABLES-15](callables.md#callables-15), [CALLABLES-16](callables.md#callables-16).

Owners: [#241](https://github.com/StarDragonStudios/sol-lang/issues/241).

- **Positive witness:** Call with zero/many homogeneous values and one trailing expansion; fixed exact overload wins ordinary calls.
- **Negative/boundary witness:** Reject non-final/multiple variadics, invalid expansion, hidden noncopyable extraction and incorrect overload selection.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-overload

Rules: [CALLABLES-17](callables.md#callables-17).

Owners: [#237](https://github.com/StarDragonStudios/sol-lang/issues/237).

- **Positive witness:** Resolve a named callable overload once using its exact expected signature.
- **Negative/boundary witness:** Reject ambiguity, implicit argument conversions changing selection and per-invocation overload re-resolution.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-transfer

Rules: [CONCURRENCY-01](concurrency.md#concurrency-01), [CONCURRENCY-02](concurrency.md#concurrency-02).

Owners: [#243](https://github.com/StarDragonStudios/sol-lang/issues/243).

- **Positive witness:** Cross a thread boundary with structurally valid transfer/share capabilities including allocator affinity and appropriate loans.
- **Negative/boundary witness:** Reject nonshareable shared loans, nontransferable exclusive content and unaudited raw exceptions.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-scope

Rules: [CONCURRENCY-03](concurrency.md#concurrency-03), [CONCURRENCY-04](concurrency.md#concurrency-04).

Owners: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244).

- **Positive witness:** On all normal exits wait for children before dependent locals die; keep handles in creator thread/scope.
- **Negative/boundary witness:** Reject handle escape/duplication and treating discarded handles as detach/cancel or early loan release.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-launch

Rules: [CONCURRENCY-05](concurrency.md#concurrency-05).

Owners: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244).

- **Positive witness:** Inject every pre-start creation failure and return intact never-started work; success accepts work exactly once.
- **Negative/boundary witness:** Reject recoverable creation failure after execution began or consumed captures on a failed launch.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-join

Rules: [CONCURRENCY-06](concurrency.md#concurrency-06).

Owners: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244).

- **Positive witness:** Observe published initialization and legal completed writes after join while retaining returned-value dependencies.
- **Negative/boundary witness:** Reject conflicting creator access before completion or stripping dependencies from the collected result.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-join-pending

Rules: [CONCURRENCY-07](concurrency.md#concurrency-07).

Owners: [#244](https://github.com/StarDragonStudios/sol-lang/issues/244).

- **Positive witness:** Explicitly collect pending worker results; auto-wait destroys discardable results in the creator.
- **Negative/boundary witness:** Reject auto-wait as resolution of an uncollected pending result.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-mutex

Rules: [CONCURRENCY-08](concurrency.md#concurrency-08), [CONCURRENCY-09](concurrency.md#concurrency-09), [CONCURRENCY-10](concurrency.md#concurrency-10), [CONCURRENCY-11](concurrency.md#concurrency-11).

Owners: [#245](https://github.com/StarDragonStudios/sol-lang/issues/245).

- **Positive witness:** Test lock/try_lock, guard-bound loans, unlock synchronization, exclusive get_mut and inner recovery; content need only be transferable.
- **Negative/boundary witness:** Reject guard transfer/escape, live-user movement, reentrant claims, mandatory global allocation and fatal-unlock/ISR guarantees.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-cancel

Rules: [CONCURRENCY-12](concurrency.md#concurrency-12), [CONCURRENCY-13](concurrency.md#concurrency-13).

Owners: [#246](https://github.com/StarDragonStudios/sol-lang/issues/246).

- **Positive witness:** Observe shared irreversible idempotent request state that survives source destruction; race success with request.
- **Negative/boundary witness:** Reject cancellation-as-rollback, source-destruction cancellation or interruption of arbitrary blocking operations.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-channel

Rules: [CONCURRENCY-14](concurrency.md#concurrency-14), [CONCURRENCY-15](concurrency.md#concurrency-15).

Owners: [#247](https://github.com/StarDragonStudios/sol-lang/issues/247).

- **Positive witness:** Exercise bounded MPSC per-sender order, explicit sender duplication, send acceptance or intact return, close/drain and final-sender closure.
- **Negative/boundary witness:** Reject loss of rejected messages, admission after close and conflating empty with end.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-channel-pending

Rules: [CONCURRENCY-16](concurrency.md#concurrency-16).

Owners: [#248](https://github.com/StarDragonStudios/sol-lang/issues/248).

- **Positive witness:** Track accepted pending messages into receiver ownership; close and handle/drain before finalization.
- **Negative/boundary witness:** Reject destroying queued obligations or finalizing an empty but still-open queue as permanently empty.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-cancel-waits

Rules: [CONCURRENCY-17](concurrency.md#concurrency-17).

Owners: [#249](https://github.com/StarDragonStudios/sol-lang/issues/249).

- **Positive witness:** Race cancellation with send/receive: a definite acceptance or returned-message outcome, and a received message or cancellation.
- **Negative/boundary witness:** Reject dual outcomes, duplicate/lost messages and consuming a message while reporting canceled receive.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-shutdown

Rules: [CONCURRENCY-18](concurrency.md#concurrency-18).

Owners: [#250](https://github.com/StarDragonStudios/sol-lang/issues/250).

- **Positive witness:** Exercise bounded-full queues with request-cancel, close, drain, producer rejection handling and explicit joins.
- **Negative/boundary witness:** Detect a test timeout for join-before-drain deadlock without promising compiler deadlock freedom.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-environment

Rules: [CONCURRENCY-19](concurrency.md#concurrency-19).

Owners: [#250](https://github.com/StarDragonStudios/sol-lang/issues/250).

- **Positive witness:** Document supported capabilities per target and test a minimal environment without a mandatory scheduler/global heap.
- **Negative/boundary witness:** Reject advertising unsupported environment facilities as universally available.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.

## C-deferred

Rules: [CONCURRENCY-20](concurrency.md#concurrency-20).

Owners: [#250](https://github.com/StarDragonStudios/sol-lang/issues/250).

- **Positive witness:** Review scope against the explicit deferred list and each version's release gate.
- **Negative/boundary witness:** Reject silently introducing forced cancellation, detached local loans, general async, new channel families, GC or safe self-reference as implemented guarantees.
- **Evidence:** planned; implementation PR must record fixture paths and results for each linked clause.
