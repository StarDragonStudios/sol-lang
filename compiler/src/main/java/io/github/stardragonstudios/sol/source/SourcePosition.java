package io.github.stardragonstudios.sol.source;

/**
 * A position in a source file.
 *
 * <p>Offsets are zero-based. Lines and columns are one-based.</p>
 */
public record SourcePosition(int offset, int line, int column) {
    public SourcePosition {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative.");
        }

        if (line < 1) {
            throw new IllegalArgumentException("Line must start at 1.");
        }

        if (column < 1) {
            throw new IllegalArgumentException("Column must start at 1.");
        }
    }
}
