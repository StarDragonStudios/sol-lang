package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record PointerDereferenceExpression(Expression pointer, SourceSpan span) implements Expression {
    public PointerDereferenceExpression {
        Objects.requireNonNull(pointer, "Dereferenced pointer expression must not be null.");
        Objects.requireNonNull(span, "Pointer dereference source span must not be null.");
    }
}
