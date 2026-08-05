package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrProgram;

import java.nio.file.Path;
import java.util.Objects;

public final class LlvmObjectBackend {
    private LlvmObjectBackend() {}

    public static Path emitHostObject(IrProgram program, String moduleName, Path destination) {
        return emitObject(program, moduleName, LlvmTargetConfiguration.host(), destination);
    }

    public static Path emitObject(IrProgram program, String moduleName, LlvmTargetConfiguration targetConfiguration, Path destination) {
        Objects.requireNonNull(program, "Compiled Sol IR program must not be null.");
        Objects.requireNonNull(moduleName, "Compiled LLVM module name must not be null.");
        Objects.requireNonNull(targetConfiguration, "LLVM target configuration must not be null.");
        Objects.requireNonNull(destination, "Native object destination must not be null.");

        try (
            var module = LlvmBackend.generate(program, moduleName);

            var targetMachine = LlvmTargetMachine.create(targetConfiguration)
        ) {
            return LlvmObjectEmitter.emit(module, targetMachine, destination);
        }
    }
}
