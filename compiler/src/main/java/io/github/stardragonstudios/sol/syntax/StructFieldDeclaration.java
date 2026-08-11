package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record StructFieldDeclaration(String name, TypeReference type, SourceSpan span) implements SyntaxNode {
    public StructFieldDeclaration {
        Objects.requireNonNull(name, "Struct field name must not be null.");
        Objects.requireNonNull(type, "Struct field type must not be null.");
        Objects.requireNonNull(span, "Struct field source span must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Struct field name must not be blank.");
    }
}
