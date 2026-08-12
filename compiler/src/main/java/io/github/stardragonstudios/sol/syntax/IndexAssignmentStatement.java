package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;
import java.util.Objects;

public record IndexAssignmentStatement(IndexExpression target, Expression value, SourceSpan span) implements Statement {
    public IndexAssignmentStatement {
        Objects.requireNonNull(target, "Index-assignment target must not be null.");
        Objects.requireNonNull(value, "Index-assignment value must not be null.");
        Objects.requireNonNull(span, "Index-assignment span must not be null.");
    }
}
