package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrProgram;

import java.util.Objects;

public final class LlvmBackend {
    private LlvmBackend() {}

    public static LlvmModule generate(IrProgram program, String moduleName) {
        Objects.requireNonNull(program, "Generated Sol IR program must not be null.");

        var module = LlvmModule.create(moduleName);

        try {
            var context = LlvmProgramPredeclarer.predeclare(program, module);

            for (var irModule : program.modules()) for (var function : irModule.functions()) LlvmFunctionBodyLowerer.lower(function, context);

            module.verify();

            return module;
        } catch (LlvmBackendException exception) {
            module.close();

            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            module.close();

            throw new LlvmBackendException("Failed to generate LLVM IR for module '%s'.".formatted(moduleName), exception);
        }
    }
}