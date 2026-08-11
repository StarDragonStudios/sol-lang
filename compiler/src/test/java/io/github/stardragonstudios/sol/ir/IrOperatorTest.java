package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrOperatorTest {
    @Test
    void exposesOperatorSpellings() {
        assertEquals("!", IrUnaryOperator.LOGICAL_NOT.spelling());
        assertEquals("-", IrUnaryOperator.NEGATE.toString());
        assertEquals("+", IrBinaryOperator.ADD.spelling());
        assertEquals("==", IrBinaryOperator.EQUAL.toString());
    }

    @Test
    void determinesUnaryResultTypes() {
        assertSame(PrimitiveIrType.INT, IrUnaryOperator.NEGATE.resultType(PrimitiveIrType.INT));
        assertSame(PrimitiveIrType.FLOAT, IrUnaryOperator.POSITIVE.resultType(PrimitiveIrType.FLOAT));
        assertSame(PrimitiveIrType.BOOLEAN, IrUnaryOperator.LOGICAL_NOT.resultType(PrimitiveIrType.BOOLEAN));
    }

    @Test
    void rejectsInvalidUnaryOperandTypes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> IrUnaryOperator.NEGATE.resultType(PrimitiveIrType.BOOLEAN)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrUnaryOperator.POSITIVE.resultType(PrimitiveIrType.CHAR)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrUnaryOperator.LOGICAL_NOT.resultType(PrimitiveIrType.INT)
        );

        assertThrows(
            NullPointerException.class,
            () -> IrUnaryOperator.NEGATE.resultType(null)
        );
    }

    @Test
    void determinesArithmeticResultTypes() {
        assertSame(
            PrimitiveIrType.INT,
            IrBinaryOperator.ADD.resultType(PrimitiveIrType.INT, PrimitiveIrType.INT)
        );

        assertSame(
            PrimitiveIrType.FLOAT,
            IrBinaryOperator.MULTIPLY.resultType(PrimitiveIrType.FLOAT, PrimitiveIrType.FLOAT)
        );

        assertSame(
            PrimitiveIrType.INT,
            IrBinaryOperator.REMAINDER.resultType(PrimitiveIrType.INT, PrimitiveIrType.INT)
        );
    }

    @Test
    void determinesComparisonResultTypes() {
        assertSame(
            PrimitiveIrType.BOOLEAN,
            IrBinaryOperator.LESS_THAN.resultType(PrimitiveIrType.INT, PrimitiveIrType.INT)
        );

        assertSame(
            PrimitiveIrType.BOOLEAN,
            IrBinaryOperator.GREATER_THAN_OR_EQUAL.resultType(PrimitiveIrType.FLOAT, PrimitiveIrType.FLOAT)
        );

        assertSame(
            PrimitiveIrType.BOOLEAN,
            IrBinaryOperator.EQUAL.resultType(PrimitiveIrType.STRING, PrimitiveIrType.STRING)
        );

        assertSame(
            PrimitiveIrType.BOOLEAN,
            IrBinaryOperator.NOT_EQUAL.resultType(PrimitiveIrType.CHAR, PrimitiveIrType.CHAR)
        );
    }

    @Test
    void determinesLogicalResultTypes() {
        assertSame(
            PrimitiveIrType.BOOLEAN,
            IrBinaryOperator.LOGICAL_AND.resultType(PrimitiveIrType.BOOLEAN, PrimitiveIrType.BOOLEAN)
        );

        assertSame(
            PrimitiveIrType.BOOLEAN,
            IrBinaryOperator.LOGICAL_OR.resultType(PrimitiveIrType.BOOLEAN, PrimitiveIrType.BOOLEAN)
        );
    }

    @Test
    void rejectsInvalidBinaryOperandTypes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> IrBinaryOperator.ADD.resultType(PrimitiveIrType.INT, PrimitiveIrType.FLOAT)
        );

        assertSame(
            PrimitiveIrType.STRING,
            IrBinaryOperator.ADD.resultType(PrimitiveIrType.STRING, PrimitiveIrType.STRING)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrBinaryOperator.REMAINDER.resultType(PrimitiveIrType.FLOAT, PrimitiveIrType.FLOAT)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrBinaryOperator.LESS_THAN.resultType(PrimitiveIrType.BOOLEAN, PrimitiveIrType.BOOLEAN)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrBinaryOperator.EQUAL.resultType(PrimitiveIrType.INT, PrimitiveIrType.FLOAT)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrBinaryOperator.LOGICAL_AND.resultType(PrimitiveIrType.INT, PrimitiveIrType.INT)
        );

        assertThrows(
            NullPointerException.class,
            () -> IrBinaryOperator.ADD.resultType(null, PrimitiveIrType.INT)
        );

        assertThrows(
            NullPointerException.class,
            () -> IrBinaryOperator.ADD.resultType(PrimitiveIrType.INT, null)
        );
    }

    @Test
    void createsTypedUnaryInstructions() {
        var operand = new IrIntConstant(new IrValueId(0), 42);
        var instruction = new IrUnaryInstruction(new IrValueId(1), IrUnaryOperator.NEGATE, operand);

        assertEquals(new IrValueId(1), instruction.id());
        assertSame(IrUnaryOperator.NEGATE, instruction.operator());
        assertSame(operand, instruction.operand());
        assertSame(PrimitiveIrType.INT, instruction.type());
    }

    @Test
    void createsTypedBinaryInstructions() {
        var left = new IrFloatConstant(new IrValueId(0), 2.0);
        var right = new IrFloatConstant(new IrValueId(1), 4.0);
        var arithmetic = new IrBinaryInstruction(new IrValueId(2), IrBinaryOperator.MULTIPLY, left, right);
        var comparison = new IrBinaryInstruction(new IrValueId(3), IrBinaryOperator.LESS_THAN, left, right);

        assertSame(PrimitiveIrType.FLOAT, arithmetic.type());
        assertSame(PrimitiveIrType.BOOLEAN, comparison.type());
        assertSame(left, arithmetic.left());
        assertSame(right, arithmetic.right());
    }

    @Test
    void rejectsInvalidInstructions() {
        var integer = new IrIntConstant(new IrValueId(0), 1);
        var floatingPoint = new IrFloatConstant(new IrValueId(1), 1.0);

        assertThrows(
            NullPointerException.class,
            () -> new IrUnaryInstruction(null, IrUnaryOperator.NEGATE, integer)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrUnaryInstruction(new IrValueId(1), null, integer)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrUnaryInstruction(new IrValueId(1), IrUnaryOperator.NEGATE, null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrUnaryInstruction(new IrValueId(1), IrUnaryOperator.LOGICAL_NOT, integer)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrBinaryInstruction(new IrValueId(2), IrBinaryOperator.ADD, null, integer)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrBinaryInstruction(new IrValueId(2), IrBinaryOperator.ADD, integer, null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrBinaryInstruction(new IrValueId(2), IrBinaryOperator.ADD, integer, floatingPoint)
        );
    }
}
