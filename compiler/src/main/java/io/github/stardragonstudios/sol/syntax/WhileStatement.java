package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record WhileStatement(Expression condition, Block body, SourceSpan span) implements Statement {
    public WhileStatement {
        Objects.requireNonNull(
            condition,
            "While condition must not be null."
        );

        Objects.requireNonNull(
            body,
            "While body must not be null."
        );

        Objects.requireNonNull(
            span,
            "While statement source span must not be null."
        );
    }
}
