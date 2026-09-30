# Decision register

Status: proposed consolidation for #207. “Agreed” means agreed design, not
implemented behavior. Owning issues below are accountable work items, not
invented human assignees. Changes require a reviewed specification PR and
updated conformance witnesses; do not silently edit historical Sol 0.2 rules.

## Agreed choices and superseded alternatives

| ID | Decision | Superseded or excluded choice | Rules / ownership |
| --- | --- | --- | --- |
| D01 | fn/struct end their own bodies; begin/end is a generic block; annotations may share a line | Mandatory begin on declarations or one annotation per line | [Syntax](syntax.md), #210 |
| D02 | Typed let, lower_snake_case annotations and non-object concepts; retain Function/Action | Untyped ordinary lets or silent renaming of approved aliases | [Syntax](syntax.md), #210/#236 |
| D03 | Dot for values/borrows, arrow only for pointers; named memory operations | Unary * and & or array syntax as the only dereference mechanism | [Syntax](syntax.md), #215 |
| D04 | Explicit move and deterministic ownership; @mut controls rebinding | Implicit duplication or @mut as a method annotation | [Ownership](ownership.md), #211–#214 |
| D05 | borrow and mut_borrow are safe types; ref/mut_ref are unsafe conversion operations | Extra C++-style reference type families | [Borrows](borrows.md), [Allocation](allocation.md), #213/#215 |
| D06 | @lifetime declares origins, @from marks dependent fields/returns, @depends resources | Lifetime diamonds or conflating allocation dependence with borrowed origin | [Borrows](borrows.md), #221 |
| D07 | optional is one absence family; raw null remains explicit | A separate high-level Optional wrapper requirement | [Results](results.md), #216 |
| D08 | Strict result handling, @must_resolve and authorized @resolves operations | Ignoring pending values, @must_consume, or automatic universal error severity | [Results](results.md), [Resolution](resolution.md), #218/#222 |
| D09 | Match arms yield locally; @exit directs a return; complete field patterns allow as | Bare branch return escaping the containing function or positional field matching | [Results](results.md), #217/#223 |
| D10 | Incremental collection processing retains/transfers remainder; explicit finalization | Requiring every access to consume the whole collection or clearing obligations by setting length | [Resolution](resolution.md), #227/#228 |
| D11 | new selects an allocator instance; raw() preserves readonly pointee qualification | new(raw/pool/arena) as built-in enum modes or a separate readonly pointer family | [Allocation](allocation.md), #215/#224 |
| D12 | @pinned is a permanent tracked guarantee; @atomic describes counters | Wrapper proliferation, safe unpin or automatic thread-safe contents | [Pinning](pinning.md), #230–#235 |
| D13 | Explicit captures; invocation capabilities; owned callable erasure is explicit and fallible | Implicit local capture, hidden heap allocation or recovery of concrete erased types | [Callables](callables.md), #236–#240 |
| D14 | Action aliases Function returning void; typed trailing variadics | A handler secretly propagating through its caller or arbitrary heterogeneous expansion | [Callables](callables.md), #241/#242 |
| D15 | Structured work and cooperative cancellation preserve ownership outcomes | Forced termination, detaching local loans, cancellation-as-rollback | [Concurrency](concurrency.md), #243–#250 |

## Open design and implementation gates

G01 now has an [explicit proposal](compatibility.md) in #208. Its CLI,
compatibility and protocol choices remain subject to PR review; the proposal
does not claim #209 is implemented. G03's exact safe factory API remains open.

These questions are deliberately not answered with invented APIs. The owning
issue must record its reviewed decision before dependent implementation merges.

| Gate | Required decision/proof | Owning task | Prerequisites / affected work |
| --- | --- | --- | --- |
| G01 | Experimental mode selection, migration and legacy/safe interoperability; no retroactive 0.2 safety | [#208](https://github.com/StarDragonStudios/sol-lang/issues/208) | #207; blocks #209 and safe-mode implementation |
| G02 | General annotation grammar, target applicability and diagnostics | [#210](https://github.com/StarDragonStudios/sol-lang/issues/210) | #207/#209; parser must reject unsupported combinations |
| G03 | Exact exclusive allocation factory contract and proposed optional-owning new migration | [#219](https://github.com/StarDragonStudios/sol-lang/issues/219) | #214–#216 and reviewed G01; ordinary failure does not restore moves |
| G04 | Lifetime annotation argument/mapping grammar and representation through generics/modules | [#221](https://github.com/StarDragonStudios/sol-lang/issues/221) | Published 0.3.0; retain agreed @lifetime/@from/@depends distinction |
| G05 | Authorized user resolution beyond ordinary complete no-destructor struct decomposition | [#222](https://github.com/StarDragonStudios/sol-lang/issues/222) | #218/#221; class/pinned in-place resolution remains deferred |
| G06 | Allocator/factory signatures, recoverable rollback reporting and allocator provenance representation | [#224](https://github.com/StarDragonStudios/sol-lang/issues/224) | #219/#221; no mandatory global allocator |
| G07 | Trusted extraction/empty proof primitives, named-action bridge and exact error-policy/consumer signatures | [#227](https://github.com/StarDragonStudios/sol-lang/issues/227) | #222/#223; blocks #228; cannot depend on full 0.3.3 lambdas |
| G08 | Native pin projections, control-block layout and atomic operations without weakening source guarantees | [#231](https://github.com/StarDragonStudios/sol-lang/issues/231), [#232](https://github.com/StarDragonStudios/sol-lang/issues/232), [#233](https://github.com/StarDragonStudios/sol-lang/issues/233) | #230 and allocator contracts; pin before sharing, no pending shared contents |
| G09 | Concrete callable specialization and compiled interface encoding, including cross-module availability | [#236](https://github.com/StarDragonStudios/sol-lang/issues/236) | Published 0.3.2; no reliance on successful inlining or stable foreign ABI |
| G10 | Uniform callable owner factory and intact-input failure path distinct from ordinary new | [#240](https://github.com/StarDragonStudios/sol-lang/issues/240) | #224/#239; preserve all lifetime/capability metadata |
| G11 | Synchronization/raw capability exceptions, environment support and native failure/race proof | [#243](https://github.com/StarDragonStudios/sol-lang/issues/243), [#250](https://github.com/StarDragonStudios/sol-lang/issues/250) | Published 0.3.3, then #244–#249; no implicit scheduler/heap/ISR support |
| G12 | Prerelease packaging, provenance and explicit seed promotion procedure | [#251](https://github.com/StarDragonStudios/sol-lang/issues/251) | #207/#208; release gates must independently verify artifacts |

Factory helpers mentioned in examples or clauses do not establish module paths,
overload sets, ABI symbols or complete signatures. Names already explicitly
agreed (for example raw, move, @must_resolve, @captures and inner) are retained;
their unresolved surrounding API must be decided in its owning task.

General recoverable constructor failures, external reconciliation protocols,
concrete callable downcasts, safe self-reference, retained asynchronous raw
pointers and extra pointer families have no approved implementation in these
deliveries. Keep them deferred rather than inventing a release obligation.
