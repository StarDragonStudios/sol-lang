package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;

public record ModulePath(List<String> segments, SourceSpan span) implements SyntaxNode {
    public ModulePath {
        Objects.requireNonNull(
            segments,
            "Module path segments must not be null."
        );

        Objects.requireNonNull(
            span,
            "Module path source span must not be null."
        );

        segments = List.copyOf(segments);

        if (segments.isEmpty()) {
            throw new IllegalArgumentException(
                "Module path must contain at least one segment."
            );
        }

        for (var segment : segments) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException(
                    "Module path segments must not be blank."
                );
            }
        }
    }
}
