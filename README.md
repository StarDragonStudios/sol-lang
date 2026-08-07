# Sol

Sol is a compiled programming language designed for clarity, strong typing,
memory safety and systems programming.

## Current status

Sol is in early bootstrap development.

The Sol 0.1 milestone focuses on establishing the initial procedural language
and its native compilation pipeline:

* static typing;
* functions;
* variables and constants;
* conditions and loops;
* a Java bootstrap compiler;
* a typed, compiler-independent Sol IR;
* LLVM IR generation;
* native object-file emission;
* host-native executable linking.

The compiler is implemented in Java, but generated Sol executables are native
programs and do not depend on the JVM.

The current native compiler pipeline is:

```text
Sol source
→ lexer
→ parser
→ semantic analysis
→ typed Sol IR
→ LLVM IR
→ native object file
→ host linker
→ native executable
```

Not every stage is exposed through the command-line interface yet.

## Native executables

Executable Sol programs designate one function with `@init`.

The language and typed IR permit entry-point parameters. The current native
startup bridge can execute parameterless entry functions returning Sol `int`.
Binding process command-line arguments to entry-point parameters will be added
separately.

The compiler currently targets the host platform. It discovers a linker driver
through `SOL_LINKER` or the host `PATH`, preferring `clang` and then `cc`.

See [Native executable toolchain](spec/native-executables.md) for the current
entry-point convention, linking process and limitations.

## Repository layout

* `compiler/`: Java bootstrap compiler, typed Sol IR, LLVM backend and native
  toolchain.
* `spec/`: language specifications and compiler design decisions.
* `examples/`: example Sol programs.

## Development

The compiler test suite can be run with:

```bash
cd compiler
./gradlew clean test
```

Repository changes should also pass:

```bash
git diff --check
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for additional contribution
information.
