package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record VariableDeclarationStatement(VariableDeclarationKind kind, String name, TypeReference type, Expression initializer, SourceSpan span) implements Statement {
    public VariableDeclarationStatement {
        Objects.requireNonNull(
            kind,
            "Variable declaration kind must not be null."
        );

        Objects.requireNonNull(
            name,
            "Variable name must not be null."
        );

        Objects.requireNonNull(
            type,
            "Variable type must not be null."
        );

        Objects.requireNonNull(
            initializer,
            "Variable initializer must not be null."
        );

        Objects.requireNonNull(
            span,
            "Variable declaration source span must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Variable name must not be blank."
            );
        }
    }
}
