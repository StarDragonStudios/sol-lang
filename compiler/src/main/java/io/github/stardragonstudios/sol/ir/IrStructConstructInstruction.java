package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrStructConstructInstruction(IrValueId id, IrStructType type, List<IrValue> fields) implements IrValueInstruction {
    public IrStructConstructInstruction {
        Objects.requireNonNull(id, "IR struct construction identifier must not be null.");
        Objects.requireNonNull(type, "Constructed IR struct type must not be null.");
        Objects.requireNonNull(fields, "IR struct construction values must not be null.");

        fields = List.copyOf(fields);

        if (fields.size() != type.fields().size())
            throw new IllegalArgumentException(
                "Construction of IR struct '%s' expects %d fields, but received %d."
                    .formatted(type.displayName(), type.fields().size(), fields.size())
            );

        for (var index = 0; index < fields.size(); index++) {
            var value = Objects.requireNonNull(fields.get(index), "IR struct construction values must not contain null values.");
            var field = type.fields().get(index);

            if (!field.type().equals(value.type()))
                throw new IllegalArgumentException(
                    "IR struct field '%s' expects type '%s', but received '%s'."
                        .formatted(field.name(), field.type().displayName(), value.type().displayName())
                );
        }
    }

    @Override
    public List<IrValue> operands() {
        return fields;
    }
}
