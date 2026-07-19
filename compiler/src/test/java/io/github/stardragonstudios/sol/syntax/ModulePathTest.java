package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModulePathTest {
    private static final SourceSpan SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(11, 1, 12)
        );

    @Test
    void createsModulePathWithExpectedValues() {
        var path = new ModulePath(
            List.of("std", "console"),
            SPAN
        );

        assertEquals(
            List.of("std", "console"),
            path.segments()
        );

        assertEquals(SPAN, path.span());
    }

    @Test
    void defensivelyCopiesSegments() {
        var segments = new ArrayList<>(
            List.of("std", "console")
        );

        var path = new ModulePath(
            segments,
            SPAN
        );

        segments.clear();

        assertEquals(
            List.of("std", "console"),
            path.segments()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> path.segments().clear()
        );
    }

    @Test
    void rejectsEmptyPath() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModulePath(
                List.of(),
                SPAN
            )
        );
    }

    @Test
    void rejectsBlankSegments() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModulePath(
                List.of("std", " "),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSegments() {
        assertThrows(
            NullPointerException.class,
            () -> new ModulePath(
                null,
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSegmentElements() {
        assertThrows(
            NullPointerException.class,
            () -> new ModulePath(
                java.util.Arrays.asList(
                    "std",
                    null
                ),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new ModulePath(
                List.of("std"),
                null
            )
        );
    }
}
