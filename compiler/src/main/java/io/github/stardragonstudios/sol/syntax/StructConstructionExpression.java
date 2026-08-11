package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;

public record StructConstructionExpression(TypeReference type, List<StructFieldInitializer> fields, SourceSpan span) implements Expression {
    public StructConstructionExpression {
        Objects.requireNonNull(type, "Constructed struct type must not be null.");
        Objects.requireNonNull(fields, "Struct field initializers must not be null.");
        Objects.requireNonNull(span, "Struct construction source span must not be null.");

        fields = List.copyOf(fields);
        fields.forEach(field -> Objects.requireNonNull(field, "Struct field initializers must not contain null values."));
    }
}
