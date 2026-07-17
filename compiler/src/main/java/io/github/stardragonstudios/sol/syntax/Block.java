package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;

public record Block(List<Statement> statements, SourceSpan span) implements SyntaxNode {
    public Block {
        Objects.requireNonNull(
            statements,
            "Block statements must not be null."
        );

        Objects.requireNonNull(
            span,
            "Block source span must not be null."
        );

        statements = List.copyOf(statements);
    }
}
