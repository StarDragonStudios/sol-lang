package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrCallInstruction;
import io.github.stardragonstudios.sol.ir.IrInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalInstruction;
import io.github.stardragonstudios.sol.ir.IrStructConstructInstruction;
import io.github.stardragonstudios.sol.ir.IrStructFieldExtractInstruction;
import io.github.stardragonstudios.sol.ir.IrUnaryInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerIndexLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerIndexStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerFieldLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerFieldStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrStringIndexInstruction;

import java.util.Objects;

final class LlvmInstructionLowerer {
    private LlvmInstructionLowerer() {}

    static void lower(IrInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered Sol IR instruction must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        switch (instruction) {
            case IrStructConstructInstruction construction -> LlvmStructInstructionLowerer.lower(construction, context);
            case IrStructFieldExtractInstruction extraction -> LlvmStructInstructionLowerer.lower(extraction, context);
            case IrUnaryInstruction unary -> LlvmUnaryInstructionLowerer.lower(unary, context);
            case IrBinaryInstruction binary -> LlvmBinaryInstructionLowerer.lower(binary, context);
            case IrLocalInstruction local -> LlvmLocalInstructionLowerer.lower(local, context);
            case IrCallInstruction call -> LlvmCallInstructionLowerer.lower(call, context);
            case IrPointerLoadInstruction pointer -> LlvmPointerInstructionLowerer.lower(pointer, context);
            case IrPointerIndexLoadInstruction pointer -> LlvmPointerInstructionLowerer.lower(pointer, context);
            case IrPointerStoreInstruction pointer -> LlvmPointerInstructionLowerer.lower(pointer, context);
            case IrPointerIndexStoreInstruction pointer -> LlvmPointerInstructionLowerer.lower(pointer, context);
            case IrPointerFieldLoadInstruction pointer -> LlvmPointerInstructionLowerer.lower(pointer, context);
            case IrPointerFieldStoreInstruction pointer -> LlvmPointerInstructionLowerer.lower(pointer, context);
            case IrStringIndexInstruction string -> LlvmStringInstructionLowerer.lower(string, context);

            default -> throw new LlvmBackendException(
                "Sol IR instruction implementation '%s' is not supported by the LLVM backend.".formatted(instruction.getClass().getSimpleName())
            );
        }
    }
}
