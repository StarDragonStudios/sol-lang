# Sol C backend

## Purpose

The C backend transforms a semantically analyzed Sol executable into
deterministic C source code.

The backend is an implementation phase of the compiler. It does not define Sol
language semantics and does not repeat parsing, name resolution, type checking,
module injection resolution, or executable entry-point discovery.

## Input contract

C generation accepts a `SemanticProgramAnalysisResult` produced by executable
semantic analysis.

The semantic program must:

* contain one resolved `ProgramEntryPoint`
* contain no program-level error diagnostics
* contain no module-level error diagnostics
* retain canonical module, function, and parameter symbols
* retain semantic type and resolved-name associations
* contain completed, frozen semantic scopes

The backend rejects invalid input with `CCodeGenerationException`.

This exception represents either:

* an invalid compiler-pipeline input
* a semantically valid construct whose C lowering belongs to a later roadmap
  issue

It does not introduce new Sol source diagnostics.

## In-memory output

Generation returns an immutable `CTranslationUnit`.

A translation unit:

* stores the complete generated C source in memory
* uses `\n` as its line ending
* ends non-empty source with exactly one newline
* performs no filesystem writes
* does not invoke a native C compiler

Writing `.c` files belongs to the CLI and toolchain layer.

## Deterministic formatting

Generated C source follows these rules:

* four spaces per indentation level
* no tab indentation
* no trailing whitespace
* no accidental leading blank lines
* no repeated trailing blank lines
* exactly one final newline for non-empty output
* no timestamps
* no absolute source paths
* no locale-dependent content
* no environment-dependent content
* no object identity hashes
* no random identifiers

Each generation call owns fresh writer and name-allocation state.

Failed, repeated, and concurrent generation calls do not share mutable
generation state.

## Translation-unit structure

Generated source is emitted in the following order:

1. generated-file comment
2. provisional primitive type aliases
3. declarations for all local Sol functions
4. definitions for all bodyful Sol functions

All function declarations are emitted before any function definition.

The placeholder declaration introduced by issue #50 is no longer emitted.

## Provisional primitive types

Until the primitive C runtime is introduced, the backend emits:

```c
typedef long long sol_int;
typedef double sol_float;
typedef int sol_boolean;
typedef unsigned int sol_char;
```

The current mappings are:

| Sol type  | Generated C type |
| --------- | ---------------- |
| `int`     | `sol_int`        |
| `float`   | `sol_float`      |
| `boolean` | `sol_boolean`    |
| `char`    | `sol_char`       |
| `void`    | `void`           |

These aliases are bootstrap representations and are not permanent ABI
guarantees.

Sol `string` is not supported by the C backend yet. String representation and
operations belong to the string runtime roadmap.

## Function collection

Functions are collected from the physical declarations of every participating
module.

Ordering is deterministic:

1. semantic program module order
2. declaration order inside each compilation unit

Injected functions are not emitted again from the injecting module.

Every canonical local `FunctionSymbol` receives exactly one generated C name.

## Temporary generated function names

Until module and symbol mangling is introduced, functions receive sequential
internal names:

```text
sol_function_0
sol_function_1
sol_function_2
```

These names:

* are allocated from canonical `FunctionSymbol` instances
* preserve semantic program and declaration order
* do not contain original Sol function names
* do not contain source offsets
* do not contain Java object hashes
* are reset for every generation call

Definitive module-based naming belongs to issue #54.

## Function declarations and definitions

A bodyful Sol function emits a static declaration:

```c
static sol_int sol_function_0(void);
```

and a later definition:

```c
static sol_int sol_function_0(void) {
    return ((sol_int)0);
}
```

A bodyless `@fn` declaration emits only an external declaration:

```c
extern sol_int sol_function_0(sol_int sol_parameter_0);
```

The backend does not yet bind bodyless declarations to final foreign C symbol
names.

A zero-parameter function uses `(void)` rather than an empty C parameter list.

## Temporary generated parameter names

Parameters receive sequential names local to their function:

```text
sol_parameter_0
sol_parameter_1
sol_parameter_2
```

Parameter numbering resets for each function.

Generated parameter references use the canonical `ParameterSymbol` association
stored in the semantic model. The backend does not resolve parameter names from
source spelling.

Original Sol parameter names are not required in generated C.

## Entry point

The resolved `ProgramEntryPoint` is generated as an ordinary bodyful Sol
function.

The backend:

* does not search for `@init`
* does not infer entry points from function names
* does not assign the entry function a special C name
* does not emit a platform `main` function yet
* does not invoke the entry function yet

Platform entry-wrapper generation is deferred until function-call and final
name generation are available.

## Supported statements

Issue #51 supports return statements.

A bare Sol return:

```sol
return
```

generates:

```c
return;
```

A value return:

```sol
return expression
```

generates:

```c
return <generated-expression>;
```

Other statement forms fail explicitly with
`CCodeGenerationException.Reason.UNSUPPORTED_STATEMENT`.

Variable declarations and assignments belong to issue #52.

Conditionals and loops belong to issue #53.

## Supported expressions

### Integer literals

```sol
42
```

generate:

```c
((sol_int)42)
```

### Floating-point literals

```sol
3.5
```

generate:

```c
((sol_float)3.5)
```

### Boolean literals

```sol
true
false
```

generate:

```c
((sol_boolean)1)
((sol_boolean)0)
```

### Character literals

```sol
'A'
```

generate:

```c
((sol_char)'A')
```

Lexer-validated escape sequences are preserved.

### Parameter references

A name expression resolved to a canonical `ParameterSymbol` generates its
allocated parameter name:

```c
sol_parameter_0
```

Name expressions associated with other symbol kinds are not yet supported.

### Parenthesized expressions

Source parentheses are retained explicitly:

```c
(<generated-expression>)
```

### Unary expressions

The current mappings are:

| Sol operator   | C operator |
| -------------- | ---------- |
| logical not    | `!`        |
| negation       | `-`        |
| unary positive | `+`        |

Every unary expression is wrapped in parentheses.

### Binary expressions

The current mappings are:

| Sol operator          | C operator |   |   |
| --------------------- | ---------- | - | - |
| multiplication        | `*`        |   |   |
| division              | `/`        |   |   |
| remainder             | `%`        |   |   |
| addition              | `+`        |   |   |
| subtraction           | `-`        |   |   |
| less than             | `<`        |   |   |
| less than or equal    | `<=`       |   |   |
| greater than          | `>`        |   |   |
| greater than or equal | `>=`       |   |   |
| equality              | `==`       |   |   |
| inequality            | `!=`       |   |   |
| logical conjunction   | `&&`       |   |   |
| logical disjunction   | `          |   | ` |

Every binary expression is generated as:

```c
(<left> <operator> <right>)
```

Explicit grouping preserves the Sol syntax tree without depending on C
operator precedence.

## Unsupported expressions

The following expression forms are currently rejected:

* string literals
* function calls
* qualified function calls
* function references
* local-variable references
* future unrecognized expression nodes

The backend never replaces an unsupported expression with a placeholder such
as `0`, `NULL`, or a comment.

Function-call and qualified-name lowering belong to issue #54.

String literals belong to the string runtime roadmap.

## Backend failure reasons

The backend currently distinguishes:

* `MISSING_ENTRY_POINT`
* `PROGRAM_SEMANTIC_ERRORS`
* `MODULE_SEMANTIC_ERRORS`
* `UNFROZEN_SEMANTIC_SCOPE`
* `UNSUPPORTED_TYPE`
* `UNSUPPORTED_STATEMENT`
* `UNSUPPORTED_EXPRESSION`

Messages are deterministic and intended for compiler-internal debugging.

They do not contain timestamps, absolute paths, random values, or object
identity hashes.

## Pipeline boundaries

The C backend remains separate from:

* source-file discovery
* lexical analysis
* parsing
* semantic analysis
* filesystem output
* native compiler discovery
* native compiler invocation
* object generation
* linking
* executable execution
* the Sol runtime

## Backend roadmap

### Issue #50

Established:

* the public generation API
* semantic input validation
* immutable in-memory C output
* deterministic source writing

### Issue #51

Adds:

* provisional primitive C types
* function declarations
* bodyful function definitions
* bodyless function declarations
* generated function names
* generated parameter names
* primitive literals
* parameter references
* parenthesized expressions
* unary expressions
* binary expressions
* return statements

### Issue #52

Adds:

* local constants
* immutable local variables
* mutable local variables
* assignments
* local-variable references

### Issue #53

Adds:

* conditional statements
* loops
* nested control-flow blocks

### Issue #54

Adds:

* function calls
* qualified function calls
* deterministic module-based name mangling
* definitive generated symbol names
* platform entry-wrapper generation when possible

Runtime headers, primitive runtime support, strings, CLI output, native
compilation, linking, and execution are handled by later roadmap phases.
