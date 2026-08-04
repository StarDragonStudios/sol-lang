package io.github.stardragonstudios.sol.backend.llvm;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

record LlvmFunctionHandle(String symbolName, LLVMTypeRef functionType, LLVMValueRef value) {
    LlvmFunctionHandle {
        Objects.requireNonNull(symbolName, "LLVM function symbol name must not be null.");
        Objects.requireNonNull(functionType, "LLVM function type must not be null.");
        Objects.requireNonNull(value, "LLVM function value must not be null.");

        if (symbolName.isBlank()) throw new IllegalArgumentException("LLVM function symbol name must not be blank.");
        if (Pointer.isNull(functionType)) throw new LlvmBackendException("LLVM function type must not be a null native pointer.");
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM function value must not be a null native pointer.");
    }
}