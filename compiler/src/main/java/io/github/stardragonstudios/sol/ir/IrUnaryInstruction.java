package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrUnaryInstruction(IrValueId id, IrUnaryOperator operator, IrValue operand) implements IrValueInstruction {
    public IrUnaryInstruction {
        Objects.requireNonNull(id, "Unary IR instruction identifier must not be null.");
        Objects.requireNonNull(operator, "Unary IR instruction operator must not be null.");
        Objects.requireNonNull(operand, "Unary IR instruction operand must not be null.");

        operator.resultType(operand.type());
    }

    @Override
    public IrType type() {
        return operator.resultType(operand.type());
    }

    @Override
    public List<IrValue> operands() {
        return List.of(operand);
    }
}
