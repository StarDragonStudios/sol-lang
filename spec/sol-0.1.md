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
