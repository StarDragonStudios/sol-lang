package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrParameter;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlvmFunctionDeclarationLowererTest {
    @Test
    void declaresTypedFunctionsAndNamesParameters() {
        var function = IrFunction.declaration(
            new IrFunctionId(0),
            "compare",
            List.of(
                new IrParameter(new IrValueId(0), "left", PrimitiveIrType.INT),
                new IrParameter(new IrValueId(1), "right", PrimitiveIrType.FLOAT)
            ),
            PrimitiveIrType.BOOLEAN
        );

        try (var module = LlvmModule.create("sol.functions")) {
            var context = new LlvmProgramLoweringContext(module);
            var lowered = LlvmFunctionDeclarationLowerer.lower(function, context);

            assertEquals("sol.function0.compare", lowered.symbolName());
            assertSame(lowered, context.function(function.id()));
            assertEquals(1, context.functionCount());
            assertEquals("left", parameterName(lowered.value(), 0));
            assertEquals("right", parameterName(lowered.value(), 1));

            module.verify();

            assertEquals(
                """
                ; ModuleID = 'sol.functions'
                source_filename = "sol.functions"

                declare i1 @sol.function0.compare(i64, double)
                """,
                normalizeNewlines(module.text())
            );
        }
    }

    @Test
    void rejectsDuplicateFunctionLoweringBeforeMutatingModule() {
        var function = IrFunction.declaration(
            new IrFunctionId(0),
            "calculate",
            List.of(),
            PrimitiveIrType.INT
        );

        try (var module = LlvmModule.create("sol.duplicates")) {
            var context = new LlvmProgramLoweringContext(module);

            LlvmFunctionDeclarationLowerer.lower(function, context);

            var exception = assertThrows(
                LlvmBackendException.class,
                () -> LlvmFunctionDeclarationLowerer.lower(function, context)
            );

            assertEquals("Sol IR function 'function0' already has an LLVM declaration.", exception.getMessage());
            assertEquals(1, context.functionCount());
        }
    }

    @Test
    void rejectsUnsupportedSignatureTypes() {
        var function = IrFunction.declaration(
            new IrFunctionId(0),
            "write",
            List.of(new IrParameter(new IrValueId(0), "text", PrimitiveIrType.STRING)),
            PrimitiveIrType.VOID
        );

        try (var module = LlvmModule.create("sol.unsupported")) {
            var context = new LlvmProgramLoweringContext(module);
            var exception = assertThrows(
                LlvmBackendException.class,
                () -> LlvmFunctionDeclarationLowerer.lower(function, context)
            );

            assertEquals("Sol IR type 'string' is not supported by the current LLVM backend.", exception.getMessage());
            assertEquals(0, context.functionCount());
        }
    }

    @Test
    void rejectsInvalidContextLookups() {
        try (var module = LlvmModule.create("sol.lookups")) {
            var context = new LlvmProgramLoweringContext(module);

            assertThrows(
                LlvmBackendException.class,
                () -> context.function(new IrFunctionId(99))
            );

            assertThrows(
                NullPointerException.class,
                () -> context.function(null)
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
