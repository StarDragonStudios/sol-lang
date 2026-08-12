package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;
import java.util.Objects;

public record IndexExpression(Expression target, Expression index, SourceSpan span) implements Expression {
    public IndexExpression {
        Objects.requireNonNull(target, "Indexed target must not be null.");
        Objects.requireNonNull(index, "Index expression must not be null.");
        Objects.requireNonNull(span, "Index-expression span must not be null.");
    }
}
