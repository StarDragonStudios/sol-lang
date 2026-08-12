package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;
import java.util.Objects;

public record PointerFieldAccessExpression(Expression pointer, String fieldName, SourceSpan fieldSpan, SourceSpan span) implements Expression {
    public PointerFieldAccessExpression {
        Objects.requireNonNull(pointer, "Pointer-field target must not be null.");
        Objects.requireNonNull(fieldName, "Pointer-field name must not be null.");
        Objects.requireNonNull(fieldSpan, "Pointer-field name span must not be null.");
        Objects.requireNonNull(span, "Pointer-field access span must not be null.");

        if (fieldName.isBlank()) throw new IllegalArgumentException("Pointer-field name must not be blank.");
    }
}
