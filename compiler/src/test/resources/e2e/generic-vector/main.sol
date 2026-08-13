inject std.collections.vector

struct Span
    start: int
    end_index: int
end

struct Token
    kind: int
    span: Span
end

@init
fn launch() -> int
    let numbers: pointer<Vector<int>> = create_vector<int>()
    vector_reserve<int>(numbers, 2)
    vector_push<int>(numbers, 10)
    vector_push<int>(numbers, 20)
    vector_push<int>(numbers, 30)
    vector_set<int>(numbers, 1, 12)

    if vector_length<int>(numbers) != 3 || vector_capacity<int>(numbers) != 4 then
        return 1
    end

    let last: int = vector_pop<int>(numbers)

    if last != 30 || vector_get<int>(numbers, 0) + vector_get<int>(numbers, 1) != 22 then
        return 2
    end

    let tokens: pointer<Vector<Token>> = create_vector<Token>()
    vector_push<Token>(tokens, Token { kind: 7, span: Span { start: 11, end_index: 13 } })
    vector_push<Token>(tokens, Token { kind: 9, span: Span { start: 20, end_index: 22 } })

    let token: Token = vector_pop<Token>(tokens)

    if token.kind != 9 || token.span.start != 20 || token.span.end_index != 22 then
        return 3
    end

    vector_clear<int>(numbers)

    if vector_length<int>(numbers) != 0 || vector_capacity<int>(numbers) != 4 then
        return 4
    end

    destroy_vector<int>(numbers)
    destroy_vector<Token>(tokens)
    return 42
end
