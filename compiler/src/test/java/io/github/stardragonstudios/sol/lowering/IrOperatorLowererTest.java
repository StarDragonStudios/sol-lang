package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBinaryOperator;
import io.github.stardragonstudios.sol.ir.IrUnaryOperator;
import io.github.stardragonstudios.sol.syntax.BinaryOperator;
import io.github.stardragonstudios.sol.syntax.UnaryOperator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrOperatorLowererTest {
    @Test
    void lowersEveryUnaryOperatorExplicitly() {
        var expected = Map.of(
            UnaryOperator.LOGICAL_NOT,
            IrUnaryOperator.LOGICAL_NOT,
            UnaryOperator.NEGATE,
            IrUnaryOperator.NEGATE,
            UnaryOperator.POSITIVE,
            IrUnaryOperator.POSITIVE
        );

        for (var entry : expected.entrySet()) assertEquals(entry.getValue(), IrOperatorLowerer.lower(entry.getKey()));
    }

    @Test
    void lowersEveryBinaryOperatorExplicitly() {
        var expected = Map.ofEntries(
            Map.entry(BinaryOperator.MULTIPLY, IrBinaryOperator.MULTIPLY),
            Map.entry(BinaryOperator.DIVIDE, IrBinaryOperator.DIVIDE),
            Map.entry(BinaryOperator.REMAINDER, IrBinaryOperator.REMAINDER),
            Map.entry(BinaryOperator.ADD, IrBinaryOperator.ADD),
            Map.entry(BinaryOperator.SUBTRACT, IrBinaryOperator.SUBTRACT),
            Map.entry(BinaryOperator.LESS_THAN, IrBinaryOperator.LESS_THAN),
            Map.entry(BinaryOperator.LESS_THAN_OR_EQUAL, IrBinaryOperator.LESS_THAN_OR_EQUAL),
            Map.entry(BinaryOperator.GREATER_THAN, IrBinaryOperator.GREATER_THAN),
            Map.entry(BinaryOperator.GREATER_THAN_OR_EQUAL, IrBinaryOperator.GREATER_THAN_OR_EQUAL),
            Map.entry(BinaryOperator.EQUAL, IrBinaryOperator.EQUAL),
            Map.entry(BinaryOperator.NOT_EQUAL, IrBinaryOperator.NOT_EQUAL),
            Map.entry(BinaryOperator.LOGICAL_AND, IrBinaryOperator.LOGICAL_AND),
            Map.entry(BinaryOperator.LOGICAL_OR, IrBinaryOperator.LOGICAL_OR)
        );

        for (var entry : expected.entrySet()) assertEquals(entry.getValue(), IrOperatorLowerer.lower(entry.getKey()));
    }

    @Test
    void rejectsNullOperators() {
        assertThrows(
            NullPointerException.class,
            () -> IrOperatorLowerer.lower((UnaryOperator) null)
        );

        assertThrows(
            NullPointerException.class,
            () -> IrOperatorLowerer.lower((BinaryOperator) null)
        );
    }
}
