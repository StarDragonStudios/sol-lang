# Experimental annotation grammar

The candidate parser implements this grammar under `safe-experimental` for
issue #210. `parse_tokens` remains the legacy entry point;
`parse_tokens_in_mode(tokens, mode)` selects a parser context explicitly.
Legacy parsing, annotation layout and diagnostics are unchanged.

The grammar preserves annotations for future checking. Apart from `@init`,
these constructs still fail the literal-return semantic subset with SOL-M001.
In particular, parsing @unsafe never grants permission to execute unchecked raw
operations, and parsing @exit does not implement directed control flow.

## Prefixes and arguments

Consecutive annotations can occupy the same line, separate lines, or both:

```sol
@returns(once, must_resolve) @public
fn example(@once @must_resolve work: int) -> void
    @exit(example) return
end
```

This is a parser example; the placeholder int parameter and future annotations
do not claim a supported callable contract. Annotation names use the recognized
lower_snake_case spellings below. Annotation argument lists accept expressions,
including symbolic names and calls. Named arguments use `alias = expression`.
Positional arguments precede named arguments; aliases cannot repeat within one
annotation. Lists permit newlines and a trailing comma. Captures require aliases,
for example `@captures(pending = move(outcome))`.

The grammar is:

```text
prefixes   := annotation (newline* annotation)* newline*
annotation := '@' name [ '(' newline* arguments? newline* ')' ]
arguments  := argument (newline* ',' newline* argument)* [ newline* ',' ]
argument   := expression | identifier '=' expression
```

The following require nonempty argument lists: `exit`, `lifetime`, `from`,
`depends`, `resolves`, `returns`, `captures`. @exit requires exactly one
positional unqualified function name. Capture arguments must all be named.
The remaining recognized names are bare and reject parentheses, including ().

Bare names: `init`, `public`, `protected`, `private`, `abstract`, `interface`,
`constructor`, `destructor`, `mut`, `copy`, `unsafe`, `must_resolve`, `pinned`,
`atomic`, `shared`, `exclusive`, `once`.

This defines the argument envelope and source representation. Binding lifetime
origins, checking return/capture qualifiers, allocator dependencies, constructor
rules and invocation capabilities belong to their technical issues. Accepting an
argument expression here does not settle those semantic contracts or evaluate it.

## Positions and blocks

Prefixes are represented on functions, classes, structs, class/struct fields,
parameters and local declarations. @fn keeps its existing meaning as a bodyless
function marker; it is not an annotation node. The initial syntactic filters are:

| Annotation | Parsed target |
| --- | --- |
| unsafe | Explicit code block only |
| exit | Return statement only |
| mut, captures | Local declaration; mut requires let |
| copy | Struct declaration |
| lifetime | Struct or class declaration |
| from, depends | Field or function declaration |
| returns, resolves, init, constructor, destructor | Function declaration |
| abstract, interface | Class declaration |
| public, protected, private | Class, class field or function declaration |
| pinned, atomic, must_resolve, shared, exclusive, once | Reserved qualifier positions on declarations, fields and parameters; semantic applicability remains checked by later features |

Names cannot repeat in a prefix list. Mutually exclusive groups are
public/protected/private, shared/exclusive/once, abstract/interface, and
init/constructor/destructor. Other semantic compatibility checks remain
unavailable until their owning checker is
implemented; the semantic subset rejects these future features today.

In experimental statement position, begin introduces an explicit block. It
remains an ordinary identifier in the legacy lexer/parser, preserving legacy
programs. Functions and structs open their own bodies and do not take begin.
Blocks can be nested; statements retain the existing newline separation rules.
A single statement can appear between begin and end on one line.

```sol
fn example() -> void
    @unsafe begin
        begin
            return
        end
    end
end
```

Only @unsafe can annotate these explicit blocks. Only @exit can annotate return
statements, on the same line or a preceding line. A generic block introduces no
additional return destination. Checking @exit targets and executing these blocks
remain downstream work (#214/#215/#217). Type-argument annotations, match arms
and lambda expressions are not added by this grammar change.

## AST contract

Existing syntax-kind numbers and legacy child layouts remain stable. Experimental
annotation nodes use `syntax_kind_annotation()` with text equal to the name:

- Child 0 is the annotation's name node, as in legacy trees.
- Subsequent children are `syntax_kind_annotation_argument()` (kind 41).
  Each holds one expression child. Its text is empty for a positional argument,
  or the alias for a named argument. Its span includes the alias and expression.
- Annotation variant 0 means bare, 1 means parenthesized. Annotation spans begin
  at @ and include the closing parenthesis when present.

On experimental targets, annotation nodes are appended after the target's
ordinary children. The target span begins at its first prefix. This preserves
name/type/expression/body child indices. An experimental @mut let also retains
the existing mutable-let variant while keeping its annotation node.

Explicit blocks use kind 42 (`syntax_kind_code_block_statement()`), variant 0
for ordinary begin/end and 1 for @unsafe. Child 0 is the existing block/body
node; following children are annotations. A block span includes its end token.
The dedicated wrapper kind makes these constructs visible to the subset guard
instead of accidentally accepting them as ordinary function-body blocks.

## Diagnostics and evidence

| Code | Meaning |
| --- | --- |
| SOL-P010 | Unknown parser mode |
| SOL-P011 | Unknown experimental annotation |
| SOL-P012 | Duplicate annotation or named argument |
| SOL-P013 | Conflicting annotation roles or qualifiers |
| SOL-P014 | Invalid annotation position or @mut const |
| SOL-P015 | Invalid annotation argument form or arity |
| SOL-P002 | Existing token/structure expectation failure |

`compiler/src/annotation_test.sol` checks compact/multiline AST equivalence,
source spans, named capture expressions, nested blocks, declarations, parameters,
annotated returns and negative cases. Both bootstrap scripts compile and execute
it with the candidate. The existing parser/grammar suites exercise legacy mode.
`test_language_mode.py` additionally verifies compact @init through the public
CLI, unchanged legacy rejection, and semantic rejection of parsed future safety
constructs. Source locations retain the established diagnostic format.
