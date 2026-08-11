# Changelog

## Sol 0.1.1 — Unreleased

### Self-host foundations

* Added value-type structs and minimal compile-time generics.
* Added typed raw `pointer<T>` values, contextual `null`, dereference and
  element indexing.
* Added `std.memory.allocate<T>`, `reallocate<T>` and `free<T>` with explicit
  zero-count, overflow and allocation-failure behavior.

The Sol 0.1.1 memory model is deliberately unsafe and manual. Bounds,
liveness, aliasing, leaks, double-free and use-after-free are programmer
responsibilities. `ref<T>`, `borrow<T>`, lifetimes, borrow checking and strict
ownership are reserved for a future release.
