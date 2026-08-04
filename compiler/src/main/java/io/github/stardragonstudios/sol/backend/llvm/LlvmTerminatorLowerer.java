package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBranchTerminator;
import io.github.stardragonstudios.sol.ir.IrConditionalBranchTerminator;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrTerminator;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRet;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRetVoid;

final class LlvmTerminatorLowerer {
    private LlvmTerminatorLowerer() {}

    static void lower(IrTerminator terminator, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(terminator, "Lowered Sol IR terminator must not be null.");
        Objects.requireNonNull(context, "LLVM function lowering context must not be null.");

        var lowered =
            switch (terminator) {
                case IrReturnTerminator returnTerminator -> lowerReturn(returnTerminator, context);
                case IrBranchTerminator branch -> LLVMBuildBr(context.builder(), context.block(branch.target()));
                case IrConditionalBranchTerminator branch -> LLVMBuildCondBr(
                    context.builder(),
                    context.value(branch.condition()),
                    context.block(branch.trueTarget()),
                    context.block(branch.falseTarget())
                );

                default -> throw new LlvmBackendException(
                    "Sol IR terminator implementation '%s' is not supported by the LLVM backend.".formatted(terminator.getClass().getSimpleName())
                );
            };

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to lower the terminator of Sol IR function '%s'.".formatted(context.function().id()));
    }

    private static LLVMValueRef lowerReturn(IrReturnTerminator terminator, LlvmFunctionLoweringContext context) {
        if (!terminator.returnsValue()) return LLVMBuildRetVoid(context.builder());

        return LLVMBuildRet(context.builder(), context.value(terminator.value().orElseThrow()));
    }
}
