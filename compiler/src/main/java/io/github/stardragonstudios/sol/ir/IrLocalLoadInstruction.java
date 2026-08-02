package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrLocalLoadInstruction(IrValueId id, IrLocal local) implements IrValueInstruction, IrLocalInstruction {

    public IrLocalLoadInstruction {
        Objects.requireNonNull(id, "IR local load value identifier must not be null.");
        Objects.requireNonNull(local, "Loaded IR local must not be null.");
    }

    @Override
    public IrType type() {
        return local.type();
    }
}
