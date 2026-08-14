inject frontend.source only SourceSpan

struct Token
    kind: int
    lexeme: string
    span: SourceSpan
end

fn token(kind: int, lexeme: string, span: SourceSpan) -> Token
    return Token {
        kind: kind,
        lexeme: lexeme,
        span: span
    }
end

fn token_kind_eof() -> int
    return 0
end

fn token_kind_newline() -> int
    return 1
end

fn token_kind_identifier() -> int
    return 2
end

fn token_kind_integer_literal() -> int
    return 3
end

fn token_kind_float_literal() -> int
    return 4
end

fn token_kind_string_literal() -> int
    return 5
end

fn token_kind_char_literal() -> int
    return 6
end

fn token_kind_fn() -> int
    return 7
end

fn token_kind_let() -> int
    return 8
end

fn token_kind_const() -> int
    return 9
end

fn token_kind_if() -> int
    return 10
end

fn token_kind_else() -> int
    return 11
end

fn token_kind_while() -> int
    return 12
end

fn token_kind_return() -> int
    return 13
end

fn token_kind_then() -> int
    return 14
end

fn token_kind_do() -> int
    return 15
end

fn token_kind_end() -> int
    return 16
end

fn token_kind_inject() -> int
    return 17
end

fn token_kind_true() -> int
    return 18
end

fn token_kind_false() -> int
    return 19
end

fn token_kind_null() -> int
    return 20
end

fn token_kind_only() -> int
    return 21
end

fn token_kind_namespace() -> int
    return 22
end

fn token_kind_as() -> int
    return 23
end

fn token_kind_struct() -> int
    return 24
end

fn token_kind_at() -> int
    return 25
end

fn token_kind_left_paren() -> int
    return 26
end

fn token_kind_right_paren() -> int
    return 27
end

fn token_kind_left_brace() -> int
    return 28
end

fn token_kind_right_brace() -> int
    return 29
end

fn token_kind_left_bracket() -> int
    return 30
end

fn token_kind_right_bracket() -> int
    return 31
end

fn token_kind_comma() -> int
    return 32
end

fn token_kind_colon() -> int
    return 33
end

fn token_kind_double_colon() -> int
    return 34
end

fn token_kind_dot() -> int
    return 35
end

fn token_kind_arrow() -> int
    return 36
end

fn token_kind_assign() -> int
    return 37
end

fn token_kind_plus() -> int
    return 38
end

fn token_kind_minus() -> int
    return 39
end

fn token_kind_star() -> int
    return 40
end

fn token_kind_slash() -> int
    return 41
end

fn token_kind_percent() -> int
    return 42
end

fn token_kind_bang() -> int
    return 43
end

fn token_kind_and_and() -> int
    return 44
end

fn token_kind_or_or() -> int
    return 45
end

fn token_kind_equal_equal() -> int
    return 46
end

fn token_kind_not_equal() -> int
    return 47
end

fn token_kind_less() -> int
    return 48
end

fn token_kind_less_equal() -> int
    return 49
end

fn token_kind_greater() -> int
    return 50
end

fn token_kind_greater_equal() -> int
    return 51
end
