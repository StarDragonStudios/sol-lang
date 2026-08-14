inject namespace std.memory as memory
inject std.collections.vector only Vector, vector_get, vector_length
inject frontend.source only SourcePosition, SourceSpan, source_position, source_span
inject frontend.diagnostic only Diagnostic, diagnostic, empty_diagnostic
inject frontend.token only Token, token_kind_eof, token_kind_newline
inject frontend.syntax only SyntaxNode, create_compilation_unit, destroy_syntax_tree

struct ParseResult
    successful: boolean
    root: pointer<SyntaxNode>
    diagnostic: Diagnostic
end

struct Parser
    tokens: pointer<Vector<Token>>
    current: int
    successful: boolean
    diagnostic: Diagnostic
end

fn parse_tokens(tokens: pointer<Vector<Token>>) -> ParseResult
    let parser: pointer<Parser> = memory::allocate<Parser>(1)

    if parser == null then
        let position: SourcePosition = source_position(0, 1, 1)

        return ParseResult {
            successful: false,
            root: null,
            diagnostic: diagnostic(
                "SOL-P000",
                "Unable to allocate parser state.",
                position,
                position
            )
        }
    end

    parser->tokens = tokens
    parser->current = 0
    parser->successful = true
    parser->diagnostic = empty_diagnostic()

    validate_token_stream(parser)

    @mut let root: pointer<SyntaxNode> = null

    if parser->successful then
        root = parse_compilation_unit(parser)
    end

    let result: ParseResult = ParseResult {
        successful: parser->successful,
        root: root,
        diagnostic: parser->diagnostic
    }

    memory::free<Parser>(parser)
    return result
end

fn destroy_parse_result(result: ParseResult) -> void
    destroy_syntax_tree(result.root)
    return
end

fn validate_token_stream(parser: pointer<Parser>) -> void
    if parser->tokens == null then
        parser_fail_at_zero(
            parser,
            "SOL-P000",
            "Parser token stream must not be null."
        )
        return
    end

    let count: int = vector_length<Token>(parser->tokens)

    if count == 0 then
        parser_fail_at_zero(
            parser,
            "SOL-P000",
            "Parser token stream must not be empty."
        )
        return
    end

    let last: Token = vector_get<Token>(parser->tokens, count - 1)

    if last.kind != token_kind_eof() then
        parser_fail(
            parser,
            "SOL-P000",
            "Parser token stream must terminate with EOF.",
            last.span
        )
        return
    end

    @mut let index: int = 0

    while index < count - 1 do
        let current: Token = vector_get<Token>(parser->tokens, index)

        if current.kind == token_kind_eof() then
            parser_fail(
                parser,
                "SOL-P000",
                "Parser token stream must not contain EOF before its end.",
                current.span
            )
            return
        end

        index = index + 1
    end

    return
end

fn parse_compilation_unit(parser: pointer<Parser>) -> pointer<SyntaxNode>
    parser_skip_newlines(parser)

    if !parser_is_at_end(parser) then
        let unexpected: Token = parser_peek(parser)

        parser_fail(
            parser,
            "SOL-P001",
            "Unexpected token '" + unexpected.lexeme + "' at top level.",
            unexpected.span
        )
        return null
    end

    let span: SourceSpan = parser_complete_source_span(parser)
    let root: pointer<SyntaxNode> = create_compilation_unit(span)

    if root == null then
        parser_fail(
            parser,
            "SOL-P000",
            "Unable to allocate compilation-unit syntax node.",
            span
        )
    end

    return root
end

fn parser_skip_newlines(parser: pointer<Parser>) -> void
    while parser_match(parser, token_kind_newline()) do
    end

    return
end

fn parser_match(parser: pointer<Parser>, kind: int) -> boolean
    if !parser_check(parser, kind) then
        return false
    end

    parser_advance(parser)
    return true
end

fn parser_check(parser: pointer<Parser>, kind: int) -> boolean
    return parser_peek(parser).kind == kind
end

fn parser_check_next(parser: pointer<Parser>, kind: int) -> boolean
    let next: int = parser->current + 1

    if next >= vector_length<Token>(parser->tokens) then
        return false
    end

    return vector_get<Token>(parser->tokens, next).kind == kind
end

fn parser_advance(parser: pointer<Parser>) -> Token
    let current: Token = parser_peek(parser)

    if !parser_is_at_end(parser) then
        parser->current = parser->current + 1
    end

    return current
end

fn parser_consume(parser: pointer<Parser>, kind: int, expectation: string) -> Token
    if parser_check(parser, kind) then
        return parser_advance(parser)
    end

    let actual: Token = parser_peek(parser)

    parser_fail(
        parser,
        "SOL-P002",
        "Expected " + expectation + ", but found " + describe_token(actual) + ".",
        actual.span
    )
    return actual
end

fn parser_is_at_end(parser: pointer<Parser>) -> boolean
    return parser_check(parser, token_kind_eof())
end

fn parser_peek(parser: pointer<Parser>) -> Token
    return vector_get<Token>(parser->tokens, parser->current)
end

fn parser_complete_source_span(parser: pointer<Parser>) -> SourceSpan
    let first: Token = vector_get<Token>(parser->tokens, 0)
    let last: Token = vector_get<Token>(
        parser->tokens,
        vector_length<Token>(parser->tokens) - 1
    )

    return source_span(first.span.start, last.span.end_position)
end

fn describe_token(token: Token) -> string
    if token.kind == token_kind_eof() then
        return "end of file"
    end

    if token.kind == token_kind_newline() then
        return "newline"
    end

    return "'" + token.lexeme + "'"
end

fn parser_fail_at_zero(parser: pointer<Parser>, code: string, message: string) -> void
    let position: SourcePosition = source_position(0, 1, 1)
    parser->successful = false
    parser->diagnostic = diagnostic(code, message, position, position)
    return
end

fn parser_fail(parser: pointer<Parser>, code: string, message: string, span: SourceSpan) -> void
    parser->successful = false
    parser->diagnostic = diagnostic(
        code,
        message,
        span.start,
        span.end_position
    )
    return
end
