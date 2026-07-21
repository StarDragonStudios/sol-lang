package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record QualifiedNameExpression(NameExpression qualifier, NameExpression member, SourceSpan span) implements Expression {
    public QualifiedNameExpression {
        Objects.requireNonNull(qualifier, "Qualified-name qualifier must not be null.");
        Objects.requireNonNull(member, "Qualified-name member must not be null.");
        Objects.requireNonNull(span, "Qualified-name source span must not be null.");
    }
}
