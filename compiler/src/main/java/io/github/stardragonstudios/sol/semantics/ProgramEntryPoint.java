package io.github.stardragonstudios.sol.semantics;

import java.util.Objects;

public record ProgramEntryPoint(
    ModuleSymbol module,
    FunctionSymbol function
) {
    public ProgramEntryPoint {
        Objects.requireNonNull(
            module,
            "Entry point module must not be null."
        );

        Objects.requireNonNull(
            function,
            "Entry point function must not be null."
        );
    }
}
