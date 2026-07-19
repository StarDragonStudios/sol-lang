package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record AssignmentStatement(NameExpression target, Expression value, SourceSpan span) implements Statement {
    public AssignmentStatement {
        Objects.requireNonNull(
            target,
            "Assignment target must not be null."
        );

        Objects.requireNonNull(
            value,
            "Assigned value must not be null."
        );

        Objects.requireNonNull(
            span,
            "Assignment statement source span must not be null."
        );
    }
}
