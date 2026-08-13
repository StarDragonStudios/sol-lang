# Changelog

## Sol 0.1.1 — 2026-08-13

### Self-host foundations

* Added strict UTF-8 input through `std.file.read_text` and
  `std.console.read_line`, including portable line-ending handling and
  deterministic I/O, EOF and decoding failures.
* Added the generic contiguous `std.collections.vector.Vector<T>` bootstrap
  collection with indexed access, geometric growth and explicit cleanup.
* Added value-type structs and minimal compile-time generics.
* Added typed raw `pointer<T>` values, contextual `null` and explicit
  pointer-to-struct field access with `pointer->field`.
* Added `std.memory.allocate<T>`, `reallocate<T>`, `free<T>`, `load<T>`,
  `store<T>`, `load_at<T>` and `store_at<T>`, with explicit zero-count,
  overflow and allocation-failure behavior.
* Added immutable UTF-8 string indexing by Unicode scalar value,
  concatenation, exact equality and inequality.
* Added `std.string.length`, `slice` and `substring`, with deterministic
  bounds failures and valid-UTF-8 results.
* Added supplementary Unicode scalar character literals and deterministic
  `SOL-L006` diagnostics for invalid Unicode source input.

The Sol 0.1.1 memory model is deliberately unsafe and manual. Bounds,
liveness, aliasing, leaks, double-free and use-after-free are programmer
responsibilities. `ref<T>`, `borrow<T>`, lifetimes, borrow checking and strict
ownership are reserved for a future release.
