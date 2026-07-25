package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrStringConstant(IrValueId id, String value) implements IrValue {
    public IrStringConstant {
        Objects.requireNonNull(id, "IR string constant identifier must not be null.");
        Objects.requireNonNull(value, "IR string constant value must not be null.");
    }

    @Override
    public IrType type() {
        return PrimitiveIrType.STRING;
    }
}
