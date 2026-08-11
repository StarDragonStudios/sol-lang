package io.github.stardragonstudios.sol.semantics.types;

import java.util.Objects;

public final class PointerType implements TypeSymbol {
    private final TypeSymbol elementType;

    public PointerType(TypeSymbol elementType) {
        this.elementType = Objects.requireNonNull(elementType, "Pointer element type must not be null.");

        if (!elementType.isValue()) throw new IllegalArgumentException(
            "Pointer element type '%s' must be a value type.".formatted(elementType.name())
        );
    }

    public TypeSymbol elementType() {
        return elementType;
    }

    @Override
    public TypeKind kind() {
        return TypeKind.POINTER;
    }

    @Override
    public String name() {
        return "pointer<%s>".formatted(elementType.name());
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
        return this == other || other instanceof PointerType pointer && elementType.equals(pointer.elementType);
    }

    @Override
    public int hashCode() {
        return 31 * elementType.hashCode() + 17;
    }

    @Override
    public String toString() {
        return name();
    }
}
