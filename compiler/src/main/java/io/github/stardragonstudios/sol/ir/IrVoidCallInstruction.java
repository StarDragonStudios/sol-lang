package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrVoidCallInstruction(IrFunctionReference target, List<IrValue> arguments) implements IrCallInstruction {
    public IrVoidCallInstruction {
        Objects.requireNonNull(target, "Void IR call target must not be null.");

        if (target.returnsValue())
            throw new IllegalArgumentException("Void IR call target '%s' must return a non-value type.".formatted(target.name()));

        arguments = IrCallValidation.copyAndValidateArguments(target, arguments);
    }

    @Override
    public List<IrValue> operands() {
        return arguments;
    }
}
