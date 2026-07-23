package io.github.stardragonstudios.sol.backend.c;

import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ParameterSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticModel;

import java.util.IdentityHashMap;
import java.util.Objects;

final class CParameterNameTable {
    private final IdentityHashMap<ParameterSymbol, String> names = new IdentityHashMap<>();

    CParameterNameTable(SemanticModel model, FunctionSymbol function) {
        Objects.requireNonNull(model, "Parameter semantic model must not be null.");
        Objects.requireNonNull(function, "Parameter function must not be null.");

        var parameters = function.declaration().parameters();

        for (var index = 0; index < parameters.size(); index++) {
            var parameter = parameters.get(index);

            var symbol = model.symbolOf(parameter).orElseThrow(
                () -> new IllegalStateException(
                    "Semantic model has no symbol for parameter '%s' of function '%s'."
                        .formatted(
                            parameter.name(),
                            function.name()
                        )
                )
            );

            names.put(symbol, "sol_parameter_" + index);
        }
    }

    String nameOf(ParameterSymbol parameter) {
        Objects.requireNonNull(parameter, "Generated parameter query must not be null.");

        var name = names.get(parameter);

        if (name == null) {
            throw new IllegalArgumentException(
                "No C name was allocated for parameter '%s'."
                    .formatted(parameter.name())
            );
        }

        return name;
    }
}
