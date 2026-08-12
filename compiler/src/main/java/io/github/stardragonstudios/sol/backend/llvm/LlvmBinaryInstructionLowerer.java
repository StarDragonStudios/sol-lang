package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;
import io.github.stardragonstudios.sol.ir.IrPointerType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildAdd;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildAnd;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildFAdd;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildFCmp;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildFDiv;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildFMul;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildFSub;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildICmp;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildMul;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildOr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildSDiv;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildSRem;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildSub;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildNot;

import static org.bytedeco.llvm.global.LLVM.LLVMIntEQ;
import static org.bytedeco.llvm.global.LLVM.LLVMIntNE;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSGE;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSGT;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSLE;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSLT;

import static org.bytedeco.llvm.global.LLVM.LLVMRealOEQ;
import static org.bytedeco.llvm.global.LLVM.LLVMRealOGE;
import static org.bytedeco.llvm.global.LLVM.LLVMRealOGT;
import static org.bytedeco.llvm.global.LLVM.LLVMRealOLE;
import static org.bytedeco.llvm.global.LLVM.LLVMRealOLT;
import static org.bytedeco.llvm.global.LLVM.LLVMRealUNE;

final class LlvmBinaryInstructionLowerer {
    private LlvmBinaryInstructionLowerer() {}

    static LLVMValueRef lower(IrBinaryInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered binary Sol IR instruction must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        var left = context.value(instruction.left());
        var right = context.value(instruction.right());

        var lowered = switch (instruction.operator()) {
            case ADD -> lowerAdd(instruction, left, right, context);
            case SUBTRACT -> lowerSubtract(instruction, left, right, context);
            case MULTIPLY -> lowerMultiply(instruction, left, right, context);
            case DIVIDE -> lowerDivide(instruction, left, right, context);
            case REMAINDER -> LLVMBuildSRem(context.builder(), left, right, valueName(instruction));
            case LESS_THAN -> lowerComparison(instruction, left, right, LLVMIntSLT, LLVMRealOLT, context);
            case LESS_THAN_OR_EQUAL -> lowerComparison(instruction, left, right, LLVMIntSLE, LLVMRealOLE, context);
            case GREATER_THAN -> lowerComparison(instruction, left, right, LLVMIntSGT, LLVMRealOGT, context);
            case GREATER_THAN_OR_EQUAL -> lowerComparison(instruction, left, right, LLVMIntSGE, LLVMRealOGE, context);
            case EQUAL -> lowerEquality(instruction, left, right, LLVMIntEQ, LLVMRealOEQ, context);
            case NOT_EQUAL -> lowerEquality(instruction, left, right, LLVMIntNE, LLVMRealUNE, context);
            case LOGICAL_AND -> LLVMBuildAnd(context.builder(), left, right, valueName(instruction));
            case LOGICAL_OR -> LLVMBuildOr(context.builder(), left, right, valueName(instruction));
        };

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to lower binary Sol IR instruction '%s'.".formatted(instruction.id()));

        return context.registerValue(instruction, lowered);
    }

    private static LLVMValueRef lowerAdd(IrBinaryInstruction instruction, LLVMValueRef left, LLVMValueRef right, LlvmFunctionLoweringContext context) {
        if (instruction.left().type() == PrimitiveIrType.STRING)
            return context.stringRuntime().concat(context.builder(), left, right, valueName(instruction));

        if (isFloat(instruction.left().type())) return LLVMBuildFAdd(context.builder(), left, right, valueName(instruction));

        return LLVMBuildAdd(context.builder(), left, right, valueName(instruction));
    }

    private static LLVMValueRef lowerSubtract(IrBinaryInstruction instruction, LLVMValueRef left, LLVMValueRef right, LlvmFunctionLoweringContext context) {
        if (isFloat(instruction.left().type())) return LLVMBuildFSub(context.builder(), left, right, valueName(instruction));

        return LLVMBuildSub(context.builder(), left, right, valueName(instruction));
    }

    private static LLVMValueRef lowerMultiply(IrBinaryInstruction instruction, LLVMValueRef left, LLVMValueRef right, LlvmFunctionLoweringContext context) {
        if (isFloat(instruction.left().type())) return LLVMBuildFMul(context.builder(), left, right, valueName(instruction));

        return LLVMBuildMul(context.builder(), left, right, valueName(instruction));
    }

    private static LLVMValueRef lowerDivide(IrBinaryInstruction instruction, LLVMValueRef left, LLVMValueRef right, LlvmFunctionLoweringContext context) {
        if (isFloat(instruction.left().type())) return LLVMBuildFDiv(context.builder(), left, right, valueName(instruction));

        return LLVMBuildSDiv(context.builder(), left, right, valueName(instruction));
    }

    private static LLVMValueRef lowerComparison(
        IrBinaryInstruction instruction,
        LLVMValueRef left,
        LLVMValueRef right,
        int integerPredicate,
        int floatingPredicate,
        LlvmFunctionLoweringContext context
    ) {
        if (isFloat(instruction.left().type())) return LLVMBuildFCmp(context.builder(), floatingPredicate, left, right, valueName(instruction));

        return LLVMBuildICmp(context.builder(), integerPredicate, left, right, valueName(instruction));
    }

    private static LLVMValueRef lowerEquality(
        IrBinaryInstruction instruction,
        LLVMValueRef left,
        LLVMValueRef right,
        int integerPredicate,
        int floatingPredicate,
        LlvmFunctionLoweringContext context
    ) {
        var operandType = instruction.left().type();

        if (isFloat(operandType)) return LLVMBuildFCmp(context.builder(), floatingPredicate, left, right, valueName(instruction));

        if (operandType == PrimitiveIrType.INT || operandType == PrimitiveIrType.BOOLEAN || operandType == PrimitiveIrType.CHAR)
            return LLVMBuildICmp(context.builder(), integerPredicate, left, right, valueName(instruction));

        if (operandType instanceof IrPointerType)
            return LLVMBuildICmp(context.builder(), integerPredicate, left, right, valueName(instruction));

        if (operandType == PrimitiveIrType.STRING) {
            var equal = context.stringRuntime().equal(context.builder(), left, right, valueName(instruction) + "_equal");

            return integerPredicate == LLVMIntEQ
                ? equal
                : LLVMBuildNot(context.builder(), equal, valueName(instruction));
        }

        throw new LlvmBackendException("LLVM equality comparison is not supported for Sol IR type '%s'.".formatted(operandType.displayName()));
    }

    private static boolean isFloat(IrType type) {
        return type == PrimitiveIrType.FLOAT;
    }

    private static String valueName(IrBinaryInstruction instruction) {
        return "value%d".formatted(instruction.id().index());
    }
}
