package io.github.stardragonstudios.sol.lexer;

public enum TokenKind {
    // Special
    EOF,
    NEWLINE,

    // Names and literals
    IDENTIFIER,
    INTEGER_LITERAL,
    FLOAT_LITERAL,
    STRING_LITERAL,
    CHAR_LITERAL,

    // Keywords
    FN,
    LET,
    CONST,
    IF,
    ELSE,
    WHILE,
    RETURN,
    THEN,
    DO,
    END,
    INJECT,
    TRUE,
    FALSE,
    ONLY,
    NAMESPACE,
    AS,

    // Punctuation
    AT,
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,
    COLON,
    DOUBLE_COLON,
    DOT,
    ARROW,

    // Operators
    ASSIGN,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    BANG,
    AND_AND,
    OR_OR,
    EQUAL_EQUAL,
    NOT_EQUAL,
    LESS,
    LESS_EQUAL,
    GREATER,
    GREATER_EQUAL
}
