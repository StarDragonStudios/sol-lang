package io.github.stardragonstudios.sol.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SourcePositionTest {
    @Test
    void storesOffsetLineAndColumn() {
        var position = new SourcePosition(12, 3, 5);

        assertEquals(12, position.offset());
        assertEquals(3, position.line());
        assertEquals(5, position.column());
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new SourcePosition(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourcePosition(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourcePosition(0, 1, 0));
    }
}
