# Compiler performance

## Semantic binding lookup

The native Sol 0.1.1 seed's semantic model groups bindings by binding kind.
Looking up a node therefore scans every recorded binding of that kind. Generic
call validation repeatedly performs these lookups while traversing function
bodies and their callees.

Two macOS CPU samples of the seed compiling `compiler/src/grammar_test.sol`
showed the same hotspot: semantic binding lookup, its vector access, and the
underlying pointer load accounted for approximately three quarters of sampled
leaf stacks. This is a compiler implementation bottleneck, not a comparison of
Sol program execution speed with other languages.

The current model selects one of 4,093 buckets using the binding kind and
syntax node's starting source offset. Buckets are allocated lazily. This is a
bounded-memory index, not a guarantee of constant-time lookup: collisions still
use a linear scan, and pathological sources can concentrate many entries in
one bucket. It avoids scanning all bindings of one kind in ordinary sources
without introducing pointer-to-integer casts or changing the language.

Important invariants:

- Semantic analysis consumes finalized syntax trees. Source offsets must not
  change while the associated semantic program is alive.
- Source offsets are only an indexing hint. Binding kind and exact node pointer
  identity are still checked, including for equal spans in different modules.
- The ordered binding vector remains authoritative for ownership and iteration;
  the index does not change insertion order or own binding objects.
- Updates reuse the existing binding. Invalid binding kinds and null inputs
  are rejected before accessing the index.

The semantic analysis suite covers bucket collisions, equal-span nodes, missing
nodes, all supported binding kinds, duplicate insertion, updates, invalid
inputs, independent semantic programs, and destruction of populated and empty
buckets.

## Reproducing the end-to-end comparison

Build the candidate compiler from the published seed first. Do not modify the
published seed, its checksum, or its provenance. Then run the same command with
each compiler core, using the same source, standard library, runtime, and
native linker. For example, from the repository root on a Unix host:

```sh
mkdir -p compiler/build/performance
SOL_SELFHOST_CORE=/absolute/path/to/seed/libexec/solc-core \
  ./compiler/solc.sh --keep-intermediates compiler/src/main.sol \
  -o compiler/build/performance/solc-candidate

time env SOL_SELFHOST_CORE=/absolute/path/to/seed/libexec/solc-core \
  ./compiler/solc.sh --keep-intermediates compiler/src/grammar_test.sol \
  -o compiler/build/performance/grammar-baseline
time env SOL_SELFHOST_CORE="$PWD/compiler/build/performance/solc-candidate" \
  ./compiler/solc.sh --keep-intermediates compiler/src/grammar_test.sol \
  -o compiler/build/performance/grammar-candidate

cmp compiler/build/performance/grammar-baseline.sol-selfhost.ll \
    compiler/build/performance/grammar-candidate.sol-selfhost.ll
cmp compiler/build/performance/grammar-baseline.sol-selfhost-literals.c \
    compiler/build/performance/grammar-candidate.sol-selfhost-literals.c
compiler/build/performance/grammar-baseline
compiler/build/performance/grammar-candidate
```

Record wall/CPU time, host and toolchain versions, compiler revisions or hashes,
and whether other builds were running. Repeat on an otherwise idle host before
treating a local result as a stable benchmark. Do not use elapsed-time thresholds
as correctness assertions in CI.

The full bootstrap compiles tests with the newly built stage 1 so compiler
improvements take effect immediately. The published seed still builds stage 1,
and conformance isolates the seed environment to avoid accidentally comparing
the candidate against itself. LLVM optimization levels, runtime string
semantics, generic recursion checks, and the published seed are unchanged.

## Local observation: 2026-08-27

Host: macOS 26.6.2, arm64, Apple Clang 21.0.0. Sources are based on `376e9af`
plus the binding-index change. Both compilers used the repository launcher,
standard library and native linker with their existing optimization settings.

| Input | Released seed wall / user CPU | Indexed compiler wall / user CPU | Wall-time speedup |
| --- | --- | --- | --- |
| `compiler/src/main.sol` | 187.70 s / 184.88 s | 59.04 s / 57.37 s | 3.18x |
| `compiler/src/grammar_test.sol` | 720.87 s / 712.62 s | 186.61 s / 181.78 s | 3.86x |

These are single end-to-end observations, including native compilation and
linking, with other validation compilations running concurrently for part of
the measurements. They are not isolated repeated benchmarks or predictions of
CI completion time. The sandbox denied `/usr/bin/time -l`'s additional resource
statistics for the seed runs after reporting wall/user/system time; successful
compilation was checked independently through generated artifacts and execution.

Compiler core SHA-256 identities:

- Published seed: `009e47023f6e30bd48b98e68a6f6be27b498bff833f239dced44652e796cace3`
- Seed-built indexed compiler: `750fb97d5212278c5bb340da62e7e2e16550ecbd741b56a8df81aebd06d82758`

For each input above, seed and candidate emitted byte-identical LLVM and literal
C. Both grammar-test executables returned zero. Compiling stage 3 with stage 2
took 56.28 seconds and preserved the stage-2 LLVM and literal C byte for byte.
The shared stage-2/stage-3 LLVM SHA-256 is
`c130e63b4b3ae0cd64ba22281c28c10b20d384b70581404bb11aca3563fea62b`.

The post-change CPU sample no longer puts binding-vector scans at the top.
Remaining costs include declaration-to-module lookup and repeated traversal in
generic validation. They are deliberately left for separately measured work;
this change does not cache or skip generic validation paths.
