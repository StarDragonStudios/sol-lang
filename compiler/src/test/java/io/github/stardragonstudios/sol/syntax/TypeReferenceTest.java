package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeReferenceTest {
    private static final SourceSpan SPAN = new SourceSpan(
        new SourcePosition(3, 1, 4),
        new SourcePosition(6, 1, 7)
    );

    @Test
    void createsTypeReferenceWithExpectedValues() {
        var type = new TypeReference(
            "int",
            SPAN
        );

        assertEquals("int", type.name());
        assertEquals(SPAN, type.span());
    }

    @Test
    void rejectsNullName() {
        assertThrows(
            NullPointerException.class,
            () -> new TypeReference(null, SPAN)
        );
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new TypeReference("  ", SPAN)
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new TypeReference("int", null)
        );
    }
}
