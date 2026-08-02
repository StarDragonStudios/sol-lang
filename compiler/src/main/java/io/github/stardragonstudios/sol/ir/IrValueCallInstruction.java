package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrValueCallInstruction(IrValueId id, IrFunctionReference target, List<IrValue> arguments) implements IrCallInstruction, IrValueInstruction {
    public IrValueCallInstruction {
        Objects.requireNonNull(id, "Value-producing IR call identifier must not be null.");
        Objects.requireNonNull(target, "Value-producing IR call target must not be null.");

        if (!target.returnsValue())
            throw new IllegalArgumentException("Value-producing IR call target '%s' must return a value.".formatted(target.name()));

        arguments = IrCallValidation.copyAndValidateArguments(target, arguments);
    }

    @Override
    public IrType type() {
        return target.returnType();
    }

    @Override
    public List<IrValue> operands() {
        return arguments;
    }
}
