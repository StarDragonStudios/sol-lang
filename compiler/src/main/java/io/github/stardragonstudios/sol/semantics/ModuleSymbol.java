package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.syntax.CompilationUnit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ModuleSymbol {
    private final ModuleName name;
    private final CompilationUnit unit;
    private final Scope scope;

    private final LinkedHashMap<String, FunctionSymbol> exportedFunctions = new LinkedHashMap<>();
    private final LinkedHashMap<String, StructSymbol> exportedStructs = new LinkedHashMap<>();

    public ModuleSymbol(ModuleName name, CompilationUnit unit) {
        this.name = Objects.requireNonNull(name, "Module symbol name must not be null.");
        this.unit = Objects.requireNonNull(unit, "Module symbol compilation unit must not be null.");

        scope = new Scope(ScopeKind.MODULE);
    }

    public ModuleName name() {
        return name;
    }

    public CompilationUnit unit() {
        return unit;
    }

    public Scope scope() {
        return scope;
    }

    public List<FunctionSymbol> exportedFunctions() {
        return List.copyOf(exportedFunctions.values());
    }

    public Optional<FunctionSymbol> exportedFunction(String name) {
        Objects.requireNonNull(name, "Exported function lookup name must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Exported function lookup name must not be blank.");

        return Optional.ofNullable(exportedFunctions.get(name));
    }

    public List<StructSymbol> exportedStructs() {
        return List.copyOf(exportedStructs.values());
    }

    public Optional<StructSymbol> exportedStruct(String name) {
        Objects.requireNonNull(name, "Exported struct lookup name must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Exported struct lookup name must not be blank.");

        return Optional.ofNullable(exportedStructs.get(name));
    }

    boolean declareExport(FunctionSymbol function) {
        Objects.requireNonNull(function, "Exported function must not be null.");

        if (!scope.declare(function)) return false;

        exportedFunctions.put(function.name(), function);

        return true;
    }

    boolean declareExport(StructSymbol struct) {
        Objects.requireNonNull(struct, "Exported struct must not be null.");

        if (!scope.declare(struct)) return false;

        exportedStructs.put(struct.name(), struct);

        return true;
    }
}
