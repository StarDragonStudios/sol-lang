package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrCallInstruction;
import io.github.stardragonstudios.sol.ir.IrInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalInstruction;
import io.github.stardragonstudios.sol.ir.IrUnaryInstruction;

import java.util.Objects;

final class LlvmInstructionLowerer {
    private LlvmInstructionLowerer() {}

    static void lower(IrInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered Sol IR instruction must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        switch (instruction) {
            case IrUnaryInstruction unary -> LlvmUnaryInstructionLowerer.lower(unary, context);
            case IrBinaryInstruction binary -> LlvmBinaryInstructionLowerer.lower(binary, context);
            case IrLocalInstruction local -> LlvmLocalInstructionLowerer.lower(local, context);
            case IrCallInstruction call -> LlvmCallInstructionLowerer.lower(call, context);

            default -> throw new LlvmBackendException(
                "Sol IR instruction implementation '%s' is not supported by the LLVM backend.".formatted(instruction.getClass().getSimpleName())
            );
        }
    }
}
