package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrProgram;

import java.util.Objects;

final class LlvmProgramPredeclarer {
    private LlvmProgramPredeclarer() {}

    static LlvmProgramLoweringContext predeclare(IrProgram program, LlvmModule module) {
        Objects.requireNonNull(program, "Predeclared Sol IR program must not be null.");
        Objects.requireNonNull(module, "LLVM destination module must not be null.");

        var context = new LlvmProgramLoweringContext(module);

        /*
         * All functions are declared before any body is generated.
         * Forward calls, recursion and calls between Sol modules can
         * therefore resolve through the same registry.
         */
        for (var irModule : program.modules()) for (var function : irModule.functions()) LlvmFunctionDeclarationLowerer.lower(function, context);

        return context;
    }
}