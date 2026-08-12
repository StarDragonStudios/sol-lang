package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrStringIndexInstruction;

import org.bytedeco.javacpp.Pointer;

import java.util.Objects;

final class LlvmStringInstructionLowerer {
    private LlvmStringInstructionLowerer() {}

    static void lower(IrStringIndexInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered string-index Sol IR instruction must not be null.");
        Objects.requireNonNull(context, "LLVM string-index lowering context must not be null.");

        var string = context.value(instruction.string());
        var index = context.value(instruction.index());
        var lowered = context.stringRuntime().index(
            context.builder(),
            string,
            index,
            "value%d".formatted(instruction.id().index())
        );

        if (Pointer.isNull(lowered)) throw new LlvmBackendException(
            "LLVM failed to lower string index Sol IR instruction '%s'.".formatted(instruction.id())
        );

        context.registerValue(instruction, lowered);
    }
}
