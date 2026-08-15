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
and the symbol, scope and semantic-type foundations needed by semantic analysis.
AST traversal and name/type resolution, typed Sol IR, LLVM generation, native
object emission, linking, and the final compiler CLI will be implemented
incrementally in later issues.

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
selfhost/build/stage1/solc
```

5. executes the generated program to verify that the native bootstrap artifact is runnable;
6. compiles and runs the self-host lexical-analysis suite;
7. compiles and runs the self-host syntax-tree and parser-foundation suite;
8. compiles and runs the complete self-host grammar suite;
9. compiles and runs the self-host symbol, scope and semantic-type suite.

The seed compiler can be selected explicitly with the `SOLC` environment variable:

```bash
SOLC=/path/to/sol-0.1.1/bin/solc ./selfhost/bootstrap.sh
```

Generated bootstrap artifacts under `selfhost/build/` are not committed to the repository.

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
selfhost\build\stage1\solc.exe
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

## Semantic model foundations

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

`selfhost/src/semantic_foundation_test.sol` validates the catalog, pointer and
generic identity rules, every symbol kind, declaration order, duplicate and
frozen-scope behavior, lexical shadowing, invalid inputs and destruction paths.
Walking the AST, resolving declarations and type references, checking
expressions, constructing module graphs and emitting semantic diagnostics remain
the responsibility of issue #119; they are intentionally absent from this
foundation layer.
