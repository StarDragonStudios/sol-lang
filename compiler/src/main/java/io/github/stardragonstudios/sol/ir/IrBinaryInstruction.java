package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrBinaryInstruction(IrValueId id, IrBinaryOperator operator, IrValue left, IrValue right) implements IrValueInstruction {
    public IrBinaryInstruction {
        Objects.requireNonNull(id, "Binary IR instruction identifier must not be null.");
        Objects.requireNonNull(operator, "Binary IR instruction operator must not be null.");
        Objects.requireNonNull(left, "Left binary IR instruction operand must not be null.");
        Objects.requireNonNull(right, "Right binary IR instruction operand must not be null.");

        operator.resultType(left.type(), right.type());
    }

    @Override
    public IrType type() {
        return operator.resultType(left.type(), right.type());
    }

    @Override
    public List<IrValue> operands() {
        return List.of(left, right);
    }
}
