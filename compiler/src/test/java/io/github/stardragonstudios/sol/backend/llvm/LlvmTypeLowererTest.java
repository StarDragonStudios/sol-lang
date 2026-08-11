package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrStructField;
import io.github.stardragonstudios.sol.ir.IrStructType;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;

import org.junit.jupiter.api.Test;

import java.util.List;

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
            assertEquals("{ ptr, i64, i64 }", printType(LlvmTypeLowerer.lower(PrimitiveIrType.STRING, module.contextHandle())));
            assertEquals("void", printType(LlvmTypeLowerer.lower(PrimitiveIrType.VOID, module.contextHandle())));
        }
    }

    @Test
    void lowersStructFieldsInDeclarationOrderAndSupportsEmptyStructs() {
        var nested = new IrStructType(
            "application::Position",
            List.of(
                new IrStructField("line", PrimitiveIrType.INT, 0),
                new IrStructField("column", PrimitiveIrType.CHAR, 1)
            )
        );
        var span = new IrStructType(
            "application::Span",
            List.of(
                new IrStructField("start", nested, 0),
                new IrStructField("valid", PrimitiveIrType.BOOLEAN, 1)
            )
        );
        var marker = new IrStructType("application::Marker", List.of());

        try (var module = LlvmModule.create("sol.struct-types")) {
            assertEquals("{ { i64, i32 }, i1 }", printType(LlvmTypeLowerer.lower(span, module.contextHandle())));
            assertEquals("{}", printType(LlvmTypeLowerer.lower(marker, module.contextHandle())));
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
