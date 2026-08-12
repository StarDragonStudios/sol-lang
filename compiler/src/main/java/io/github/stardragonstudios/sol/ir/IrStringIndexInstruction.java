package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrStringIndexInstruction(IrValueId id, IrValue string, IrValue index) implements IrValueInstruction {
    public IrStringIndexInstruction {
        Objects.requireNonNull(id, "IR string-index identifier must not be null.");
        Objects.requireNonNull(string, "Indexed IR string must not be null.");
        Objects.requireNonNull(index, "IR string index must not be null.");

        if (string.type() != PrimitiveIrType.STRING) throw new IllegalArgumentException(
            "IR string index requires a 'string' operand, but found '%s'.".formatted(string.type().displayName())
        );
        if (index.type() != PrimitiveIrType.INT) throw new IllegalArgumentException(
            "IR string index must have type 'int', but found '%s'.".formatted(index.type().displayName())
        );
    }

    @Override
    public IrType type() {
        return PrimitiveIrType.CHAR;
    }

    @Override
    public List<IrValue> operands() {
        return List.of(string, index);
    }
}
