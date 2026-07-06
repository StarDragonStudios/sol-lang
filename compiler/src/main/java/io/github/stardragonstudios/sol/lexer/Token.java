package io.github.stardragonstudios.sol.lexer;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record Token(TokenKind kind, String lexeme, SourceSpan span) {
    public Token {
        Objects.requireNonNull(kind, "Token kind cannot be null.");
        Objects.requireNonNull(lexeme, "Token lexeme cannot be null.");
        Objects.requireNonNull(span, "Token span cannot be null.");
    }
}
