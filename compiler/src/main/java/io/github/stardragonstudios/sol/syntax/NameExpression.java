package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record NameExpression(String name, SourceSpan span) implements Expression {
    public NameExpression {
        Objects.requireNonNull(
            name,
            "Referenced name must not be null."
        );

        Objects.requireNonNull(
            span,
            "Name expression source span must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Referenced name must not be blank."
            );
        }
    }
}
