package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrStructFieldExtractInstruction(IrValueId id, IrValue target, IrStructField field) implements IrValueInstruction {
    public IrStructFieldExtractInstruction {
        Objects.requireNonNull(id, "IR struct field extraction identifier must not be null.");
        Objects.requireNonNull(target, "IR struct field extraction target must not be null.");
        Objects.requireNonNull(field, "Extracted IR struct field must not be null.");

        if (!(target.type() instanceof IrStructType structType))
            throw new IllegalArgumentException("IR field extraction target must have a struct type.");

        if (field.index() >= structType.fields().size() || structType.fields().get(field.index()) != field)
            throw new IllegalArgumentException("IR field '%s' is not canonical for struct type '%s'.".formatted(field.name(), structType.displayName()));
    }

    @Override
    public IrType type() {
        return field.type();
    }

    @Override
    public List<IrValue> operands() {
        return List.of(target);
    }
}
