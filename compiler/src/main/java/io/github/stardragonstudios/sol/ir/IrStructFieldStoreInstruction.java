package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrStructFieldStoreInstruction(IrLocal local, List<IrStructField> path, IrValue value) implements IrLocalInstruction {
    public IrStructFieldStoreInstruction {
        Objects.requireNonNull(local, "Updated IR struct local must not be null.");
        Objects.requireNonNull(path, "Updated IR struct field path must not be null.");
        Objects.requireNonNull(value, "Stored IR struct field value must not be null.");

        path = List.copyOf(path);

        if (!local.isMutable()) throw new IllegalArgumentException("Cannot update a field of immutable IR local '%s'.".formatted(local.name()));
        if (path.isEmpty()) throw new IllegalArgumentException("IR struct field update path must not be empty.");

        IrType currentType = local.type();

        for (var field : path) {
            Objects.requireNonNull(field, "IR struct field update path must not contain null values.");

            if (!(currentType instanceof IrStructType structType))
                throw new IllegalArgumentException("IR struct field update path crosses non-struct type '%s'.".formatted(currentType.displayName()));

            if (field.index() >= structType.fields().size() || structType.fields().get(field.index()) != field)
                throw new IllegalArgumentException("IR field '%s' is not canonical for struct type '%s'.".formatted(field.name(), structType.displayName()));

            currentType = field.type();
        }

        if (!currentType.equals(value.type()))
            throw new IllegalArgumentException(
                "Stored field value type '%s' does not match field type '%s'.".formatted(value.type().displayName(), currentType.displayName())
            );
    }

    @Override
    public List<IrValue> operands() {
        return List.of(value);
    }
}
