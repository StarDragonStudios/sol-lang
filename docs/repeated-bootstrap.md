# Repeated compiler bootstrap

The repeated-bootstrap gate proves that the native Sol compiler reaches a
content fixed point without using Java at any active build stage.
It builds an isolated chain:

```text
released Sol 0.1.1 seed -> stage1 -> stage2 -> stage3
```

Stage 1 is compiled by the released seed. Stage 1 compiles stage 2, and stage
2 compiles stage 3 through the public native `solc` command. The gate then
requires byte-for-byte equality between stage 2 and stage 3 for:

- generated LLVM;
- generated C literal registries; and
- the canonical source inventory.

Linked executable bytes are recorded but are not the fixed-point authority,
because native linkers may embed platform-specific metadata. Each isolated
stage contains a deterministic `source-inventory.json` and a
`provenance.json` with compiler and artifact SHA-256 digests. Both stage 2 and
stage 3 must pass the complete conformance catalog and public CLI contract.

Run the gate from the repository root with the released compiler selected by
`SOLC`:

```sh
SOLC=/path/to/sol-bootstrap-0.1.1-<platform>/bin/solc ./compiler/repeated-bootstrap/run.sh
```

On Windows:

```bat
set SOLC=C:\path\to\sol-bootstrap-0.1.1-windows-x86_64\bin\solc.bat
compiler\repeated-bootstrap\run.bat
```

Outputs are written below `compiler/build/repeated bootstrap/`. The runner
deletes that directory before every invocation and fails closed on a missing
input, compilation error, content mismatch, or conformance failure.
