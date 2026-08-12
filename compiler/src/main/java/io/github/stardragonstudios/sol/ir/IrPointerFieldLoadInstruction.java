package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrPointerFieldLoadInstruction(IrValueId id, IrValue pointer, IrStructField field) implements IrValueInstruction {
    public IrPointerFieldLoadInstruction {
        Objects.requireNonNull(id, "IR pointer-field-load identifier must not be null.");
        Objects.requireNonNull(pointer, "IR pointer-field-load pointer must not be null.");
        Objects.requireNonNull(field, "IR pointer-field-load field must not be null.");

        if (!(pointer.type() instanceof IrPointerType pointerType) || !(pointerType.elementType() instanceof IrStructType structType))
            throw new IllegalArgumentException("IR pointer field load requires a pointer-to-struct operand.");
        if (field.index() >= structType.fields().size() || !structType.fields().get(field.index()).equals(field))
            throw new IllegalArgumentException("IR pointer field '%s' does not belong to '%s'.".formatted(field.name(), structType.displayName()));
    }

    @Override
    public IrType type() {
        return field.type();
    }

    @Override
    public List<IrValue> operands() {
        return List.of(pointer);
    }
}
