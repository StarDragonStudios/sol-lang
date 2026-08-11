package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record NullExpression(SourceSpan span) implements Expression {
    public NullExpression {
        Objects.requireNonNull(span, "Null expression source span must not be null.");
    }
}
