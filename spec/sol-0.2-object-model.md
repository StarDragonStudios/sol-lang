# Sol 0.2 object model

Status: **approved source-language design, not implemented**. Tracks
[#129](https://github.com/StarDragonStudios/sol-lang/issues/129) within the
[Sol 0.2 roadmap](https://github.com/StarDragonStudios/sol-lang/issues/113).

This document defines the first Sol object model. Its examples are contracts
for downstream compiler work; the current compiler does not yet accept Sol 0.2
syntax. The implemented Sol 0.1 specification remains normative until the
parser, semantic, IR, backend, runtime and conformance work is complete.

## Scope

Classes add nominal identity, mutable state, methods, construction, inheritance,
interfaces and dynamic dispatch. Existing structs retain their copyable value
semantics and procedural APIs.

The first model includes top-level concrete/abstract classes, top-level
interfaces, fields, instance methods, multiple constructors, exact overloads,
one direct class base, multiple interfaces, access control, abstract contracts,
direct construction and raw-pointer `new` / `delete`.

It excludes generic or nested classes/interfaces, static members, properties,
automatic accessors, operator overloading, interface default methods, downcasts,
runtime type tests, destructors, reflection, multiple class inheritance,
direct-instance copy/move, safe references, advanced ownership and GC. There is
no universal `Object`; a class without `<<` is an independent root.

## Declarations and headers

```sol
@public
class Document << Entity < Printable, Serializable
    // fields, methods and constructors
end
```

`<<` introduces at most one concrete or abstract class base. `<` introduces a
comma-separated interface list. The base must precede the interfaces. Duplicate
interfaces, wrong declaration categories and inheritance cycles are invalid.

Abstract classes use `@abstract`; interfaces reuse `class` with `@interface`:

```sol
@public
@abstract
class Entity
    @public
    @fn describe() -> string
end

@public
@interface
class Printable
    @public
    @fn print() -> void
end
```

An interface may extend multiple interfaces using `<`:

```sol
@public
@interface
class Persistable < Serializable, Identifiable
    @public
    @fn save() -> void
end
```

Interfaces cannot use `<<`, extend classes, declare fields/constructors, or
provide method bodies. Requirements accumulate; exact duplicates unify and
incompatible equal-name/parameter requirements are invalid. Interface cycles
are invalid.

Abstract classes may contain fields, constructors, implemented methods and
bodyless `@fn` methods. Concrete classes must implement every inherited abstract
and interface requirement. Abstract classes and interfaces are not constructible.

```text
class-header ::= "class" Identifier [ "<<" type-name ]
                 [ "<" type-name { "," type-name } ] NEWLINE
type-name    ::= Identifier { "::" Identifier }
```

`type-name` uses existing namespace qualification, not class generics. `<<` is
a class-header delimiter, not a shift operator. Existing `<` generic/comparison
and `->` contexts retain their meanings.

## Visibility

Sol uses `@public`, `@protected` and `@private`. Omission means `@public`,
matching current module declarations. Canonical style nevertheless writes
exactly one visibility annotation on every type, field, method and constructor.

Top-level type visibility is:

- `@public`: external code may name, construct, inherit from or implement it.
- `@private`: only its declaring module may name it.
- `@protected`: its module may use it normally. Other modules may name a class
  only as a base and while implementing its subclasses, or name an interface
  only in an inheritance/implementation list and while implementing it.

Public APIs cannot expose protected/private types in public fields, parameters
or returns. Sol 0.2 has no `@internal` visibility.

Member visibility is public everywhere the type is visible; private only in the
declaring class; protected in that class and subclasses through `this` or
`base`, never through an arbitrary object. A protected constructor is callable
through `base(...)`, not arbitrary external construction.

Overrides cannot reduce visibility. Public remains public; protected may remain
protected or become public. Interface requirements/implementations are public.

## Fields, receivers and lookup

Fields are mutable object state. `@mut` controls rebinding a variable, not
method or field mutation:

```sol
let person: Person = Person("Ana")
person.rename("Laura")       // Valid.
person.name = "Laura"        // Valid when accessible.
person = Person("Marta")     // Invalid rebinding.

@mut let current: Person = Person("Ana")
current = Person("Marta")    // Valid reconstruction.
```

This deliberately differs from a struct's deep immutability under `let`.
Methods have no `@mut` annotation.

Instance access always uses an explicit receiver:

```sol
@private
name: string

@public
fn rename(name: string) -> void
    this.name = name
    this.validate()
end
```

`this.member` accesses the current instance; `base.method()` selects the base
implementation. `this` cannot be rebound and is invalid outside instance
members. Unqualified names resolve parameters, locals and module symbols, not
instance members. Externally, direct instances use `.` and pointers use `->`.
No unary `*`, `&` or hidden dereference syntax is added.

A derived class cannot redeclare any base field name, including a private one.
Fields and methods may share a name because access and calls differ. Lookup
starts at the static class and follows its one base chain. Constructors/private
members are not inherited accessibly; new overloads do not hide inherited ones.

## Methods, overloads and dispatch

Public/protected instance methods are overridable and dynamically dispatched by
default. Private methods are not inherited or overridable and dispatch
statically. Every replacement or implementation requires `@override`:

```sol
@public
@override
fn print() -> void
end
```

Missing `@override` and spurious `@override` are errors. Overrides require exact
parameter and return types; covariant returns are deferred. Visibility follows
the preceding section.

Methods overload by name. All constructors in a class form one overload set,
regardless of their declaration names. Selection uses exact argument count and
types: no promotions, implicit conversions, optional parameters or variadic
ranking. Returns do not select overloads. Equal parameter signatures are
duplicates. Method selection uses the receiver's static type, then dynamic
dispatch chooses the implementation of that signature.

Exact interface requirements unify. Incompatible ones are errors. A compatible
inherited concrete public method may satisfy an interface requirement.

## Constructors and definite initialization

A constructor is any `fn` annotated `@constructor`. Its name is only a source
label: it is not used by construction and is not an ordinary callable method.
Constructors return `void`, are not inherited/overridden, and may have any
visibility.

```sol
@public
@constructor
fn from_name(name: string) -> void
    this.name = name
    this.age = 0
end

@public
@constructor
fn with_age(name: string, age: int) -> void
    this.name = name
    this.age = age
end
```

`Person("Ana")` and `new Person("Ana")` resolve the same overload by arguments;
`from_name` is absent from the call. There is no implicit default constructor.
A concrete class without an accessible `@constructor` cannot be instantiated.

A root constructor has no `base(...)`. Every derived constructor must reach one
base constructor directly or through `this(...)` delegation.

Statements may precede `base(...)` or `this(...)`. This early-construction
prologue may declare locals, compute/validate parameters and call module
functions. It cannot access `this`, `base`, fields or instance methods. The
constructor invocation cannot be nested inside `if`/`while`; `return` before it
is invalid; every normally completing path must reach it.

```sol
@protected
@constructor
fn create(id: int, name: string) -> void
    @mut let normalized: string = normalize(name)
    if normalized == "" then
        normalized = "unknown"
    end
    base(id)
    this.name = normalized
end
```

`this(arguments)` delegates to another same-class constructor by exact types. A
constructor directly invokes `this(...)` or `base(...)`, not both. The chain
must terminate in `base(...)` for a derived class and cannot cycle. Code after
`this(...)` runs after delegated initialization completes.

The base initializes base fields. Every constructor definitely initializes all
fields declared by its class on every normal path. There are no implicit field
defaults or declaration-site initializers. Fields cannot be read before definite
initialization. `this` methods cannot be called until all class fields are
initialized; `base.method(...)` is permitted after `base(...)`. A `return` is
valid only after complete initialization. A loop alone cannot establish definite
initialization because it may execute zero times.

## Direct construction and manual memory

Direct construction initializes its destination without guaranteeing physical
stack placement:

```sol
let person: Person = Person("Ana")
```

Direct class instances are noncopyable/nonmovable. Existing instances cannot be
assigned, passed or returned by value; there is no direct-instance equality.
`@mut let` may be reconstructed from a fresh constructor expression, not another
instance. Structs keep their current copy/argument/return behavior.

Dynamic construction returns a raw pointer:

```sol
let person: pointer<Person> = new Person("Ana")
if person == null then
    return 1
end
person->rename("Laura")
delete person
```

Allocation failure returns `null` without running the constructor. `delete null`
is a no-op. `delete` is valid exactly once for a pointer returned by `new` whose
view matches the concrete allocated class. Deleting other storage, double delete
and use after delete are undefined behavior in this provisional model.

`delete` is distinct from `memory::free`. Raw load/store/reallocation or byte
copy of class instances is unauthorized. Direct instances do not use `delete`;
their storage follows their destination. There are no destructors yet, so
`delete` does not provide automatic external-resource cleanup.

## Pointer polymorphism

Class/interface pointers add controlled covariance to otherwise invariant raw
pointers:

```sol
let document: pointer<Document> = new Document()
let printable: pointer<Printable> = document
let entity: pointer<Entity> = document
printable->print()
```

`pointer<Derived>` implicitly converts to `pointer<Base>` and implemented
interfaces. There are no downcasts or unrelated conversions. Direct instances
do not convert by value, preventing slicing. Members/overloads use the static
pointer type; eligible methods dispatch through the dynamic class. Interface
views preserve object identity regardless of backend representation.

Converted pointers are non-owning views. Deletion through a base/interface view
is invalid; the original concrete pointer is required. Copies transfer no
responsibility. Equality is defined only between the same static pointer type;
callers may first convert both to one valid common view.

## Implementation and conformance handoff

- #131: tokens, syntax nodes, annotations, headers, members and exact spans.
- #132–#134: class/interface symbols, visibility, lookup, receivers, overloads
  and dispatch contracts.
- #135–#138: constructor flow, initialization, inheritance, overrides, abstract
  requirements and interface conformance.
- #139–#142: typed operations, object/view representation, layouts, dispatch and
  provisional `new` / `delete`.
- #143–#145: positive/negative conformance, integrated specification, packaging
  and release.

Implementation preserves the seed, functions, modules, current generic
structs/functions and struct value semantics. Tests must cover declarations and
cycles; constructor overload/delegation/prologues/initialization; visibility and
explicit receivers; override/interface conflicts; direct noncopyability;
allocation failure/deletion/upcasts; source spans and diagnostics; grammar
compatibility; deterministic IR; CLI conformance and bootstrap fixed point.

The compiler must reject out-of-scope syntax and semantic ambiguity rather than
selecting whichever backend representation is easiest. Public diagnostic codes
are allocated during implementation, not by this design document.
