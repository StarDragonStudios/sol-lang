package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record FieldAccessExpression(Expression target, String fieldName, SourceSpan fieldSpan, SourceSpan span) implements Expression {
    public FieldAccessExpression {
        Objects.requireNonNull(target, "Field access target must not be null.");
        Objects.requireNonNull(fieldName, "Field access name must not be null.");
        Objects.requireNonNull(fieldSpan, "Field access name source span must not be null.");
        Objects.requireNonNull(span, "Field access source span must not be null.");

        if (fieldName.isBlank()) throw new IllegalArgumentException("Field access name must not be blank.");
    }
}
