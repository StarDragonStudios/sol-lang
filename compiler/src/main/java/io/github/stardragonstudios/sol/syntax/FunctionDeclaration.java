package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record FunctionDeclaration(String name, TypeReference returnType, Block body, SourceSpan span) implements Declaration {
    public FunctionDeclaration {
        Objects.requireNonNull(
            name,
            "Function name must not be null."
        );

        Objects.requireNonNull(
            returnType,
            "Function return type must not be null."
        );

        Objects.requireNonNull(
            body,
            "Function body must not be null."
        );

        Objects.requireNonNull(
            span,
            "Function source span must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Function name must not be blank."
            );
        }
    }
}
