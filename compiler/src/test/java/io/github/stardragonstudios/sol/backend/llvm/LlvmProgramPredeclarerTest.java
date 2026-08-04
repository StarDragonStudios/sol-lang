package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.bytedeco.llvm.global.LLVM.LLVMGetParam;
import static org.bytedeco.llvm.global.LLVM.LLVMGetValueName2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlvmProgramPredeclarerTest {
    @Test
    void predeclaresFunctionsAcrossSolModulesInProgramOrder() {
        var first = IrFunction.declaration(
            new IrFunctionId(0),
            "calculate",
            List.of(new IrParameter(new IrValueId(0), "value", PrimitiveIrType.INT)),
            PrimitiveIrType.INT
        );

        var second = IrFunction.declaration(
            new IrFunctionId(1),
            "calculate",
            List.of(new IrParameter(new IrValueId(0), "marker", PrimitiveIrType.CHAR)),
            PrimitiveIrType.VOID
        );

        var program = IrProgram.library(
            List.of(
                new IrModule(new IrModuleName(List.of("first")), List.of(first)),
                new IrModule(new IrModuleName(List.of("second")), List.of(second))
            )
        );

        try (var module = LlvmModule.create("sol.program")) {
            var context = LlvmProgramPredeclarer.predeclare(program, module);

            assertEquals(2, context.functionCount());

            var firstHandle = context.function(first.id());
            var secondHandle = context.function(second.id());

            assertEquals("sol.function0.calculate", firstHandle.symbolName());
            assertEquals("sol.function1.calculate", secondHandle.symbolName());
            assertNotEquals(firstHandle.value().address(), secondHandle.value().address());
            assertSame(firstHandle, context.function(first.id()));
            assertEquals("value", parameterName(firstHandle.value(), 0));
            assertEquals("marker", parameterName(secondHandle.value(), 0));

            module.verify();

            assertEquals(
                """
                ; ModuleID = 'sol.program'
                source_filename = "sol.program"

                declare i64 @sol.function0.calculate(i64)

                declare void @sol.function1.calculate(i32)
                """,
                normalizeNewlines(module.text())
            );
        }
    }

    @Test
    void acceptsEmptyPrograms() {
        try (var module = LlvmModule.create("sol.empty-program")) {
            var context = LlvmProgramPredeclarer.predeclare(IrProgram.library(List.of()), module);

            assertEquals(0, context.functionCount());

            module.verify();
        }
    }

    @Test
    void rejectsInvalidInputs() {
        try (var module = LlvmModule.create("sol.invalid-program")) {
            assertThrows(
                NullPointerException.class,
                () -> LlvmProgramPredeclarer.predeclare(null, module)
            );

            assertThrows(
                NullPointerException.class,
                () -> LlvmProgramPredeclarer.predeclare(IrProgram.library(List.of()), null)
            );
        }
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }

    private static String parameterName(LLVMValueRef function, int index) {
        var parameter = LLVMGetParam(function, index);

        if (Pointer.isNull(parameter)) throw new AssertionError("LLVM function has no parameter at index %d.".formatted(index));

        try (var length = new SizeTPointer(1)) {
            var name = LLVMGetValueName2(parameter, length);
            var byteLength = length.get();

            if (Pointer.isNull(name) || byteLength == 0) return "";
            if (byteLength > Integer.MAX_VALUE) throw new AssertionError("LLVM parameter name is too large to copy.");

            var bytes = new byte[Math.toIntExact(byteLength)];

            name.get(bytes, 0, bytes.length);

            return new String(
                bytes,
                StandardCharsets.UTF_8
            );
        }
    }
}
