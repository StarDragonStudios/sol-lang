package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrPointerType(IrType elementType) implements IrType {
    public IrPointerType {
        Objects.requireNonNull(elementType, "IR pointer element type must not be null.");

        if (!elementType.isValue()) throw new IllegalArgumentException(
            "IR pointer element type '%s' must be a value type.".formatted(elementType.displayName())
        );
    }

    @Override
    public String displayName() {
        return "pointer<%s>".formatted(elementType.displayName());
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
