package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;
import java.util.Optional;

public record ConditionalStatement(Expression condition, Block thenBlock, Optional<Block> elseBlock, SourceSpan span) implements Statement {
    public ConditionalStatement {
        Objects.requireNonNull(
            condition,
            "Conditional condition must not be null."
        );

        Objects.requireNonNull(
            thenBlock,
            "Conditional then block must not be null."
        );

        Objects.requireNonNull(
            elseBlock,
            "Conditional else block must not be null."
        );

        Objects.requireNonNull(
            span,
            "Conditional statement source span must not be null."
        );
    }
}
