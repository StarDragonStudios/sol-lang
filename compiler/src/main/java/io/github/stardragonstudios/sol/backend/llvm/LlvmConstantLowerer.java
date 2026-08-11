package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBooleanConstant;
import io.github.stardragonstudios.sol.ir.IrCharConstant;
import io.github.stardragonstudios.sol.ir.IrFloatConstant;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrStringConstant;
import io.github.stardragonstudios.sol.ir.IrNullConstant;
import io.github.stardragonstudios.sol.ir.IrValue;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMConstInt;
import static org.bytedeco.llvm.global.LLVM.LLVMConstReal;
import static org.bytedeco.llvm.global.LLVM.LLVMConstNull;

final class LlvmConstantLowerer {
    private LlvmConstantLowerer() {}

    static LLVMValueRef lower(IrValue value, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(value, "Lowered Sol IR constant must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        /*
         * Strings require module-level byte storage in addition to the
         * aggregate value, so their lowering is handled by the dedicated
         * string lowerer.
         */
        if (value instanceof IrStringConstant stringConstant) return LlvmStringLowerer.lower(stringConstant, context);

        var lowered = switch (value) {
            case IrIntConstant constant -> LLVMConstInt(LlvmTypeLowerer.lower(constant.type(), context.llvmContext()), constant.value(), 1);
            case IrFloatConstant constant -> LLVMConstReal(LlvmTypeLowerer.lower(constant.type(), context.llvmContext()), constant.value());
            case IrBooleanConstant constant -> LLVMConstInt(LlvmTypeLowerer.lower(constant.type(), context.llvmContext()), constant.value() ? 1 : 0, 0);
            case IrCharConstant constant -> LLVMConstInt(LlvmTypeLowerer.lower(constant.type(), context.llvmContext()), constant.codePoint(), 0);
            case IrNullConstant constant -> LLVMConstNull(LlvmTypeLowerer.lower(constant.type(), context.llvmContext()));

            default -> throw new LlvmBackendException("Sol IR value implementation '%s' is not yet supported by the LLVM backend.".formatted(value.getClass().getSimpleName()));
        };

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to lower Sol IR value '%s'.".formatted(value.id()));

        return context.registerValue(value, lowered);
    }
}
