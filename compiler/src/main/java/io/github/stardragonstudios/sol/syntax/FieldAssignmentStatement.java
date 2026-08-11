package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record FieldAssignmentStatement(FieldAccessExpression target, Expression value, SourceSpan span) implements Statement {
    public FieldAssignmentStatement {
        Objects.requireNonNull(target, "Field assignment target must not be null.");
        Objects.requireNonNull(value, "Field assignment value must not be null.");
        Objects.requireNonNull(span, "Field assignment source span must not be null.");
    }
}
