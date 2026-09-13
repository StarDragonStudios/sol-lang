# Compiler conformance gate

`compiler/conformance/catalog.json` is the versioned behavioral contract used
to compare the published native Sol 0.1.1 seed with a freshly rebuilt compiler.
Run it after building stage 1:

```text
SOLC=/path/to/released/solc compiler/conformance/run.sh
compiler\conformance\run.bat
```

The runner copies each case into isolated paths containing spaces, compiles it
through both public `solc` commands, and compares declared observable behavior.
It does not use linked executable bytes as the compatibility authority because
host linkers may encode platform metadata. Language, runtime, diagnostics and
deterministic pre-link artifacts are validated separately.

## Comparison contract

Successful programs must match the catalog's exit status, UTF-8 stdout,
UTF-8 stderr and declared filesystem effects. Rejected programs must match the
same source-relative diagnostic header: one-based line and column, severity,
code and message. Only the isolated absolute root and platform path separator
are implicitly different.

Runtime failures execute as child processes and must match status 70 and the
complete diagnostic stream. A mismatch between the released seed and rebuilt
compiler is a conformance failure; the runner does not normalize it away.

The public-CLI section additionally checks version output, missing and unknown
arguments, missing input, `--`, paths containing spaces, inline output,
retained artifacts, stdin preservation, `sol run` status propagation and a
controlled linker failure. A `java` shim precedes the rebuilt-compiler checks on
`PATH`; any attempt to invoke it fails the gate and leaves an audit marker.

## Coverage matrix

| Sol 0.1.1 area | Cases | Authoritative observations |
| --- | --- | --- |
| Primitive literals and types | `language-runtime` | int, float, boolean, char, string and null compile/run |
| Arithmetic, comparison, equality and logic | `language-runtime` | precedence and computed values determine status 41 |
| Functions and returns | `language-runtime`, `cyclic-modules` | ordinary and generic calls, typed results |
| Variables and control flow | `language-runtime` | const, mutable let, assignment, if/else and while |
| Value structs | `language-runtime` | construction, copy through generic call and field mutation |
| Minimal generics | `language-runtime` | `Pair<T>`, generic function and monomorphized `Vector<int>` |
| Raw pointers and manual memory | `language-runtime` | allocate/reallocate/free, load/store, indexed access, `->`, invalid sizes |
| UTF-8 strings and chars | `language-runtime`, `string-index-runtime` | scalar length/index, slice, substring, concat/equality and bounds failure |
| Console | `stdin-preservation`, `console-eof-runtime` | inherited stdin/stdout and EOF failure |
| Files | `language-runtime`, `file-missing-runtime` | exists/read/write/append, relative working directory and missing input |
| Generic vector | `language-runtime`, `vector-*-runtime` | growth/get/set/pop/clear/capacity and stable failure paths |
| Module injections | `cyclic-modules`, `language-runtime` | direct, selective and namespace forms; deterministic cycle termination |
| Lexer | `lexical-error` | exact `SOL-L002` location and message |
| Parser | `parsing-error` | exact `SOL-P002` EOF location and message |
| Semantic analysis | `semantic-error` | exact `SOL-S008` type diagnostic |
| Native CLI/toolchain | public-CLI checks | options, outputs, retained artifacts, linker failure and cleanup |

Lowering and backend failures are not fabricated with source programs when the
validated frontend cannot construct the invalid IR required to trigger them.
Their stable rejection remains covered by the focused compiler lowering,
LLVM and native-artifact suites run by the bootstrap.

## Sol 0.2 object conformance

`objects.json` is a separate candidate-only contract: the immutable 0.1.1 seed
does not support objects and is not used as their behavioral oracle. Normal
bootstrap runs both catalogs. To run only object conformance after building
the candidate, use `python3 compiler/conformance/run.py --objects-only` with
`SOL_SELFHOST_CORE` pointing to that core (use `python` on Windows).

The readable native programs cover three-level inheritance, abstract methods,
interface diamonds, generic dispatch, exact overload selection, constructor-time
direct calls, private/base calls, mutation, reconstruction, embedded objects,
target-aligned layouts, new/delete and identity-preserving pointer views.
Twelve negative programs pin diagnostic code, message and one-based location
for invalid copying/returns, initialization, visibility, overrides, contracts,
abstract construction, downcasts, argument conversion, interface deletion and
inheritance/delegation cycles. Rejections must leave no executable.

The existing grammar/semantic suites cover the larger declaration and visibility
matrix; IR/lowering suites cover canonical identities, validation failures and
deterministic generation. The native lifetime test covers forced allocation
failure without relying on exhausting system memory. Undefined raw-pointer
operations are not executed as tests; in particular the compiler does not track
allocation provenance to diagnose every invalid deletion through a class view.

## Isolation and cleanup

All generated content lives under `compiler/build/conformance suite/`. Catalog
metadata is validated before execution: unknown case kinds, duplicate IDs,
missing sources or malformed versions fail closed. Every declared effect must
exist with exact content. The build directory is recreated for each invocation
and remains ignored by Git for debugging after a failure.
