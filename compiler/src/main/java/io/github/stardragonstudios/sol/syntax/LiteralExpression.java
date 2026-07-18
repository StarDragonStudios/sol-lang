package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record LiteralExpression(LiteralKind kind, String lexeme, SourceSpan span) implements Expression {
    public LiteralExpression {
        Objects.requireNonNull(
            kind,
            "Literal kind must not be null."
        );

        Objects.requireNonNull(
            lexeme,
            "Literal lexeme must not be null."
        );

        Objects.requireNonNull(
            span,
            "Literal source span must not be null."
        );

        if (lexeme.isEmpty()) {
            throw new IllegalArgumentException(
                "Literal lexeme must not be empty."
            );
        }
    }
}
