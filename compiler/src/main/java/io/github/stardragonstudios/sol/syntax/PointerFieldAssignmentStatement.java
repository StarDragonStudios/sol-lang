package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;
import java.util.Objects;

public record PointerFieldAssignmentStatement(PointerFieldAccessExpression target, Expression value, SourceSpan span) implements Statement {
    public PointerFieldAssignmentStatement {
        Objects.requireNonNull(target, "Pointer-field assignment target must not be null.");
        Objects.requireNonNull(value, "Pointer-field assignment value must not be null.");
        Objects.requireNonNull(span, "Pointer-field assignment span must not be null.");
    }
}
