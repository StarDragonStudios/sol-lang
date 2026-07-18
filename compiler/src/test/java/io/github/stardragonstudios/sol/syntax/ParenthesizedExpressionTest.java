package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParenthesizedExpressionTest {
    private static final SourceSpan INNER_SPAN =
        new SourceSpan(
            new SourcePosition(1, 1, 2),
            new SourcePosition(3, 1, 4)
        );

    private static final SourceSpan COMPLETE_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(4, 1, 5)
        );

    private static final LiteralExpression INNER =
        new LiteralExpression(
            LiteralKind.INTEGER,
            "42",
            INNER_SPAN
        );

    @Test
    void createsParenthesizedExpression() {
        var expression = new ParenthesizedExpression(
            INNER,
            COMPLETE_SPAN
        );

        assertEquals(INNER, expression.expression());
        assertEquals(COMPLETE_SPAN, expression.span());
    }

    @Test
    void rejectsNullExpression() {
        assertThrows(
            NullPointerException.class,
            () -> new ParenthesizedExpression(
                null,
                COMPLETE_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new ParenthesizedExpression(
                INNER,
                null
            )
        );
    }
}
