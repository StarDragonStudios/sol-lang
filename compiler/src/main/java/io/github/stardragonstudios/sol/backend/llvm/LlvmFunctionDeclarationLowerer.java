package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMAddFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMFunctionType;
import static org.bytedeco.llvm.global.LLVM.LLVMGetParam;
import static org.bytedeco.llvm.global.LLVM.LLVMSetValueName2;

final class LlvmFunctionDeclarationLowerer {
    private LlvmFunctionDeclarationLowerer() {}

    static LlvmFunctionHandle lower(IrFunction function, LlvmProgramLoweringContext context) {
        Objects.requireNonNull(function, "Lowered Sol IR function must not be null.");
        Objects.requireNonNull(context, "LLVM program lowering context must not be null.");

        /*
         * Check before mutating the native LLVM module. A duplicate
         * lowering attempt must not create an extra LLVM function.
         */
        context.ensureFunctionUnassigned(function);

        var llvmContext = context.module().contextHandle();
        var returnType = LlvmTypeLowerer.lower(function.returnType(), llvmContext);
        var parameterTypes = new LLVMTypeRef[function.parameters().size()];

        for (var index = 0; index < parameterTypes.length; index++) parameterTypes[index] = LlvmTypeLowerer.lower(function.parameters().get(index).type(), llvmContext);

        try (var nativeParameterTypes = new PointerPointer<LLVMTypeRef>(parameterTypes)) {
            var functionType = LLVMFunctionType(returnType, nativeParameterTypes, parameterTypes.length, 0);

            if (Pointer.isNull(functionType)) throw new LlvmBackendException("LLVM failed to create the type of Sol IR function '%s'.".formatted(function.id()));

            var symbolName = symbolName(function);
            var loweredFunction = LLVMAddFunction(context.module().moduleHandle(), symbolName, functionType);

            if (Pointer.isNull(loweredFunction)) throw new LlvmBackendException("LLVM failed to declare Sol IR function '%s'.".formatted(function.id()));

            nameParameters(function, loweredFunction);

            return context.registerFunction(function, new LlvmFunctionHandle(symbolName, functionType, loweredFunction));
        }
    }

    private static void nameParameters(IrFunction function, LLVMValueRef loweredFunction) {
        for (var index = 0; index < function.parameters().size(); index++) {
            var parameter = function.parameters().get(index);
            var loweredParameter = LLVMGetParam(loweredFunction, index);

            if (Pointer.isNull(loweredParameter)) throw new LlvmBackendException("LLVM function '%s' has no parameter at index %d.".formatted(function.id(), index));

            LLVMSetValueName2(loweredParameter, parameter.name(), utf8Length(parameter.name()));
        }
    }

    private static String symbolName(IrFunction function) {
        return "sol.%s.%s".formatted(function.id(), function.name());
    }

    private static long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}