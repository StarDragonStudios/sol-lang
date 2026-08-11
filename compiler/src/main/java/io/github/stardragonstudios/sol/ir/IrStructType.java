package io.github.stardragonstudios.sol.ir;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class IrStructType implements IrType {
    private final String displayName;
    private List<IrStructField> fields;

    public IrStructType(String displayName, List<IrStructField> fields) {
        this(displayName);
        defineFields(fields);
    }

    /**
     * Creates a canonical forward declaration. It must be defined exactly
     * once before the containing IR program is exposed.
     */
    public IrStructType(String displayName) {
        this.displayName = Objects.requireNonNull(displayName, "IR struct type name must not be null.");

        if (displayName.isBlank()) throw new IllegalArgumentException("IR struct type name must not be blank.");
    }

    public String displayName() {
        return displayName;
    }

    public List<IrStructField> fields() {
        if (fields == null) throw new IllegalStateException("IR struct type '%s' has not been defined.".formatted(displayName));

        return fields;
    }

    public boolean isDefined() {
        return fields != null;
    }

    public void defineFields(List<IrStructField> fields) {
        Objects.requireNonNull(fields, "IR struct fields must not be null.");

        if (this.fields != null) throw new IllegalStateException("IR struct type '%s' has already been defined.".formatted(displayName));

        var copy = List.copyOf(fields);
        var names = new HashSet<String>();

        for (var index = 0; index < copy.size(); index++) {
            var field = Objects.requireNonNull(copy.get(index), "IR struct fields must not contain null values.");

            if (field.index() != index)
                throw new IllegalArgumentException("IR struct field '%s' has index %d, but appears at index %d.".formatted(field.name(), field.index(), index));

            if (!names.add(field.name()))
                throw new IllegalArgumentException("IR struct type '%s' must not contain duplicate field '%s'.".formatted(displayName, field.name()));
        }

        this.fields = copy;
    }

    public Optional<IrStructField> field(String name) {
        Objects.requireNonNull(name, "IR struct field lookup name must not be null.");

        return fields().stream().filter(field -> field.name().equals(name)).findFirst();
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

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof IrStructType struct && displayName.equals(struct.displayName);
    }

    @Override
    public int hashCode() {
        return displayName.hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
