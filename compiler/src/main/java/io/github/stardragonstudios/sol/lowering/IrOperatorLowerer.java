package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBinaryOperator;
import io.github.stardragonstudios.sol.ir.IrUnaryOperator;
import io.github.stardragonstudios.sol.syntax.BinaryOperator;
import io.github.stardragonstudios.sol.syntax.UnaryOperator;

import java.util.Objects;

final class IrOperatorLowerer {
    private IrOperatorLowerer() {}

    static IrUnaryOperator lower(UnaryOperator operator) {
        Objects.requireNonNull(operator, "Lowered unary operator must not be null.");

        return switch (operator) {
            case LOGICAL_NOT -> IrUnaryOperator.LOGICAL_NOT;
            case NEGATE -> IrUnaryOperator.NEGATE;
            case POSITIVE -> IrUnaryOperator.POSITIVE;
        };
    }

    static IrBinaryOperator lower(BinaryOperator operator) {
        Objects.requireNonNull(operator, "Lowered binary operator must not be null.");

        return switch (operator) {
            case MULTIPLY -> IrBinaryOperator.MULTIPLY;
            case DIVIDE -> IrBinaryOperator.DIVIDE;
            case REMAINDER -> IrBinaryOperator.REMAINDER;
            case ADD -> IrBinaryOperator.ADD;
            case SUBTRACT -> IrBinaryOperator.SUBTRACT;
            case LESS_THAN -> IrBinaryOperator.LESS_THAN;
            case LESS_THAN_OR_EQUAL -> IrBinaryOperator.LESS_THAN_OR_EQUAL;
            case GREATER_THAN -> IrBinaryOperator.GREATER_THAN;
            case GREATER_THAN_OR_EQUAL -> IrBinaryOperator.GREATER_THAN_OR_EQUAL;
            case EQUAL -> IrBinaryOperator.EQUAL;
            case NOT_EQUAL -> IrBinaryOperator.NOT_EQUAL;
            case LOGICAL_AND -> IrBinaryOperator.LOGICAL_AND;
            case LOGICAL_OR -> IrBinaryOperator.LOGICAL_OR;
        };
    }
}
