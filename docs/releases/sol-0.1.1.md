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

Prebuilt distributions are published for Linux, macOS and Windows on both
x86_64 and ARM64. Java 25 and a native linker driver (`clang`, `cc`, or one
selected with `SOL_LINKER`) are required to run the compiler. Programs compiled
by Sol are native executables and do not require the JVM.

## Bootstrap seed and integrity

The six platform archives published with this release are the official Sol
0.1.1 bootstrap seeds. `SHA256SUMS.txt` records their SHA-256 digests, and
`SEED-PROVENANCE.txt` records the tag, source commit and release workflow used
to build them.

After downloading an archive and the checksum file, verify that archive from
their directory (replace the filename with the selected platform archive):

```text
grep 'sol-0.1.1-linux-x86_64.tar.gz' SHA256SUMS.txt | sha256sum --check
```

On systems without `sha256sum`, compare the archive with the corresponding
entry using the platform's SHA-256 utility.

The self-host build will remain pinned to this published release while issues
#115–#127 implement and stabilize the compiler written in Sol.
