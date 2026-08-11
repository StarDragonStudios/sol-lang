package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrPointerLoadInstruction(IrValueId id, IrValue pointer) implements IrValueInstruction {
    public IrPointerLoadInstruction {
        Objects.requireNonNull(id, "IR pointer-load identifier must not be null.");
        Objects.requireNonNull(pointer, "Loaded IR pointer must not be null.");

        if (!(pointer.type() instanceof IrPointerType)) throw new IllegalArgumentException(
            "IR pointer load requires a pointer operand, but found '%s'.".formatted(pointer.type().displayName())
        );
    }

    @Override
    public IrType type() {
        return ((IrPointerType) pointer.type()).elementType();
    }

    @Override
    public List<IrValue> operands() {
        return List.of(pointer);
    }
}
