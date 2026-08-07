package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;

import org.junit.jupiter.api.Test;

import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage;
import static org.bytedeco.llvm.global.LLVM.LLVMPrintTypeToString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlvmTypeLowererTest {
    @Test
    void lowersSupportedPrimitiveTypes() {
        try (var module = LlvmModule.create("sol.types")) {
            assertEquals("i64", printType(LlvmTypeLowerer.lower(PrimitiveIrType.INT, module.contextHandle())));
            assertEquals("double", printType(LlvmTypeLowerer.lower(PrimitiveIrType.FLOAT, module.contextHandle())));
            assertEquals("i1", printType(LlvmTypeLowerer.lower(PrimitiveIrType.BOOLEAN, module.contextHandle())));
            assertEquals("i32", printType(LlvmTypeLowerer.lower(PrimitiveIrType.CHAR, module.contextHandle())));
            assertEquals("{ ptr, i64 }", printType(LlvmTypeLowerer.lower(PrimitiveIrType.STRING, module.contextHandle())));
            assertEquals("void", printType(LlvmTypeLowerer.lower(PrimitiveIrType.VOID, module.contextHandle())));
        }
    }



    @Test
    void rejectsInvalidInputs() {
        try (var module = LlvmModule.create("sol.invalid-types")) {
            assertThrows(
                NullPointerException.class,
                () -> LlvmTypeLowerer.lower(null, module.contextHandle())
            );

            assertThrows(
                NullPointerException.class,
                () -> LlvmTypeLowerer.lower(PrimitiveIrType.INT, null)
            );
        }
    }

    private static String printType(LLVMTypeRef type) {
        BytePointer nativeText = LLVMPrintTypeToString(type);

        if (Pointer.isNull(nativeText)) throw new AssertionError("LLVM failed to print a type during testing.");

        try {
            return nativeText.getString();
        } finally {
            LLVMDisposeMessage(nativeText);

            nativeText.setNull();
        }
    }
}
