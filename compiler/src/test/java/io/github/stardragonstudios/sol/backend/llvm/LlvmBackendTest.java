package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmBackendTest {
    @Test
    void generatesFunctionDefinitionsAndReturnsParameters() {
        var parameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.INT);

        var identity = IrFunction.definition(
            new IrFunctionId(0),
            "identity",
            List.of(parameter),
            PrimitiveIrType.INT,
            List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(parameter)))
        );

        var noop = IrFunction.definition(
            new IrFunctionId(1),
            "noop",
            List.of(),
            PrimitiveIrType.VOID,
            List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.bare()))
        );

        var declaration = IrFunction.declaration(
            new IrFunctionId(2),
            "external",
            List.of(),
            PrimitiveIrType.INT
        );

        var program = IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("application")), List.of(identity, noop, declaration))));

        try (var module = LlvmBackend.generate(program, "sol.definitions")) {
            assertEquals(
                """
                ; ModuleID = 'sol.definitions'
                source_filename = "sol.definitions"

                define i64 @sol.function0.identity(i64 %value) {
                block0:
                  ret i64 %value
                }

                define void @sol.function1.noop() {
                block0:
                  ret void
                }

                declare i64 @sol.function2.external()
                """,
                normalizeNewlines(module.text())
            );

            module.verify();
        }
    }

    @Test
    void generatesPrimitiveConstants() {
        var integer = constantFunction(
            new IrFunctionId(0),
            "integer",
            PrimitiveIrType.INT,
            new IrIntConstant(new IrValueId(0), -42)
        );

        var floating = constantFunction(
            new IrFunctionId(1),
            "floating",
            PrimitiveIrType.FLOAT,
            new IrFloatConstant(new IrValueId(0), 1.5)
        );

        var booleanValue = constantFunction(
            new IrFunctionId(2),
            "boolean_value",
            PrimitiveIrType.BOOLEAN,
            new IrBooleanConstant(new IrValueId(0), true)
        );

        var character = constantFunction(
            new IrFunctionId(3),
            "character",
            PrimitiveIrType.CHAR,
            new IrCharConstant(new IrValueId(0), 0x1F600)
        );

        var stringValue = constantFunction(
            new IrFunctionId(4),
            "string_value",
            PrimitiveIrType.STRING,
            new IrStringConstant(new IrValueId(0), "Hello")
        );

        var program = IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("constants")), List.of(integer, floating, booleanValue, character, stringValue))));

        try (var module = LlvmBackend.generate(program, "sol.constants")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("ret i64 -42"));
            assertTrue(text.contains("ret double 1.500000e+00"));
            assertTrue(text.contains("ret i1 true"));
            assertTrue(text.contains("ret i32 128512"));
            assertTrue(text.contains("define { ptr, i64 } @sol.function4.string_value()"));
            assertTrue(text.contains("c\"Hello\\00\""));
            assertTrue(text.contains("i64 5"));

            module.verify();
        }
    }

    @Test
    void isolatesGeneratedModulesBetweenInvocations() {
        var program = IrProgram.library(List.of());

        try (
            var first = LlvmBackend.generate(program, "sol.first-generation");
            var second = LlvmBackend.generate(program, "sol.second-generation")
        ) {
            assertTrue(first.text().contains("sol.first-generation"));
            assertTrue(second.text().contains("sol.second-generation"));
            assertTrue(first.contextHandle().address() != second.contextHandle().address());
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(
            NullPointerException.class,
            () -> LlvmBackend.generate(null, "sol.invalid")
        );

        assertThrows(
            NullPointerException.class,
            () -> LlvmBackend.generate(IrProgram.library(List.of()), null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> LlvmBackend.generate(IrProgram.library(List.of()), "   ")
        );
    }

    @Test
    void measuresStringConstantsInUtf8Bytes() {
        var stringValue = constantFunction(
            new IrFunctionId(0),
            "utf8_string",
            PrimitiveIrType.STRING,
            new IrStringConstant(new IrValueId(0), "ñ")
        );

        var program = IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("strings")), List.of(stringValue))));

        try (var module = LlvmBackend.generate(program, "sol.utf8-string")) {
            var text = normalizeNewlines(module.text());

            /*
             * U+00F1 occupies two bytes in UTF-8.
             */
            assertTrue(text.contains("i64 2"));

            module.verify();
        }
    }

    @Test
    void lowersStandardConsoleFunctionsToNativeImplementations() {
        var printParameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.STRING);
        var print = IrFunction.declaration(new IrFunctionId(0), "print", List.of(printParameter), PrimitiveIrType.VOID);
        var printLineParameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.STRING);
        var printLine = IrFunction.declaration(new IrFunctionId(1), "print_line", List.of(printLineParameter), PrimitiveIrType.VOID);

        var program = IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("std", "console")), List.of(print, printLine))));

        try (var module = LlvmBackend.generate(program, "sol.console")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("declare i32 @putchar(i32)"));
            assertTrue(text.contains("define void @sol.function0.print({ ptr, i64 } %value)"));
            assertTrue(text.contains("define void @sol.function1.print_line({ ptr, i64 } %value)"));
            assertTrue(text.contains("console.condition:"));
            assertTrue(text.contains("console.body:"));
            assertTrue(text.contains("console.exit:"));

            /*
             * print_line emits ASCII LF after the string bytes.
             */
            assertTrue(text.contains("call i32 @putchar(i32 10)"));

            module.verify();
        }
    }

    private static IrFunction constantFunction(IrFunctionId identifier, String name, PrimitiveIrType returnType, IrValue value) {
        return IrFunction.definition(identifier, name, List.of(), returnType, List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(value))));
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }
}
