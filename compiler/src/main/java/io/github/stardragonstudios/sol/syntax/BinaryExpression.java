package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record BinaryExpression(Expression left, BinaryOperator operator, Expression right, SourceSpan span) implements Expression {
    public BinaryExpression {
        Objects.requireNonNull(
            left,
            "Binary left operand must not be null."
        );

        Objects.requireNonNull(
            operator,
            "Binary operator must not be null."
        );

        Objects.requireNonNull(
            right,
            "Binary right operand must not be null."
        );

        Objects.requireNonNull(
            span,
            "Binary expression source span must not be null."
        );
    }
}
