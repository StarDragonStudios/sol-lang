package io.github.stardragonstudios.sol.ir;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record IrModule(IrModuleName name, List<IrFunction> functions) {
    public IrModule {
        Objects.requireNonNull(name, "IR module name must not be null.");
        Objects.requireNonNull(functions, "IR module functions must not be null.");

        validateFunctions(functions);

        functions = List.copyOf(functions);
    }

    public Optional<IrFunction> function(IrFunctionId id) {
        Objects.requireNonNull(id, "IR function lookup identifier must not be null.");

        return functions.stream()
            .filter(function -> function.id().equals(id))
            .findFirst();
    }

    public Optional<IrFunction> function(String name) {
        Objects.requireNonNull(name, "IR function lookup name must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("IR function lookup name must not be blank.");

        return functions.stream()
            .filter(function -> function.name().equals(name))
            .findFirst();
    }

    private static void validateFunctions(List<IrFunction> functions) {
        var identifiers = new HashSet<IrFunctionId>();
        var names = new HashSet<String>();

        Set<IrFunction> instances = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var function : functions) {
            Objects.requireNonNull(function, "IR module functions must not contain null values.");

            if (!instances.add(function))
                throw new IllegalArgumentException("IR module must not contain the same function instance more than once.");

            if (!identifiers.add(function.id()))
                throw new IllegalArgumentException("IR module must not contain duplicate function identifier '%s'.".formatted(function.id()));

            if (!names.add(function.name()))
                throw new IllegalArgumentException("IR module must not contain duplicate function name '%s'.".formatted(function.name()));
        }
    }
}
