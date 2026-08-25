# Sol 0.1.1 — Self-host Foundations

Sol 0.1.1 freezes the language and toolchain foundation that will be used to
resume implementation of the Sol compiler in Sol.

## Highlights

- Value-type structs without identity, inheritance or an object model.
- Minimal compile-time generics implemented through monomorphization.
- Typed raw `pointer<T>` values and explicit manual allocation, reallocation,
  loading, storing and freeing through `std.memory`.
- Explicit pointer-to-struct field access with `pointer->field`.
- Immutable UTF-8 string length, Unicode-scalar indexing, slicing,
  concatenation and exact equality.
- Strict UTF-8 text input through `std.file.read_text` and line input through
  `std.console.read_line`.
- Generic contiguous `std.collections.vector.Vector<T>` with indexed access,
  capacity management, geometric growth and explicit cleanup.

## Memory model

The 0.1.1 bootstrap memory model is deliberately raw and manual. Bounds,
liveness, aliasing, leaks, double-free and use-after-free remain programmer
responsibilities. `ref<T>`, `borrow<T>`, lifetimes, borrow checking and strict
ownership are reserved for future releases.

## Supported platforms

Native bootstrap seeds are published for Linux, macOS and Windows on both
x86_64 and ARM64. They contain the stage-3 compiler and do not require Java.
A native linker driver (`clang`, `cc`, or one selected with `SOL_LINKER`) is
required to compile programs.

## Bootstrap seed and integrity

The six `sol-bootstrap-0.1.1-*` platform archives published alongside the JVM
distributions are the official Sol 0.1.1 bootstrap seeds. `SHA256SUMS` records
the native bootstrap archive SHA-256 digests,
`NATIVE-SEED-PROVENANCE.txt` records the tag, source commit and release workflow, and
the target-specific JSON manifests record the fixed-point compiler provenance.

After downloading an archive and the checksum file, verify that archive from
their directory (replace the filename with the selected platform archive):

```text
grep 'sol-bootstrap-0.1.1-linux-x86_64.tar.gz' SHA256SUMS | sha256sum --check
```

On systems without `sha256sum`, compare the archive with the corresponding
entry using the platform's SHA-256 utility.

Each archive is built twice from clean stage directories and accepted only when
the stage-3 compiler, manifests and final archive are byte-identical. See
`BOOTSTRAP.md` inside the seed for verification, offline bootstrap and recovery
instructions. SHA-256 checksums are integrity identifiers, not signatures.
