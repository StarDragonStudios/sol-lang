# Sol 0.1 Language Specification

## Primitive types

Sol 0.1 defines the following primitive types:

| Type      | Category          | Initial purpose               |
|-----------|-------------------|-------------------------------|
| `int`     | numeric, integral | Signed whole-number values    |
| `float`   | numeric           | Floating-point values         |
| `boolean` | logical           | The values `true` and `false` |
| `char`    | character         | One character value           |
| `string`  | text              | Sequences of characters       |
| `void`    | non-value         | Absence of a returned value   |

Primitive type names are lowercase and case-sensitive. For example,
`int` is a primitive type, while `Int` and `Integer` are not.

Primitive type identity and semantics are defined by Sol itself. They are
not defined by Java classes, JVM runtime types, C types, or host-platform
implementation details.

The precise bit width, target memory layout, ABI representation, and C
lowering of primitive types are defined by later backend specifications.

The `void` type represents the absence of a returned value. Whether `void`
is valid in declarations other than function return types is determined by
later semantic validation.

## Expressions and operators

Sol 0.1 requires primitive operands to match exactly. There is no implicit
conversion between `int` and `float`.

### Unary operators

| Operator | Operand   | Result    |
|----------|-----------|-----------|
| `!`      | `boolean` | `boolean` |
| `-`      | `int`     | `int`     |
| `-`      | `float`   | `float`   |
| `+`      | `int`     | `int`     |
| `+`      | `float`   | `float`   |

### Arithmetic operators

| Operators          | Operands          | Result   |
|--------------------|-------------------|----------|
| `*`, `/`, `+`, `-` |  `int`, `int`     |  `int`   |
| `*`, `/`, `+`, `-` |  `float`, `float` |  `float` |
| `%`                |  `int`, `int`     |  `int`   |

### Relational operators

| Operators            | Operands         | Result    |
|----------------------|------------------|-----------|
| `<`, `<=`, `>`, `>=` | `int`, `int`     | `boolean` |
| `<`, `<=`, `>`, `>=` | `float`, `float` | `boolean` |

### Equality operators

| Operators  | Operands              | Result    |
|------------|-----------------------|-----------|
| `==`, `!=` |  matching value types | `boolean` |

### Logical operators

| Operators    | Operands             | Result    |
|--------------|----------------------|-----------|
| `&&`, `\|\|` | `boolean`, `boolean` | `boolean` |

The `char` type is not numeric and does not participate in arithmetic or
relational operations. Logical operators require `boolean` operands and do
not perform truthiness conversions.

String concatenation is not defined in Sol 0.1.

Invalid expressions recover internally to the semantic error type so that
semantic analysis can continue and report independent later errors.

## Variables and assignments

Every Sol 0.1 variable declaration has an explicit type and an initializer.

| Form                       | Meaning                        |
|----------------------------|--------------------------------|
| `const name: T = value`    | Immutable constant declaration |
| `let name: T = value`      | Immutable local variable       |
| `@mut let name: T = value` | Mutable local variable         |

Local variables cannot have type `void`.

A variable initializer must have exactly the declared semantic type. Sol does
not perform implicit conversions between `int`, `float`, or any other
primitive types.

Both `const` and ordinary `let` declarations are immutable after
initialization. Only a local declared with `@mut let` can be reassigned.

Function parameters are immutable.

The value assigned to a mutable local variable must have exactly the target's
declared type. Sol 0.1 performs no implicit assignment conversions.

Initialization is not considered reassignment. Whether a `const` initializer
can be evaluated at compile time is defined by later compiler work.

## Functions, calls, and returns

A bodyful function is declared with `fn`:

```sol
fn add(left: int, right: int) -> int
    return left + right
end
```

A bodyless function signature is declared with `@fn`:

```sol
@fn external(value: int) -> int
```

Every function parameter has an explicit value type. Parameters cannot have
type `void` and are immutable inside the function.

Every function has an explicit return type. The type `void` indicates that the
function returns no value.

Sol 0.1 calls only directly declared functions. Function values, closures, and
other callable objects are not supported.

A call must provide exactly one argument for every declared parameter.
Arguments are matched to parameters by position, and every argument type must
exactly match its parameter type. Sol 0.1 performs no implicit call
conversions.

A function returning `void` uses a bare return:

```sol
return
```

A value-returning function returns an expression whose type exactly matches
the declared return type:

```sol
return expression
```

Explicit return statements are validated in every nested block. Sol 0.1 does
not yet verify that every possible control-flow path reaches a return
statement.

## Modules and injections

A Sol module is identified by an external, case-sensitive module name. Module
names contain one or more identifier segments separated by `.`:

```sol
std.console
company.project.utilities
application
```

Module names are supplied to semantic analysis together with their parsed
compilation units. Sol 0.1 does not locate modules in the filesystem or derive
module names from source filenames.

Every function declared directly at the top level of a module is exported.
This includes bodyful `fn` declarations and bodyless `@fn` declarations.

Functions injected into a module are not automatically re-exported from that
module.

### Direct injections

A direct injection introduces the target module's exported functions as
unqualified names:

```sol
inject math.operations
```

A direct injection can select specific functions with `only`:

```sol
inject math.operations only add, subtract
```

Example:

```sol
inject math.operations only add

fn calculate() -> int
    return add(1, 2)
end
```

Without `only`, every function declared directly in the target module is
injected. With `only`, only the listed functions are injected.

Injected functions retain the identity and signature of their declarations in
the target module. Argument and return-type validation therefore behaves in
the same way as for locally declared functions.

Local declarations are registered before injections. A local function keeps
its name when an injection attempts to introduce a conflicting name. Conflicts
between local functions, directly injected functions, and namespace names are
compile-time errors.

### Namespace injections

A namespace injection introduces one namespace name instead of introducing
each function separately:

```sol
inject namespace std.console
```

Without an explicit alias, the namespace name is the final segment of the
module name. The previous declaration therefore introduces `console`.

An explicit namespace alias is declared with `as`:

```sol
inject namespace std.console as io
```

Functions in an injected namespace are accessed with `::`:

```sol
inject namespace std.console as io

fn show() -> void
    io::print_line("Hello")
    return
end
```

Namespace names and aliases are case-sensitive. A namespace is not a runtime
value and cannot be assigned, returned, or passed as an argument.

Sol 0.1 supports one namespace qualifier followed by one function name:

```sol
namespace::function
```

Chained qualification such as `a::b::function` is not supported.

### Module resolution

Semantic analysis receives all participating modules explicitly and performs
program-wide declaration phases:

1. register every module
2. create every module scope
3. predeclare every locally declared function
4. resolve every injection
5. bind every function signature
6. bind every function body
7. freeze every completed scope

Because all local functions are predeclared before injections and function
bodies are analyzed, forward references and mutually dependent modules can be
resolved.

Sol 0.1 does not reject cyclic module references at the function-declaration
level. Module-level executable initialization and initialization-cycle
detection are not defined.
