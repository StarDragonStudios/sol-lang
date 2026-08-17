# Sol native bootstrap seed

This archive is a minimal native Sol 0.1.1 bootstrap toolchain. It contains a
stage-3 `solc-core`, public `solc` and `sol` commands, the native link driver,
the C runtime and the canonical bootstrap standard library. It contains no
JVM, Java classes or Java compiler launcher.

Add `bin` to `PATH`, ensure Clang (or a compatible `cc`) is installed, and run:

```text
solc --version
sol --version
```

See `BOOTSTRAP.md` for checksum verification, the trust chain, offline use and
recovery instructions. `SHA256SUMS` covers every other file in this extracted
archive. The JSON manifests below `share/sol` identify the exact compiler,
source inventory and fixed-point artifacts used to construct this seed.
