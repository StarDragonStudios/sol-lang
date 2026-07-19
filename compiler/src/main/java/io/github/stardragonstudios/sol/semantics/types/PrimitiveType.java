package io.github.stardragonstudios.sol.semantics.types;

import java.util.Objects;

public final class PrimitiveType implements TypeSymbol {

    private final String name;
    private final boolean value;
    private final boolean numeric;
    private final boolean integral;

    PrimitiveType(String name, boolean value, boolean numeric, boolean integral) {
        this.name = Objects.requireNonNull(name, "Primitive type name must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Primitive type name must not be blank.");
        if (integral && !numeric) throw new IllegalArgumentException("An integral primitive type must also be numeric.");

        this.value = value;
        this.numeric = numeric;
        this.integral = integral;
    }

    @Override
    public TypeKind kind() {
        return TypeKind.PRIMITIVE;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isValue() {
        return value;
    }

    @Override
    public boolean isNumeric() {
        return numeric;
    }

    @Override
    public boolean isIntegral() {
        return integral;
    }

    @Override
    public String toString() {
        return name;
    }
}
