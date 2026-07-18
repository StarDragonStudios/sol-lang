package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record ParenthesizedExpression(Expression expression, SourceSpan span ) implements Expression {
    public ParenthesizedExpression {
        Objects.requireNonNull(
            expression,
            "Parenthesized expression must not be null."
        );

        Objects.requireNonNull(
            span,
            "Parenthesized expression source span must not be null."
        );
    }
}
