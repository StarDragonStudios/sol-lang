package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record PointerAssignmentStatement(Expression target, Expression value, SourceSpan span) implements Statement {
    public PointerAssignmentStatement {
        Objects.requireNonNull(target, "Pointer assignment target must not be null.");
        Objects.requireNonNull(value, "Pointer assignment value must not be null.");
        Objects.requireNonNull(span, "Pointer assignment source span must not be null.");

        if (!(target instanceof PointerDereferenceExpression) && !(target instanceof PointerIndexExpression))
            throw new IllegalArgumentException("Pointer assignment target must be a dereference or pointer index expression.");
    }
}
