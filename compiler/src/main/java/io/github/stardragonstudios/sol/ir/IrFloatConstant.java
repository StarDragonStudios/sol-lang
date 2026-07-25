package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrFloatConstant(IrValueId id, double value) implements IrValue {
    public IrFloatConstant {
        Objects.requireNonNull(id, "IR floating-point constant identifier must not be null.");

        if (!Double.isFinite(value)) throw new IllegalArgumentException("IR floating-point constant must be finite.");
    }

    @Override
    public IrType type() {
        return PrimitiveIrType.FLOAT;
    }
}
