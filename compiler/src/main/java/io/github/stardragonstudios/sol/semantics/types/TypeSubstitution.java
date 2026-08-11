package io.github.stardragonstudios.sol.semantics.types;

import io.github.stardragonstudios.sol.semantics.TypeParameterSymbol;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class TypeSubstitution {
    private TypeSubstitution() {}

    public static TypeSymbol substitute(TypeSymbol type, Map<TypeParameterSymbol, TypeSymbol> substitutions) {
        Objects.requireNonNull(type, "Substituted type must not be null.");
        Objects.requireNonNull(substitutions, "Type substitutions must not be null.");

        if (type instanceof TypeParameterType parameter)
            return substitutions.getOrDefault(parameter.symbol(), parameter);

        if (!(type instanceof StructType struct) || struct.arguments().isEmpty()) return type;

        var arguments = new ArrayList<TypeSymbol>(struct.arguments().size());
        var changed = false;

        for (var argument : struct.arguments()) {
            var substituted = substitute(argument, substitutions);

            arguments.add(substituted);
            changed |= substituted != argument;
        }

        return changed ? new StructType(struct.symbol(), arguments) : struct;
    }
}
