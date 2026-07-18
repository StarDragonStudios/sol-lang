package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiteralExpressionTest {
    private static final SourceSpan SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(2, 1, 3)
        );

    @Test
    void createsLiteralExpressionWithExpectedValues() {
        var expression = new LiteralExpression(
            LiteralKind.INTEGER,
            "42",
            SPAN
        );

        assertEquals(
            LiteralKind.INTEGER,
            expression.kind()
        );
        assertEquals("42", expression.lexeme());
        assertEquals(SPAN, expression.span());
    }

    @Test
    void definesEveryInitialLiteralKind() {
        assertEquals(
            5,
            LiteralKind.values().length
        );

        assertEquals(
            LiteralKind.INTEGER,
            LiteralKind.valueOf("INTEGER")
        );
        assertEquals(
            LiteralKind.FLOAT,
            LiteralKind.valueOf("FLOAT")
        );
        assertEquals(
            LiteralKind.BOOLEAN,
            LiteralKind.valueOf("BOOLEAN")
        );
        assertEquals(
            LiteralKind.CHARACTER,
            LiteralKind.valueOf("CHARACTER")
        );
        assertEquals(
            LiteralKind.STRING,
            LiteralKind.valueOf("STRING")
        );
    }

    @Test
    void rejectsNullKind() {
        assertThrows(
            NullPointerException.class,
            () -> new LiteralExpression(
                null,
                "42",
                SPAN
            )
        );
    }

    @Test
    void rejectsNullLexeme() {
        assertThrows(
            NullPointerException.class,
            () -> new LiteralExpression(
                LiteralKind.INTEGER,
                null,
                SPAN
            )
        );
    }

    @Test
    void rejectsEmptyLexeme() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new LiteralExpression(
                LiteralKind.INTEGER,
                "",
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new LiteralExpression(
                LiteralKind.INTEGER,
                "42",
                null
            )
        );
    }
}
