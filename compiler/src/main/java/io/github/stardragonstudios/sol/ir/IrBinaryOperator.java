package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public enum IrBinaryOperator {
    MULTIPLY("*"),
    DIVIDE("/"),
    REMAINDER("%"),
    ADD("+"),
    SUBTRACT("-"),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    EQUAL("=="),
    NOT_EQUAL("!="),
    LOGICAL_AND("&&"),
    LOGICAL_OR("||");

    private final String spelling;

    IrBinaryOperator(String spelling) {
        this.spelling = Objects.requireNonNull(spelling, "Binary IR operator spelling must not be null.");

        if (spelling.isBlank()) throw new IllegalArgumentException("Binary IR operator spelling must not be blank.");
    }

    public String spelling() {
        return spelling;
    }

    public IrType resultType(IrType leftType, IrType rightType) {
        Objects.requireNonNull(leftType, "Left binary IR operand type must not be null.");
        Objects.requireNonNull(rightType, "Right binary IR operand type must not be null.");

        return switch (this) {
            case MULTIPLY, DIVIDE, ADD, SUBTRACT -> {
                if (!matchingNumericTypes(leftType, rightType)) throw invalidOperands(leftType, rightType);

                yield leftType;
            }

            case REMAINDER -> {
                if (leftType != PrimitiveIrType.INT || rightType != PrimitiveIrType.INT)
                    throw invalidOperands(leftType, rightType);

                yield PrimitiveIrType.INT;
            }

            case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL -> {
                if (!matchingNumericTypes(leftType, rightType)) throw invalidOperands(leftType, rightType);

                yield PrimitiveIrType.BOOLEAN;
            }

            case EQUAL, NOT_EQUAL -> {
                if (!leftType.equals(rightType) || !leftType.isValue()) throw invalidOperands(leftType, rightType);

                yield PrimitiveIrType.BOOLEAN;
            }

            case LOGICAL_AND, LOGICAL_OR -> {
                if (leftType != PrimitiveIrType.BOOLEAN || rightType != PrimitiveIrType.BOOLEAN)
                    throw invalidOperands(leftType, rightType);

                yield PrimitiveIrType.BOOLEAN;
            }
        };
    }

    private boolean matchingNumericTypes(IrType leftType, IrType rightType) {
        return leftType.equals(rightType) && leftType.isNumeric();
    }

    private IllegalArgumentException invalidOperands(IrType leftType, IrType rightType) {
        return new IllegalArgumentException(
            "Binary IR operator '%s' is not defined for types '%s' and '%s'."
                .formatted(spelling, leftType.displayName(), rightType.displayName())
        );
    }

    @Override
    public String toString() {
        return spelling;
    }
}
