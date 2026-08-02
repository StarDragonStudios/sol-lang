package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrLocalInitializeInstruction(IrLocal local, IrValue initializer) implements IrLocalInstruction {
    public IrLocalInitializeInstruction {
        Objects.requireNonNull(local, "Initialized IR local must not be null.");
        Objects.requireNonNull(initializer, "IR local initializer must not be null.");

        if (!local.type().equals(initializer.type()))
            throw new IllegalArgumentException(
                "IR local initializer type '%s' does not match local '%s' type '%s'.".formatted(
                    initializer.type().displayName(),
                    local.name(),
                    local.type().displayName()
                )
            );
    }

    @Override
    public List<IrValue> operands() {
        return List.of(initializer);
    }
}
