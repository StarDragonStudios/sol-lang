# Native bootstrap seeds

Sol 0.1.1 publishes a minimal native bootstrap seed for each supported target:

| Platform | Archive |
| --- | --- |
| Linux x86_64 | `sol-bootstrap-0.1.1-linux-x86_64.tar.gz` |
| Linux ARM64 | `sol-bootstrap-0.1.1-linux-arm64.tar.gz` |
| macOS x86_64 | `sol-bootstrap-0.1.1-macosx-x86_64.tar.gz` |
| macOS ARM64 | `sol-bootstrap-0.1.1-macosx-arm64.tar.gz` |
| Windows x86_64 | `sol-bootstrap-0.1.1-windows-x86_64.zip` |
| Windows ARM64 | `sol-bootstrap-0.1.1-windows-arm64.zip` |

These native stage-3 toolchains are the official compiler distributions. Each
contains a native stage-3 compiler core, relocatable `solc` and `sol`
launchers, the native link driver, C runtime, canonical standard library,
license, documentation, source inventory, provenance and an embedded per-file
`SHA256SUMS`. Java is not present or invoked after extraction. A host C compiler
driver (`clang`, compatible `cc`, or `SOL_LINKER`) is still required to link
programs compiled by the seed.

## Verify and extract

Download the archive and the release-level `SHA256SUMS`. On Linux:

```sh
sha256sum --check --ignore-missing SHA256SUMS
tar -xzf sol-bootstrap-0.1.1-linux-x86_64.tar.gz
cd sol-bootstrap-0.1.1-linux-x86_64
sha256sum --check SHA256SUMS
./bin/solc --version
```

On macOS, use `shasum -a 256` to compare the published digest, extract the
archive, then run `shasum -a 256 -c SHA256SUMS`. On Windows, compare
`Get-FileHash -Algorithm SHA256` with the release manifest, expand the ZIP and
verify each embedded entry before running `bin\solc.bat --version`.

Never continue after a missing archive, unexpected filename, checksum failure,
malformed manifest or version mismatch. Delete the archive and extracted tree,
download them again from the designated GitHub release and repeat verification.

## Trust chain and reproducibility

The published Sol 0.1.1 seeds retain provenance back to the frozen Java root
that originally produced their initial native stage. From that immutable
historical boundary onward, the native compiler builds stage 2, stage 2 builds
stage 3, and the generated
LLVM, C literal registry and source inventory must reach the repeated-bootstrap
fixed point. Seed construction repeats this process in two clean directories
and requires byte-identical stage-3 binaries, manifests and final archives.
The extracted seed then rebuilds the compiler and passes conformance with a
`java` failure shim first on `PATH`.

CI downloads the published target-native seed from the v0.1.1 release, verifies
the release checksum and embedded file manifest, and then starts seed
construction without Java. Manual seed publication
is restricted to `main`, and existing native seed assets are never overwritten;
the recorded source revision therefore remains the immutable authority for the
supplemental seed publication.

Archive entries use sorted POSIX paths, fixed permissions and timestamps, zero
tar ownership, and deterministic gzip/ZIP compression metadata. Linux seeds
use a content-derived ELF build ID, macOS seeds use a reproducible content-based
Mach-O UUID, and Windows seeds request reproducible PE/COFF linking and reserve
a 16 MiB native stack so the compiler can process the complete
bootstrap source tree.

The release checksum identifies bytes delivered by GitHub; the embedded
manifest identifies every extracted file. SHA-256 checksums detect corruption
or disagreement, but they are not signatures and do not establish publisher
identity. Artifact signing, diverse double compilation and formal attestations
remain separate supply-chain work.

## Offline bootstrap

After the archive and a fresh source checkout are available locally, network
access and Java are not required. Put the extracted `bin` directory on `PATH`,
select its `solc` as `SOLC`, and run the repeated-bootstrap gate:

```sh
SOLC=/absolute/path/to/sol-bootstrap-0.1.1-linux-x86_64/bin/solc \
  ./compiler/repeated-bootstrap/run.sh
```

Keep the archive, its release checksum and the embedded provenance together.
If rebuilding fails, first re-verify both checksum layers, confirm the archive
matches the host OS/architecture, confirm Clang is available, and retry in a
clean checkout and output directory. A persistent fixed-point or conformance
failure means the seed must not be promoted as a bootstrap root.
