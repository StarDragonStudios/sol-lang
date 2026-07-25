package io.github.stardragonstudios.sol.ir;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record IrProgram(List<IrModule> modules, Optional<IrEntryPoint> entryPoint) {
    public IrProgram {
        Objects.requireNonNull(modules, "IR program modules must not be null.");
        Objects.requireNonNull(entryPoint, "IR program entry point must not be null.");

        validateModules(modules);
        List<IrModule> finalModules = modules;

        modules = List.copyOf(modules);
        entryPoint.ifPresent(value -> validateEntryPoint(finalModules, value));
    }

    public static IrProgram library(List<IrModule> modules) {
        return new IrProgram(modules, Optional.empty());
    }

    public static IrProgram executable(List<IrModule> modules, IrEntryPoint entryPoint) {
        Objects.requireNonNull(entryPoint, "Executable IR program entry point must not be null.");

        return new IrProgram(modules, Optional.of(entryPoint));
    }

    public boolean hasEntryPoint() {
        return entryPoint.isPresent();
    }

    public Optional<IrModule> module(IrModuleName name) {
        Objects.requireNonNull(name, "IR module lookup name must not be null.");

        return modules.stream().filter(module -> module.name().equals(name)).findFirst();
    }

    public Optional<IrFunction> function(IrFunctionId id) {
        Objects.requireNonNull(id, "IR function lookup identifier must not be null.");

        return modules.stream()
            .flatMap(module -> module.functions().stream())
            .filter(function -> function.id().equals(id))
            .findFirst();
    }

    public Optional<IrModule> entryModule() {
        return entryPoint.map(IrEntryPoint::module);
    }

    public Optional<IrFunction> entryFunction() {
        return entryPoint.map(IrEntryPoint::function);
    }

    private static void validateModules(List<IrModule> modules) {
        var moduleNames = new HashSet<IrModuleName>();
        var functionIdentifiers = new HashSet<IrFunctionId>();

        Set<IrModule> moduleInstances = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IrFunction> functionInstances = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var module : modules) {
            Objects.requireNonNull(module, "IR program modules must not contain null values.");

            if (!moduleInstances.add(module))
                throw new IllegalArgumentException("IR program must not contain the same module instance more than once.");

            if (!moduleNames.add(module.name()))
                throw new IllegalArgumentException("IR program must not contain duplicate module name '%s'.".formatted(module.name().qualifiedName()));

            for (var function : module.functions()) {
                if (!functionInstances.add(function))
                    throw new IllegalArgumentException("IR program must not contain the same function instance in more than one module.");

                if (!functionIdentifiers.add(function.id()))
                    throw new IllegalArgumentException("IR program must not contain duplicate global function identifier '%s'.".formatted(function.id()));
            }
        }
    }

    private static void validateEntryPoint(List<IrModule> modules, IrEntryPoint entryPoint) {
        var canonicalModule = modules.stream().anyMatch(module -> module == entryPoint.module());

        if (!canonicalModule)
            throw new IllegalArgumentException("IR program entry point must reference a canonical module from the program.");
    }
}
