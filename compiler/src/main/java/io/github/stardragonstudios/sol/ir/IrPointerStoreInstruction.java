package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrPointerStoreInstruction(IrValue pointer, IrValue value) implements IrInstruction {
    public IrPointerStoreInstruction {
        Objects.requireNonNull(pointer, "Stored-through IR pointer must not be null.");
        Objects.requireNonNull(value, "IR pointer-stored value must not be null.");

        if (!(pointer.type() instanceof IrPointerType pointerType)) throw new IllegalArgumentException(
            "IR pointer store requires a pointer operand, but found '%s'.".formatted(pointer.type().displayName())
        );
        if (!pointerType.elementType().equals(value.type())) throw new IllegalArgumentException(
            "IR pointer-stored value type '%s' does not match element type '%s'."
                .formatted(value.type().displayName(), pointerType.elementType().displayName())
        );
    }

    @Override
    public List<IrValue> operands() {
        return List.of(pointer, value);
    }
}
