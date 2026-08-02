package io.github.stardragonstudios.sol.ir;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
        var functionsByIdentifier = new HashMap<IrFunctionId, IrFunction>();

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

                var previous = functionsByIdentifier.putIfAbsent(function.id(), function);

                if (previous != null)
                    throw new IllegalArgumentException("IR program must not contain duplicate global function identifier '%s'.".formatted(function.id()));
            }
        }

        validateCallTargets(modules, functionsByIdentifier);
    }

    private static void validateCallTargets(List<IrModule> modules, Map<IrFunctionId, IrFunction> functionsByIdentifier) {
        /*
         * Every call to one function identifier must share the same
         * canonical IrFunctionReference instance.
         */
        var canonicalReferences = new HashMap<IrFunctionId, IrFunctionReference>();

        for (var module : modules)
            for (var function : module.functions())
                for (var block : function.blocks())
                    for (var instruction : block.instructions())
                        if (instruction instanceof IrCallInstruction call)
                            validateCallTarget(call.target(), functionsByIdentifier, canonicalReferences);
    }

    private static void validateCallTarget(
        IrFunctionReference reference,
        Map<IrFunctionId, IrFunction> functionsByIdentifier,
        Map<IrFunctionId, IrFunctionReference> canonicalReferences
    ) {
        Objects.requireNonNull(reference, "IR call target reference must not be null.");

        var function = functionsByIdentifier.get(reference.id());

        if (function == null)
            throw new IllegalArgumentException("IR call references undeclared function identifier '%s'.".formatted(reference.id()));

        if (!function.name().equals(reference.name()))
            throw new IllegalArgumentException(
                "IR call target '%s' does not match canonical function name '%s' for identifier '%s'.".formatted(reference.name(), function.name(), reference.id())
            );

        var parameterTypes = function.parameters().stream().map(IrParameter::type).toList();

        if (!parameterTypes.equals(reference.parameterTypes()))
            throw new IllegalArgumentException(
                "IR call target '%s' does not match the canonical parameter types of function '%s'.".formatted(reference.id(), function.name())
            );

        if (!function.returnType().equals(reference.returnType()))
            throw new IllegalArgumentException(
                "IR call target '%s' returns '%s', but canonical function '%s' returns '%s'.".formatted(
                    reference.id(),
                    reference.returnType().displayName(),
                    function.name(),
                    function.returnType().displayName()
                )
            );

        var canonicalReference = canonicalReferences.putIfAbsent(reference.id(), reference);

        if (canonicalReference != null && canonicalReference != reference)
            throw new IllegalArgumentException(
                "IR calls to function '%s' must share one canonical function-reference instance.".formatted(function.name())
            );
    }

    private static void validateEntryPoint(List<IrModule> modules, IrEntryPoint entryPoint) {
        var canonicalModule = modules.stream().anyMatch(module -> module == entryPoint.module());

        if (!canonicalModule)
            throw new IllegalArgumentException("IR program entry point must reference a canonical module from the program.");
    }
}
