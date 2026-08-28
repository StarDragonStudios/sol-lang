# Sol 0.2 object model — design draft

Status: **design review, not implemented**. Tracks
[#129](https://github.com/StarDragonStudios/sol-lang/issues/129), within
the [Sol 0.2 roadmap](https://github.com/StarDragonStudios/sol-lang/issues/113).

This document records the source-language decisions agreed for the first
object model. It separates those decisions from proposals that still require
approval. Neither merging this draft nor accepting its examples means the
current compiler accepts Sol 0.2 syntax. Issue #129 stays open until its required
design decisions are resolved; downstream implementation must not silently
turn a proposal or an open question into a language rule.

## 1. Confirmed decisions

| Area | Agreement |
| --- | --- |
| Classes and structs | Classes introduce the object model. Existing structs retain their value semantics and procedural APIs. |
| Constructors | Constructors are ordinary `fn` declarations annotated with `@constructor`. |
| Current instance | The instance receiver is spelled `this`. |
| Class inheritance | `<<` introduces the base class. A class has at most one direct base class, which may be concrete or abstract. |
| Interfaces | `<` introduces implemented interfaces. A class may implement multiple interfaces. Interfaces are marked `@interface`. |
| Abstract classes | Abstract classes are marked `@abstract`. Bodyless methods use the roadmap's `@fn` form. |
| Visibility | The annotations are `@public`, `@protected`, and `@private`. Defaults and precise access rules remain proposals below. |
| Direct construction | `Person(...)` constructs an instance directly in its destination; the spelling does not imply a heap allocation. |
| Dynamic construction | `new Person(...)` allocates dynamically, constructs an instance, and returns `pointer<Person>`. |
| Member access | Direct instances use `.`, and pointers to instances use `->`. No unary `*` or `&` memory operators are introduced. |
| Initial memory model | `new` / `delete` provide provisional manual object allocation and release. Strict ownership and borrowing are not prerequisites for the first object model. |
| Future memory management | GC is a future direction, not a feature of this first manual model. Removing manual release is not assumed to be a source-compatible change. |

The constructor function's exact name and signature, the declaration following
`@interface`, and the separator between multiple interfaces are not implied by
these agreements. Section 3 proposes concrete spellings for review.

## 2. Construction, storage, and the manual boundary

These are the agreed call-site forms:

```sol
let person: Person = Person("Ana", 25)
let person_ptr: pointer<Person> = new Person("Ana", 25)
```

Both expressions create an instance. In the first case the constructor
initializes its destination, such as a local variable or a field. This is not
a promise that every direct instance is physically placed on the stack.
Returning a class value or passing it by value still depends on the transfer
rules listed in section 4; those operations are not approved by this example.

In the second case, dynamic storage is reserved before the constructor runs.
`new` produces a raw pointer, not an owning or garbage-collected handle. Copying
that pointer copies the address; it does not copy the object, extend its
lifetime, or arrange automatic release.

```sol
let alias: pointer<Person> = person_ptr
delete person_ptr
// Neither person_ptr nor alias may subsequently access that instance.
```

`delete` is the explicit release operation paired with `new`. The example does
not define deletion through an adjusted base/interface pointer, null deletion,
constructor failure, or destructor behavior. Those require the minimum memory
contract before implementation; they must not be inherited silently from C++.

The current raw allocator and load/store APIs remain independent. In particular:

- `memory::free` releases raw storage; it is not an object destructor call.
- Allowing `pointer<Class>` for `new` is an extension to the current model; it
  does not automatically authorize raw byte-copy, `load<Class>`,
  `store<Class>`, or `reallocate<Class>` operations on constructed objects.
- Extending raw allocation to class types, taking addresses of direct
  instances, and permitting pointer escape each require an explicit rule.
- Direct class assignment, arguments, and returns must not silently acquire
  struct-style copying, implicit movement, or shared-reference semantics.

For example, the following remains a design question, not valid-by-default
Sol 0.2 code:

```sol
let other: Person = person
```

The compiler still needs a small, implementable object-lifetime contract. It
does **not** need a borrow checker, a complete ownership system, GC, or safe
reference types before basic object features can be developed.

### Future GC

`new` can remain the dynamic-construction syntax if a future version introduces
GC. That version must define which references keep an object alive and how they
differ from raw pointers. `delete` must not become a silent no-op for old manual
code without an explicit migration decision.

Reclaiming memory is distinct from closing files, connections, and other
external resources. A future GC is not a promise of deterministic resource
cleanup. This draft chooses no GC algorithm, root representation, ownership
annotations, or destructor syntax.

## 3. Concrete syntax and member proposals — approval required

All rules and examples in this section are **proposals**, not additional
confirmed decisions. They are provided so design review can approve or change
specific behavior rather than leaving implementation to guess.

### P1. Constructor declaration and invocation

Propose a constructor function named exactly like its class, with an explicit
`-> void` return type. It initializes `this`; it does not return a separate
object or allocate its own destination. The class call expression yields the
initialized instance, while `new` yields the pointer.

```sol
class Person
    @private
    name: string

    @private
    age: int

    @public
    @constructor
    fn Person(name: string, age: int) -> void
        this.name = name
        this.age = age
        return
    end

    @public
    fn get_age() -> int
        return this.age
    end
end
```

Proposed constraints:

- Constructors are instance members, not module functions or inherited methods.
- No implicit default constructor; creation requires an accessible declared one.
- Constructors may be overloaded by parameter types, not by return type.
- No ordinary member-call spelling for re-running a constructor on an already
  initialized object.
- Initialization may write the receiver's fields even when the destination is
  an immutable `let`. Subsequent mutation follows the separate receiver rules.
- All required instance fields must be initialized before normal completion;
  field defaults, definite-initialization analysis, base initialization, and
  constructor failure need the decisions in section 4.

### P2. Class headers, interfaces, and bodyless methods

Propose `@interface class Name` for an interface declaration, with the annotation
on the preceding line, and a comma-separated interface list:

```sol
@interface
class Named
    @public
    @fn get_name() -> string
end

@interface
class Printable
    @public
    @fn print() -> void
end

@abstract
class Entity
    @public
    @fn get_name() -> string
end

@abstract
class NamedEntity << Entity < Named, Printable
end
```

`NamedEntity` deliberately remains abstract: this sketch is not a concrete
class that has implemented all inherited requirements. Interfaces describe
behavior; under this proposal they have no instance fields, constructors,
default method bodies, or base class. Interface-to-interface inheritance and
generic class/interface declarations are not introduced by this proposal.

Proposed header grammar, omitting the existing annotation/newline machinery:

```text
class-header     ::= "class" Identifier [ "<<" type-name ]
                    [ "<" type-name { "," type-name } ] NEWLINE
type-name        ::= Identifier { "::" Identifier }
```

The first `Identifier` names the declared class. `type-name` allows the current
namespace-qualified name form, not a generic class syntax. The body terminates
with `end`. Interface-only implementation omits the `<<` clause. A header is
one logical line; no implicit continuation after an interface comma is proposed.

The base clause must precede the interface clause. A second `<<`, an interface
as the class base, a class in the interface list, duplicate interfaces, and
inheritance cycles are invalid under this proposal. Combining `@abstract` and
`@interface` is also rejected rather than given an accidental meaning.

`<<` is a class-header delimiter, not a newly introduced shift operator. The
existing expression comparison `<`, generic argument delimiters, and return /
pointer-member `->` retain their contexts. Lexer/parser work must explicitly
test these boundaries; this document chooses no token representation.

### P3. Visibility and name lookup

Propose private-by-default members. Exactly one visibility annotation may
apply to a member:

- `@public`: accessible from any code that can name the declaring type.
- `@private`: accessible only from members of the declaring class.
- `@protected`: accessible in the declaring class and derived classes through
  their own `this` receiver; arbitrary base-typed receivers are not an access
  bypass.

Constructor access is checked at both direct and `new` construction sites.
Interface method requirements must be explicitly `@public`; private or
protected requirements are rejected. No class-level visibility rules are
proposed here; their annotation placement remains a review question.

Within methods, retain the existing lexical lookup for unqualified locals and
parameters. Propose requiring `this.member` for implicit-instance fields and
methods rather than making fields compete with local names. `this` is not a
normal parameter and cannot be rebound; it has no meaning at module scope or
inside an ordinary module-level function.

Propose instance calls as `person.get_age()` and
`person_ptr->get_age()`. A method on a raw pointer gets the same logical `this`
receiver as a method on a direct instance; this is not a safe borrow guarantee.
Module qualification continues to use `::`.

Propose member lookup in the current class followed by its single base chain,
with lexical access checks preserved. Do not inherit constructors or private
members into a derived class's lookup scope. Reject field hiding in the first
model. Method overloading, overriding, and interface requirement matching need
the exact signature and dispatch decisions below before semantic code is added.

## 4. Remaining decisions and implementation gates

The following are deliberately **unresolved**. None may be decided by lowering
an ambiguous construct into whichever native representation is easiest.

| Decision | Required before | Review question |
| --- | --- | --- |
| P1–P3 approval | #131–#135 as applicable | Constructor names/signatures, interface spelling/list syntax, visibility and lookup rules. |
| Class-level visibility | #131, #132 | Which annotations are legal on classes/interfaces, what is the default, and how does access interact with module injection? |
| Receiver mutation | #134 | How is a mutating method declared? How do immutable `let`, mutable `@mut let`, raw receivers, and override compatibility interact? |
| Overrides and dispatch | #136–#138, #141 | Is an override explicit? Which methods dispatch dynamically? Define exact parameter/return matching, visibility compatibility, and conflicting inherited requirements. |
| Base construction and calls | #135, #137 | Spelling for base-constructor/base-method calls, required initialization order, defaults, and definite initialization. |
| Direct-instance lifetime and transfer | Minimal #130 contract, #139–#142 | Scope/container lifetime, field nesting, arguments, returns, assignment, equality, and whether initially unsupported transfers are rejected. No implicit copy/move/share default. |
| Raw class storage | Minimal #130 contract, #140, #142 | Permitted operations on `pointer<Class>`, aliasing obligations, address escape, alignment, and which raw allocator operations remain restricted. |
| Allocation failure and deletion | Minimal #130 contract, #142 | Null versus runtime failure for `new`, partial construction cleanup, `delete null`, provenance checks/obligations, destructors, and deletion through base/interface aliases. |
| Polymorphic views | #137–#141 | Class/interface conversions, whether views use pointers or another representation, preservation of object identity, and prevention of accidental slicing. |

Proposal for handling #130: separate a **minimum manual object contract** from
future advanced ownership and GC work. The existing issue text still describes
the broader ownership work; this draft does not claim that issue has already
been rewritten or its entire design resolved.

Parser scaffolding for approved syntax need not wait for a GC or borrow checker.
Semantic, IR, and runtime behavior must wait for the specific contracts they
consume. No source program should be accepted with undefined *language design*
merely because manual memory already places some runtime obligations on users.

## 5. Scope and implementation handoff

The first model adds classes, fields, instance methods, constructor and method
overloading, single class inheritance, multiple interface implementation,
abstract classes, access control, receiver context, and object-aware typed IR
and native dispatch. Interfaces are an explicit addition to the original
roadmap's capability list, not just another spelling for abstract base classes.

Existing struct copying, generic struct/function monomorphization, procedural
calls, module injection, raw-memory APIs, and the published seed remain in
place. No special copying or runtime class metadata is implied for existing
structs. The old bootstrap must continue to build the compiler.

No implementation of GC, borrow checking, lifetimes, strict ownership, safe
`ref<T>` / `borrow<T>`, multiple class inheritance, reflection, properties,
static members, interface default methods, generic classes/interfaces, or
destructor syntax is authorized by this draft. Additional features require
their own scope decisions rather than being assumed from another language.

Implementation responsibilities to refine after review:

- #131: lexical/parser support and complete spans for approved object syntax.
- #132–#134: nominal class/interface symbols, member scopes, access, receiver
  and method lookup; no implicit change to struct semantics.
- #135–#138: constructors, overloads, inheritance, abstract/interface contracts,
  and their diagnostics. Interface support must be included explicitly when
  refining these issues; their current placeholder descriptions are incomplete.
- #139–#142: typed object operations, target-independent semantic contracts,
  native storage, dispatch, and the minimum manual lifetime operations.
- #143–#145: object-model conformance, final normative specification, packaging
  and release. Draft examples are not substitutes for executable conformance.

## 6. Acceptance-test design

These are test requirements for the later implementation, **not tests reported
as passing today**. Proposal-dependent cases remain conditional on approval.

| Area | Positive coverage | Negative / regression coverage |
| --- | --- | --- |
| Declarations | Concrete/abstract classes, interfaces, one base plus multiple interfaces, qualified names | Multiple bases, cycles, duplicate interfaces, wrong declaration categories, misplaced/conflicting annotations |
| Constructors | Annotated functions, overload selection, direct and dynamic creation, full initialization | Missing/inaccessible/ambiguous constructor, invalid return, repeated initialization, abstract/interface instantiation |
| Receiver and fields | Direct `.` and pointer `->`, `this`, lexical shadowing, inherited accessible members | `this` outside a member, illegal receiver mutation, access violations, field hiding |
| Methods and contracts | Overloads, base overrides, interface dispatch, concrete implementation of abstract requirements | Missing implementations, incompatible signatures/access/mutability, ambiguous lookup |
| Memory | Direct destination initialization, explicit `new`/`delete`, raw pointer alias identity | Unapproved class copies/transfers, illegal raw class operations, specified failure/deletion cases; no promise to diagnose every raw use-after-free |
| Grammar compatibility | Existing functions, structs, generics, comparisons, `->` returns and pointer access | No accidental shift operator or expression/generic parsing changes from class headers |
| End to end | Public CLI programs, deterministic IR, supported native targets, seed bootstrap and fixed point | Existing procedural conformance and struct value semantics must remain unchanged |

Diagnostics must identify the failing annotation, member/name, constructor
call, clause, or expression with its actual source span. When two declarations
conflict, retain both locations for useful diagnostics. Do not allocate public
diagnostic codes in this design PR before integration with the diagnostic
catalogue.

## 7. Review completion

Before #129 is closed:

1. Approve or revise P1–P3 and the remaining source-level member, construction,
   inheritance, and interface rules in section 4.
2. Agree the minimum manual-memory contract needed by those rules, with advanced
   ownership and GC explicitly deferred rather than silently specified.
3. Refine the affected implementation issues, including interface support and
   the smaller role of #130 for this phase.
4. Convert approved proposals into normative design text and map the acceptance
   cases to the corresponding implementation work.
5. Merge the reviewed design through a PR. Keep the implemented 0.1 specification
   and the unimplemented 0.2 design visibly distinct until the compiler catches up.
