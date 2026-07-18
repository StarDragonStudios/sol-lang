package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record UnaryExpression(UnaryOperator operator, Expression operand, SourceSpan span) implements Expression {
    public UnaryExpression {
        Objects.requireNonNull(
            operator,
            "Unary operator must not be null."
        );

        Objects.requireNonNull(
            operand,
            "Unary operand must not be null."
        );

        Objects.requireNonNull(
            span,
            "Unary expression source span must not be null."
        );
    }
}
