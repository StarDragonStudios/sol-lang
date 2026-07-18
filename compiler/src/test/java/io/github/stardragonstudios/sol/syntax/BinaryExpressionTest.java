package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinaryExpressionTest {
    private static final SourceSpan LEFT_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(4, 1, 5)
        );

    private static final SourceSpan RIGHT_SPAN =
        new SourceSpan(
            new SourcePosition(7, 1, 8),
            new SourcePosition(12, 1, 13)
        );

    private static final SourceSpan COMPLETE_SPAN =
        new SourceSpan(
            LEFT_SPAN.start(),
            RIGHT_SPAN.end()
        );

    private static final NameExpression LEFT =
        new NameExpression("left", LEFT_SPAN);

    private static final NameExpression RIGHT =
        new NameExpression("right", RIGHT_SPAN);

    @Test
    void definesEveryInitialBinaryOperator() {
        assertEquals(13, BinaryOperator.values().length);

        assertEquals(
            BinaryOperator.MULTIPLY,
            BinaryOperator.valueOf("MULTIPLY")
        );
        assertEquals(
            BinaryOperator.DIVIDE,
            BinaryOperator.valueOf("DIVIDE")
        );
        assertEquals(
            BinaryOperator.REMAINDER,
            BinaryOperator.valueOf("REMAINDER")
        );
        assertEquals(
            BinaryOperator.ADD,
            BinaryOperator.valueOf("ADD")
        );
        assertEquals(
            BinaryOperator.SUBTRACT,
            BinaryOperator.valueOf("SUBTRACT")
        );
        assertEquals(
            BinaryOperator.LESS_THAN,
            BinaryOperator.valueOf("LESS_THAN")
        );
        assertEquals(
            BinaryOperator.LESS_THAN_OR_EQUAL,
            BinaryOperator.valueOf("LESS_THAN_OR_EQUAL")
        );
        assertEquals(
            BinaryOperator.GREATER_THAN,
            BinaryOperator.valueOf("GREATER_THAN")
        );
        assertEquals(
            BinaryOperator.GREATER_THAN_OR_EQUAL,
            BinaryOperator.valueOf("GREATER_THAN_OR_EQUAL")
        );
        assertEquals(
            BinaryOperator.EQUAL,
            BinaryOperator.valueOf("EQUAL")
        );
        assertEquals(
            BinaryOperator.NOT_EQUAL,
            BinaryOperator.valueOf("NOT_EQUAL")
        );
        assertEquals(
            BinaryOperator.LOGICAL_AND,
            BinaryOperator.valueOf("LOGICAL_AND")
        );
        assertEquals(
            BinaryOperator.LOGICAL_OR,
            BinaryOperator.valueOf("LOGICAL_OR")
        );
    }

    @Test
    void createsBinaryExpressionWithExpectedValues() {
        var expression = new BinaryExpression(
            LEFT,
            BinaryOperator.ADD,
            RIGHT,
            COMPLETE_SPAN
        );

        assertEquals(LEFT, expression.left());
        assertEquals(
            BinaryOperator.ADD,
            expression.operator()
        );
        assertEquals(RIGHT, expression.right());
        assertEquals(COMPLETE_SPAN, expression.span());
    }

    @Test
    void rejectsNullLeftOperand() {
        assertThrows(
            NullPointerException.class,
            () -> new BinaryExpression(
                null,
                BinaryOperator.ADD,
                RIGHT,
                COMPLETE_SPAN
            )
        );
    }

    @Test
    void rejectsNullOperator() {
        assertThrows(
            NullPointerException.class,
            () -> new BinaryExpression(
                LEFT,
                null,
                RIGHT,
                COMPLETE_SPAN
            )
        );
    }

    @Test
    void rejectsNullRightOperand() {
        assertThrows(
            NullPointerException.class,
            () -> new BinaryExpression(
                LEFT,
                BinaryOperator.ADD,
                null,
                COMPLETE_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new BinaryExpression(
                LEFT,
                BinaryOperator.ADD,
                RIGHT,
                null
            )
        );
    }
}
