# Sol 0.2.0 release preparation

Status: **not published; release gate open**. Tracks #145. This checklist does
not authorize a tag push or release publication.

## Intended changes

Sol 0.2 adds concrete and abstract classes, interfaces, explicit receivers,
annotated constructors, exact overloads, access control, single inheritance,
dynamic method dispatch and raw-pointer object construction/deletion. Structs
retain value semantics. Generic methods use compile-time monomorphization.

The initial lifetime model is manual. No destructors, ownership/borrowing,
lifetimes, automatic resource cleanup or GC are included. Allocation failure
returns null without construction; deletion requires the concrete allocation
view. Invalid raw lifetime operations are not made safe by this release.

## Merge and version gates

- [x] Merge object conformance #143 and specification #144.
- [x] Separate the immutable **input bootstrap version 0.1.1** from the
  **output distribution version 0.2.0** in metadata/download/build validation.
  Do not repoint the trusted download to an unpublished 0.2.0 seed.
- [x] Update both Unix and Windows launcher version output and candidate CLI
  expectations to 0.2.0, without changing the baseline seed expectations.
- [x] Make extracted-seed reconstruction accept the verified 0.2.0 candidate
  while keeping ordinary bootstrap rooted in the immutable 0.1.1 seed.
- [x] Update release workflow default tag, installation examples and archive
  documentation after the version split is implemented.

`compiler/seed/metadata.json` now records `bootstrap_version` for downloads and
`version` for output archives. Ordinary bootstrap still requires 0.1.1; only
the extracted-package verification invokes `--verified-candidate-seed` to
rebuild with the candidate. This option selects a version expectation, not a
substitute for archive/checksum/provenance verification.

## Validation gates

- [ ] Run the complete compiler suites, original seed-compatibility catalog,
  candidate-only object catalog and linked allocation-failure regression.
- [ ] Prove stage-2/stage-3 pre-link fixed point.
- [ ] Run native builds on linux-x86_64, linux-arm64, macosx-x86_64,
  macosx-arm64, windows-x86_64 and windows-arm64.
- [ ] Build independent identical archives, validate embedded manifests and
  provenance, freshly extract every archive and execute packaged CLI smoke
  tests and object programs on its native target.
- [ ] Verify archive-level SHA256SUMS, six archives and six provenance files
  from the same approved source revision.

The release workflow rejects a tag/version mismatch before starting expensive
builds, runs object runtime tests as a separate failing step and uses the same
180-minute native build budget as CI. Publication retains its final metadata
check and refuses to overwrite existing assets.

## Publication handoff

After the preceding gates and explicit maintainer approval, create the version
tag from the validated revision and publish the verified artifacts. Record the
tag, revision, workflow run and checksums here. Keep #145 open until publication
and download/install smoke verification are complete; do not replace the
immutable 0.1.1 bootstrap assets or close the roadmap on preparation alone.
