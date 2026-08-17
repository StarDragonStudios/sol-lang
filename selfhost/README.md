# Sol self-hosted compiler

This directory contains the new Sol compiler implementation written in Sol.

The self-hosted compiler is being built from scratch against the released Sol 0.1.x language and the current compiler architecture. It does not reuse or adapt obsolete pre-0.1 self-host sources or previous Rust compiler implementations.

## Bootstrap

The self-host bootstrap is frozen to the released Sol 0.1.1 compiler as its seed until the frontend roadmap is complete:

```text
Sol 0.1.1 seed compiler
        ↓
selfhost/src/main.sol
        ↓
stage 1 native compiler executable
```

The self-host now contains its source model, token representation, lexical
scanner, uniform syntax-tree representation, complete Sol 0.1.x grammar parser,
complete semantic analysis across ordered source modules, a validated,
target-independent typed Sol IR model, and deterministic semantic-to-IR
lowering, deterministic textual LLVM IR generation from the sealed IR, a
portable native runtime/link pipeline, recursive source discovery and public
`solc`/`sol run` launchers.

The full compiler architecture remains:

```text
Sol source
→ frontend
→ typed Sol IR
→ LLVM IR
→ native object
→ host linker
→ native executable
```

## Building stage 1

On macOS, run:

```bash
./selfhost/bootstrap.sh
```

The bootstrap script:

1. locates the Sol 0.1.1 seed compiler;
2. verifies its version;
3. compiles `selfhost/src/main.sol`;
4. writes the stage 1 executable to:
```text
selfhost/build/stage1/solc-core
```

5. validates the public `solc` and `sol` launchers against the generated core;
6. compiles and runs the self-host lexical-analysis suite;
7. compiles and runs the self-host syntax-tree and parser-foundation suite;
8. compiles and runs the complete self-host grammar suite;
9. compiles and runs the self-host symbol, scope and semantic-type suite;
10. compiles and runs the self-host semantic-analysis and module-resolution suite;
11. compiles and runs the self-host typed Sol IR suite;
12. compiles and runs the semantic-to-IR lowering suite;
13. compiles and runs the textual LLVM generation suite;
14. generates a representative LLVM module and verifies it with host Clang;
15. generates deterministic LLVM and C literal artifacts for a complete program;
16. compiles the LLVM, runtime and literal registry to native objects;
17. links and executes the resulting Unicode/file/memory smoke-test program;
18. compiles and runs a multi-module program through self-host `solc`;
19. validates retained artifacts, CLI rejection, source diagnostics and `sol run` status propagation.

The seed compiler can be selected explicitly with the `SOLC` environment variable:

```bash
SOLC=/path/to/sol-0.1.1/bin/solc ./selfhost/bootstrap.sh
```

Generated bootstrap artifacts under `selfhost/build/` are not committed to the repository.

The verifier defaults to `clang`. Select another Clang executable explicitly
with `SOL_CLANG=/path/to/clang`. The native linker driver is selected with
`SOL_LINKER` and otherwise discovered as `clang`, then `cc`. Set
`SOL_KEEP_INTERMEDIATES=1` to retain its deterministic object files. See
[`docs/selfhost-llvm-backend.md`](../docs/selfhost-llvm-backend.md) for the
target-independent type mapping, deterministic naming rules and native-runtime
boundary, and
[`docs/selfhost-native-toolchain.md`](../docs/selfhost-native-toolchain.md) for
object generation and linking. The public commands and private bootstrap
request are documented in
[`docs/selfhost-cli.md`](../docs/selfhost-cli.md).

### Windows

Run:

```bat
selfhost\bootstrap.bat
```

An explicit seed compiler can be selected with:

```bat
set SOLC=C:\path\to\sol-0.1.1\bin\solc.bat
selfhost\bootstrap.bat
```

The generated stage 1 executable is written to:

```text
selfhost\build\stage1\solc-core.exe
```

## Source model and lexer

The frontend implementation under `selfhost/src/frontend/` provides:

* zero-based Unicode-scalar offsets with one-based lines and columns;
* half-open source spans `[start, end)`;
* value-type tokens and stable integer token kinds;
* keywords, identifiers, numeric, string and character literals;
* punctuation, arithmetic, logical, comparison and pointer-arrow operators;
* LF, CRLF and CR newline handling;
* line and block comments;
* explicit lexical results with stable `SOL-L001` through `SOL-L005`
  diagnostics.

Sol strings already guarantee valid UTF-8, so malformed input is rejected by
the text-input boundary before lexical analysis. Lexer offsets therefore count
Unicode scalar values, matching the indexing semantics of Sol strings.

`selfhost/src/lexer_test.sol` is a separate native test entry point. Both
bootstrap scripts compile and execute it with the frozen Sol 0.1.1 seed.

## Syntax tree and parser

Sol 0.1.1 does not yet provide enums, sealed hierarchies or tagged unions. The
self-host syntax tree therefore uses one uniform `SyntaxNode` value containing:

* a stable integer node kind;
* a kind-specific variant for operators, literals and declaration forms;
* optional textual payload;
* a source span;
* an ordered `Vector<pointer<SyntaxNode>>` of owned children.

The catalog covers every declaration, statement and expression shape in the
current Java syntax model. Each node has one owner, and recursive destruction
releases the complete tree without casts or untyped pointers.

The parser owns only its temporary cursor state and borrows the lexer's token
vector. `ParseResult` owns the resulting syntax tree. It validates that token
streams are non-empty and contain exactly one terminal EOF, then implements the
complete released grammar for declarations, types and generics, injections,
statements, blocks, expressions, calls, structs and supported multiline lists.
Parsing is deliberately fail-fast and reports stable `SOL-P000`, `SOL-P001` and
`SOL-P002` diagnostics with half-open source spans.

### Uniform node contract

Named syntactic forms keep their convenient identifier in `text` and also own
a `Name` child when the exact identifier-token span is needed by later semantic
diagnostics. Ordered children use the following contract:

| Node kind | `text` / `variant` | Ordered children |
| --- | --- | --- |
| `CompilationUnit` | empty / none | declarations |
| `Annotation` | annotation name / none | `Name` |
| `TypeParameter` | parameter name / none | none |
| `Parameter` | parameter name / none | `Name`, type reference |
| `TypeReference` | type name / none | `Name`, explicit type arguments |
| `ModulePath` | dotted path / none | segment `Name` nodes |
| `FunctionDeclaration` | function name / bodyful or bodyless | annotations, `Name`, type parameters, parameters, return type, optional body block |
| `StructDeclaration` | struct name / none | `Name`, type parameters, field declarations |
| `StructFieldDeclaration` | field name / none | `Name`, field type |
| `InjectionDeclaration` | namespace alias or empty / direct or namespace | module path, selected names or optional alias `Name` |
| `Block` | empty / none | statements |
| `VariableDeclarationStatement` | local name / `const`, `let` or mutable `let` | `Name`, declared type, initializer |
| `AssignmentStatement` | target name / none | name expression, value |
| Field, pointer-field and index assignments | empty / none | access target, value |
| `CallStatement` | empty / none | call expression |
| `ReturnStatement` | empty / none | optional returned expression |
| `ConditionalStatement` | empty / none | condition, then block, optional else block |
| `WhileStatement` | empty / none | condition, body block |
| Name and literal expressions | source lexeme / literal variant where applicable | none |
| `QualifiedNameExpression` | qualified spelling / none | qualifier and member name expressions |
| `NullExpression` | `null` / none | none |
| `ParenthesizedExpression` | empty / none | inner expression |
| `UnaryExpression` | operator lexeme / unary operator | operand |
| `BinaryExpression` | empty / binary operator | left and right operands |
| `CallExpression` | empty / none | callee, explicit type arguments, value arguments |
| Field and pointer-field access | field name / none | target, field `Name` |
| `IndexExpression` | empty / none | target, index |
| `StructConstructionExpression` | type name / none | type reference, field initializers |
| `StructFieldInitializer` | field name / none | field `Name`, value |
| `Name` | identifier / none | none |

The child-kind boundaries make variable-length groups unambiguous without
requiring enums, casts, unions or an object model. Each child has exactly one
owning parent, and recursive destruction releases the entire tree. The lexer
result must remain alive only while `parse_tokens` is executing; the completed
tree does not retain token pointers.

`selfhost/src/parser_test.sol` preserves the parser-foundation and malformed
stream coverage. `selfhost/src/grammar_test.sol` validates every declaration and
statement family, expression precedence, postfix and generic forms, empty and
multiline forms, ordered payloads, exact spans and representative malformed
grammar diagnostics.

## Semantic model and analysis

The semantic foundation under `selfhost/src/semantics/` supplies the stable
model that the semantic-analysis pass will populate:

* canonical `int`, `float`, `boolean`, `char`, `string`, `void` and error types;
* structural pointer types and declaration-identified struct and type-parameter
  types, including ordered generic arguments;
* function, parameter, local-variable, imported-name, module-namespace, struct,
  struct-field and type-parameter symbols;
* ordered module, function and block scopes with local and lexical lookup,
  duplicate rejection, shadowing and explicit freezing.

Scope storage deliberately uses `Vector` and linear lookup. This preserves
declaration order and avoids making a map implementation a prerequisite for the
frontend; the representation can be replaced without changing the lookup API.

Ownership is explicit. A scope owns every successfully declared symbol, and a
symbol owns its child symbols and constructed semantic type. The type catalog
owns its canonical primitive and error types. Parent scopes, syntax
declarations, symbol owners, pointer element types and generic type arguments
are borrowed. Consequently, child scopes must be destroyed before parents, the
syntax tree must outlive its symbols and declaration-identified types, and the
catalog must outlive all users of its canonical types.

`model.sol` extends these foundations with ordered source modules, explicit
entry-point state and syntax-identity associations for scopes, declarations,
resolved names and types, calls, assignments, struct fields and injections. The
program owns all modules, scopes, rejected symbols, constructed types,
association tables and diagnostics while borrowing the parsed syntax trees.

`analyzer.sol` binds a set of already parsed modules in deterministic passes:

1. register modules and predeclare structs and functions;
2. resolve direct, selective and namespace injections;
3. bind struct fields and function signatures;
4. validate recursive value layouts and executable entry points;
5. bind function bodies and validate reachable generic specializations.

Forward declarations and cyclic function-level module dependencies are valid.
Only declarations made directly by a module are exports, so injected names are
never re-exported transitively. Module discovery is intentionally outside this
layer: callers provide an ordered `Vector<SourceModule>` after parsing all
participating sources.

Expression binding covers literals, contextual `null`, lexical and qualified
names, unary and binary operators, calls, string and raw-pointer indexing,
struct construction, field access and pointer-field access. Statement binding validates declarations,
assignments and mutability, conditions, calls and returns. Invalid nodes receive
the canonical error type to suppress avoidable cascades. Diagnostics preserve
the released `SOL-S001` through `SOL-S047` catalog and are ordered by module and
half-open source span.

`selfhost/src/semantic_foundation_test.sol` validates the lower-level catalog,
symbol and scope contracts. `selfhost/src/semantic_analysis_test.sol` validates
successful single- and multi-module programs, every semantic diagnostic code,
association lookup, scope freezing, generic recursion, representative source
ordering and complete destruction.

## Typed Sol IR

The target-independent representation under `selfhost/src/ir/` mirrors the
canonical boundary documented in `docs/sol-ir.md` without depending on lexer,
syntax, semantic, diagnostic, LLVM, C, ABI or platform types. It represents:

* canonical primitive, concrete struct and typed pointer types;
* deterministic function, block, local and value identifiers;
* constants, typed operations, calls, locals, structs, pointers and immutable
  string indexing;
* explicit basic blocks with separate return and branch terminators;
* bodyless declarations, definitions, ordered modules, libraries and executable
  entry points;
* deterministic text intended for tests and compiler inspection, not stable
  serialization.

Sol 0.1.1 has no enums, tagged unions, interfaces, exceptions or object model,
so IR variants use stable integer kinds with explicitly validated payloads.
Construction is incremental: blocks, functions, modules and programs become
immutable by contract after they are sealed. Sealing verifies exact types,
identifier uniqueness, value availability, local initialization and mutability,
canonical block/function references, returns, calls, module ownership and entry
point constraints.

An `IrArena` owns every IR allocation. Programs, modules, functions, blocks and
instructions borrow their cross-references from that arena, which permits cyclic
control-flow graphs and canonical call references without recursive ownership or
double frees. Destroying an `IrProgram` destroys its arena exactly once; callers
that stop before creating a program destroy the arena directly.

Open source generic parameters are intentionally absent. Semantic-to-IR
lowering discovers reachable concrete applications and creates monomorphized IR
structs and functions. Lowering depends on semantic and syntax models, but the
IR package remains independent and performs no name or type resolution.

`selfhost/src/ir_test.sol` covers the type system, identities, values,
instructions, structs, pointer operations, locals, calls, forward branches,
loop back-edges, functions, modules, executable entry points, invalid graph
rejection, deterministic formatting and complete arena destruction.

## Semantic-to-IR lowering

The lowering implementation under `selfhost/src/lowering/` accepts only a
complete, diagnostic-free `SemanticProgram`. It first builds a deterministic
plan of concrete function and struct instances, assigns canonical IR identities
for forward references, and then lowers every function body into sealed basic
blocks. Generic functions and structs are monomorphized from the semantic call
graph; open type parameters never cross the typed-IR boundary.

Lowering covers literals, locals, unary and binary operations, direct and
qualified calls, struct construction and value-field mutation, raw-pointer
field and index loads/stores, immutable string indexing, returns, conditionals
and loop back-edges. Modules retain semantic source order and every concrete
definition stays with its declaring module. Executable entry points are mapped
to their canonical lowered function; library programs remain entryless.

The pass borrows the semantic program and never rewrites semantic types or
symbols. Temporary specialization and ownership tables are released after the
sealed `IrProgram` assumes ownership of its arena. Failures return a stable
message and no partial program. LLVM types, target ABI decisions and native
symbol mangling remain outside this layer.

`selfhost/src/lowering_test.sol` exercises complete and rejected programs,
deterministic repeated lowering, generic specialization, multiple modules,
structs, raw pointers, primitive operations and explicit control flow.
