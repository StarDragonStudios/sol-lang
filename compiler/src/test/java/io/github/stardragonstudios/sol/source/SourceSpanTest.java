package io.github.stardragonstudios.sol.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SourceSpanTest {
    @Test
    void calculatesLengthUsingOffsets() {
        var start = new SourcePosition(4, 1, 5);
        var end = new SourcePosition(9, 1, 10);

        var span = new SourceSpan(start, end);

        assertEquals(5, span.length());
    }

    @Test
    void rejectsAnEndBeforeItsStart() {
        var start = new SourcePosition(8, 1, 9);
        var end = new SourcePosition(4, 1, 5);

        assertThrows(IllegalArgumentException.class, () -> new SourceSpan(start, end));
    }
}
