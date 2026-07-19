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
