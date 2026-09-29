# Complete-function witnesses

These are proposed Sol 0.3 contract examples, **not programs supported by the
current compiler**. Each fenced program is independent. Accepted/rejected means
the required result once its owning feature is implemented. Match and ownership
examples target 0.3.0; callable examples target 0.3.3. No provisional factory or
lifetime annotation argument syntax is assumed here.

## E01 — accepted move, rejected source reuse

Covers [C-moves](conformance.md#c-moves) and [C-copy](conformance.md#c-copy).

Accepted: Data is move-only in the experimental model. Its int field may be
read/copied; the aggregate itself transfers explicitly.

```sol
struct Data
    value: int
end

fn transfer(data: Data) -> int
    let owned: Data = move(data)
    return owned.value
end
```

Rejected: data is used after being moved, even though its field is copyable.

```sol
struct Data
    value: int
end

fn reuse(data: Data) -> int
    let owned: Data = move(data)
    return data.value + owned.value
end
```

## E02 — last-use loans and conflicting ownership

Covers [C-loans](conformance.md#c-loans). Accepted: no loan survives the move.
Obtaining a loan does not itself require @mut on the owner binding.

```sol
struct Data
    value: int
end

fn inspect_then_move(data: Data) -> int
    let view: borrow<Data> = borrow(data)
    let observed: int = view.value

    let owned: Data = move(data)
    return observed + owned.value
end
```

Rejected: view is used after the attempted move, so its loan is still active.

```sol
struct Data
    value: int
end

fn move_during_loan(data: Data) -> int
    let view: borrow<Data> = borrow(data)
    let owned: Data = move(data)

    return view.value + owned.value
end
```

## E03 — branch-local return versus function exit

Covers [C-match](conformance.md#c-match), [C-exit](conformance.md#c-exit),
[C-branch-types](conformance.md#c-branch-types) and
[C-pending](conformance.md#c-pending).

Accepted: the error arm exits process with a result; the success arm yields int
to the match destination. The long-arm return below is local to the match.
There is no implicit conversion from err(error) to int.

```sol
fn process(outcome: result<int, int>) -> result<int, int>
    let value: int = match move(outcome)
        case err(error: int) => @exit(process) err(error)
        case ok(value: int) => begin
            let adjusted: int = value + 1
            return adjusted
        end
    end

    return ok(value)
end
```

The error arm can equivalently use `=> begin` followed by
`@exit(process) return err(error)` and `end`. The success arm is equivalent to
`=> value + 1`. A generic begin/end block alone does not introduce a new return
destination.

Rejected: the error arm attempts to yield a result where int is required.

```sol
fn wrong_destination(outcome: result<int, int>) -> result<int, int>
    let value: int = match move(outcome)
        case err(error: int) => err(error)
        case ok(value: int) => value
    end

    return ok(value)
end
```

## E04 — resolution or transfer, not silent loss

Covers [C-pending](conformance.md#c-pending). Accepted: the caller receives the
obligation; move is a transfer, not a resolution claim.

```sol
fn forward(outcome: result<int, int>) -> result<int, int>
    return move(outcome)
end
```

Rejected: the owned parameter is still pending at normal return. The same rule
applies if the programmer calls generic drop instead, or only borrows/inspects
the outcome before returning.

```sol
fn forget(outcome: result<int, int>) -> void
    return
end
```

## E05 — deterministic cleanup after a move

Covers [C-cleanup](conformance.md#c-cleanup) and [C-drop](conformance.md#c-drop).
Accepted: at return, destroy third and then second. Do not destroy moved-from
first. A lowering witness must inspect cleanup events/IR; this plain Data has no
user-visible destructor effect, so output alone is not evidence of ordering.

```sol
struct Data
    value: int
end

fn cleanup_order() -> void
    let first: Data = Data { value: 1 }
    let second: Data = Data { value: 2 }
    let third: Data = move(first)
    return
end
```

Rejected: explicit early destruction invalidates owned before its later use.

```sol
struct Data
    value: int
end

fn use_after_drop(data: Data) -> int
    let owned: Data = move(data)
    drop(move(owned))
    return owned.value
end
```

The native C-cleanup family must additionally use instrumented destructors to
observe body/field/base order and exactly-once behavior on every normal exit.
These examples do not settle destructor declaration placement or runtime ABI;
the parser/lowering tasks must validate those details. Fatal termination does
not promise the cleanup observed on ordinary return.

## E06 — explicit pending capture and consuming execution (0.3.3)

Covers [C-callable-pending](conformance.md#c-callable-pending),
[C-capture](conformance.md#c-capture) and
[C-capability](conformance.md#c-capability). Accepted: the capture is moved into
the action, matched on execution, and both payloads are passed to an explicit
application handler. The handlers intentionally demonstrate discardable int
payloads; whether ignoring these particular integers is useful is an application
decision, not a guarantee of recovery made by the compiler.

```sol
fn report_error(error: int) -> void
    return
end

fn consume_data(value: int) -> void
    return
end

@returns(once, must_resolve)
fn make_work(outcome: result<int, int>) -> Action<[]>
    @once @must_resolve @captures(pending = move(outcome))
    let work: Action<[]> = () => begin
        match move(pending)
            case err(error: int) => report_error(error)
            case ok(value: int) => consume_data(value)
        end
    end

    return move(work)
end

fn execute(@once @must_resolve work: Action<[]>) -> void
    move(work)()
end

fn example(outcome: result<int, int>) -> void
    @once @must_resolve let work: Action<[]> = make_work(move(outcome))
    execute(move(work))
end
```

Rejected independently: @once is not permission to abandon the obligation.

```sol
fn abandon(@once @must_resolve work: Action<[]>) -> void
    return
end
```

## Coverage beyond these examples

The [conformance plan](conformance.md) also requires branch/backedge move tests,
destructor rejection, nested obligations, directed exits with other live pending
values, allocation fault injection and later-version lifetime/concurrency tests.
These readable witnesses are a starting set, not a substitute for those cases.
