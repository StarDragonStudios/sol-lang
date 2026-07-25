package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public enum IrUnaryOperator {
    LOGICAL_NOT("!"),
    NEGATE("-"),
    POSITIVE("+");

    private final String spelling;

    IrUnaryOperator(String spelling) {
        this.spelling = Objects.requireNonNull(spelling, "Unary IR operator spelling must not be null.");

        if (spelling.isBlank()) throw new IllegalArgumentException("Unary IR operator spelling must not be blank.");
    }

    public String spelling() {
        return spelling;
    }

    public IrType resultType(IrType operandType) {
        Objects.requireNonNull(operandType, "Unary IR operand type must not be null.");

        return switch (this) {
            case LOGICAL_NOT -> {
                if (operandType != PrimitiveIrType.BOOLEAN) throw invalidOperand(operandType);

                yield PrimitiveIrType.BOOLEAN;
            }

            case NEGATE, POSITIVE -> {
                if (!operandType.isNumeric()) throw invalidOperand(operandType);

                yield operandType;
            }
        };
    }

    private IllegalArgumentException invalidOperand(IrType operandType) {
        return new IllegalArgumentException(
            "Unary IR operator '%s' is not defined for type '%s'.".formatted(spelling, operandType.displayName())
        );
    }

    @Override
    public String toString() {
        return spelling;
    }
}
