package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrNullConstant(IrValueId id, IrPointerType type) implements IrValue {
    public IrNullConstant {
        Objects.requireNonNull(id, "IR null constant identifier must not be null.");
        Objects.requireNonNull(type, "IR null constant pointer type must not be null.");
    }
}
