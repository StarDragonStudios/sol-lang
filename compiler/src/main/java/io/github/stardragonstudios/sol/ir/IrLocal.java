package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrLocal(IrLocalId id, String name, IrType type, IrLocalKind kind) {
    public IrLocal {
        Objects.requireNonNull(id, "IR local identifier must not be null.");
        Objects.requireNonNull(name, "IR local name must not be null.");
        Objects.requireNonNull(type, "IR local type must not be null.");
        Objects.requireNonNull(kind, "IR local kind must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("IR local name must not be blank.");

        if (!type.isValue())
            throw new IllegalArgumentException("IR local '%s' must have a value type, but has '%s'.".formatted(name, type.displayName()));
    }

    public boolean isMutable() {
        return kind.isMutable();
    }

    public boolean isConstant() {
        return kind.isConstant();
    }
}
