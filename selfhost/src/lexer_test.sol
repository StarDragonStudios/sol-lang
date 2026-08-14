inject namespace std.console as console
inject std.collections.vector only vector_get, vector_length
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.token

@init
fn launch() -> int
    let keywords: int = test_keywords_and_identifiers()

    if keywords != 0 then
        console::print_line("self-host lexer test failed: keywords and identifiers")
        return 10 + keywords
    end

    let numbers: int = test_numbers_and_literals()

    if numbers != 0 then
        console::print_line("self-host lexer test failed: numbers and literals")
        return 20 + numbers
    end

    let punctuation: int = test_punctuation_and_operators()

    if punctuation != 0 then
        console::print_line("self-host lexer test failed: punctuation and operators")
        return 30 + punctuation
    end

    let newlines: int = test_newlines_and_spans()

    if newlines != 0 then
        console::print_line("self-host lexer test failed: newlines and spans")
        return 40 + newlines
    end

    let comments: int = test_comments()

    if comments != 0 then
        console::print_line("self-host lexer test failed: comments")
        return 50 + comments
    end

    let diagnostics: int = test_diagnostics()

    if diagnostics != 0 then
        console::print_line("self-host lexer test failed: diagnostics")
        return 60 + diagnostics
    end

    let unicode: int = test_unicode_scalar_spans()

    if unicode != 0 then
        console::print_line("self-host lexer test failed: Unicode scalar spans")
        return 70 + unicode
    end

    return 0
end

fn test_keywords_and_identifiers() -> int
    let result: LexResult = scan_source(
        "fn let const if else while return then do end inject true false null only namespace as struct value_42 Fn"
    )
    @mut let failure: int = 0

    if !result.successful || vector_length<Token>(result.tokens) != 21 then
        failure = 1
    end

    if failure == 0 && !token_matches(result, 0, token_kind_fn(), "fn") then
        failure = 2
    end

    if failure == 0 && !token_matches(result, 6, token_kind_return(), "return") then
        failure = 3
    end

    if failure == 0 && !token_matches(result, 13, token_kind_null(), "null") then
        failure = 4
    end

    if failure == 0 && !token_matches(result, 17, token_kind_struct(), "struct") then
        failure = 5
    end

    if failure == 0 && !token_matches(result, 18, token_kind_identifier(), "value_42") then
        failure = 6
    end

    if failure == 0 && !token_matches(result, 19, token_kind_identifier(), "Fn") then
        failure = 7
    end

    if failure == 0 && !token_matches(result, 20, token_kind_eof(), "") then
        failure = 8
    end

    destroy_lex_result(result)
    return failure
end

fn test_numbers_and_literals() -> int
    let result: LexResult = scan_source("0 42 3.14 10.5 \"Sol 🐉\" 'a' '\\n' '🐉'")
    @mut let failure: int = 0

    if !result.successful || vector_length<Token>(result.tokens) != 9 then
        failure = 1
    end

    if failure == 0 && !token_matches(result, 0, token_kind_integer_literal(), "0") then
        failure = 2
    end

    if failure == 0 && !token_matches(result, 2, token_kind_float_literal(), "3.14") then
        failure = 3
    end

    if failure == 0 && !token_matches(result, 4, token_kind_string_literal(), "\"Sol 🐉\"") then
        failure = 4
    end

    if failure == 0 && !token_matches(result, 5, token_kind_char_literal(), "'a'") then
        failure = 5
    end

    if failure == 0 && !token_matches(result, 6, token_kind_char_literal(), "'\\n'") then
        failure = 6
    end

    if failure == 0 && !token_matches(result, 7, token_kind_char_literal(), "'🐉'") then
        failure = 7
    end

    destroy_lex_result(result)
    return failure
end

fn test_punctuation_and_operators() -> int
    let result: LexResult = scan_source("@ ( ) { } [ ] , : :: . -> = + - * / % ! && || == != < <= > >=")
    @mut let failure: int = 0

    if !result.successful || vector_length<Token>(result.tokens) != 28 then
        failure = 1
    end

    if failure == 0 && !token_matches(result, 0, token_kind_at(), "@") then
        failure = 2
    end

    if failure == 0 && !token_matches(result, 7, token_kind_comma(), ",") then
        failure = 3
    end

    if failure == 0 && !token_matches(result, 9, token_kind_double_colon(), "::") then
        failure = 4
    end

    if failure == 0 && !token_matches(result, 11, token_kind_arrow(), "->") then
        failure = 5
    end

    if failure == 0 && !token_matches(result, 19, token_kind_and_and(), "&&") then
        failure = 6
    end

    if failure == 0 && !token_matches(result, 22, token_kind_not_equal(), "!=") then
        failure = 7
    end

    if failure == 0 && !token_matches(result, 26, token_kind_greater_equal(), ">=") then
        failure = 8
    end

    destroy_lex_result(result)
    return failure
end

fn test_newlines_and_spans() -> int
    let result: LexResult = scan_source("fn\nx\r\ny\rz")
    @mut let failure: int = 0

    if !result.successful || vector_length<Token>(result.tokens) != 8 then
        failure = 1
    end

    if failure == 0 && !span_matches(result, 0, 0, 1, 1, 2, 1, 3) then
        failure = 2
    end

    if failure == 0 && !token_matches(result, 1, token_kind_newline(), "\n") then
        failure = 3
    end

    if failure == 0 && !span_matches(result, 2, 3, 2, 1, 4, 2, 2) then
        failure = 4
    end

    if failure == 0 && !token_matches(result, 3, token_kind_newline(), "\r\n") then
        failure = 5
    end

    if failure == 0 && !span_matches(result, 4, 6, 3, 1, 7, 3, 2) then
        failure = 6
    end

    if failure == 0 && !token_matches(result, 5, token_kind_newline(), "\r") then
        failure = 7
    end

    if failure == 0 && !span_matches(result, 6, 8, 4, 1, 9, 4, 2) then
        failure = 8
    end

    destroy_lex_result(result)
    return failure
end

fn test_comments() -> int
    let result: LexResult = scan_source("left/* one\n two\r\n*/right // tail\nend")
    @mut let failure: int = 0

    if !result.successful || vector_length<Token>(result.tokens) != 5 then
        failure = 1
    end

    if failure == 0 && !token_matches(result, 0, token_kind_identifier(), "left") then
        failure = 2
    end

    if failure == 0 && !token_matches(result, 1, token_kind_identifier(), "right") then
        failure = 3
    end

    if failure == 0 && !span_matches(result, 1, 19, 3, 3, 24, 3, 8) then
        failure = 4
    end

    if failure == 0 && !token_matches(result, 2, token_kind_newline(), "\n") then
        failure = 5
    end

    if failure == 0 && !token_matches(result, 3, token_kind_end(), "end") then
        failure = 6
    end

    destroy_lex_result(result)
    return failure
end

fn test_diagnostics() -> int
    if !diagnostic_matches("&", "SOL-L001", "Unexpected character '&'.", 0, 1) then
        return 1
    end

    if !diagnostic_matches("\"unterminated", "SOL-L002", "Unterminated string literal.", 0, 13) then
        return 2
    end

    if !diagnostic_matches("\"bad\\x\"", "SOL-L003", "Invalid escape sequence '\\x'.", 4, 6) then
        return 3
    end

    if !diagnostic_matches("'ab'", "SOL-L004", "Invalid character literal.", 0, 4) then
        return 4
    end

    if !diagnostic_matches("/* open", "SOL-L005", "Unterminated block comment.", 0, 7) then
        return 5
    end

    return 0
end

fn test_unicode_scalar_spans() -> int
    let result: LexResult = scan_source("\"🐉\"\nname")
    @mut let failure: int = 0

    if !result.successful || vector_length<Token>(result.tokens) != 4 then
        failure = 1
    end

    if failure == 0 && !span_matches(result, 0, 0, 1, 1, 3, 1, 4) then
        failure = 2
    end

    if failure == 0 && !span_matches(result, 2, 4, 2, 1, 8, 2, 5) then
        failure = 3
    end

    destroy_lex_result(result)
    return failure
end

fn token_matches(result: LexResult, index: int, kind: int, lexeme: string) -> boolean
    if !result.successful || index < 0 || index >= vector_length<Token>(result.tokens) then
        return false
    end

    let actual: Token = vector_get<Token>(result.tokens, index)
    return actual.kind == kind && actual.lexeme == lexeme
end

fn span_matches(result: LexResult, index: int, start_offset: int, start_line: int, start_column: int, end_offset: int, end_line: int, end_column: int) -> boolean
    if !result.successful || index < 0 || index >= vector_length<Token>(result.tokens) then
        return false
    end

    let actual: Token = vector_get<Token>(result.tokens, index)
    return actual.span.start.offset == start_offset && actual.span.start.line == start_line && actual.span.start.column == start_column && actual.span.end_position.offset == end_offset && actual.span.end_position.line == end_line && actual.span.end_position.column == end_column
end

fn diagnostic_matches(source: string, code: string, message: string, start_offset: int, end_offset: int) -> boolean
    let result: LexResult = scan_source(source)
    let matches: boolean = !result.successful && result.diagnostic.code == code && result.diagnostic.message == message && result.diagnostic.span.start.offset == start_offset && result.diagnostic.span.end_position.offset == end_offset

    destroy_lex_result(result)
    return matches
end
