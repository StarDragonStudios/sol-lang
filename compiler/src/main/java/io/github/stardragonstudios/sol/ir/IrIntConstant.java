package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrIntConstant(IrValueId id, long value) implements IrValue {
    public IrIntConstant {
        Objects.requireNonNull(id, "IR integer constant identifier must not be null.");
    }

    @Override
    public IrType type() {
        return PrimitiveIrType.INT;
    }
}
