package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrProgram;

import java.util.Objects;

public final class LlvmBackend {
    private LlvmBackend() {}

    public static LlvmModule generate(IrProgram program, String moduleName) {
        Objects.requireNonNull(program, "Generated Sol IR program must not be null.");

        var module = LlvmModule.create(moduleName);

        try {
            /*
             * Validate the native startup boundary before lowering
             * parameter types. Entry-point parameters remain valid
             * Sol IR even when the current native bridge cannot bind
             * them yet.
             */
            LlvmEntryPointLowerer.validate(program);

            var context = LlvmProgramPredeclarer.predeclare(program, module);

            LlvmStandardLibraryLowerer.lower(program, context);

            for (var irModule : program.modules()) for (var function : irModule.functions()) LlvmFunctionBodyLowerer.lower(function, context);

            LlvmEntryPointLowerer.lower(program, context);

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
