package io.github.stardragonstudios.sol.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record IrFunctionReference(IrFunctionId id, String name, List<IrType> parameterTypes, IrType returnType) {
    public IrFunctionReference {
        Objects.requireNonNull(id, "IR function reference identifier must not be null.");
        Objects.requireNonNull(name, "IR function reference name must not be null.");
        Objects.requireNonNull(parameterTypes, "IR function reference parameter types must not be null.");
        Objects.requireNonNull(returnType, "IR function reference return type must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("IR function reference name must not be blank.");

        var copiedParameterTypes = new ArrayList<IrType>(parameterTypes.size());

        for (var parameterType : parameterTypes) {
            var validatedType = getValidatedType(parameterType);

            copiedParameterTypes.add(validatedType);
        }

        parameterTypes = List.copyOf(copiedParameterTypes);
    }

    private static IrType getValidatedType(IrType parameterType) {
        var validatedType = Objects.requireNonNull(parameterType, "IR function reference parameter types must not contain null values.");

        if (!validatedType.isValue())
            throw new IllegalArgumentException("IR function reference parameter type '%s' must be a value type.".formatted(validatedType.displayName()));

        return validatedType;
    }

    public int arity() {
        return parameterTypes.size();
    }

    public boolean returnsValue() {
        return returnType.isValue();
    }
}
