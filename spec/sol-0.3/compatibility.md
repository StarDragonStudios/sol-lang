# Experimental mode and compatibility boundary

Status: **proposed design for review in #208**, to be implemented by #209 and
the dependent safety issues. The options, request version and metadata described
here are not implemented by this documentation PR. Merging this design approves
the contract, not a claim that safe compilation is available.

This resolves the mode/migration gate in the [decision register](decisions.md).
The [0.3 contract](../sol-0.3.md), unchanged [0.2 contract](../sol-0.2.md), and
current [CLI documentation](../../docs/compiler-cli.md) remain distinct.

## M01 — explicit selection, unchanged default

The proposed public options are:

```text
solc --language-mode=legacy program.sol -o program
solc --language-mode=safe-experimental program.sol -o program
sol run --language-mode=safe-experimental program.sol
```

`--language-mode VALUE` and `--language-mode=VALUE` are equivalent. The option
belongs to compiler invocation, not to the language's source annotation system.
For `solc`, it can occur with other compiler options before the `--` terminator;
for `sol run`, it occurs after `run` and before the input/terminator. For example,
`sol run --language-mode=safe-experimental -- program.sol` is valid.
After `--`, option-looking text is a source path, not a mode override.

Exactly two values are initially recognized: `legacy` and `safe-experimental`.
Omission selects legacy. Unknown/empty values, missing values and repeated mode
options (even identical ones) fail with command-line status 2 before compilation.
The launcher must not strip, ignore or silently downgrade the selection.
`sol run` forwards it through the same compilation path and preserves program
stdin. Unix and Windows must implement identical parsing rules.

There is no environment-variable override, source-level mode switch or automatic
selection from a version suffix, file extension or imported module. A compiler
release version describes the tool, not the selected semantics. Renaming an
archive to alpha/beta does not activate safety.

## M02 — legacy is a compatibility contract

Legacy preserves current 0.2 source semantics, including copyable-by-default
structs and provisional raw class allocation/deletion. It does not mean selecting
arbitrary historical compiler behavior; the unchanged 0.1.1 subset must continue
to satisfy the seed conformance oracle, while 0.2 object behavior is validated
by the candidate-only object suite.

New safe-only constructs are not enabled in legacy. Adding ownership annotations
must not silently change a legacy struct's semantics; reject unsupported syntax
or annotations through the normal frontend diagnostic path (status 4).
Legacy compilation remains explicitly unsafe where the 0.2 contract says so.

No default switch, legacy removal, migration warning on every build, or automatic
source rewriting is part of 0.3.0–0.3.4. A future deprecation needs a separately
reviewed proposal, migration guide, release notes and compatibility evidence.

## M03 — one mode per compilation graph

Resolve the selected mode once and propagate it to every reachable source
module, generic specialization, semantic analysis, typed IR unit and lowering
operation. Recursive injections cannot reset the mode. Preserve existing cycle
termination, canonical module identities and discovery order.

Local source modules compiled in a safe invocation are checked in that mode,
not imported as unchecked legacy implementations. A source module originally
written for legacy may be deliberately rebuilt as safe only if all required
checks pass. This is a new checked build, not retroactive certification of its
old artifact. No per-module pragma enables mixed-mode compilation.

Bundled library source is not automatically safe merely because it is bundled.
Safe invocations must select an explicitly identified safe library source set
and validate reachable modules under the same mode. The legacy library set
remains the default for legacy invocations. Missing safe modules fail explicitly;
there is no fallback to legacy stdlib sources or binaries. Preserve canonical
stdlib precedence, including preventing a filesystem module from impersonating
an unavailable reserved standard module.

The safe library set may initially be small. #209 establishes selection and
rejection boundaries; it must not claim implementation of later ownership or
stdlib features. A declaration not yet fully checked/lowered in an alpha's
advertised subset fails closed. Do not blanket-mark the self-host compiler or
its current raw-memory helpers safe.

## M04 — no implicit mixed-mode bridge

The first design rejects calls/imports between safe and legacy compiled Sol
modules in either direction, even for apparently simple signatures. In
particular, `@unsafe` does not authorize loading a legacy module, stripping
ownership metadata or returning a legacy pointer as a safe owner/borrow.

Source recompilation of the complete relevant graph in one mode is the initial
migration path. A cross-mode bridge is deliberately unavailable, rather than
implicitly inferred. Any future bridge requires a reviewed ABI and contract for
ownership transfer, lifetimes, destruction, allocators, pending results, pinning
and failures before mixed-mode calls are accepted. Matching layout is not enough.

Audited compiler/runtime primitives are a separate trusted implementation
boundary. Their internal native implementation may use C/OS facilities, but the
safe compiler must expose only reviewed contracts and supported capabilities.
This does not grant arbitrary user-linked native code or existing raw stdlib
wrappers a safety guarantee. No general FFI bridge is introduced by #208/#209.

## M05 — private request protocol

Preserve the current seven-line `SOL-SELFHOST-REQUEST-1` contract unchanged.
Request v1 always means legacy; new cores continue to accept it. New launchers
emit v1 for default and explicit legacy compilation, retaining the existing
launcher/core bootstrap path. Never reinterpret a v1 request as safe.

Safe requests use exactly eight UTF-8 lines:

```text
SOL-SELFHOST-REQUEST-2
safe-experimental
<absolute entry source>
<absolute module root>
<entry module name>
<absolute safe bundled stdlib root>
<LLVM output>
<generated C literal output>
```

This initial v2 contract accepts only `safe-experimental`; legacy uses v1.
The core independently validates the version, field count, nonempty fields and
mode. Missing/unknown mode or malformed requests fail with status 2. Preserve
newline path rejection and support for spaces. An old core rejects v2 as an
unknown request; the launcher must propagate failure, never retry using v1.
The request travels through the existing private pipe, not program stdin.

Mode must be carried explicitly in the compiler's per-compilation context; do
not infer it from incidental temporary filenames, paths or mutable global state
that could leak between compilations. Internal encodings are implementation
details provided they preserve this contract.

## M06 — metadata, stale artifacts and caches

Every reusable safe compilation artifact must carry, or be bound to, metadata
recording the mode, safety-contract revision, compiler build identity, target
and ABI/data-layout identity, relevant compiler options, source/dependency
digests and selected stdlib/runtime contract identities. A release version string
alone is not an adequate safety-contract or cache identity.

Metadata must remain attached through module interfaces, typed IR and native
lowering. Retained output/provenance must identify the selected mode; a comment
in LLVM text alone is not an import authorization or a proof of validation.
Opaque native objects are not acceptable safe Sol module imports without the
validated interface/provenance contract. This does not claim control over users
manually invoking an external linker outside the compiler's supported pipeline.

For the initial experiment, reusable artifacts require exact compiler build and
contract compatibility; no cross-compiler ABI compatibility is promised. Before
loading an explicit precompiled input, reject missing/malformed metadata,
wrong mode, stale dependencies or mismatched contract/target/runtime identities.
Unmarked old artifacts are never inferred safe. Current legacy paths remain
unchanged; this design does not require retrofitting old release artifacts.

No persistent module/build cache exists as a prerequisite for this issue. If an
implementation adds one, its key includes the metadata identities above. A stale
internal cache entry may be discarded and rebuilt from available source in the
requested mode; it must never be reused or trigger a mode downgrade. Invalid
explicit artifact inputs are diagnosed, not silently replaced by a different
input. Existing per-analysis generic caches must remain scoped to a compilation
context; any wider reuse must include mode and contract identity.

Source import incompatibilities use source-located frontend diagnostics (status
4); malformed private requests use status 2. Exact new diagnostic codes and
metadata serialization are assigned in #209 with tests. This does not introduce
an unreviewed public precompiled-module command or require a persistent cache.

## M07 — source migration

| Legacy behavior | Safe-mode requirement | Owning issues |
| --- | --- | --- |
| Implicit struct copy | Explicit move, or valid @copy with copyable fields and no destructor | #211–#212 |
| Unchecked raw aliases | Safe loans/owners when possible; audited raw operations in @unsafe otherwise | #213/#215/#219 |
| Raw new returns pointer/null; manual delete | Separate reviewed safe exclusive-owner factory semantics; no implicit pointer-to-owner adoption | #219/#224 |
| Raw field ownership has no recursive release guarantee | Explicit owned representation and exactly-once deterministic destruction | #214 |
| Nullable raw pointer used for absence | optional for safe values; null remains raw, not a valid safe owner/borrow | #215–#216 |
| Ignored errors/results | Exhaustive handling or responsibility transfer on every normal path | #217–#218 |
| Existing raw-backed containers | Port implementation and prove preservation of stored obligations; do not merely rename/wrap as safe | #227–#228 |

Safe new's proposed optional unique ownership is not silently substituted into
legacy calls. Exact safe factory syntax/failure behavior remains the #219 gate.
Manual raw memory remains available only under its defined unsafe contract; it
does not automatically acquire cleanup, lifetime or provenance checking.

## M08 — bootstrap and seed separation

The verified Sol 0.1.1 seed must continue compiling the compiler's own source
with its existing command line. Do not pass new flags to the immutable seed,
change its archive, or start using unsupported 0.2/0.3 source features in the
compiler implementation merely because the output compiler supports them.

Stage 1, repeated stages, focused legacy suites and seed comparisons retain
legacy compilation. Safe-mode fixtures use the rebuilt candidate explicitly and
are never compared to the seed as though the seed supported 0.3. Repeated
self-compilation and fixed-point validation remain independent of new-language
feature availability. Seed promotion requires its own release/provenance gate.

## M09 — compatibility and executable fixture plan

The following are required future executable cases, not tests claimed to run in
this documentation PR. #209 owns mode/protocol cases; dependent issues own
feature behavior. Record actual fixture paths and diagnostic evidence when
implementing each row. Shared fixture text must be copied into isolated paths
with spaces and run through Unix and Windows launchers where supported.

| ID | Fixture / invocation | Required observation | Owner |
| --- | --- | --- | --- |
| MODE-01 | Existing compiler source through immutable 0.1.1 seed, no new flags | Builds stage 1; seed identity unchanged | #209 |
| MODE-02 | Existing 0.1.1 conformance corpus, omitted mode vs explicit legacy | Same established outputs, diagnostics and effects | #209 |
| MODE-03 | Existing 0.2 object corpus, omitted mode vs explicit legacy on candidate | Same object/manual-memory behavior; not run as a seed oracle | #209 |
| MODE-04 | Minimal supported source in safe mode, both option spellings, compile/run | Identical mode reaches every stage; stdin and exit status preserved | #209 |
| MODE-05 | Empty/unknown/missing/repeated mode, including conflicting duplicates | Status 2 before compilation, no executable | #209 |
| MODE-06 | Option-looking source after --; filenames and roots with spaces | Existing path semantics; no accidental mode override | #209 |
| MODE-07 | Same implicit-copy struct source in both modes | Legacy copies; safe rejects unless valid @copy is introduced | #211/#212 |
| MODE-08 | Legacy raw new/null/delete source vs safe ownership source | Each mode preserves its own contract; no implicit return-type migration | #215/#219 |
| MODE-09 | Owned move, local loan, result and directed-exit witnesses | Safe validates the implemented subset; legacy rejects safe-only constructs | #210–#220 |
| MODE-10 | Cyclic modules and cross-module generic instantiations | One mode/context throughout; termination and deterministic identities retained | #209 |
| MODE-11 | Legacy-only bundled module requested by safe graph, plus filesystem impersonation | Explicit rejection; no stdlib fallback or precedence bypass | #209 |
| MODE-12 | Safe/legacy compiled-module mismatch in either direction, inside/outside @unsafe | Reject before lowering/linking; @unsafe cannot bypass the boundary | #209 |
| MODE-13 | Old launcher/new core v1; new launcher/old core v2; malformed v2 | v1 remains legacy; unsupported/malformed v2 fails with status 2, no v1 retry | #209 |
| MODE-14 | Safe artifact with missing, stale or mismatched identity/dependency metadata | Explicit inputs rejected; stale internal cache only rebuilt from source in selected mode | #209 |
| MODE-15 | Sequential legacy/safe/legacy requests and generic contexts | No mode leakage or incompatible reuse; output provenance agrees | #209 |
| MODE-16 | Unsupported feature from a later delivery in early safe alpha | Explicit rejection, not unchecked acceptance or automatic legacy fallback | #209 and feature owner |
| MODE-17 | Candidate safe fixtures plus legacy repeated bootstrap | Both suites pass independently; no safe fixture sent to seed oracle | #220/#252 |

Where reusable module artifacts/caches are not implemented, MODE-12/14 are
context/metadata unit tests and import-boundary rejection tests, not permission
to fabricate a public artifact-loading command. Their native integration cases
become mandatory before that capability is enabled. A fixture rejection must
leave no newly produced executable or reusable successful artifact; cleanup must
not delete unrelated pre-existing user files.

## Review gate

Review #208 before implementing #209, specifically the option spellings,
legacy default, whole-graph policy, initial mixed-mode prohibition, v1/v2 request
split, strict metadata compatibility and absence of automatic deprecation.
Do not close downstream implementation tasks merely because this design is
approved. Later design changes must update this matrix and their dependent tests.
