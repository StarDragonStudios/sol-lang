# Compiler command-line interface

This page documents implemented CLI behavior. The
[Sol 0.3 mode contract](../spec/sol-0.3/compatibility.md) defines the compatibility
boundary. Only the mode-isolation subset below is implemented; it is not the
complete Sol 0.3 ownership/borrowing language.

The native compiler is split across a compiler core written in Sol and small
host launchers:

```text
solc.sh / solc.ps1
    → versioned request file
    → solc-core (frontend → IR → native content)
    → native-link.sh / native-link.bat
    → executable
```

This preserves the bootstrap boundary established by the native toolchain.
Sol 0.1.1 does not gain a general process API or a hidden command-line
intrinsic. The core owns language compilation; the launchers own host argument
parsing, path policy, process execution and cleanup.

## Public commands

The Unix entry points are `compiler/solc.sh` and `compiler/sol.sh`. Windows uses
the corresponding `.bat` entry points, which invoke the checked-in PowerShell
implementations without interpolating user paths into PowerShell source.

`solc` supports:

```text
solc program.sol
solc program.sol -o output
solc program.sol --output output
solc program.sol --output=output
solc --keep-intermediates program.sol
solc -- program.sol
solc --version
solc --language-mode=legacy program.sol
solc --language-mode safe-experimental program.sol
```

The default executable is the absolute source path without its `.sol` suffix.
Windows appends `.exe` unless it is already present. The entry file's directory
is the filesystem module root.

`sol run [--] program.sol` creates private temporary storage, invokes the same
compile-only path, launches the native output directly, and removes the
temporary executable. The child inherits the launcher's stdin, stdout, stderr
and current working directory. Once launched, its status is returned unchanged.

Compiler command failures retain the public categories: command line 2, input
3, frontend 4, lowering 5, backend 6, toolchain 7 and execution 8.

## Experimental mode-isolation subset

Omitted mode is `legacy`; explicit `--language-mode=legacy` is equivalent.
`--language-mode VALUE` and `--language-mode=VALUE` are accepted once, with
case-sensitive values `legacy` or `safe-experimental`. Unknown, blank, missing
and duplicate values fail with status 2. `sol run` accepts this option before
the source/`--` and forwards it unchanged; default runs pass no new flag to
overridden compilers. Text after `--` is a source path, not a mode option.

`safe-experimental` currently allows only parameterless functions with bodies,
int/void return types, integer-literal or void returns, `@init`, and injections
of local source modules that satisfy the same restrictions. Ordinary entry-point
and semantic validation still applies. Cyclic source imports remain supported.
Structs, classes, variables, arithmetic, calls, generics, pointers and all other
unchecked syntax fail with source-located `SOL-M001` (or existing lexer/parser
diagnostics for syntax not yet parsed). This deliberately small allowlist is a
mode-isolation test surface, **not a Rust-level safety checker**.

All `std`/`std.*` imports fail with `SOL-M002`. The safe library root is the
separate, currently empty `stdlib-safe` shipped with the compiler. Neither the
legacy library override nor filesystem impersonation can provide a fallback.
Legacy sources are not mixed in as trusted compiled modules: reachable local
source is rechecked in the selected mode. There is no precompiled Sol module
loader, cache, FFI bridge or safe/legacy binary import facility to bypass this
check. Those facilities require the metadata validation described in the design
before they may be enabled.

Mode is stored per request, discovery, semantic program and IR program. Generic
validation caches are per semantic analysis, never shared across mode contexts;
the safe subset rejects generics before instantiation. No global mode or
environment-driven mode default exists. Unknown internal modes are rejected.

Retained safe LLVM/C outputs record `safe-experimental`, the implementation
contract `literal-return-1`, and `reusable-module: no`. This marker identifies
output, not authenticated reusable module metadata. No artifact with missing or
stale metadata is accepted as a safe module because no such import path exists.
Legacy artifact contents remain unchanged. Tool version and immutable input seed
are unchanged; the new flag does not publish a Sol 0.3 release.

## Source discovery

The compiler core registers each module before traversing its injections, so
cycles terminate and first-discovery order remains deterministic. A module
`utilities.math` maps to `<entry-directory>/utilities/math.sol`.

The canonical standard-library sources live under `compiler/stdlib/` and take
precedence over filesystem modules with the same names. Missing non-standard
injections remain absent so semantic analysis emits the normal `SOL-S019`
diagnostic at the importing source span.

Lexical, parsing and semantic diagnostics use:

```text
path:line:column: error [CODE]: message
```

Lines and columns are one-based and module diagnostics retain their owning
source path.

## Private request contract

The launcher writes a UTF-8, line-oriented request and sends only its temporary
path to the core's standard input. Version 1 contains exactly seven lines:

```text
SOL-SELFHOST-REQUEST-1
<absolute entry source>
<absolute module root>
<entry module name>
<absolute bundled stdlib root>
<LLVM output>
<generated C literal output>
```

The request is an internal bootstrap contract, not a user API. Empty fields,
unknown versions and wrong field counts are rejected with command-line status
2. Public paths containing newlines are rejected because they cannot be encoded
by this protocol; spaces are supported throughout.

The request pipe is independent of the launcher's own stdin. Consequently,
`sol run` does not consume input intended for the compiled program.

Version 1 always selects legacy. Safe requests instead have eight lines: header
`SOL-SELFHOST-REQUEST-2`, mode `safe-experimental`, then the same six fields (with
the separate safe library root). No other mode is valid in v2. The core validates
mode and count independently of the launcher. Unsupported versions, empty fields
and malformed mode values return status 2; launchers never retry using v1.
Existing seven-line requests and old launchers keep their legacy behavior.

## Artifact ownership

Without `--keep-intermediates`, the launcher removes the generated LLVM,
literal C and native objects. With it, the following deterministic files remain
beside the executable:

```text
<output>.sol-selfhost.ll
<output>.sol-selfhost-literals.c
<output>.sol-link.o
<output>.sol-runtime.o
<output>.sol-literals.o
```

Windows uses `.obj`. Request files and failed partial content are always
removed. `sol run` never exposes its temporary compilation directory.

Package manifests, arguments for the compiled program, optimization flags,
cross-compilation and installed-distribution layout remain outside this phase.
