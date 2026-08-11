package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrPointerIndexStoreInstruction(IrValue pointer, IrValue index, IrValue value) implements IrInstruction {
    public IrPointerIndexStoreInstruction {
        Objects.requireNonNull(pointer, "Indexed IR pointer must not be null.");
        Objects.requireNonNull(index, "IR pointer index must not be null.");
        Objects.requireNonNull(value, "IR pointer-index-stored value must not be null.");

        if (!(pointer.type() instanceof IrPointerType pointerType)) throw new IllegalArgumentException(
            "IR pointer index store requires a pointer operand, but found '%s'.".formatted(pointer.type().displayName())
        );
        if (index.type() != PrimitiveIrType.INT) throw new IllegalArgumentException(
            "IR pointer index must have type 'int', but found '%s'.".formatted(index.type().displayName())
        );
        if (!pointerType.elementType().equals(value.type())) throw new IllegalArgumentException(
            "IR pointer-index-stored value type '%s' does not match element type '%s'."
                .formatted(value.type().displayName(), pointerType.elementType().displayName())
        );
    }

    @Override
    public List<IrValue> operands() {
        return List.of(pointer, index, value);
    }
}
