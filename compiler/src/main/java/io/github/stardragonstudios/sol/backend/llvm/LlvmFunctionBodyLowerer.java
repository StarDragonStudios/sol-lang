package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;

import java.util.Objects;

final class LlvmFunctionBodyLowerer {
    private LlvmFunctionBodyLowerer() {}

    static void lower(IrFunction function, LlvmProgramLoweringContext programContext) {
        Objects.requireNonNull(function, "Lowered Sol IR function must not be null.");
        Objects.requireNonNull(programContext, "LLVM program lowering context must not be null.");

        if (!function.hasBody()) return;

        try (var context = LlvmFunctionLoweringContext.create(function, programContext)) {
            for (var block : function.blocks()) {
                context.positionAtEnd(block);

                for (var instruction : block.instructions()) LlvmInstructionLowerer.lower(instruction, context);

                LlvmTerminatorLowerer.lower(block.terminator(), context);
            }
        }
    }
}
