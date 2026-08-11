package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerIndexLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerIndexStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildGEP2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStore;

final class LlvmPointerInstructionLowerer {
    private LlvmPointerInstructionLowerer() {}

    static void lower(IrInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered pointer IR instruction must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        switch (instruction) {
            case IrPointerLoadInstruction load -> lowerLoad(load, context);
            case IrPointerIndexLoadInstruction load -> lowerIndexLoad(load, context);
            case IrPointerStoreInstruction store -> lowerStore(store, context);
            case IrPointerIndexStoreInstruction store -> lowerIndexStore(store, context);

            default -> throw new LlvmBackendException(
                "Unsupported pointer IR instruction '%s'.".formatted(instruction.getClass().getSimpleName())
            );
        }
    }

    private static void lowerLoad(IrPointerLoadInstruction instruction, LlvmFunctionLoweringContext context) {
        var lowered = LLVMBuildLoad2(
            context.builder(),
            LlvmTypeLowerer.lower(instruction.type(), context.llvmContext()),
            context.value(instruction.pointer()),
            valueName(instruction.id().index())
        );

        requireValue(lowered, "pointer load");
        context.registerValue(instruction, lowered);
    }

    private static void lowerIndexLoad(IrPointerIndexLoadInstruction instruction, LlvmFunctionLoweringContext context) {
        var address = indexedAddress(instruction.pointer().type(), context.value(instruction.pointer()), context.value(instruction.index()), context);
        var lowered = LLVMBuildLoad2(
            context.builder(),
            LlvmTypeLowerer.lower(instruction.type(), context.llvmContext()),
            address,
            valueName(instruction.id().index())
        );

        requireValue(lowered, "pointer index load");
        context.registerValue(instruction, lowered);
    }

    private static void lowerStore(IrPointerStoreInstruction instruction, LlvmFunctionLoweringContext context) {
        requireValue(
            LLVMBuildStore(context.builder(), context.value(instruction.value()), context.value(instruction.pointer())),
            "pointer store"
        );
    }

    private static void lowerIndexStore(IrPointerIndexStoreInstruction instruction, LlvmFunctionLoweringContext context) {
        var address = indexedAddress(instruction.pointer().type(), context.value(instruction.pointer()), context.value(instruction.index()), context);

        requireValue(LLVMBuildStore(context.builder(), context.value(instruction.value()), address), "pointer index store");
    }

    private static LLVMValueRef indexedAddress(
        io.github.stardragonstudios.sol.ir.IrType pointerType,
        LLVMValueRef pointer,
        LLVMValueRef index,
        LlvmFunctionLoweringContext context
    ) {
        var elementType = ((IrPointerType) pointerType).elementType();
        final LLVMValueRef address;

        try (var indices = new PointerPointer<LLVMValueRef>(1)) {
            indices.put(0, index);
            address = LLVMBuildGEP2(
                context.builder(),
                LlvmTypeLowerer.lower(elementType, context.llvmContext()),
                pointer,
                indices,
                1,
                "pointer_index"
            );
        }

        requireValue(address, "pointer indexed address");

        return address;
    }

    private static String valueName(int index) {
        return "value%d".formatted(index);
    }

    private static void requireValue(Pointer value, String description) {
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM failed to create %s.".formatted(description));
    }
}
