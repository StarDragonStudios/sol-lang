package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnotationTest {
    private static final SourceSpan SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(5, 1, 6)
        );

    @Test
    void createsAnnotationWithExpectedValues() {
        var annotation = new Annotation(
            "init",
            SPAN
        );

        assertEquals("init", annotation.name());
        assertEquals(SPAN, annotation.span());
    }

    @Test
    void rejectsNullName() {
        assertThrows(
            NullPointerException.class,
            () -> new Annotation(
                null,
                SPAN
            )
        );
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Annotation(
                " ",
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new Annotation(
                "init",
                null
            )
        );
    }
}
