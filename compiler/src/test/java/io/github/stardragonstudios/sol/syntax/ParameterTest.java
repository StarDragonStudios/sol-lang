package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParameterTest {
    private static final SourceSpan TYPE_SPAN =
        new SourceSpan(
            new SourcePosition(7, 1, 8),
            new SourcePosition(10, 1, 11)
        );

    private static final SourceSpan PARAMETER_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(10, 1, 11)
        );

    private static final TypeReference TYPE =
        new TypeReference("int", TYPE_SPAN);

    @Test
    void createsParameterWithExpectedValues() {
        var parameter = new Parameter(
            "value",
            TYPE,
            PARAMETER_SPAN
        );

        assertEquals("value", parameter.name());
        assertEquals(TYPE, parameter.type());
        assertEquals(PARAMETER_SPAN, parameter.span());
    }

    @Test
    void rejectsNullName() {
        assertThrows(
            NullPointerException.class,
            () -> new Parameter(
                null,
                TYPE,
                PARAMETER_SPAN
            )
        );
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Parameter(
                "  ",
                TYPE,
                PARAMETER_SPAN
            )
        );
    }

    @Test
    void rejectsNullType() {
        assertThrows(
            NullPointerException.class,
            () -> new Parameter(
                "value",
                null,
                PARAMETER_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new Parameter(
                "value",
                TYPE,
                null
            )
        );
    }
}
