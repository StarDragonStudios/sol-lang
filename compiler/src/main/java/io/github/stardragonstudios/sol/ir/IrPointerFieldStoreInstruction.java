package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrPointerFieldStoreInstruction(IrValue pointer, IrStructField field, IrValue value) implements IrInstruction {
    public IrPointerFieldStoreInstruction {
        Objects.requireNonNull(pointer, "IR pointer-field-store pointer must not be null.");
        Objects.requireNonNull(field, "IR pointer-field-store field must not be null.");
        Objects.requireNonNull(value, "IR pointer-field-store value must not be null.");

        if (!(pointer.type() instanceof IrPointerType pointerType) || !(pointerType.elementType() instanceof IrStructType structType))
            throw new IllegalArgumentException("IR pointer field store requires a pointer-to-struct operand.");
        if (field.index() >= structType.fields().size() || !structType.fields().get(field.index()).equals(field))
            throw new IllegalArgumentException("IR pointer field '%s' does not belong to '%s'.".formatted(field.name(), structType.displayName()));
        if (!field.type().equals(value.type()))
            throw new IllegalArgumentException("IR pointer field value type '%s' does not match '%s'."
                .formatted(value.type().displayName(), field.type().displayName()));
    }

    @Override
    public List<IrValue> operands() {
        return List.of(pointer, value);
    }
}
