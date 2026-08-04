package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.*;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStore;

final class LlvmLocalInstructionLowerer {
    private LlvmLocalInstructionLowerer() {}

    static void lower(IrLocalInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered local Sol IR instruction must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        switch (instruction) {
            case IrLocalInitializeInstruction initialization -> lowerStore(initialization.local(), initialization.initializer(), context);
            case IrLocalLoadInstruction load -> lowerLoad(load, context);
            case IrLocalStoreInstruction store -> lowerStore(store.local(), store.value(), context);

            default -> throw new LlvmBackendException(
                "Sol IR local instruction implementation '%s' is not supported by the LLVM backend.".formatted(instruction.getClass().getSimpleName())
            );
        }
    }

    private static void lowerLoad(IrLocalLoadInstruction instruction, LlvmFunctionLoweringContext context) {
        var lowered = LLVMBuildLoad2(
            context.builder(),
            LlvmTypeLowerer.lower(instruction.local().type(), context.llvmContext()),
            context.localSlot(instruction.local()),
            valueName(instruction)
        );

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to load Sol IR local '%s'.".formatted(instruction.local().id()));

        context.registerValue(instruction, lowered);
    }

    private static void lowerStore(IrLocal local, IrValue value, LlvmFunctionLoweringContext context) {
        var lowered = LLVMBuildStore(context.builder(), context.value(value), context.localSlot(local));

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to store a value in Sol IR local '%s'.".formatted(local.id()));
    }

    private static String valueName(IrLocalLoadInstruction instruction) {
        return "value%d".formatted(instruction.id().index());
    }
}
