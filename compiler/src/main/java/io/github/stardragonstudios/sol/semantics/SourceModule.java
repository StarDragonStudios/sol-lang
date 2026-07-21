package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.syntax.CompilationUnit;

import java.util.Objects;

public record SourceModule(ModuleName name, CompilationUnit unit) {
    public SourceModule {
        Objects.requireNonNull(name, "Source module name must not be null.");
        Objects.requireNonNull(unit, "Source module compilation unit must not be null.");
    }
}
