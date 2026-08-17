# Typed Sol intermediate representation

The typed Sol intermediate representation is the compiler layer between semantic analysis and native backend generation.

## Pipeline

```text
Sol source
→ lexer
→ parser
→ semantic analysis
→ semantic-to-IR lowering
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

User-defined value structs are represented by `IrStructType`. A struct type
retains its qualified diagnostic name and ordered `IrStructField` definitions.
Field order is canonical and every field has an exact value type. Struct types
are non-numeric first-class values and may appear in locals, parameters, return
types and fields of other structs. Recursive by-value layouts are invalid, but
an `IrPointerType` field may refer back to a canonical forward-declared
`IrStructType` because the pointer does not embed the pointee layout.

`IrPointerType` contains the exact pointed-to `IrType`. It is a non-numeric
value type and preserves typed pointer distinctions throughout lowering even
though the LLVM backend uses opaque native pointers.

Source-level generic parameters never appear as open Sol IR types.
Semantic-to-IR lowering monomorphizes every reachable concrete generic function and struct
application. Each concrete struct application becomes its own `IrStructType`,
with type parameters substituted recursively through its ordered fields.

## Identity

Identifiers are deterministic objects rather than source names.

* `IrFunctionId` identifies a function globally inside an `IrProgram`.
* `IrBlockId` identifies a basic block inside an `IrFunction`.
* `IrLocalId` identifies local storage inside an `IrFunction`.
* `IrValueId` identifies parameters, constants and value-producing instructions inside an `IrFunction`.

Source names may be retained for diagnostics and textual inspection, but they do not define IR identity.

## Programs and modules

`IrProgram` preserves module order.

A program may represent:

* a library, without an entry point;
* an executable, with a canonical `IrEntryPoint`.

Module names use value equality and preserve their ordered segments.

Non-generic functions preserve declaration order inside each module. Reachable
generic specializations follow them in deterministic call-discovery order.

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

* a canonical `IrBlockTarget`;
* a deterministic `IrBlockId`;
* an ordered immutable instruction list;
* exactly one terminator.

A block cannot exist without a terminator.

Instructions cannot appear after the terminator because the terminator is stored
separately from the instruction list.

`IrBlockTarget` permits forward references and cyclic control-flow graphs
without making basic blocks mutable.

Block identifiers use deterministic value equality. Branch membership is
validated using the canonical target instance, preventing a terminator from
referencing an equivalent target belonging to another function.

A function rejects:

* duplicate block identifiers;
* repeated canonical block-target instances;
* branches to undeclared targets;
* branches to equivalent but non-canonical target wrappers.

## Control flow

Every `IrTerminator` exposes:

* its ordered value operands;
* its ordered basic-block targets.

The supported terminators are:

* `IrReturnTerminator`, for bare and value returns;
* `IrBranchTerminator`, for unconditional branches;
* `IrConditionalBranchTerminator`, for boolean conditional branches.

Conditional branch conditions must have exact type `boolean`.

Branch targets must be canonical targets declared by blocks of the same
function.

Sol IR supports forward branches, merge blocks, loop back-edges and nested
cyclic control-flow graphs without depending on LLVM block objects or textual
source labels.

## Values and instructions

Every `IrValue` has:

- an `IrValueId`;
- an explicit `IrType`.

`IrInstruction` represents an ordered operation inside a basic block.

An instruction does not necessarily produce a value. Local initialization,
local updates, `void` calls, future destruction operations and other side-effect
operations may exist only as instructions.

`IrValueInstruction` represents an instruction that also produces a typed
value.

The initial value forms are:

* logical negation;
* numeric negation;
* numeric positive;
* value-returning function calls;
* struct construction;
* struct field extraction;
* typed null constants;
* direct pointer loads;
* indexed pointer loads.
* Unicode-scalar string indexing.

`IrStructConstructInstruction` consumes one value per field in canonical field
order and produces the complete aggregate value. Semantic-to-IR lowering may
evaluate named source initializers in a different source order before arranging
their resulting values into canonical field order.

`IrStructFieldExtractInstruction` reads one canonical field from a struct
value. `IrStructFieldStoreInstruction` updates a non-empty field path rooted in
a mutable local. Nested field stores preserve every field outside that path.

`IrNullConstant` carries an exact `IrPointerType`. `IrPointerLoadInstruction`
and `IrPointerIndexLoadInstruction` produce the pointer element type.
`IrPointerStoreInstruction` and `IrPointerIndexStoreInstruction` are
side-effecting instructions whose stored value must exactly match that element
type; indexed forms additionally require an `int` index.

`IrStringIndexInstruction` consumes a `string` and an `int` scalar index and
produces `char`. String concatenation and exact content equality use typed
`IrBinaryInstruction` values with `string` operands; their result types are
`string` and `boolean`, respectively.

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

## Local storage

`IrLocal` represents target-independent function-local storage.

Every local contains:

* a deterministic `IrLocalId`;
* a diagnostic source name;
* an explicit value type;
* an `IrLocalKind`.

The supported local kinds are:

* `CONSTANT`, corresponding to `const`;
* `IMMUTABLE`, corresponding to ordinary `let`;
* `MUTABLE`, corresponding to `@mut let`.

An `IrLocal` is not an `IrValue` and does not represent a native address,
pointer or stack slot.

Local storage is manipulated through:

* `IrLocalInitializeInstruction`, which initializes one local without producing
  a value;
* `IrLocalLoadInstruction`, which reads one local and produces a typed value;
* `IrLocalStoreInstruction`, which updates mutable local storage without
  producing a value;
* `IrStructFieldStoreInstruction`, which replaces one direct or nested field of
  a mutable struct local without producing a value.

Initialization and storage operations require exact type equality.

A store can only target an `IrLocal` whose kind is `MUTABLE`. Constants and
immutable locals cannot be represented as valid store targets.

Local identifiers and value identifiers belong to independent identifier
spaces. `local0` and `%0` may therefore coexist in the same function.

Source names do not define local identity. Distinct shadowed locals may preserve
the same diagnostic name while retaining different `IrLocalId` values and
different canonical instances.

Function validation requires every referenced local instance to be introduced
by exactly one local-initialization instruction. Duplicate local identifiers,
duplicate initialization and references to undeclared local instances are
rejected.

Sol IR does not prescribe whether a local becomes an LLVM `alloca`, an SSA
value, a register, a native stack slot or another backend representation.

## Function references and calls

`IrFunctionReference` represents the target-independent identity and typed
signature of a called function.

A function reference contains:

* the global `IrFunctionId`;
* the diagnostic function name;
* the ordered parameter types;
* the return type.

Function references do not contain:

* function bodies;
* LLVM symbols or blocks;
* native linker names;
* calling conventions;
* ABI metadata.

Every semantic `FunctionSymbol` receives one canonical
`IrFunctionReference` before function bodies are lowered. Direct calls,
directly injected calls and namespace-qualified calls that resolve to the same
semantic function therefore share the same canonical reference instance.

Call arguments preserve source evaluation order.

`IrValueCallInstruction` represents a call whose function returns a value. It
is both an instruction and an `IrValue`.

`IrVoidCallInstruction` represents a call whose function returns `void`. It is
an instruction without a fabricated result value.

Call construction validates:

* argument count;
* argument order;
* exact argument types;
* whether the target returns a value or `void`.

Program validation additionally requires:

* every called function identifier to exist in the IR program;
* the referenced name to match the canonical function;
* parameter and return types to match the canonical function signature;
* all calls to one function to share one canonical function-reference instance.

Functions without bodies remain valid call targets when their declarations are
present in the program. Sol IR does not invent bodies for external functions.

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

## Semantic-to-IR lowering

Semantic-to-IR lowering is implemented under:

```text
io.github.stardragonstudios.sol.lowering
```

The public entry point is:

```java
IrProgramLowerer.lower(
    SemanticProgramAnalysisResult program
)
```

Lowering accepts a completed semantic program and produces an immutable
`IrProgram`.

Programs containing semantic errors are rejected before any IR objects are
produced. Warnings do not prevent lowering.

### Boundary rules

The lowering layer may depend on syntax and semantic models because it is the
bridge between the frontend and Sol IR.

The `ir` package itself remains independent from:

* syntax-tree nodes;
* semantic symbols;
* diagnostics;
* source-name resolution;
* LLVM;
* native ABI details.

Lowering uses the canonical symbols and types already stored in the semantic
model. It does not resolve source names or type names again.

Function, parameter and entry-point associations are preserved by object
identity.

### Deterministic lowering

Lowering first discovers reachable concrete generic instantiations from every
non-generic function, following module, declaration and source call order. It
rejects recursive expansion into a different specialization before IR is
created.

It then materializes concrete struct layouts, assigns every ordinary function
or generic specialization a globally unique `IrFunctionId`, creates canonical
typed call references, and finally lowers modules and function bodies.

Generic specialization names encode their concrete type arguments. Struct
arguments include their declaring module in that encoding, so distinct
same-named types cannot collide. The ordering, names and identifiers are stable
for the same semantic program.

Inside each function:

* parameters receive the first `IrValueId` values in declaration order;
* local declarations receive `IrLocalId` values in source declaration order;
* constants and value-producing instructions receive subsequent value
  identifiers in evaluation order;
* local and value identifiers use independent counters;
* basic-block targets receive local `IrBlockId` values in deterministic
  reservation order;
* completed blocks are preserved in deterministic lowering order;
* operands are lowered before the instruction or terminator that consumes them;
* local initializers are lowered before their initialization instruction;
* assignment values are lowered before their store instruction;
* public IR collections preserve their original deterministic order.

No static or process-global counters are used.

### Supported function subset

The current lowering subset supports:

* bodyless function declarations;
* ordered parameters;
* direct function calls;
* directly injected function calls;
* namespace-qualified function calls;
* ordered call arguments;
* calls returning values;
* calls returning `void`;
* primitive and struct parameter and return types;
* generic function specializations with explicit concrete type arguments;
* `const` local declarations;
* immutable `let` declarations;
* mutable `@mut let` declarations;
* assignment statements targeting mutable locals;
* direct and nested struct field mutation;
* pointer-to-struct field mutation;
* bare returns;
* value returns;
* conditional statements;
* optional `else` branches;
* nested conditionals;
* `while` loops;
* nested loops;
* nested statement blocks.

Bodyless declarations remain bodyless and do not receive fabricated basic
blocks.

Bodyful functions lower to deterministic control-flow graphs containing one or
more basic blocks.

Every reachable path must terminate explicitly. Statements appearing after a
terminator in the same syntax block are rejected as unreachable during
lowering.

Conditionals create branch, optional merge and continuation blocks only when
the corresponding control-flow paths can continue.

Loops create a condition block, body block, continuation block and a canonical
back-edge from the body path to the condition.

### Supported expression subset

The initial expression subset supports:

* integer literals;
* floating-point literals;
* boolean literals;
* character literals;
* string literals;
* contextually typed null literals;
* parameter references;
* local-variable references;
* parenthesized expressions;
* numeric positive and negation;
* logical negation;
* arithmetic binary operators;
* relational operators;
* equality and inequality;
* logical conjunction and disjunction;
* struct construction and field access;
* typed pointer-to-struct field access;
* immutable string indexing, concatenation and equality.

Primitive literal lexemes are decoded during lowering after lexical and
semantic validation.

Parentheses do not create additional IR values.

Unary and binary expressions emit value-producing instructions after their
operands have been lowered.

A local declaration lowers its initializer first, creates one canonical
`IrLocal`, and then emits an `IrLocalInitializeInstruction`.

A local-variable reference resolves through its canonical
`LocalVariableSymbol` and emits an `IrLocalLoadInstruction`.

An assignment resolves through the canonical assignment-target symbol, lowers
the assigned expression, and emits an `IrLocalStoreInstruction`.

Pointer-field assignment lowers the pointer before its value, then emits a
typed `IrPointerFieldStoreInstruction`. Pointer-field mutation is independent
of local binding mutability because it changes addressed storage rather than
rebinding the pointer local. Reads emit `IrPointerFieldLoadInstruction`.

Calls to `std.memory.load<T>`, `store<T>`, `load_at<T>` and `store_at<T>` retain
ordinary typed call instructions. Their concrete bodyless specializations
receive compiler-supplied LLVM bodies, keeping raw address operations out of
Sol source syntax.

Generic `std.collections.vector` functions are monomorphized through the same
plan as user-defined generics. Vector storage, growth and element operations
lower from their Sol implementations; the IR has no vector-specific types or
instructions. Only bodyless runtime-failure helpers are completed by the LLVM
standard-library boundary.

String indexing lowers independently to `IrStringIndexInstruction`; it never
becomes a pointer load and cannot be used as an assignment target. Calls to
`std.string` retain ordinary typed call instructions, so the IR surface does
not expose the native UTF-8 representation or raw allocation pointers.

A call resolves exclusively through the canonical `FunctionSymbol` and
semantic type arguments associated with its `CallExpression`. The current
function specialization substitutes its own concrete arguments before the
canonical target specialization is selected. Lowering does not inspect the
source spelling of a direct, injected or namespace-qualified callee.

Arguments are lowered from left to right before the call instruction is
emitted.

Calls returning values emit `IrValueCallInstruction`. Calls returning `void`
emit `IrVoidCallInstruction` and may appear as standalone call statements.

The parser permits standalone statements only for call expressions. Arbitrary
expressions whose values are discarded are not accepted as statements.

Lowering does not repeat source-name lookup, function selection, signature
resolution, mutability checking or assignment type checking.

### Unsupported syntax

The following constructs are not lowered by the current layer:

* safe references and borrows;
* global variables;
* `break`;
* `continue`;
* exceptions;
* pattern matching;

Encountering unsupported syntax produces an `IrLoweringException` with a
deterministic explanation.

Unsupported syntax is never silently discarded or replaced with placeholder
IR.

### Failure model

Source-program mistakes must be reported by semantic diagnostics before
lowering.

An `IrLoweringException` represents one of the following compiler-side
failures:

* lowering was invoked for a semantically invalid program;
* required canonical semantic information is missing;
* unsupported syntax reached the lowering layer;
* semantically validated input produced structurally invalid IR.

IR constructors continue to enforce their own invariants independently from
the lowering layer.

## LLVM backend

The target-independent LLVM generation layer is documented in
[`llvm-backend.md`](llvm-backend.md). The Sol 0.1.1 self-host implements its
own target-independent textual boundary, documented in
[`selfhost-llvm-backend.md`](selfhost-llvm-backend.md).

The backend consumes only validated Sol IR and does not expose LLVM objects
through the Sol IR package boundary.

## Future extensions

The current design must permit later representation of:

* global storage;
* safe references and borrows;
* ownership operations;
* destruction;
* `break` and `continue`;
* exceptions;
* pattern matching;
* SSA values or a later SSA transformation layer.

LLVM-specific types, blocks, instructions and ownership rules belong exclusively to the LLVM backend.
