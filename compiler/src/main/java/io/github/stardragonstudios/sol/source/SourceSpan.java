package io.github.stardragonstudios.sol.source;

import java.util.Objects;

/**
 * A half-open range in a source file: [start, end).
 */
public record SourceSpan(SourcePosition start, SourcePosition end) {
    public SourceSpan {
        Objects.requireNonNull(start, "Start position cannot be null.");
        Objects.requireNonNull(end, "End position cannot be null.");

        if (end.offset() < start.offset()) {
            throw new IllegalArgumentException("End position cannot precede start position.");
        }
    }

    public int length() {
        return end.offset() - start.offset();
    }
}
