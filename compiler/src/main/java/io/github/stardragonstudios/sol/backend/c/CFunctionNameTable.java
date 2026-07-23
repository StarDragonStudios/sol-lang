package io.github.stardragonstudios.sol.backend.c;

import io.github.stardragonstudios.sol.semantics.FunctionSymbol;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

final class CFunctionNameTable {
    private final IdentityHashMap<FunctionSymbol, String> names = new IdentityHashMap<>();

    CFunctionNameTable(List<FunctionSymbol> functions) {
        Objects.requireNonNull(functions, "Generated function list must not be null.");

        for (var index = 0; index < functions.size(); index++) {
            var function = Objects.requireNonNull(functions.get(index), "Generated function list must not contain null values.");

            if (names.put(function, "sol_function_" + index) != null) {
                throw new IllegalArgumentException(
                    "Generated function list must not contain the same canonical function more than once."
                );
            }
        }
    }

    String nameOf(FunctionSymbol function) {
        Objects.requireNonNull(function, "Generated function query must not be null.");

        var name = names.get(function);

        if (name == null) {
            throw new IllegalArgumentException(
                "No C name was allocated for function '%s'."
                    .formatted(function.name())
            );
        }

        return name;
    }
}
