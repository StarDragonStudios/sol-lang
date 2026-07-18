package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;

public record CallExpression(Expression callee, List<Expression> arguments, SourceSpan span) implements Expression {
    public CallExpression {
        Objects.requireNonNull(
            callee,
            "Call callee must not be null."
        );

        Objects.requireNonNull(
            arguments,
            "Call arguments must not be null."
        );

        Objects.requireNonNull(
            span,
            "Call expression source span must not be null."
        );

        arguments = List.copyOf(arguments);
    }
}
