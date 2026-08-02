package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record CallStatement(CallExpression call, SourceSpan span) implements Statement {
    public CallStatement {
        Objects.requireNonNull(call, "Call statement expression must not be null.");
        Objects.requireNonNull(span, "Call statement source span must not be null.");
    }
}
