package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;
import java.util.Optional;

public record ReturnStatement(Optional<Expression> expression, SourceSpan span) implements Statement {
    public ReturnStatement {
        Objects.requireNonNull(
            expression,
            "Return expression must not be null."
        );

        Objects.requireNonNull(
            span,
            "Return statement source span must not be null."
        );
    }
}
