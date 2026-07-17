package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record TypeReference(
    String name,
    SourceSpan span
) implements SyntaxNode {
    public TypeReference {
        Objects.requireNonNull(
            name,
            "Type reference name must not be null."
        );

        Objects.requireNonNull(
            span,
            "Type reference source span must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Type reference name must not be blank."
            );
        }
    }
}
