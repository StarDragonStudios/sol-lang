package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrLocalStoreInstruction(IrLocal local, IrValue value) implements IrLocalInstruction {
    public IrLocalStoreInstruction {
        Objects.requireNonNull(local, "Stored IR local must not be null.");
        Objects.requireNonNull(value, "Stored IR local value must not be null.");

        if (!local.isMutable())
            throw new IllegalArgumentException("Cannot update immutable IR local '%s'.".formatted(local.name()));

        if (!local.type().equals(value.type()))
            throw new IllegalArgumentException(
                "Stored value type '%s' does not match local '%s' type '%s'.".formatted(
                    value.type().displayName(),
                    local.name(),
                    local.type().displayName()
                )
            );
    }

    @Override
    public List<IrValue> operands() {
        return List.of(value);
    }
}
