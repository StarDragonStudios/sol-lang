package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record Parameter(String name, TypeReference type, SourceSpan span) implements SyntaxNode {
    public Parameter {
        Objects.requireNonNull(
            name,
            "Parameter name must not be null."
        );

        Objects.requireNonNull(
            type,
            "Parameter type must not be null."
        );

        Objects.requireNonNull(
            span,
            "Parameter source span must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Parameter name must not be blank."
            );
        }
    }
}
