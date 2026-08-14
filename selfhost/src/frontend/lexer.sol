inject namespace std.memory as memory
inject namespace std.string as strings
inject std.collections.vector
inject frontend.source only SourcePosition, SourceSpan, source_position, source_span
inject frontend.diagnostic only LexicalDiagnostic, empty_lexical_diagnostic, lexical_diagnostic
inject frontend.token

struct LexResult
    successful: boolean
    tokens: pointer<Vector<Token>>
    diagnostic: LexicalDiagnostic
end

struct Lexer
    source: string
    length: int
    offset: int
    line: int
    column: int
    successful: boolean
    tokens: pointer<Vector<Token>>
    diagnostic: LexicalDiagnostic
end

fn scan_source(source: string) -> LexResult
    let tokens: pointer<Vector<Token>> = create_vector<Token>()
    let lexer: pointer<Lexer> = memory::allocate<Lexer>(1)

    if lexer == null then
        let position: SourcePosition = source_position(0, 1, 1)

        return LexResult {
            successful: false,
            tokens: tokens,
            diagnostic: lexical_diagnostic(
                "SOL-L000",
                "Unable to allocate lexical scanner state.",
                position,
                position
            )
        }
    end

    lexer->source = source
    lexer->length = strings::length(source)
    lexer->offset = 0
    lexer->line = 1
    lexer->column = 1
    lexer->successful = true
    lexer->tokens = tokens
    lexer->diagnostic = empty_lexical_diagnostic()

    while !lexer_is_at_end(lexer) && lexer->successful do
        scan_next_token(lexer)
    end

    if lexer->successful then
        let position: SourcePosition = lexer_current_position(lexer)
        vector_push<Token>(
            lexer->tokens,
            token(token_kind_eof(), "", source_span(position, position))
        )
    end

    let result: LexResult = LexResult {
        successful: lexer->successful,
        tokens: lexer->tokens,
        diagnostic: lexer->diagnostic
    }

    memory::free<Lexer>(lexer)
    return result
end

fn destroy_lex_result(result: LexResult) -> void
    destroy_vector<Token>(result.tokens)
    return
end

fn scan_next_token(lexer: pointer<Lexer>) -> void
    let start: SourcePosition = lexer_current_position(lexer)
    let current: char = lexer_peek(lexer)

    if is_identifier_start(current) then
        scan_identifier(lexer, start)
        return
    end

    if is_digit(current) then
        scan_number(lexer, start)
        return
    end

    if current == ' ' || current == '\t' then
        lexer_advance(lexer)
        return
    end

    if current == '\n' then
        scan_newline(lexer, start)
        return
    end

    if current == '\r' then
        scan_carriage_return(lexer, start)
        return
    end

    if current == '"' then
        scan_string_literal(lexer, start)
        return
    end

    if current == '\'' then
        scan_character_literal(lexer, start)
        return
    end

    if current == '@' then
        scan_single_character_token(lexer, start, token_kind_at())
        return
    end

    if current == '(' then
        scan_single_character_token(lexer, start, token_kind_left_paren())
        return
    end

    if current == ')' then
        scan_single_character_token(lexer, start, token_kind_right_paren())
        return
    end

    if current == '{' then
        scan_single_character_token(lexer, start, token_kind_left_brace())
        return
    end

    if current == '}' then
        scan_single_character_token(lexer, start, token_kind_right_brace())
        return
    end

    if current == '[' then
        scan_single_character_token(lexer, start, token_kind_left_bracket())
        return
    end

    if current == ']' then
        scan_single_character_token(lexer, start, token_kind_right_bracket())
        return
    end

    if current == ',' then
        scan_single_character_token(lexer, start, token_kind_comma())
        return
    end

    if current == ':' then
        scan_one_or_two_character_token(
            lexer,
            start,
            ':',
            token_kind_colon(),
            token_kind_double_colon()
        )
        return
    end

    if current == '.' then
        scan_single_character_token(lexer, start, token_kind_dot())
        return
    end

    if current == '+' then
        scan_single_character_token(lexer, start, token_kind_plus())
        return
    end

    if current == '*' then
        scan_single_character_token(lexer, start, token_kind_star())
        return
    end

    if current == '/' then
        scan_slash_or_comment(lexer, start)
        return
    end

    if current == '%' then
        scan_single_character_token(lexer, start, token_kind_percent())
        return
    end

    if current == '-' then
        scan_one_or_two_character_token(
            lexer,
            start,
            '>',
            token_kind_minus(),
            token_kind_arrow()
        )
        return
    end

    if current == '=' then
        scan_one_or_two_character_token(
            lexer,
            start,
            '=',
            token_kind_assign(),
            token_kind_equal_equal()
        )
        return
    end

    if current == '<' then
        scan_one_or_two_character_token(
            lexer,
            start,
            '=',
            token_kind_less(),
            token_kind_less_equal()
        )
        return
    end

    if current == '>' then
        scan_one_or_two_character_token(
            lexer,
            start,
            '=',
            token_kind_greater(),
            token_kind_greater_equal()
        )
        return
    end

    if current == '!' then
        scan_one_or_two_character_token(
            lexer,
            start,
            '=',
            token_kind_bang(),
            token_kind_not_equal()
        )
        return
    end

    if current == '&' then
        scan_required_two_character_token(lexer, start, '&', token_kind_and_and())
        return
    end

    if current == '|' then
        scan_required_two_character_token(lexer, start, '|', token_kind_or_or())
        return
    end

    lexer_advance(lexer)
    lexer_fail(
        lexer,
        "SOL-L001",
        "Unexpected character '" + strings::substring(lexer->source, start.offset, 1) + "'.",
        start
    )
    return
end

fn scan_identifier(lexer: pointer<Lexer>, start: SourcePosition) -> void
    let start_offset: int = lexer->offset

    lexer_advance(lexer)

    while lexer_peek_is_identifier_part(lexer) do
        lexer_advance(lexer)
    end

    let lexeme: string = strings::slice(lexer->source, start_offset, lexer->offset)
    add_token(lexer, keyword_kind(lexeme), lexeme, start)
    return
end

fn keyword_kind(lexeme: string) -> int
    if lexeme == "fn" then
        return token_kind_fn()
    end

    if lexeme == "let" then
        return token_kind_let()
    end

    if lexeme == "const" then
        return token_kind_const()
    end

    if lexeme == "if" then
        return token_kind_if()
    end

    if lexeme == "else" then
        return token_kind_else()
    end

    if lexeme == "while" then
        return token_kind_while()
    end

    if lexeme == "return" then
        return token_kind_return()
    end

    if lexeme == "then" then
        return token_kind_then()
    end

    if lexeme == "do" then
        return token_kind_do()
    end

    if lexeme == "end" then
        return token_kind_end()
    end

    if lexeme == "inject" then
        return token_kind_inject()
    end

    if lexeme == "true" then
        return token_kind_true()
    end

    if lexeme == "false" then
        return token_kind_false()
    end

    if lexeme == "null" then
        return token_kind_null()
    end

    if lexeme == "only" then
        return token_kind_only()
    end

    if lexeme == "namespace" then
        return token_kind_namespace()
    end

    if lexeme == "as" then
        return token_kind_as()
    end

    if lexeme == "struct" then
        return token_kind_struct()
    end

    return token_kind_identifier()
end

fn scan_number(lexer: pointer<Lexer>, start: SourcePosition) -> void
    let start_offset: int = lexer->offset

    while lexer_peek_is_digit(lexer) do
        lexer_advance(lexer)
    end

    @mut let kind: int = token_kind_integer_literal()

    if lexer_peek_is(lexer, '.') && lexer_peek_next_is_digit(lexer) then
        kind = token_kind_float_literal()
        lexer_advance(lexer)

        while lexer_peek_is_digit(lexer) do
            lexer_advance(lexer)
        end
    end

    add_token(
        lexer,
        kind,
        strings::slice(lexer->source, start_offset, lexer->offset),
        start
    )
    return
end

fn scan_newline(lexer: pointer<Lexer>, start: SourcePosition) -> void
    let start_offset: int = lexer->offset
    lexer_advance(lexer)
    lexer->line = lexer->line + 1
    lexer->column = 1
    add_token(
        lexer,
        token_kind_newline(),
        strings::slice(lexer->source, start_offset, lexer->offset),
        start
    )
    return
end

fn scan_carriage_return(lexer: pointer<Lexer>, start: SourcePosition) -> void
    let start_offset: int = lexer->offset
    lexer_advance(lexer)

    if lexer_peek_is(lexer, '\n') then
        lexer_advance(lexer)
    end

    lexer->line = lexer->line + 1
    lexer->column = 1
    add_token(
        lexer,
        token_kind_newline(),
        strings::slice(lexer->source, start_offset, lexer->offset),
        start
    )
    return
end

fn scan_string_literal(lexer: pointer<Lexer>, start: SourcePosition) -> void
    let start_offset: int = lexer->offset
    lexer_advance(lexer)

    while !lexer_is_at_end(lexer) do
        if lexer_peek(lexer) == '"' then
            lexer_advance(lexer)
            add_token(
                lexer,
                token_kind_string_literal(),
                strings::slice(lexer->source, start_offset, lexer->offset),
                start
            )
            return
        end

        if lexer_peek(lexer) == '\n' || lexer_peek(lexer) == '\r' then
            lexer_fail(lexer, "SOL-L002", "Unterminated string literal.", start)
            return
        end

        if lexer_peek(lexer) == '\\' then
            scan_escape_sequence(lexer)

            if !lexer->successful then
                return
            end
        else
            lexer_advance(lexer)
        end
    end

    lexer_fail(lexer, "SOL-L002", "Unterminated string literal.", start)
    return
end

fn scan_character_literal(lexer: pointer<Lexer>, start: SourcePosition) -> void
    let start_offset: int = lexer->offset
    lexer_advance(lexer)

    if lexer_is_at_end(lexer) then
        lexer_fail(lexer, "SOL-L004", "Invalid character literal.", start)
        return
    end

    if lexer_peek(lexer) == '\n' || lexer_peek(lexer) == '\r' then
        lexer_fail(lexer, "SOL-L004", "Invalid character literal.", start)
        return
    end

    if lexer_peek(lexer) == '\'' then
        lexer_advance(lexer)
        lexer_fail(lexer, "SOL-L004", "Invalid character literal.", start)
        return
    end

    if lexer_peek(lexer) == '\\' then
        scan_escape_sequence(lexer)

        if !lexer->successful then
            return
        end
    else
        lexer_advance(lexer)
    end

    if lexer_peek_is(lexer, '\'') then
        lexer_advance(lexer)
        add_token(
            lexer,
            token_kind_char_literal(),
            strings::slice(lexer->source, start_offset, lexer->offset),
            start
        )
        return
    end

    consume_invalid_character_literal_tail(lexer)
    lexer_fail(lexer, "SOL-L004", "Invalid character literal.", start)
    return
end

fn scan_escape_sequence(lexer: pointer<Lexer>) -> void
    let escape_start: SourcePosition = lexer_current_position(lexer)
    lexer_advance(lexer)

    if lexer_is_at_end(lexer) then
        lexer_fail(lexer, "SOL-L003", "Incomplete escape sequence.", escape_start)
        return
    end

    let escaped: char = lexer_advance(lexer)

    if !is_valid_escape_character(escaped) then
        lexer_fail(
            lexer,
            "SOL-L003",
            "Invalid escape sequence '" + strings::substring(lexer->source, escape_start.offset, 2) + "'.",
            escape_start
        )
    end
    return
end

fn scan_slash_or_comment(lexer: pointer<Lexer>, start: SourcePosition) -> void
    let start_offset: int = lexer->offset
    lexer_advance(lexer)

    if lexer_is_at_end(lexer) then
        add_token(
            lexer,
            token_kind_slash(),
            strings::slice(lexer->source, start_offset, lexer->offset),
            start
        )
        return
    end

    if lexer_peek(lexer) == '/' then
        scan_line_comment(lexer)
        return
    end

    if lexer_peek(lexer) == '*' then
        scan_block_comment(lexer, start)
        return
    end

    add_token(
        lexer,
        token_kind_slash(),
        strings::slice(lexer->source, start_offset, lexer->offset),
        start
    )
    return
end

fn scan_line_comment(lexer: pointer<Lexer>) -> void
    while lexer_has_non_line_break(lexer) do
        lexer_advance(lexer)
    end
    return
end

fn scan_block_comment(lexer: pointer<Lexer>, start: SourcePosition) -> void
    lexer_advance(lexer)

    while !lexer_is_at_end(lexer) do
        if lexer_peek(lexer) == '*' && lexer_peek_next_is(lexer, '/') then
            lexer_advance(lexer)
            lexer_advance(lexer)
            return
        end

        if lexer_peek(lexer) == '\n' || lexer_peek(lexer) == '\r' then
            skip_comment_line_break(lexer)
        else
            lexer_advance(lexer)
        end
    end

    lexer_fail(lexer, "SOL-L005", "Unterminated block comment.", start)
    return
end

fn skip_comment_line_break(lexer: pointer<Lexer>) -> void
    if lexer_peek(lexer) == '\r' then
        lexer_advance(lexer)

        if lexer_peek_is(lexer, '\n') then
            lexer_advance(lexer)
        end
    else
        lexer_advance(lexer)
    end

    lexer->line = lexer->line + 1
    lexer->column = 1
    return
end

fn scan_single_character_token(lexer: pointer<Lexer>, start: SourcePosition, kind: int) -> void
    let start_offset: int = lexer->offset
    lexer_advance(lexer)
    add_token(
        lexer,
        kind,
        strings::slice(lexer->source, start_offset, lexer->offset),
        start
    )
    return
end

fn scan_one_or_two_character_token(lexer: pointer<Lexer>, start: SourcePosition, expected: char, single_kind: int, double_kind: int) -> void
    let start_offset: int = lexer->offset
    lexer_advance(lexer)
    @mut let kind: int = single_kind

    if lexer_peek_is(lexer, expected) then
        lexer_advance(lexer)
        kind = double_kind
    end

    add_token(
        lexer,
        kind,
        strings::slice(lexer->source, start_offset, lexer->offset),
        start
    )
    return
end

fn scan_required_two_character_token(lexer: pointer<Lexer>, start: SourcePosition, expected: char, kind: int) -> void
    let start_offset: int = lexer->offset
    lexer_advance(lexer)

    if lexer_is_at_end(lexer) then
        lexer_fail(
            lexer,
            "SOL-L001",
            "Unexpected character '" + strings::substring(lexer->source, start_offset, 1) + "'.",
            start
        )
        return
    end

    if lexer_peek(lexer) != expected then
        lexer_fail(
            lexer,
            "SOL-L001",
            "Unexpected character '" + strings::substring(lexer->source, start_offset, 1) + "'.",
            start
        )
        return
    end

    lexer_advance(lexer)
    add_token(
        lexer,
        kind,
        strings::slice(lexer->source, start_offset, lexer->offset),
        start
    )
    return
end

fn add_token(lexer: pointer<Lexer>, kind: int, lexeme: string, start: SourcePosition) -> void
    vector_push<Token>(
        lexer->tokens,
        token(kind, lexeme, source_span(start, lexer_current_position(lexer)))
    )
    return
end

fn lexer_fail(lexer: pointer<Lexer>, code: string, message: string, start: SourcePosition) -> void
    lexer->successful = false
    lexer->diagnostic = lexical_diagnostic(
        code,
        message,
        start,
        lexer_current_position(lexer)
    )
    return
end

fn consume_invalid_character_literal_tail(lexer: pointer<Lexer>) -> void
    while lexer_has_character_literal_tail(lexer) do
        lexer_advance(lexer)
    end

    if lexer_peek_is(lexer, '\'') then
        lexer_advance(lexer)
    end
    return
end

fn lexer_current_position(lexer: pointer<Lexer>) -> SourcePosition
    return source_position(lexer->offset, lexer->line, lexer->column)
end

fn lexer_is_at_end(lexer: pointer<Lexer>) -> boolean
    return lexer->offset >= lexer->length
end

fn lexer_has_next_character(lexer: pointer<Lexer>) -> boolean
    return lexer->offset + 1 < lexer->length
end

fn lexer_peek(lexer: pointer<Lexer>) -> char
    return lexer->source[lexer->offset]
end

fn lexer_peek_next(lexer: pointer<Lexer>) -> char
    return lexer->source[lexer->offset + 1]
end

fn lexer_peek_is(lexer: pointer<Lexer>, expected: char) -> boolean
    if lexer_is_at_end(lexer) then
        return false
    end

    return lexer_peek(lexer) == expected
end

fn lexer_peek_next_is(lexer: pointer<Lexer>, expected: char) -> boolean
    if !lexer_has_next_character(lexer) then
        return false
    end

    return lexer_peek_next(lexer) == expected
end

fn lexer_peek_is_identifier_part(lexer: pointer<Lexer>) -> boolean
    if lexer_is_at_end(lexer) then
        return false
    end

    return is_identifier_part(lexer_peek(lexer))
end

fn lexer_peek_is_digit(lexer: pointer<Lexer>) -> boolean
    if lexer_is_at_end(lexer) then
        return false
    end

    return is_digit(lexer_peek(lexer))
end

fn lexer_peek_next_is_digit(lexer: pointer<Lexer>) -> boolean
    if !lexer_has_next_character(lexer) then
        return false
    end

    return is_digit(lexer_peek_next(lexer))
end

fn lexer_has_non_line_break(lexer: pointer<Lexer>) -> boolean
    if lexer_is_at_end(lexer) then
        return false
    end

    return lexer_peek(lexer) != '\n' && lexer_peek(lexer) != '\r'
end

fn lexer_has_character_literal_tail(lexer: pointer<Lexer>) -> boolean
    if lexer_is_at_end(lexer) then
        return false
    end

    return lexer_peek(lexer) != '\n' && lexer_peek(lexer) != '\r' && lexer_peek(lexer) != '\''
end

fn lexer_advance(lexer: pointer<Lexer>) -> char
    let current: char = lexer->source[lexer->offset]
    lexer->offset = lexer->offset + 1
    lexer->column = lexer->column + 1
    return current
end

fn is_identifier_start(character: char) -> boolean
    return is_ascii_letter(character) || character == '_'
end

fn is_identifier_part(character: char) -> boolean
    return is_identifier_start(character) || is_digit(character)
end

fn is_ascii_letter(character: char) -> boolean
    return character_in("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", character)
end

fn is_digit(character: char) -> boolean
    return character_in("0123456789", character)
end

fn is_valid_escape_character(character: char) -> boolean
    return character == 'n' || character == 'r' || character == 't' || character == '\\' || character == '"' || character == '\''
end

fn character_in(characters: string, character: char) -> boolean
    @mut let index: int = 0
    let length: int = strings::length(characters)

    while index < length do
        if characters[index] == character then
            return true
        end

        index = index + 1
    end

    return false
end
