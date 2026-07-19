package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record Annotation(
    String name,
    SourceSpan span
) implements SyntaxNode {
    public Annotation {
        Objects.requireNonNull(
            name,
            "Annotation name must not be null."
        );

        Objects.requireNonNull(
            span,
            "Annotation source span must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Annotation name must not be blank."
            );
        }
    }
}
