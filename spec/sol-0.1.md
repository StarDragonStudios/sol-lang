# Sol 0.1 Language Specification

Sol 0.1 defines the procedural bootstrap language implemented by the Java
bootstrap compiler. Sol 0.1.1 extends the released 0.1.0 language with
user-defined value-type structs, minimal compile-time generics and explicit raw
memory facilities while preserving the procedural model.

This specification describes the source-language behavior of Sol 0.1.
Backend-specific representation, native executable construction and host
toolchain behavior are documented separately where they do not affect the
language semantics.

## Lexical structure

Sol source is case-sensitive.

Keywords, primitive type names, identifiers, annotations and module names must
therefore use their exact spelling.

### Identifiers

An identifier begins with an ASCII letter or `_` and may continue with ASCII
letters, decimal digits or `_`.

Informally:

```text
[A-Za-z_][A-Za-z0-9_]*
```

Examples of valid identifiers include:

```sol
value
_value
value2
calculate_total
```

The following words are reserved keywords in Sol 0.1:

```text
fn
let
const
if
else
while
return
then
do
end
inject
true
false
null
only
namespace
as
struct
```

Annotation names such as `init` and `mut` are not keywords by themselves.
Their meaning depends on the annotation syntax in which they appear.

### Whitespace and newlines

Spaces, horizontal tabs and form-feed characters separate tokens where
necessary and are otherwise ignored.

Newlines are syntactically significant. They separate declarations and
statements and terminate several declaration forms.

Function parameter lists, call argument lists, type-parameter lists and
type-argument lists are explicit exceptions. Within those delimited lists,
newlines may appear between the opening delimiter, list elements, commas and
the closing delimiter.

Blank lines are therefore permitted inside parameter and argument lists.

This exception does not provide general implicit line continuation. In
particular, a newline does not by itself allow an expression to continue across
source lines.

Both LF and CRLF source line endings are accepted.

Blank lines are permitted between top-level declarations and between
statements inside blocks.

### Comments

A line comment begins with `//` and continues until the next line break:

```sol
// This is a line comment.
```

A block comment begins with `/*` and ends with the next `*/`:

```sol
/*
 * This is a block comment.
 */
```

Block comments may span multiple source lines and do not nest.

Comments do not produce language-level values or declarations.

## Primitive types

Sol 0.1 defines the following primitive types:

| Type      | Category          | Meaning                       |
| --------- | ----------------- | ----------------------------- |
| `int`     | numeric, integral | Signed whole-number values    |
| `float`   | numeric           | Floating-point values         |
| `boolean` | logical           | The values `true` and `false` |
| `char`    | character         | One character value           |
| `string`  | text              | Sequences of characters       |
| `void`    | non-value         | Absence of a returned value   |

Primitive type names are lowercase and case-sensitive. For example, `int` is
a primitive type, while `Int` and `Integer` are not.

Primitive type identity and source-language semantics are defined by Sol
itself. They are not defined by Java classes, JVM runtime types, C types or
host-platform implementation details.

The native bootstrap backend assigns concrete LLVM representations to these
types, but those representations do not change their source-language identity.
See `native-executables.md` for backend-specific details where relevant.

`void` is not a value type. In Sol 0.1 it is used as the return type of
functions that return no value. Variables and function parameters cannot have
type `void`.

## Literals

Sol 0.1 provides literals for every primitive value type except `void`, plus
the contextual raw-pointer literal `null`.

### Integer literals

An integer literal contains one or more decimal digits:

```sol
0
7
42
123456
```

A leading sign is not part of the literal. Negative and explicitly positive
values use the unary `-` and `+` operators:

```sol
-42
+42
```

Sol 0.1 does not define hexadecimal, binary, octal, exponent or type-suffixed
integer literal forms.

### Floating-point literals

A floating-point literal contains decimal digits on both sides of `.`:

```sol
0.0
3.14
42.5
```

The forms `.5` and `5.` are not Sol 0.1 floating-point literals.

A leading sign is expressed with a unary operator:

```sol
-3.14
+0.5
```

Exponent notation and type suffixes are not defined in Sol 0.1.

### Boolean literals

The boolean literals are:

```sol
true
false
```

They have type `boolean`.

### Character literals

A character literal is enclosed in single quotes:

```sol
'A'
'x'
'\n'
```

It contains exactly one character or one supported escape sequence and has type
`char`.

### String literals

A string literal is enclosed in double quotes:

```sol
"Hello"
"Sol 0.1"
""
```

A string literal cannot contain an unescaped source newline.

String literals have type `string`.

### Escape sequences

Character and string literals support the following escape sequences:

| Escape | Meaning         |
| ------ | --------------- |
| `\n`   | newline         |
| `\r`   | carriage return |
| `\t`   | horizontal tab  |
| `\\`   | backslash       |
| `\"`   | double quote    |
| `\'`   | single quote    |

Other escape sequences are invalid in Sol 0.1.

### Null literal

`null` denotes a pointer that addresses no object. It has no independent
source type and requires a contextual `pointer<T>` type, for example from a
variable declaration, assignment, parameter, return type, struct field or
comparison with a typed pointer:

```sol
let data: pointer<int> = null

if data == null then
    return 0
end
```

Using `null` where no pointer type can be determined is invalid. There is no
implicit conversion from `null` to an integer, string, struct or other
non-pointer value.

## Expressions and operators

Sol 0.1 is statically typed and performs no implicit conversion between
primitive types.

For example, an `int` is not implicitly converted to `float`, and a numeric
value is not implicitly converted to `boolean`.

### Primary expressions

Primary expressions include:

* literals;
* names;
* namespace-qualified names;
* parenthesized expressions;
* struct construction expressions;
* pointer dereference and indexing expressions.

Examples:

```sol
42
value
io::print_line
(value + 1)
Point { x: 10, y: 20 }
```

A namespace-qualified name contains exactly one namespace qualifier and one
member:

```sol
namespace::function
```

Qualification cannot be chained:

```sol
a::b::function
```

is not valid Sol 0.1 syntax.

### Function calls

A callable expression is followed by `(`, zero or more comma-separated
arguments, and `)`:

```sol
show()
add(1, 2)
io::print_line("Hello")
identity<int>(42)
```

Parameters are separated by commas:

```sol
fn add(left: int, right: int) -> int
```

Parameter lists may span multiple source lines:

```sol
fn add(
    left: int,
    right: int
) -> int
    return left + right
end
```

Newlines may appear immediately after `(`, after a parameter, after `,`, and
before `)`.

A single trailing comma is permitted in both single-line and multiline
parameter lists:

```sol
fn add(left: int, right: int,) -> int
    return left + right
end
```

```sol
fn add(
    left: int,
    right: int,
) -> int
    return left + right
end
```

A trailing comma does not introduce an additional parameter. Multiple trailing
commas and lists containing only a comma are invalid.

### Operator precedence

From highest precedence to lowest, Sol 0.1 expressions are grouped as follows:

| Precedence | Operators / form                | Associativity |
| ---------: |---------------------------------|---------------|
|          1 | postfix call, construction, field access, indexing | left |
|          2 | unary `!`, unary `-`, unary `+`, dereference `*` | right |
|          3 | `*`, `/`, `%`                   | left          |
|          4 | `+`, `-`                        | left          |
|          5 | `<`, `<=`, `>`, `>=`            | left          |
|          6 | `==`, `!=`                      | left          |
|          7 | `&&`                            | left          |
|          8 | `\|\|`                          | left          |

Parentheses override the normal precedence:

```sol
(1 + 2) * 3
```

### Unary operators

| Operator | Operand   | Result    |
| -------- | --------- | --------- |
| `!`      | `boolean` | `boolean` |
| `-`      | `int`     | `int`     |
| `-`      | `float`   | `float`   |
| `+`      | `int`     | `int`     |
| `+`      | `float`   | `float`   |
| `*`      | `pointer<T>` | `T`    |

### Arithmetic operators

| Operators          | Operands         | Result  |
| ------------------ | ---------------- | ------- |
| `*`, `/`, `+`, `-` | `int`, `int`     | `int`   |
| `*`, `/`, `+`, `-` | `float`, `float` | `float` |
| `%`                | `int`, `int`     | `int`   |

Mixed numeric operations are invalid:

```sol
1 + 2.0
```

Sol 0.1 does not perform numeric promotion.

### Relational operators

| Operators            | Operands         | Result    |
| -------------------- | ---------------- | --------- |
| `<`, `<=`, `>`, `>=` | `int`, `int`     | `boolean` |
| `<`, `<=`, `>`, `>=` | `float`, `float` | `boolean` |

`char`, `boolean` and `string` do not participate in relational operations.

### Equality operators

The equality operators are:

```text
==
!=
```

Sol 0.1 defines them for matching operands of the following types:

```text
int
float
boolean
char
pointer<T>
```

The result has type `boolean`.

The two operands must have exactly the same type. Pointer equality compares
addresses, including equality with a contextually typed `null`; it does not
compare pointee contents.

String equality and inequality are not defined in Sol 0.1. In particular, a
native implementation must not substitute pointer identity for string-content
equality.

### Logical operators

| Operators | Operands             | Result    |
|-----------|----------------------|-----------|
| `&&`      | `boolean`, `boolean` | `boolean` |
| `\|\|`    | `boolean`, `boolean` | `boolean` |

Logical operators require `boolean` operands. Sol 0.1 has no truthiness
conversion for numeric, character or string values.

### Unsupported expression operations

Sol 0.1 does not define:

* implicit primitive conversions;
* string concatenation;
* string equality;
* function values;
* closures;
* class or object member dispatch;
* chained namespace qualification.

Invalid expressions may internally use the compiler's semantic error type so
that analysis can continue and report independent diagnostics. That error type
is a compiler recovery mechanism and is not a Sol source-language type.

## Statements and blocks

Function bodies in Sol 0.1 contain statements.

The procedural bootstrap supports the following statement forms:

* local variable declarations;
* assignments;
* struct field assignments;
* pointer dereference and index assignments;
* function-call statements;
* `if` conditionals;
* `while` loops;
* `return` statements.

Consecutive statements are separated by newlines. Semicolons are not statement
terminators in Sol 0.1.

Blank lines are permitted between statements.

Blocks are delimited by the syntax of the construct that owns them and are
closed with `end`.

For example:

```sol
fn example() -> int
    let value: int = 10

    if value > 5 then
        return value
    end

    return 0
end
```

The body of a function forms a function scope. The bodies introduced by `if`,
`else` and `while` form nested block scopes.

## Variables and assignments

Every Sol 0.1 local variable declaration has:

* a name;
* an explicit value type;
* an initializer.

The supported forms are:

| Form                       | Mutability |
| -------------------------- | ---------- |
| `const name: T = value`    | immutable  |
| `let name: T = value`      | immutable  |
| `@mut let name: T = value` | mutable    |

Examples:

```sol
const answer: int = 42
let name: string = "Sol"
@mut let counter: int = 0
```

Local variables cannot have type `void`.

The initializer must have exactly the declared type:

```sol
let count: int = 10
```

is valid, while:

```sol
let count: int = 10.0
```

is invalid.

Sol 0.1 performs no implicit conversions during initialization.

### Immutable locals

Both `const` and ordinary `let` declarations are immutable after
initialization:

```sol
let value: int = 1
value = 2
```

is invalid.

Likewise:

```sol
const value: int = 1
value = 2
```

is invalid.

Sol 0.1 preserves the distinction between `const` and `let` in the language
model, but the procedural bootstrap does not require a `const` initializer to
be evaluated at compile time.

### Mutable locals

A local declared with `@mut let` may be reassigned:

```sol
@mut let value: int = 1
value = 2
```

The assigned expression must have exactly the declared type.

For example:

```sol
@mut let value: int = 1
value = 2.0
```

is invalid.

Assignment targets are local-variable names or fields rooted in local
variables. Assignment to functions, namespaces and other non-variable symbols
is invalid.

Function parameters are also immutable and cannot be assignment targets.

## Struct value types

Sol 0.1.1 adds `struct` as the user-defined data-model primitive required by
the self-hosted compiler.

A struct declaration contains a name and zero or more ordered fields:

```sol
struct SourcePosition
    offset: int
    line: int
    column: int
end
```

Each field has a unique name and an explicit value type. A field cannot have
type `void`. Struct fields may use another struct type, which permits nested
value models:

```sol
struct SourceSpan
    start: SourcePosition
    end: SourcePosition
end
```

Direct or indirect recursive by-value struct layouts are invalid. A pointer
breaks the by-value cycle, so recursive structures such as a
`pointer<Node>` field are valid.

Struct names share the module declaration namespace with functions and cannot
reuse a built-in type name. Duplicate top-level names are invalid. A directly
injected struct may be used by its unqualified name; namespace-qualified type
syntax is not defined in 0.1.1.

### Construction

A struct value is constructed with braces and named field initializers:

```sol
let position: SourcePosition = SourcePosition {
    offset: 0,
    line: 1,
    column: 1,
}
```

Initializers may appear in any order and are evaluated in source order. Every
declared field must be initialized exactly once. Missing, duplicate and unknown
field initializers are errors. An initializer value must have exactly the field
type; Sol performs no implicit conversion.

The empty form is valid for a struct with no fields:

```sol
struct Marker
end

let marker: Marker = Marker {}
```

### Field access

The `.` operator reads a named field:

```sol
position.line
span.start.column
```

Field access may be chained through nested structs. Accessing an unknown field
or applying `.` to a non-struct value is invalid.

### Field mutation

Fields may be updated only when the complete access path is rooted in an
`@mut let` local:

```sol
@mut let position: SourcePosition = SourcePosition {
    offset: 0,
    line: 1,
    column: 1,
}

position.line = 2
```

Nested mutation updates the containing value:

```sol
@mut let span: SourceSpan = initial_span
span.start.line = 2
```

Ordinary `let`, `const` and parameters are immutable, including all of their
nested fields. The assigned value must have exactly the field type.

### Value semantics

Structs have value semantics. They have no object identity, hidden object
header or implicit heap allocation. Initialization, assignment, argument
passing and return copy the complete struct value. Mutating one copy does not
mutate another copy.

Field order in the declaration defines the canonical Sol IR and native layout
order. Source construction order does not change layout. Layout is deterministic
for a fixed target ABI.

Structs do not introduce classes, inheritance, methods, virtual dispatch,
constructors, destructors or the Sol 0.2 object model.

## Minimal generics

Sol 0.1.1 provides compile-time generics for bootstrap data structures and
functions. Generic structs and functions declare one or more type parameters
between `<` and `>`:

```sol
struct Pair<A, B>
    first: A
    second: B
end

fn identity<T>(value: T) -> T
    return value
end
```

Type parameters are visible only inside their owning struct or function. They
may appear wherever that declaration accepts a value type, including nested
generic types:

```sol
struct Box<T>
    value: T
end

struct Envelope<T>
    payload: Box<T>
end
```

Type-parameter names must be unique within one declaration. A type parameter
does not define operators, methods, constraints or runtime behavior.

### Type application

A generic type must always receive its exact number of explicit type
arguments:

```sol
Pair<int, string>
Box<Pair<int, string>>
```

Omitting an argument, supplying extra arguments or applying type arguments to
a non-generic type is invalid. `void` cannot be used as a type argument because
it is not a value type.

Generic struct construction uses the same explicit type application:

```sol
let pair: Pair<int, string> = Pair<int, string> {
    first: 42,
    second: "Sol",
}
```

The field types of a concrete struct are obtained by substituting its type
arguments for the declaration's type parameters. All normal construction,
access, mutation and value-semantic rules then apply to the concrete type.

### Generic calls

Generic functions require explicit type arguments at every call site, even
when those arguments could be inferred from values:

```sol
identity<int>(42)
utilities::identity<string>("Sol")
```

`identity(42)` is invalid when `identity` is generic. Conversely, type
arguments cannot be supplied to a non-generic function.

Parameter and return types are checked after substituting the explicit type
arguments. Generic declarations retain their canonical identity across direct
and namespace injections.

### Compile-time monomorphization

Generics have no runtime representation in Sol 0.1.1. The compiler
monomorphizes every reachable concrete function and struct instantiation into
ordinary typed Sol IR. Different concrete argument lists produce distinct,
deterministically named specializations and layouts.

Unused open generic declarations do not produce open IR functions or runtime
type metadata. A generic function may recursively call the same concrete
specialization. A recursive call chain that requests a different specialization
of a function is rejected because it could require an unbounded set of
compile-time instantiations.

Sol 0.1.1 does not define:

* implicit generic argument inference;
* bounds, traits or interfaces on type parameters;
* variance;
* higher-kinded types;
* specialization rules;
* runtime type reification;
* generic overload resolution.

## Raw pointers and manual memory

Sol 0.1.1 provides `pointer<T>` as an explicitly unsafe, typed raw pointer for
bootstrap containers and compiler-owned buffers. `T` must be a value type;
`pointer<void>` is invalid. Pointer types are invariant and distinct:
`pointer<int>` and `pointer<boolean>` cannot be assigned, compared or converted
to one another.

Pointers are ordinary copyable values. Copying a pointer copies only its
address and creates no ownership, lifetime or uniqueness relationship.
`pointer<T>` has no automatic allocation, initialization or destruction.

Sol 0.1.1 deliberately does not provide:

* implicit or explicit pointer/integer conversion;
* unrestricted conversion between different pointer element types;
* a universal `void*` escape type;
* pointer arithmetic;
* automatic bounds or liveness checks;
* `ref<T>`, `borrow<T>`, lifetimes, a borrow checker or strict ownership.

Those safer reference and ownership facilities are reserved for future
language design.

### Dereference and indexing

Unary `*` loads the element addressed by a pointer:

```sol
let first: int = *values
```

Indexing computes the address of the indexed element and loads it:

```sol
let third: int = values[2]
```

The index must have type `int`. Indexing is the only source-level offset
operation in Sol 0.1.1; arbitrary `pointer + int` arithmetic is not defined.

Both forms may be assignment targets:

```sol
*values = 10
values[1] = 20
```

Mutating the addressed storage does not rebind the pointer value, so it is
permitted through a pointer held in an immutable local or parameter. The
stored value must have exactly type `T`.

Dereferencing or indexing a null, dangling, freed or otherwise invalid pointer
has undefined behavior. A negative or out-of-bounds index also has undefined
behavior. The implementation is not required to diagnose those operations.
Reading an element before a valid `T` value has been stored in its bytes has
undefined behavior.

### Pointer equality

`==` and `!=` are defined for two values of the same `pointer<T>` type. They
compare addresses. A pointer compares equal to `null` exactly when it addresses
no object. Ordering comparisons are not defined for pointers.

### Allocation responsibility

Every successful allocation must eventually be released exactly once unless
its lifetime intentionally extends until process termination. The programmer
is responsible for retaining the only pointer needed to release an allocation,
updating stale aliases after reallocation and preventing leaks, double-free,
use-after-free and overlapping mutable access.

These rules describe programmer obligations, not checks performed by the Sol
0.1.1 compiler.

## Declaration visibility

A local variable becomes visible only after its initializer has been analyzed.

Consequently, the declaration being introduced is not visible inside its own
initializer:

```sol
let value: int = value
```

does not refer to the newly declared `value`.

If an outer scope already contains a visible declaration with that name, normal
outer-scope lookup applies while the initializer is analyzed.

## Scopes and name visibility

Sol 0.1 uses lexical scopes.

A function scope contains its parameters and locals declared directly in the
function body.

Each `if`, `else` and `while` body introduces a child block scope.

A name declared in a nested scope may shadow a name from an outer scope:

```sol
fn example(value: int) -> int
    if true then
        let value: int = 42
        return value
    end

    return value
end
```

The inner `value` exists only inside the `if` block.

Two declarations with the same name in the same scope are invalid.

Names are resolved from the innermost active scope outward through enclosing
scopes.

A local declared inside a nested block is not visible after that block ends.

## Conditional statements

A conditional statement uses `if`, `then` and `end`:

```sol
if condition then
    statements
end
```

The condition must have type `boolean`.

Sol 0.1 does not perform truthiness conversion, so values such as `0`, `1`,
`""` or `"text"` cannot be used directly as conditions.

An optional `else` block may be provided:

```sol
if condition then
    statements
else
    statements
end
```

A newline is required after `then` and after `else`.

The `then` and `else` bodies have separate nested scopes.

Sol 0.1 does not define a dedicated `else if` or `elif` construct. Equivalent
control flow can be expressed by nesting another `if` inside an `else` block.

## While loops

A `while` loop has the form:

```sol
while condition do
    statements
end
```

The condition must have type `boolean`.

A newline is required after `do`.

The loop body forms a nested lexical scope.

The condition is evaluated before each iteration. If it evaluates to `false`,
execution continues after the loop.

Sol 0.1 does not define `break`, `continue` or loop labels.

## Functions, calls, and returns

### Function declarations

A function with a Sol body is declared with `fn`:

```sol
fn add(left: int, right: int) -> int
    return left + right
end
```

Every function declaration has:

* a name;
* an optional type-parameter list;
* a parameter list;
* an explicit return type.

The parameter list may be empty:

```sol
fn answer() -> int
    return 42
end
```

Parameters are separated by commas:

```sol
fn add(left: int, right: int) -> int
```

Trailing commas are not supported.

Every parameter has an explicit value type. Parameters cannot have type
`void`.

Parameters are immutable inside the function.

Function names must be unique among functions declared directly in the same
module. Function overloading is not part of the Sol 0.1 procedural bootstrap.

A generic function places its type parameters after its name:

```sol
fn choose<T>(value: T) -> T
    return value
end
```

### Bodyless function declarations

A function signature without a Sol body is declared with `@fn`:

```sol
@fn external(value: int) -> int
```

A bodyless declaration contains the same function name, parameter and return
type information as a bodyful function, but does not provide a Sol
implementation.

The declaration ends at a newline or end of file and is not followed by `end`.

Bodyless declarations are used by compiler-provided procedural interfaces such
as the Sol 0.1 standard-library modules.

A native executable can call a bodyless function only when its implementation
is supplied by the compiler, runtime or native environment. Sol 0.1 does not
define a general user-facing native foreign-function ABI.

### Function annotations

Annotations associated with a function appear before the `fn` declaration,
one annotation per line:

```sol
@init
fn launch() -> int
    return 0
end
```

Multiple annotations may precede a declaration.

The executable-entry-point semantics of `@init` are defined separately in this
specification.

`@fn` is the Sol 0.1 marker for a bodyless function declaration.

### Calls

A function call supplies zero or more positional arguments:

```sol
show()
add(1, 2)
```

A call to a generic function supplies its explicit type arguments before the
value argument list:

```sol
identity<int>(42)
```

Namespace-injected functions use the same call syntax after qualification:

```sol
csl::print_line("Hello")
```

A call must provide exactly the number of parameters declared by the target
function.

Arguments are matched to parameters by position.

Each argument must have exactly the corresponding parameter type. Sol 0.1 does
not perform implicit argument conversions.

For example, given:

```sol
fn calculate(value: float) -> float
    return value
end
```

the call:

```sol
calculate(1.0)
```

is valid, while:

```sol
calculate(1)
```

is invalid.

A call to a value-returning function may appear as part of an expression:

```sol
let result: int = add(1, 2)
```

A function call may also appear as a standalone statement:

```sol
csl::print_line("Hello")
```

Sol 0.1 does not provide function values, closures or indirect calls.

### Return statements

A function returning `void` may return without a value:

```sol
fn show() -> void
    return
end
```

A bare return from a value-returning function is invalid.

A value-returning function uses:

```sol
return expression
```

The returned expression must have exactly the function's declared return type.

For example:

```sol
fn answer() -> int
    return 42
end
```

is valid, while returning a `float`, `boolean`, `char` or `string` from that
function is invalid.

A function returning `void` cannot return a value.

Return statements are valid inside nested `if`, `else` and `while` blocks and
always return from the containing function.

The Sol 0.1 semantic analyzer validates every explicit `return` statement but
does not perform complete control-flow analysis to prove that every possible
execution path of a value-returning function reaches a return statement.

## Modules and injections

Sol 0.1 source files participate in named modules.

A module name contains one or more case-sensitive identifier segments separated
by `.`:

```sol
application
utilities.math
company.project.utilities
```

Sol 0.1 source syntax does not contain a `module` declaration. Module identity
is assigned by the compilation environment.

For command-line compilation, the Java bootstrap compiler derives and discovers
modules from the source filesystem as described below.

Every function declared directly at the top level of a module is exported by
that module. This includes both bodyful `fn` declarations and bodyless `@fn`
declarations.

Injected functions are not automatically re-exported from the module that
injects them. Only functions declared directly by a module form that module's
exports.

### Filesystem module discovery

When compiling an executable from an explicitly supplied source file, the
directory containing that file becomes the filesystem module root.

For example:

```text
project/
├── main.sol
├── helper.sol
└── utilities/
    └── math.sol
```

Compiling:

```text
project/main.sol
```

uses:

```text
project/
```

as the filesystem module root.

The entry source module takes its name from the source filename without the
`.sol` extension. In the previous example, `main.sol` is the entry module
`main`.

An injected module path is mapped to a source path relative to the filesystem
module root.

For example:

```sol
inject helper
inject utilities.math
```

maps to:

```text
helper          → project/helper.sol
utilities.math  → project/utilities/math.sol
```

Each module-path segment corresponds to one filesystem path segment, with the
final segment receiving the `.sol` extension.

Filesystem module lookup is case-sensitive at the Sol language level. Actual
filesystem behavior may impose additional host-platform restrictions.

Injected modules are discovered recursively.

A module is registered before its own injections are recursively discovered.
Consequently, cyclic function-level module dependencies terminate normally
during source discovery:

```text
a.sol → inject b
b.sol → inject a
```

Sol 0.1 therefore permits cyclic module dependencies when they are used only to
resolve function declarations and calls.

Module-level executable initialization and initialization-order semantics are
not defined in Sol 0.1.

If a filesystem module referenced by an injection does not exist, source
discovery does not invent or substitute another module. Semantic analysis
reports the unresolved module at the injection site.

The current filesystem module-root convention belongs to the Sol 0.1 bootstrap
environment. A future package or project system may define module roots and
module discovery differently.

### Compiler-provided modules

The compiler may provide bundled modules that do not correspond to files in the
user's module root.

When resolving an injected module, the bootstrap compiler checks for a bundled
module before attempting filesystem resolution.

Consequently, when the compiler provides a module with a particular qualified
name, a project-local source file with the same module name does not override
the bundled module.

The compiler-provided Sol 0.1 standard-library modules are documented in the
Standard library section.

### Direct injections

A direct injection introduces exported functions from another module into the
current module scope:

```sol
inject utilities.math
```

Without an `only` clause, every function declared directly by the target module
is considered for injection.

For example, given a target module containing:

```sol
fn add(left: int, right: int) -> int
    return left + right
end

fn subtract(left: int, right: int) -> int
    return left - right
end
```

the declaration:

```sol
inject utilities.math
```

makes both `add` and `subtract` directly available:

```sol
let result: int = add(1, 2)
```

Injected functions retain the canonical identity, signature and declaring
module of their original declarations.

Injection does not copy or create a new function declaration.

### Selective direct injections

A direct injection may select individual exported functions with `only`:

```sol
inject utilities.math only add
```

Multiple names are comma-separated:

```sol
inject utilities.math only add, subtract
```

Trailing commas are not supported.

Only functions declared directly by the target module may be selected.

If an `only` clause names a function that the target module does not export,
semantic analysis reports an error for that selected symbol.

An injected symbol is not re-exported merely because another module injected
it. Consequently, injections do not create implicit transitive exports.

For example, if module `b` contains:

```sol
inject a
```

and module `c` contains:

```sol
inject b
```

functions declared by `a` are not made available to `c` through `b` unless `b`
declares its own corresponding functions.

### Namespace injections

A namespace injection introduces one namespace symbol instead of introducing
the target module's individual functions:

```sol
inject namespace std.console
```

Without an explicit alias, the namespace name is the final segment of the
target module name.

Therefore:

```sol
inject namespace std.console
```

introduces the namespace:

```text
console
```

An explicit alias is introduced with `as`:

```sol
inject namespace std.console as csl
```

The target module's exported functions are then accessed with `::`:

```sol
csl::print_line("Hello")
```

A namespace symbol is a compile-time name. It is not a runtime value and cannot
be stored in a variable, passed as an argument or returned from a function.

Sol 0.1 supports exactly one namespace qualifier followed by one function name:

```sol
namespace::function
```

Chained namespace qualification is not supported:

```sol
a::b::function
```

A namespace-qualified member must refer to a function exported directly by the
target module.

### Injection name conflicts

All functions declared directly by a module are registered before that module's
injections are resolved.

A local function therefore keeps its declaration when an injection attempts to
introduce the same name.

For example:

```sol
inject utilities.math

fn add(left: int, right: int) -> int
    return 0
end
```

is a duplicate-name error if `utilities.math` also exports `add`; the local
function remains the module's local declaration.

Likewise, two injections cannot successfully introduce the same name into one
module scope.

Namespace names participate in the same module-level name space as directly
available functions.

Consequently, conflicts may occur between:

* local function names;
* directly injected function names;
* namespace names or aliases.

Such conflicts are compile-time errors.

### Program-wide module resolution

After source discovery has collected the participating modules, semantic
analysis resolves the program in program-wide phases.

Local functions from every participating module are predeclared before
injections are resolved and before function bodies are analyzed.

This allows:

```sol
fn first() -> int
    return second()
end

fn second() -> int
    return 0
end
```

and allows functions in mutually dependent discovered modules to refer to each
other where the required injections make those names visible.

Function signatures are bound before function bodies, so calls are checked
against the resolved canonical declarations rather than source order.

Module scopes are lexical compiler scopes and are not runtime objects.

## Executable entry point

A complete Sol executable identifies exactly one entry function with the exact,
case-sensitive `@init` annotation:

```sol
@init
fn launch() -> int
    return 0
end
```

The function name itself has no special meaning.

For example, functions named `main`, `init` or `start` remain ordinary
functions unless they carry `@init`.

### Entry-point uniqueness

A complete executable must contain exactly one function declaration annotated
with `@init` across all participating modules.

The annotated function may be declared in any module discovered for the
program.

Injecting that function into another module does not create another entry
point. Entry-point identity belongs to the original declaration.

If no `@init` function exists, executable compilation fails.

If more than one function declaration is annotated with `@init`, executable
compilation also fails.

Repeating `@init` on the same function declaration still identifies that
declaration as one entry-point candidate.

Annotation names are case-sensitive:

```sol
@init
```

identifies an entry point, while:

```sol
@Init
@INIT
@initialize
```

do not.

### Entry-point type requirements

An entry-point function must:

* declare no type parameters;
* have a Sol function body;
* return the built-in `int` type.

For example:

```sol
@init
fn launch() -> int
    return 7
end
```

is a valid entry-point declaration.

A bodyless function cannot be an executable entry point:

```sol
@init
@fn native_launch() -> int
```

Likewise, an entry point returning `void`, `float`, `boolean`, `char` or
`string` is invalid.

The return value represents the program's process status.

By convention:

```text
0      successful program execution
non-0  application-defined status or failure
```

### Entry-point parameters

The Sol 0.1 language model permits ordinary parameters on an entry-point
function:

```sol
@init
fn launch(argument: string, retries: int) -> int
    return 0
end
```

Such parameters follow the same type and immutability rules as parameters on
any other function.

Sol 0.1 does not define a language-level restriction requiring an `@init`
function to have zero parameters.

However, the current native startup bridge does not yet define how operating
system process arguments are converted into Sol values.

Consequently, the Java bootstrap compiler can currently produce a native
executable only when the selected entry function has no parameters:

```sol
@init
fn launch() -> int
    return 0
end
```

A parameterized entry point remains valid at the semantic and typed Sol IR
levels, but native LLVM compilation reports that startup argument binding is not
yet supported.

This distinction is intentional: the general Sol language and IR model do not
invent an operating-system argument ABI before one is specified.

### Native process status

In the current native backend, Sol `int` is represented as a 64-bit signed
integer.

The generated platform-visible native `main` function calls the parameterless
Sol entry function and converts its result to the native 32-bit process-status
type.

Conceptually:

```text
Sol @init function
        │
        │ returns Sol int
        ▼
native startup bridge
        │
        │ converts to native process status
        ▼
operating system
```

The exact LLVM representation and linker behavior are backend concerns and are
documented in `native-executables.md`.

### Library and partial-program analysis

Not every set of Sol modules represents a complete executable.

Semantic analysis may be performed on:

* libraries;
* editor buffers;
* individual modules;
* tests;
* incomplete module graphs.

Those forms of analysis do not require an `@init` declaration.

When an `@init` declaration is present, its declaration-level requirements may
still be validated.

Executable compilation, in contrast, requires exactly one valid entry point.

## Standard library

Sol 0.1 includes a minimal compiler-provided procedural standard library.

Standard-library modules live under the reserved `std` module namespace and
are bundled with the compiler.

They do not require corresponding source files in the user's filesystem module
root.

Bundled standard-library modules are resolved before project-local filesystem
modules. A local source file therefore cannot override a bundled module with the
same qualified module name.

The Sol 0.1 procedural bootstrap currently provides:

```text
std.console
std.file
std.memory
```

These modules expose bodyless Sol function declarations whose native
implementations are supplied by the compiler backend.

## `std.memory`

The raw allocation module is:

```text
std.memory
```

It exports:

```sol
@fn allocate<T>(count: int) -> pointer<T>
@fn reallocate<T>(value: pointer<T>, count: int) -> pointer<T>
@fn free<T>(value: pointer<T>) -> void
```

The conventional namespace alias is `memory` or the shorter `mem`:

```sol
inject namespace std.memory as mem
```

`allocate<T>(count)` reserves storage aligned for `T` and large enough for
`count` contiguous elements. The bytes are uninitialized. A zero or negative
count, a zero-sized element type, byte-size overflow or allocator failure
returns `null`.

`reallocate<T>(value, count)` changes the requested element capacity. When
`count` is zero it releases `value` and returns `null`; this also applies when
`value` is already `null`. A negative count, zero-sized element type or
byte-size overflow returns `null` without releasing or changing a non-null
input allocation. For a positive valid count, passing `null` behaves as a new
allocation. On success, the byte prefix shared by the old and new sizes is
preserved and every previous alias must be treated as invalid. On allocation
failure it returns `null` and the original allocation remains valid and must
still be released.

`free<T>(value)` releases an allocation returned by a successful matching
allocation operation. `free<T>(null)` is a no-op. Passing an interior pointer,
a pointer of the wrong allocation type, a pointer not returned by the allocator
or a pointer already freed has undefined behavior. After a successful free,
all aliases to that allocation are dangling.

On every supported Sol 0.1.1 target, `int` and the native pointer width are 64
bits. Allocation byte counts are checked before calling the host allocator,
and the host allocator supplies alignment sufficient for every currently
representable Sol value type.

## `std.console`

The console module is:

```text
std.console
```

It exports:

```sol
@fn print(value: string) -> void
@fn print_line(value: string) -> void
```

The conventional namespace alias used by Sol 0.1 programs is `csl`:

```sol
inject namespace std.console as csl
```

For example:

```sol
inject namespace std.console as csl

@init
fn launch() -> int
    csl::print("Hello ")
    csl::print_line("Sol")
    return 0
end
```

### `print`

```sol
csl::print(value)
```

writes the supplied `string` to the process standard output without adding a
line terminator.

### `print_line`

```sol
csl::print_line(value)
```

writes the supplied `string` to standard output and then writes a newline.

Console strings are emitted as UTF-8 data.

For example:

```sol
csl::print_line("Sol ñ")
```

preserves the UTF-8 text represented by the Sol string.

`csl` is only a namespace alias. It is not a runtime console object.

Console input is not part of the Sol 0.1 procedural standard library.

## `std.file`

The filesystem module is:

```text
std.file
```

It exports:

```sol
@fn exists(path: string) -> boolean
@fn write_text(path: string, content: string) -> boolean
@fn append_text(path: string, content: string) -> boolean
```

The conventional namespace alias is `file`:

```sol
inject namespace std.file as file
```

For example:

```sol
inject namespace std.file as file

@init
fn launch() -> int
    if file::write_text("output.txt", "Hello") then
        file::append_text("output.txt", " Sol")
        return 0
    else
        return 1
    end
end
```

### `exists`

```sol
file::exists("data.txt")
```

tests whether the supplied path identifies a file that can be opened by the
native implementation.

It returns:

```text
true   when the file can be opened
false  when it cannot be opened
```

`exists` does not return file contents.

### `write_text`

```sol
file::write_text("data.txt", "Hello")
```

writes the complete supplied string to the target file.

If the file does not exist, it is created.

If the file already exists, its previous contents are replaced.

The function returns `true` only when the complete string is written and the
file is successfully closed. Otherwise, it returns `false`.

### `append_text`

```sol
file::append_text("data.txt", " Sol")
```

writes the supplied string at the end of the target file.

If the file does not exist, it is created.

If it already exists, its existing contents are preserved.

The function returns `true` only when the complete string is appended and the
file is successfully closed. Otherwise, it returns `false`.

### File text encoding

Text written through `std.file` uses the UTF-8 bytes represented by the Sol
`string`.

The string's byte length determines the amount of file data written.

Sol strings passed to these functions are not interpreted as
NUL-terminated C strings.

### Path resolution

Relative file paths are interpreted relative to the current working directory
of the generated native process.

For a directly executed compiled program, this is the working directory from
which that executable is launched unless the surrounding environment changes
it.

For:

```text
sol run program.sol
```

the program inherits the working directory of the `sol` process.

Temporary compilation storage used by `sol run` therefore does not change the
meaning of relative paths.

Absolute paths are interpreted according to the host operating system.

### Current filesystem limitations

Sol 0.1 does not define:

* reading file contents into a `string`;
* filesystem directory APIs;
* file objects;
* explicit file handles in source code;
* a portable path abstraction;
* runtime filesystem exceptions.

The procedural API reports operation success through `boolean` results.

Reading dynamically sized file data will require string ownership and lifetime
semantics beyond the current Sol 0.1 bootstrap.

## Sol 0.1.1 procedural scope

Sol 0.1 intentionally defines a small procedural language used to bootstrap the
compiler and native execution model.

The version includes:

* primitive static types;
* primitive literals;
* user-defined struct value types;
* minimal generic structs and functions with explicit type arguments;
* compile-time monomorphization;
* typed raw pointers, pointer indexing and manual allocation;
* unary and binary expressions;
* lexical local scopes;
* immutable and mutable local variables;
* functions and bodyless function declarations;
* direct and namespace module injections;
* conditional statements;
* `while` loops;
* executable entry points;
* native compilation;
* minimal console, filesystem and raw-memory standard-library modules.

Sol 0.1 does not define:

* classes or objects;
* inheritance;
* interfaces;
* arrays;
* safe references, borrowing, lifetimes or strict ownership;
* generic inference, bounds, variance or runtime reification;
* function overloading;
* closures;
* first-class functions;
* exceptions;
* `break` or `continue`;
* string concatenation;
* string equality;
* automatic numeric conversions;
* package manifests;
* command-line argument binding for `@init`;
* console input;
* file-content reading.

Features outside this procedural bootstrap may be introduced by later Sol
language versions without changing the meaning of programs valid under the
Sol 0.1 specification.
