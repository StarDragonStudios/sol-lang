package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrParameter(IrValueId id, String name, IrType type) implements IrValue {
    public IrParameter {
        Objects.requireNonNull(id, "IR parameter value identifier must not be null.");
        Objects.requireNonNull(name, "IR parameter name must not be null.");
        Objects.requireNonNull(type, "IR parameter type must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("IR parameter name must not be blank.");
        if (!type.isValue()) throw new IllegalArgumentException("IR parameter type must be a value type.");
    }
}
