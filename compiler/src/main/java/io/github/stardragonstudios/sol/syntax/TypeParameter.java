package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record TypeParameter(String name, SourceSpan span) implements SyntaxNode {
    public TypeParameter {
        Objects.requireNonNull(name, "Type parameter name must not be null.");
        Objects.requireNonNull(span, "Type parameter source span must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Type parameter name must not be blank.");
    }
}
