package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrCallInstruction;
import io.github.stardragonstudios.sol.ir.IrValueCallInstruction;
import io.github.stardragonstudios.sol.ir.IrVoidCallInstruction;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildCall2;

final class LlvmCallInstructionLowerer {
    private LlvmCallInstructionLowerer() {}

    static void lower(IrCallInstruction instruction, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(instruction, "Lowered call Sol IR instruction must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        var target = context.function(instruction.target());
        var arguments = new LLVMValueRef[instruction.arguments().size()];

        /*
         * Preserve the exact argument order already established
         * by Sol IR.
         */
        for (var index = 0; index < arguments.length; index++) arguments[index] = context.value(instruction.arguments().get(index));

        var resultName = switch (instruction) {
                case IrValueCallInstruction valueCall -> valueName(valueCall);
                case IrVoidCallInstruction ignored -> "";

                default -> throw new LlvmBackendException(
                    "Sol IR call instruction implementation '%s' is not supported by the LLVM backend.".formatted(instruction.getClass().getSimpleName())
                );
            };

        try (var nativeArguments = new PointerPointer<LLVMValueRef>(arguments)) {
            var lowered = LLVMBuildCall2(context.builder(), target.functionType(), target.value(), nativeArguments, arguments.length, resultName);

            if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to lower call to Sol IR function '%s'.".formatted(instruction.target().id()));
            if (instruction instanceof IrValueCallInstruction valueCall) context.registerValue(valueCall, lowered);
        }
    }

    private static String valueName(IrValueCallInstruction instruction) {
        return "value%d".formatted(instruction.id().index());
    }
}
