inject namespace std.console as console
inject std.collections.vector
inject frontend.source only SourcePosition, SourceSpan, source_position, source_span
inject frontend.token only Token, token, token_kind_eof, token_kind_identifier
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.syntax
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens

@init
fn launch() -> int
    let tree: int = test_syntax_tree_ownership_and_navigation()

    if tree != 0 then
        console::print_line("self-host parser test failed: syntax tree")
        return 10 + tree
    end

    let empty: int = test_empty_compilation_unit()

    if empty != 0 then
        console::print_line("self-host parser test failed: empty compilation unit")
        return 20 + empty
    end

    let newlines: int = test_newline_only_compilation_unit()

    if newlines != 0 then
        console::print_line("self-host parser test failed: newline-only compilation unit")
        return 30 + newlines
    end

    let unexpected: int = test_unexpected_top_level_token()

    if unexpected != 0 then
        console::print_line("self-host parser test failed: unexpected top-level token")
        return 40 + unexpected
    end

    let validation: int = test_token_stream_validation()

    if validation != 0 then
        console::print_line("self-host parser test failed: token stream validation")
        return 50 + validation
    end

    return 0
end

fn test_syntax_tree_ownership_and_navigation() -> int
    let start: SourcePosition = source_position(0, 1, 1)
    let end_position: SourcePosition = source_position(8, 1, 9)
    let span: SourceSpan = source_span(start, end_position)
    let root: pointer<SyntaxNode> = create_compilation_unit(span)
    let function: pointer<SyntaxNode> = create_syntax_node(
        syntax_kind_function_declaration(),
        syntax_function_bodyless(),
        "compile",
        span
    )
    let return_type: pointer<SyntaxNode> = create_syntax_node(
        syntax_kind_type_reference(),
        syntax_variant_none(),
        "int",
        span
    )
    @mut let failure: int = 0

    if root == null || function == null || return_type == null then
        failure = 1
    end

    if failure == 0 && !syntax_add_child(function, return_type) then
        failure = 2
    end

    if failure == 0 && !syntax_add_child(root, function) then
        failure = 3
    end

    if failure == 0 && syntax_child_count(root) != 1 then
        failure = 4
    end

    if failure == 0 && syntax_child(root, 0)->kind != syntax_kind_function_declaration() then
        failure = 5
    end

    if failure == 0 && syntax_child(root, 0)->variant != syntax_function_bodyless() then
        failure = 6
    end

    if failure == 0 && syntax_child(root, 0)->text != "compile" then
        failure = 7
    end

    if failure == 0 && syntax_child_count(syntax_child(root, 0)) != 1 then
        failure = 8
    end

    if failure == 0 && syntax_child(syntax_child(root, 0), 0)->text != "int" then
        failure = 9
    end

    if failure == 0 && syntax_child(root, 0)->span.end_position.offset != 8 then
        failure = 10
    end

    if failure == 0 && syntax_add_child(null, root) then
        failure = 11
    end

    if failure == 0 && syntax_child_count(null) != 0 then
        failure = 12
    end

    if root != null then
        destroy_syntax_tree(root)
    else
        destroy_syntax_tree(function)
        destroy_syntax_tree(return_type)
    end

    destroy_syntax_tree(null)
    return failure
end

fn test_empty_compilation_unit() -> int
    let lexical: LexResult = scan_source("")
    let parsed: ParseResult = parse_tokens(lexical.tokens)
    @mut let failure: int = 0

    if !lexical.successful || !parsed.successful || parsed.root == null then
        failure = 1
    end

    if failure == 0 && parsed.root->kind != syntax_kind_compilation_unit() then
        failure = 2
    end

    if failure == 0 && syntax_child_count(parsed.root) != 0 then
        failure = 3
    end

    if failure == 0 && parsed.root->span.start.offset != 0 then
        failure = 4
    end

    if failure == 0 && parsed.root->span.end_position.offset != 0 then
        failure = 5
    end

    destroy_parse_result(parsed)
    destroy_lex_result(lexical)
    return failure
end

fn test_newline_only_compilation_unit() -> int
    let lexical: LexResult = scan_source("\n\r\n\r")
    let parsed: ParseResult = parse_tokens(lexical.tokens)
    @mut let failure: int = 0

    if !lexical.successful || !parsed.successful || parsed.root == null then
        failure = 1
    end

    if failure == 0 && parsed.root->span.start.offset != 0 then
        failure = 2
    end

    if failure == 0 && parsed.root->span.end_position.offset != 4 then
        failure = 3
    end

    if failure == 0 && parsed.root->span.end_position.line != 4 then
        failure = 4
    end

    if failure == 0 && parsed.root->span.end_position.column != 1 then
        failure = 5
    end

    destroy_parse_result(parsed)
    destroy_lex_result(lexical)
    return failure
end

fn test_unexpected_top_level_token() -> int
    let lexical: LexResult = scan_source("fn")
    let parsed: ParseResult = parse_tokens(lexical.tokens)
    @mut let failure: int = 0

    if !lexical.successful || parsed.successful || parsed.root != null then
        failure = 1
    end

    if failure == 0 && parsed.diagnostic.code != "SOL-P001" then
        failure = 2
    end

    if failure == 0 && parsed.diagnostic.message != "Unexpected token 'fn' at top level." then
        failure = 3
    end

    if failure == 0 && parsed.diagnostic.span.start.offset != 0 then
        failure = 4
    end

    if failure == 0 && parsed.diagnostic.span.end_position.offset != 2 then
        failure = 5
    end

    destroy_parse_result(parsed)
    destroy_lex_result(lexical)
    return failure
end

fn test_token_stream_validation() -> int
    let null_result: ParseResult = parse_tokens(null)

    if null_result.successful || null_result.diagnostic.code != "SOL-P000" then
        destroy_parse_result(null_result)
        return 1
    end

    destroy_parse_result(null_result)

    let empty_tokens: pointer<Vector<Token>> = create_vector<Token>()
    let empty_result: ParseResult = parse_tokens(empty_tokens)

    if empty_result.successful || empty_result.diagnostic.message != "Parser token stream must not be empty." then
        destroy_parse_result(empty_result)
        destroy_vector<Token>(empty_tokens)
        return 2
    end

    destroy_parse_result(empty_result)
    destroy_vector<Token>(empty_tokens)

    let start: SourcePosition = source_position(0, 1, 1)
    let end_position: SourcePosition = source_position(1, 1, 2)
    let span: SourceSpan = source_span(start, end_position)
    let missing_eof: pointer<Vector<Token>> = create_vector<Token>()
    vector_push<Token>(
        missing_eof,
        token(token_kind_identifier(), "x", span)
    )

    let missing_result: ParseResult = parse_tokens(missing_eof)

    if missing_result.successful || missing_result.diagnostic.message != "Parser token stream must terminate with EOF." then
        destroy_parse_result(missing_result)
        destroy_vector<Token>(missing_eof)
        return 3
    end

    destroy_parse_result(missing_result)
    destroy_vector<Token>(missing_eof)

    let early_eof: pointer<Vector<Token>> = create_vector<Token>()
    vector_push<Token>(early_eof, token(token_kind_eof(), "", span))
    vector_push<Token>(early_eof, token(token_kind_eof(), "", span))

    let early_result: ParseResult = parse_tokens(early_eof)

    if early_result.successful || early_result.diagnostic.message != "Parser token stream must not contain EOF before its end." then
        destroy_parse_result(early_result)
        destroy_vector<Token>(early_eof)
        return 4
    end

    destroy_parse_result(early_result)
    destroy_vector<Token>(early_eof)
    return 0
end
