package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record PointerIndexExpression(Expression pointer, Expression index, SourceSpan span) implements Expression {
    public PointerIndexExpression {
        Objects.requireNonNull(pointer, "Indexed pointer expression must not be null.");
        Objects.requireNonNull(index, "Pointer index expression must not be null.");
        Objects.requireNonNull(span, "Pointer index source span must not be null.");
    }
}
