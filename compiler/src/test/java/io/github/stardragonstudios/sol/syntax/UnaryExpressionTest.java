package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnaryExpressionTest {
    private static final SourceSpan OPERAND_SPAN =
        new SourceSpan(
            new SourcePosition(1, 1, 2),
            new SourcePosition(6, 1, 7)
        );

    private static final SourceSpan COMPLETE_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(6, 1, 7)
        );

    private static final NameExpression OPERAND =
        new NameExpression(
            "value",
            OPERAND_SPAN
        );

    @Test
    void definesInitialUnaryOperators() {
        assertEquals(3, UnaryOperator.values().length);
        assertEquals(
            UnaryOperator.LOGICAL_NOT,
            UnaryOperator.valueOf("LOGICAL_NOT")
        );
        assertEquals(
            UnaryOperator.NEGATE,
            UnaryOperator.valueOf("NEGATE")
        );
        assertEquals(
            UnaryOperator.POSITIVE,
            UnaryOperator.valueOf("POSITIVE")
        );
    }

    @Test
    void createsUnaryExpressionWithExpectedValues() {
        var expression = new UnaryExpression(
            UnaryOperator.LOGICAL_NOT,
            OPERAND,
            COMPLETE_SPAN
        );

        assertEquals(
            UnaryOperator.LOGICAL_NOT,
            expression.operator()
        );
        assertEquals(OPERAND, expression.operand());
        assertEquals(COMPLETE_SPAN, expression.span());
    }

    @Test
    void rejectsNullOperator() {
        assertThrows(
            NullPointerException.class,
            () -> new UnaryExpression(
                null,
                OPERAND,
                COMPLETE_SPAN
            )
        );
    }

    @Test
    void rejectsNullOperand() {
        assertThrows(
            NullPointerException.class,
            () -> new UnaryExpression(
                UnaryOperator.NEGATE,
                null,
                COMPLETE_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new UnaryExpression(
                UnaryOperator.POSITIVE,
                OPERAND,
                null
            )
        );
    }
}
