package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;

public record CallExpression(
    Expression callee,
    List<TypeReference> typeArguments,
    List<Expression> arguments,
    SourceSpan span
) implements Expression {
    public CallExpression {
        Objects.requireNonNull(
            callee,
            "Call callee must not be null."
        );

        Objects.requireNonNull(
            typeArguments,
            "Call type arguments must not be null."
        );

        Objects.requireNonNull(
            arguments,
            "Call arguments must not be null."
        );

        Objects.requireNonNull(
            span,
            "Call expression source span must not be null."
        );

        typeArguments = List.copyOf(typeArguments);
        arguments = List.copyOf(arguments);

        typeArguments.forEach(argument -> Objects.requireNonNull(argument, "Call type arguments must not contain null values."));
    }

    public CallExpression(Expression callee, List<Expression> arguments, SourceSpan span) {
        this(callee, List.of(), arguments, span);
    }
}
