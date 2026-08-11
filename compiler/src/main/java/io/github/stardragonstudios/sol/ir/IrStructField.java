package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrStructField(String name, IrType type, int index) {
    public IrStructField {
        Objects.requireNonNull(name, "IR struct field name must not be null.");
        Objects.requireNonNull(type, "IR struct field type must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("IR struct field name must not be blank.");
        if (!type.isValue()) throw new IllegalArgumentException("IR struct field '%s' must have a value type.".formatted(name));
        if (index < 0) throw new IllegalArgumentException("IR struct field index must not be negative.");
    }
}
