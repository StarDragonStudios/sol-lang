package io.github.stardragonstudios.sol.ir;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record IrStructType(String displayName, List<IrStructField> fields) implements IrType {
    public IrStructType {
        Objects.requireNonNull(displayName, "IR struct type name must not be null.");
        Objects.requireNonNull(fields, "IR struct fields must not be null.");

        if (displayName.isBlank()) throw new IllegalArgumentException("IR struct type name must not be blank.");

        fields = List.copyOf(fields);

        var names = new HashSet<String>();

        for (var index = 0; index < fields.size(); index++) {
            var field = Objects.requireNonNull(fields.get(index), "IR struct fields must not contain null values.");

            if (field.index() != index)
                throw new IllegalArgumentException("IR struct field '%s' has index %d, but appears at index %d.".formatted(field.name(), field.index(), index));

            if (!names.add(field.name()))
                throw new IllegalArgumentException("IR struct type '%s' must not contain duplicate field '%s'.".formatted(displayName, field.name()));
        }
    }

    public Optional<IrStructField> field(String name) {
        Objects.requireNonNull(name, "IR struct field lookup name must not be null.");

        return fields.stream().filter(field -> field.name().equals(name)).findFirst();
    }

    @Override
    public boolean isValue() {
        return true;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isIntegral() {
        return false;
    }
}
