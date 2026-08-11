package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class LlvmProgramLoweringContext {
    private final LlvmModule module;
    private final Map<IrFunctionId, LlvmFunctionHandle> functions = new HashMap<>();
    private LlvmStringRuntime stringRuntime;

    LlvmProgramLoweringContext(LlvmModule module) {
        this.module = Objects.requireNonNull(module, "LLVM lowering module must not be null.");

        /*
         * Accessing the native handle also verifies that the module
         * has not already been closed.
         */
        module.moduleHandle();
    }

    LlvmModule module() {
        return module;
    }

    void ensureFunctionUnassigned(IrFunction function) {
        Objects.requireNonNull(function, "Queried Sol IR function must not be null.");

        if (functions.containsKey(function.id())) throw new LlvmBackendException("Sol IR function '%s' already has an LLVM declaration.".formatted(function.id()));
    }

    LlvmFunctionHandle registerFunction(IrFunction function, LlvmFunctionHandle lowered) {
        Objects.requireNonNull(function, "Registered Sol IR function must not be null.");
        Objects.requireNonNull(lowered, "Registered LLVM function must not be null.");

        ensureFunctionUnassigned(function);

        functions.put(function.id(), lowered);

        return lowered;
    }

    LlvmFunctionHandle function(IrFunctionId identifier) {
        Objects.requireNonNull(identifier, "Queried Sol IR function identifier must not be null.");

        var lowered = functions.get(identifier);

        if (lowered == null) throw new LlvmBackendException("Sol IR function '%s' has no LLVM declaration.".formatted(identifier));

        return lowered;
    }

    LlvmStringRuntime stringRuntime() {
        if (stringRuntime == null) stringRuntime = new LlvmStringRuntime(this);

        return stringRuntime;
    }

    int functionCount() {
        return functions.size();
    }
}
