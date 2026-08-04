package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrUnaryInstruction;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildFNeg;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildNeg;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildNot;

final class LlvmUnaryInstructionLowerer {
    private LlvmUnaryInstructionLowerer() {}

    static LLVMValueRef lower(IrUnaryInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered unary Sol IR instruction must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        var operand = context.value(instruction.operand());
        var lowered = switch (instruction.operator()) {
            case LOGICAL_NOT -> LLVMBuildNot(context.builder(), operand, valueName(instruction));
            case NEGATE -> lowerNegation(instruction, operand, context);

            /*
             * Unary positive does not alter its operand. Register
             * the existing LLVM value under the new Sol IR value
             * identity instead of fabricating a redundant native
             * instruction.
             */
            case POSITIVE -> operand;
        };

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to lower unary Sol IR instruction '%s'.".formatted(instruction.id()));

        return context.registerValue(instruction, lowered);
    }

    private static LLVMValueRef lowerNegation(IrUnaryInstruction instruction, LLVMValueRef operand, LlvmFunctionLoweringContext context) {
        if (instruction.operand().type() == PrimitiveIrType.INT) return LLVMBuildNeg(context.builder(), operand, valueName(instruction));
        if (instruction.operand().type() == PrimitiveIrType.FLOAT) return LLVMBuildFNeg(context.builder(), operand, valueName(instruction));

        throw new LlvmBackendException("LLVM cannot lower unary negation for Sol IR type '%s'.".formatted(instruction.operand().type().displayName()));
    }

    private static String valueName(IrUnaryInstruction instruction) {
        return "value%d".formatted(instruction.id().index());
    }
}