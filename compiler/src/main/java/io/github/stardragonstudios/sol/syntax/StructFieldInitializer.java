package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record StructFieldInitializer(String name, Expression value, SourceSpan span) implements SyntaxNode {
    public StructFieldInitializer {
        Objects.requireNonNull(name, "Struct field initializer name must not be null.");
        Objects.requireNonNull(value, "Struct field initializer value must not be null.");
        Objects.requireNonNull(span, "Struct field initializer source span must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Struct field initializer name must not be blank.");
    }
}
