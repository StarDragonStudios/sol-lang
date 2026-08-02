package io.github.stardragonstudios.sol.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class IrCallValidation {
    private IrCallValidation() {}

    static List<IrValue> copyAndValidateArguments(IrFunctionReference target, List<IrValue> arguments) {
        Objects.requireNonNull(target, "IR call target must not be null.");
        Objects.requireNonNull(arguments, "IR call arguments must not be null.");

        if (arguments.size() != target.parameterTypes().size())
            throw new IllegalArgumentException("IR call to '%s' expects %d arguments, but got %d.".formatted(target.name(), target.parameterTypes().size(), arguments.size()));

        var copiedArguments = new ArrayList<IrValue>(arguments.size());

        for (var index = 0; index < arguments.size(); index++) {
            var argument = Objects.requireNonNull(arguments.get(index), "IR call arguments must not contain null values.");

            var expectedType = target.parameterTypes().get(index);

            if (!expectedType.equals(argument.type())) {
                throw new IllegalArgumentException(
                    "IR call argument %d for function '%s' has type '%s', but parameter type is '%s'."
                        .formatted(index, target.name(), argument.type().displayName(), expectedType.displayName())
                );
            }

            copiedArguments.add(argument);
        }

        return List.copyOf(copiedArguments );
    }
}
