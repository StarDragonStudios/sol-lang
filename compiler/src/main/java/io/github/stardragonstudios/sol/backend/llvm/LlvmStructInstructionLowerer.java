package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrStructConstructInstruction;
import io.github.stardragonstudios.sol.ir.IrStructField;
import io.github.stardragonstudios.sol.ir.IrStructFieldExtractInstruction;
import io.github.stardragonstudios.sol.ir.IrStructFieldStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrStructType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.List;
import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildExtractValue;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildInsertValue;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStore;
import static org.bytedeco.llvm.global.LLVM.LLVMGetUndef;

final class LlvmStructInstructionLowerer {
    private LlvmStructInstructionLowerer() {}

    static void lower(IrStructConstructInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered IR struct construction must not be null.");

        var aggregate = LLVMGetUndef(LlvmTypeLowerer.lower(instruction.type(), context.llvmContext()));

        if (Pointer.isNull(aggregate)) throw new LlvmBackendException("LLVM failed to create an undefined value for IR struct '%s'.".formatted(instruction.type().displayName()));

        for (var field : instruction.type().fields()) {
            aggregate = LLVMBuildInsertValue(
                context.builder(),
                aggregate,
                context.value(instruction.fields().get(field.index())),
                field.index(),
                temporaryName(instruction.id().index(), field.index())
            );

            if (Pointer.isNull(aggregate)) throw new LlvmBackendException(
                "LLVM failed to initialize field '%s' of IR struct '%s'.".formatted(field.name(), instruction.type().displayName())
            );
        }

        context.registerValue(instruction, aggregate);
    }

    static void lower(IrStructFieldExtractInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered IR struct field extraction must not be null.");

        var lowered = LLVMBuildExtractValue(
            context.builder(),
            context.value(instruction.target()),
            instruction.field().index(),
            "value%d".formatted(instruction.id().index())
        );

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to extract IR struct field '%s'.".formatted(instruction.field().name()));

        context.registerValue(instruction, lowered);
    }

    static void lower(IrStructFieldStoreInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered IR struct field store must not be null.");

        var localType = (IrStructType) instruction.local().type();
        var aggregate = LLVMBuildLoad2(
            context.builder(),
            LlvmTypeLowerer.lower(localType, context.llvmContext()),
            context.localSlot(instruction.local()),
            "struct.load.%d".formatted(instruction.local().id().index())
        );

        if (Pointer.isNull(aggregate)) throw new LlvmBackendException("LLVM failed to load IR struct local '%s' for field update.".formatted(instruction.local().id()));

        var updated = updateAggregate(
            aggregate,
            localType,
            instruction.path(),
            0,
            context.value(instruction.value()),
            context
        );
        var stored = LLVMBuildStore(context.builder(), updated, context.localSlot(instruction.local()));

        if (Pointer.isNull(stored)) throw new LlvmBackendException("LLVM failed to store updated IR struct local '%s'.".formatted(instruction.local().id()));
    }

    private static LLVMValueRef updateAggregate(
        LLVMValueRef aggregate,
        IrStructType type,
        List<IrStructField> path,
        int depth,
        LLVMValueRef value,
        LlvmFunctionLoweringContext context
    ) {
        var field = path.get(depth);
        LLVMValueRef updatedField;

        if (depth == path.size() - 1) {
            updatedField = value;
        } else {
            var nested = LLVMBuildExtractValue(
                context.builder(),
                aggregate,
                field.index(),
                "struct.path.%d".formatted(depth)
            );

            if (Pointer.isNull(nested)) throw new LlvmBackendException("LLVM failed to read nested IR struct field '%s'.".formatted(field.name()));

            updatedField = updateAggregate(nested, (IrStructType) field.type(), path, depth + 1, value, context);
        }

        var updated = LLVMBuildInsertValue(
            context.builder(),
            aggregate,
            updatedField,
            field.index(),
            "struct.update.%d".formatted(depth)
        );

        if (Pointer.isNull(updated)) throw new LlvmBackendException("LLVM failed to update IR struct field '%s'.".formatted(field.name()));

        return updated;
    }

    private static String temporaryName(int valueIndex, int fieldIndex) {
        return "struct%d.field%d".formatted(valueIndex, fieldIndex);
    }
}
