# Sol compiler

This directory contains the official Sol compiler implementation, written in
Sol itself.

It was built from scratch against the released Sol 0.1.x language and current
compiler architecture. The retired Java bootstrap compiler is not part of the
active source or build tree.

## Bootstrap

The published native Sol 0.1.1 stage-3 compiler is the immutable seed for
rebuilding the official compiler:

```text
Sol 0.1.1 seed compiler
        ↓
compiler/src/main.sol
        ↓
stage 1 native compiler executable
```

The compiler contains its source model, token representation, lexical
scanner, uniform syntax-tree representation, complete Sol 0.1.x grammar parser
with the staged Sol 0.2 object-syntax extensions,
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
./compiler/bootstrap.sh
```

The bootstrap script:

1. locates the Sol 0.1.1 seed compiler;
2. verifies its version;
3. compiles `compiler/src/main.sol`;
4. writes the stage 1 executable to:
```text
compiler/build/stage1/solc-core
```

5. validates the public `solc` and `sol` launchers against the generated core;
6. compiles and runs the lexical-analysis suite;
7. compiles and runs the syntax-tree and parser-foundation suite;
8. compiles and runs the complete grammar suite;
9. compiles and runs the symbol, scope and semantic-type suite;
10. compiles and runs the semantic-analysis and module-resolution suite;
11. compiles and runs the typed Sol IR suite;
12. compiles and runs the semantic-to-IR lowering suite;
13. compiles and runs the textual LLVM generation suite;
14. generates a representative LLVM module and verifies it with host Clang;
15. generates deterministic LLVM and C literal artifacts for a complete program;
16. compiles the LLVM, runtime and literal registry to native objects;
17. links and executes the resulting Unicode/file/memory smoke-test program;
18. compiles and runs a multi-module program through `solc`;
19. validates retained artifacts, CLI rejection, source diagnostics and `sol run` status propagation;
20. runs the seed-versus-current language, runtime and CLI conformance gate.

After building stage 1 with the immutable seed, the script uses stage 1 to
compile the test suites. The conformance gate still compares against the
actual published seed: candidate core, standard-library and native-link
overrides are removed from the seed's environment.
See [compiler performance](../docs/compiler-performance.md) for the binding
index invariants and a reproducible before/after compilation comparison.

The seed compiler can be selected explicitly with the `SOLC` environment variable:

```bash
SOLC=/path/to/sol-bootstrap-0.1.1-<platform>/bin/solc ./compiler/bootstrap.sh
```

Generated bootstrap artifacts under `compiler/build/` are not committed to the repository.

The verifier defaults to `clang`. Select another Clang executable explicitly
with `SOL_CLANG=/path/to/clang`. The native linker driver is selected with
`SOL_LINKER` and otherwise discovered as `clang`, then `cc`. Set
`SOL_KEEP_INTERMEDIATES=1` to retain its deterministic object files. See
[`docs/compiler-llvm-backend.md`](../docs/compiler-llvm-backend.md) for the
target-independent type mapping, deterministic naming rules and native-runtime
boundary, and
[`docs/compiler-native-toolchain.md`](../docs/compiler-native-toolchain.md) for
object generation and linking. The public commands and private bootstrap
request are documented in
[`docs/compiler-cli.md`](../docs/compiler-cli.md).
The portable conformance catalog and comparison rules are documented in
[`docs/compiler-conformance.md`](../docs/compiler-conformance.md). The isolated
stage 1 → stage 2 → stage 3 fixed-point gate, deterministic source inventory
and provenance manifests are documented in
[`docs/repeated-bootstrap.md`](../docs/repeated-bootstrap.md).
Reproducible native seed construction, archive verification, the six-target
matrix and offline recovery are documented in
[`docs/bootstrap-seeds.md`](../docs/bootstrap-seeds.md).

### Windows

Run:

```bat
compiler\bootstrap.bat
```

An explicit seed compiler can be selected with:

```bat
set SOLC=C:\path\to\sol-bootstrap-0.1.1-windows-x86_64\bin\solc.bat
compiler\bootstrap.bat
```

The generated stage 1 executable is written to:

```text
compiler\build\stage1\solc-core.exe
```

## Source model and lexer

The frontend implementation under `compiler/src/frontend/` provides:

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

`compiler/src/lexer_test.sol` is a separate native test entry point. Both
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
published syntax contract. Each node has one owner, and recursive destruction
releases the complete tree without casts or untyped pointers.

The parser owns only its temporary cursor state and borrows the lexer's token
vector. `ParseResult` owns the resulting syntax tree. It validates that token
streams are non-empty and contain exactly one terminal EOF, then implements the
complete released grammar for declarations, types and generics, injections,
statements, blocks, expressions, calls, structs, class declarations, object
lifetime forms and supported multiline lists. Object syntax is represented for
the downstream Sol 0.2 work; semantic acceptance remains gated on #132–#142.
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
| `ClassDeclaration` | class name / none | annotations, `Name`, optional base clause, interface clauses, fields and methods |
| `ClassFieldDeclaration` | field name / none | annotations, `Name`, field type |
| Class base/interface clause | qualified type name / none | type reference |
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
| `NewExpression` | qualified class name / none | type reference, constructor arguments |
| `DeleteStatement` | empty / none | pointer expression |
| `Name` | identifier / none | none |

The child-kind boundaries make variable-length groups unambiguous without
requiring enums, casts, unions or an object model. Each child has exactly one
owning parent, and recursive destruction releases the entire tree. The lexer
result must remain alive only while `parse_tokens` is executing; the completed
tree does not retain token pointers.

`compiler/src/parser_test.sol` preserves the parser-foundation and malformed
stream coverage. `compiler/src/grammar_test.sol` validates every declaration and
statement family, expression precedence, postfix and generic forms, empty and
multiline forms, ordered payloads, exact spans and representative malformed
grammar diagnostics.

## Semantic model and analysis

The semantic foundation under `compiler/src/semantics/` supplies the stable
model that the semantic-analysis pass will populate:

* canonical `int`, `float`, `boolean`, `char`, `string`, `void` and error types;
* structural pointer types, declaration-identified struct and type-parameter
  types, and nominal class/interface types, including ordered struct generic
  arguments;
* function, method, constructor, explicit-receiver, parameter, local-variable, imported-name,
  module-namespace, struct, struct-field, type-parameter, class, interface and
  class-field symbols;
* ordered module, class-member, function and block scopes with local and lexical lookup,
  duplicate rejection, shadowing and explicit freezing.

Scope storage deliberately uses `Vector` and linear lookup. This preserves
declaration order and avoids making a map implementation a prerequisite for the
frontend; the representation can be replaced without changing the lookup API.

Ownership is explicit. A scope owns every successfully declared symbol, and a
symbol owns its child symbols and constructed semantic type. Base-class and
overridden-method links are borrowed, never additional owners. The type catalog
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

1. register modules and predeclare classes, interfaces, structs and functions;
2. resolve direct, selective and namespace injections;
3. resolve class bases, reject cycles, establish class-member scopes and bind
   class/struct fields plus function, method and constructor signatures;
4. validate inherited members, exact overrides, overload declarations, recursive
   value layouts and executable entry points;
5. bind bodies, check constructor flow/delegation graphs and validate reachable
   generic specializations.

Forward declarations and cyclic function-level module dependencies are valid.
Only declarations made directly by a module are exports, so injected names are
never re-exported transitively. Module discovery is intentionally outside this
layer: callers provide an ordered `Vector<SourceModule>` after parsing all
participating sources.

Expression binding covers literals, contextual `null`, lexical and qualified
names, unary and binary operators, calls, string and raw-pointer indexing,
struct construction, struct/class field access and pointer-field access.
Class fields carry explicit visibility and always-mutable object-state semantics;
their enclosing variable binding still follows its independent rebinding rule.
Instance-method scopes contain an explicit `this` symbol and inherit lexical
module names, never class members. Field and method selection therefore requires
`this.`, `.`, or `->`; unqualified names cannot implicitly capture members.
Method calls bind their selected overload and preserve private static versus
public/protected virtual classification. Overloads use exact parameter types,
argument count and explicit generic arguments, with no conversion ranking or
return-type selection. Generic duplicate signatures compare type parameters by
position, not spelling. Contextual `null` matches pointer parameters; multiple
matching pointer overloads are ambiguous. Candidate probing binds each ordinary
argument once and supplies null's context only after selecting a unique target.
Constructors are distinct, non-virtual class members selected through
`Class(args)`, `new Class(args)`, or same-class `this(args)` delegation; their
source function names are labels rather than callable methods. Direct
construction produces a noncopyable class value, while `new` produces an owned
raw `pointer<Class>`. There is no implicit constructor. The analyzer currently
selects one exact constructor overload, validates arguments and visibility, and
requires every own field to be initialized on every normal path. Constructors
cannot accept generic arguments. Same-class delegation cannot repeat or form
direct/indirect cycles; `this(...)` produces `void`, not a fresh class value.
Neither delegation nor `new` can initialize/reconstruct a direct class binding.
Class lookup follows one validated base chain and keeps inherited overloads;
private methods are excluded from accessible inheritance. All inherited field
names remain reserved, including private fields. Exact overrides require
`@override`, the same return type and non-reduced visibility. Protected members
are accessible in their class/subclasses only through `this` or `base`.
`base.method()` binds the base implementation explicitly. Base relationships,
overridden methods and base receivers are queryable through the semantic model.

Derived constructors must reach `base(...)` directly or through `this(...)`.
The invocation must be one top-level statement. Before it, the flexible prologue
may compute using locals/parameters but cannot access the early instance.
Constructor flow intersects branch initialization sets, ignores loop-only writes
as guarantees, and validates every normal exit. Own fields cannot be read before
initialization; `this` methods require complete own-field initialization, while
base-method calls are available after base initialization.

Class-pointer views may upcast during typed initialization and assignment;
downcasts and direct-value slicing are excluded. Method/constructor argument
matching stays exact, requiring callers to create the typed view first.
Object returns must use pointer types, even when the returned expression would
construct a fresh instance. Direct class fields also require fresh exact-class
construction rather than copying an existing instance or storing a pointer.
Interface requirements, abstract completeness and the remaining top-level type
API visibility rules continue in #138; executable layout/dispatch remain in
#139–#142.
Statement binding validates declarations,
assignments and mutability, conditions, calls and returns. Invalid nodes receive
the canonical error type to suppress avoidable cascades. Diagnostics preserve
the released `SOL-S001` through `SOL-S047` catalog and extend it with staged
class, field, method, receiver and constructor validation through `SOL-S086`.
`SOL-S065` now identifies ambiguous exact overloads; `SOL-S073`–`SOL-S076`
identify duplicate signatures, no matching overload, repeated delegation and
delegation cycles respectively. `SOL-S077`–`SOL-S085` cover invalid/cyclic bases,
inherited field hiding, invalid overrides, constructor invocation placement,
missing base initialization, early instance access and uninitialized reads.
`SOL-S086` rejects direct object return types.
They are ordered by module and
half-open source span.

`compiler/src/semantic_foundation_test.sol` validates the lower-level catalog,
symbol and scope contracts. `compiler/src/semantic_analysis_test.sol` validates
successful single- and multi-module programs, every semantic diagnostic code,
association lookup, scope freezing, generic recursion, representative source
ordering and complete destruction.

## Typed Sol IR

The target-independent representation under `compiler/src/ir/` mirrors the
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

`compiler/src/ir_test.sol` covers the type system, identities, values,
instructions, structs, pointer operations, locals, calls, forward branches,
loop back-edges, functions, modules, executable entry points, invalid graph
rejection, deterministic formatting and complete arena destruction.

## Semantic-to-IR lowering

The lowering implementation under `compiler/src/lowering/` accepts only a
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

`compiler/src/lowering_test.sol` exercises complete and rejected programs,
deterministic repeated lowering, generic specialization, multiple modules,
structs, raw pointers, primitive operations and explicit control flow.
