package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrPointerIndexLoadInstruction(IrValueId id, IrValue pointer, IrValue index) implements IrValueInstruction {
    public IrPointerIndexLoadInstruction {
        Objects.requireNonNull(id, "IR pointer-index-load identifier must not be null.");
        Objects.requireNonNull(pointer, "Indexed IR pointer must not be null.");
        Objects.requireNonNull(index, "IR pointer index must not be null.");

        if (!(pointer.type() instanceof IrPointerType)) throw new IllegalArgumentException(
            "IR pointer index load requires a pointer operand, but found '%s'.".formatted(pointer.type().displayName())
        );
        if (index.type() != PrimitiveIrType.INT) throw new IllegalArgumentException(
            "IR pointer index must have type 'int', but found '%s'.".formatted(index.type().displayName())
        );
    }

    @Override
    public IrType type() {
        return ((IrPointerType) pointer.type()).elementType();
    }

    @Override
    public List<IrValue> operands() {
        return List.of(pointer, index);
    }
}
