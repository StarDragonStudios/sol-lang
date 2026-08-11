package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;

public record StructDeclaration(
    String name,
    List<TypeParameter> typeParameters,
    List<StructFieldDeclaration> fields,
    SourceSpan span
) implements Declaration {
    public StructDeclaration {
        Objects.requireNonNull(name, "Struct name must not be null.");
        Objects.requireNonNull(typeParameters, "Struct type parameters must not be null.");
        Objects.requireNonNull(fields, "Struct fields must not be null.");
        Objects.requireNonNull(span, "Struct source span must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Struct name must not be blank.");

        typeParameters = List.copyOf(typeParameters);
        typeParameters.forEach(parameter -> Objects.requireNonNull(parameter, "Struct type parameters must not contain null values."));
        fields = List.copyOf(fields);
        fields.forEach(field -> Objects.requireNonNull(field, "Struct fields must not contain null values."));
    }

    public StructDeclaration(String name, List<StructFieldDeclaration> fields, SourceSpan span) {
        this(name, List.of(), fields, span);
    }
}
