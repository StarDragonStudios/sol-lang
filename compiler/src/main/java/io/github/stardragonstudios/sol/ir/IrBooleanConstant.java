package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrBooleanConstant(IrValueId id, boolean value) implements IrValue {
    public IrBooleanConstant {
        Objects.requireNonNull(id, "IR boolean constant identifier must not be null.");
    }

    @Override
    public IrType type() {
        return PrimitiveIrType.BOOLEAN;
    }
}
