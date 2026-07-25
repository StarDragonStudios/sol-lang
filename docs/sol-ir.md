# Typed Sol intermediate representation

The typed Sol intermediate representation is the compiler layer between semantic analysis and native backend generation.

## Pipeline

```text
Sol source
→ lexer
→ parser
→ semantic analysis
→ typed Sol IR
→ LLVM IR
→ native object code
→ linker + Sol runtime
→ native executable
```

Sol IR represents a semantically validated program without depending on source syntax or a specific native backend.

## Package boundary

The IR is implemented under:

```text
io.github.stardragonstudios.sol.ir
```

The package must not depend on:

* lexer tokens
* parser nodes
* syntax-tree declarations or expressions
* semantic symbols
* LLVM classes
* C source-generation concepts
* platform ABI details

The semantic-to-IR lowering layer is responsible for converting frontend types, symbols and resolved expressions into canonical IR objects.

## Type model

The initial canonical primitive IR types are:

* `int`
* `float`
* `boolean`
* `char`
* `string`
* `void`

Primitive types are represented by `PrimitiveIrType`.

`void` is a valid function return type, but it is not a value type.

There is no IR error type. Programs containing semantic errors must not be lowered into Sol IR.

## Identity

Identifiers are deterministic objects rather than source names.

* `IrFunctionId` identifies a function globally inside an `IrProgram`.
* `IrBlockId` identifies a basic block inside an `IrFunction`.
* `IrValueId` identifies parameters, constants and instructions inside an `IrFunction`.

Source names may be retained for diagnostics and textual inspection, but they do not define IR identity.

## Programs and modules

`IrProgram` preserves module order.

A program may represent:

* a library, without an entry point;
* an executable, with a canonical `IrEntryPoint`.

Module names use value equality and preserve their ordered segments.

Functions preserve declaration order inside each module.

Function identifiers are globally unique across the program.

## Functions

An `IrFunction` contains:

* a deterministic function identifier;
* a diagnostic name;
* ordered typed parameters;
* an explicit return type;
* either no body or an ordered list of basic blocks.

A bodyless function represents a declaration whose implementation is provided outside the current IR program.

A defined function contains at least one basic block.

Parameter names and identifiers are unique inside a function.

All value identifiers are unique inside a function.

## Basic blocks

An `IrBasicBlock` contains:

* a deterministic block identifier;
* an ordered immutable instruction list;
* exactly one terminator.

A block cannot exist without a terminator.

Instructions cannot appear after the terminator because the terminator is stored separately from the instruction list.

Branch terminators and branch-target validation will be introduced with control-flow lowering.

## Values and instructions

Every `IrValue` has:

- an `IrValueId`;
- an explicit `IrType`.

`IrInstruction` represents an ordered operation inside a basic block.

An instruction does not necessarily produce a value. Operations such as future
stores, assignments, destruction operations and `void` calls may exist only as
instructions.

`IrValueInstruction` represents an instruction that also produces a typed
value.

The initial value forms are:

* logical negation;
* numeric negation;
* numeric positive.

The initial binary operations are:

* multiplication;
* division;
* remainder;
* addition;
* subtraction;
* relational comparisons;
* equality and inequality;
* logical conjunction and disjunction.

Operator constructors enforce their structural type rules immediately.

Semantic analysis remains responsible for reporting source diagnostics. IR validation treats invalid construction as a compiler error.

## Returns

`IrReturnTerminator` represents:

* a bare return;
* a return containing one typed value.

`IrFunction` validates return terminators against its declared return type.

A `void` function requires a bare return.

A value-returning function requires a returned value of the exact declared IR type.

## Entry point

An IR entry point references a canonical module and function.

The initial executable entry-point contract requires:

* a function belonging to the referenced module;
* a function body;
* an `int` return type.

Parameter-count and parameter-type policy remains established by semantic analysis.

## Immutability and validation

Public IR objects are immutable.

Collection-valued components use defensive immutable copies and preserve deterministic order.

Invalid construction fails immediately through `NullPointerException` or `IllegalArgumentException`, depending on whether the input is absent or structurally invalid.

## Textual inspection

`IrTextFormatter` provides deterministic text intended for:

* automated tests;
* compiler debugging;
* architecture inspection.

Example:

```text
program {
  entry @application::function0

  module @application {
    define @function0 launch(%0 argument: int) -> int {
      block0:
        %1: int = const 2
        %2: int = add %0, %1
        return %2
    }
  }
}
```

Primitive constants are printed before their first use in a function.

The textual form is a debugging and testing representation. It is not currently a stable serialization format and is not accepted as compiler input.

## Future extensions

The current design must permit later representation of:

* local storage;
* assignments;
* unconditional branches;
* conditional branches;
* loops;
* calls;
* structs;
* references;
* ownership operations;
* destruction;
* SSA values or a later SSA transformation layer.

LLVM-specific types, blocks, instructions and ownership rules belong exclusively to the LLVM backend.
