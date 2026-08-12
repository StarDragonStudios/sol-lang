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
            assertTrue(text.contains("define { ptr, i64, i64 } @sol.function4.string_value()"));
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
            assertTrue(text.contains("define void @sol.function0.print({ ptr, i64, i64 } %value)"));
            assertTrue(text.contains("define void @sol.function1.print_line({ ptr, i64, i64 } %value)"));
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

    @Test
    void lowersStandardFileExistsToNativeImplementation() {
        var pathParameter = new IrParameter(new IrValueId(0), "path", PrimitiveIrType.STRING);
        var exists = IrFunction.declaration(new IrFunctionId(0), "exists", List.of(pathParameter), PrimitiveIrType.BOOLEAN);
        var program = IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("std", "file")), List.of(exists))));

        try (var module = LlvmBackend.generate(program, "sol.file")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("declare ptr @fopen(ptr, ptr)"));
            assertTrue(text.contains("declare i32 @fclose(ptr)"));
            assertTrue(text.contains("define i1 @sol.function0.exists({ ptr, i64, i64 } %path)"));
            assertTrue(text.contains("c\"rb\\00\""));
            assertTrue(text.contains("@llvm.memcpy"));
            assertTrue(text.contains("store i8 0"));
            assertTrue(text.contains("file.opened:"));
            assertTrue(text.contains("file.missing:"));
            assertTrue(text.contains("call ptr @fopen"));
            assertTrue(text.contains("call i32 @fclose"));

            module.verify();
        }
    }

    @Test
    void lowersStandardFileWriteFunctionsToNativeImplementations() {
        var writePath = new IrParameter(new IrValueId(0), "path", PrimitiveIrType.STRING);
        var writeContent = new IrParameter(new IrValueId(1), "content", PrimitiveIrType.STRING);
        var writeText = IrFunction.declaration(new IrFunctionId(0), "write_text", List.of(writePath, writeContent), PrimitiveIrType.BOOLEAN);
        var appendPath = new IrParameter(new IrValueId(0), "path", PrimitiveIrType.STRING);
        var appendContent = new IrParameter(new IrValueId(1), "content", PrimitiveIrType.STRING);
        var appendText = IrFunction.declaration(new IrFunctionId(1), "append_text", List.of(appendPath, appendContent), PrimitiveIrType.BOOLEAN);
        var program = IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("std", "file")), List.of(writeText, appendText))));

        try (var module = LlvmBackend.generate(program, "sol.file-write")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("declare i64 @fwrite(ptr, i64, i64, ptr)"));
            assertTrue(text.contains("define i1 @sol.function0.write_text({ ptr, i64, i64 } %path, { ptr, i64, i64 } %content)"));
            assertTrue(text.contains("define i1 @sol.function1.append_text({ ptr, i64, i64 } %path, { ptr, i64, i64 } %content)"));
            assertTrue(text.contains("c\"wb\\00\""));
            assertTrue(text.contains("c\"ab\\00\""));
            assertTrue(text.contains("call i64 @fwrite"));
            assertTrue(text.contains("all_bytes_written"));
            assertTrue(text.contains("file_write_succeeded"));
            assertTrue(text.contains("file.open_failed:"));

            module.verify();
        }
    }

    @Test
    void lowersStandardTextInputFunctionsWithStrictUtf8Validation() {
        var readPath = new IrParameter(new IrValueId(0), "path", PrimitiveIrType.STRING);
        var readText = IrFunction.declaration(new IrFunctionId(0), "read_text", List.of(readPath), PrimitiveIrType.STRING);
        var readLine = IrFunction.declaration(new IrFunctionId(1), "read_line", List.of(), PrimitiveIrType.STRING);
        var program = IrProgram.library(List.of(
            new IrModule(new IrModuleName(List.of("std", "file")), List.of(readText)),
            new IrModule(new IrModuleName(List.of("std", "console")), List.of(readLine))
        ));

        try (var module = LlvmBackend.generate(program, "sol.text-input")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("define { ptr, i64, i64 } @sol.function0.read_text"));
            assertTrue(text.contains("define { ptr, i64, i64 } @sol.function1.read_line()"));
            assertTrue(text.contains("declare i32 @fgetc(ptr)"));
            assertTrue(text.contains("declare i32 @getchar()"));
            assertTrue(text.contains("file.read.open_failed:"));
            assertTrue(text.contains("console.read.eof_without_data:"));
            assertTrue(text.contains("utf8.invalid:"));
            assertTrue(text.contains("text input is not valid UTF-8"));

            module.verify();
        }
    }

    @Test
    void lowersTypedPointerLoadsStoresAndIndexingThroughOpaqueNativePointers() {
        var pointerType = new IrPointerType(PrimitiveIrType.INT);
        var pointer = new IrParameter(new IrValueId(0), "pointer", pointerType);
        var index = new IrParameter(new IrValueId(1), "index", PrimitiveIrType.INT);
        var indexed = new IrPointerIndexLoadInstruction(new IrValueId(2), pointer, index);
        var direct = new IrPointerLoadInstruction(new IrValueId(3), pointer);
        var function = IrFunction.definition(
            new IrFunctionId(0),
            "read_and_store",
            List.of(pointer, index),
            PrimitiveIrType.INT,
            List.of(new IrBasicBlock(
                new IrBlockId(0),
                List.of(
                    indexed,
                    new IrPointerIndexStoreInstruction(pointer, index, indexed),
                    direct,
                    new IrPointerStoreInstruction(pointer, direct)
                ),
                IrReturnTerminator.returning(indexed)
            ))
        );
        var program = IrProgram.library(List.of(new IrModule(
            new IrModuleName(List.of("memory")),
            List.of(function)
        )));

        try (var module = LlvmBackend.generate(program, "sol.pointer-operations")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("define i64 @sol.function0.read_and_store(ptr %pointer, i64 %index)"));
            assertTrue(text.contains("getelementptr i64, ptr %pointer, i64 %index"));
            assertTrue(text.contains("load i64, ptr"));
            assertTrue(text.contains("store i64"));
            module.verify();
        }
    }

    @Test
    void lowersUtf8StringIndexConcatenationAndEqualityThroughSharedRuntime() {
        var left = new IrParameter(new IrValueId(0), "left", PrimitiveIrType.STRING);
        var right = new IrParameter(new IrValueId(1), "right", PrimitiveIrType.STRING);
        var index = new IrParameter(new IrValueId(2), "index", PrimitiveIrType.INT);
        var concatenated = new IrBinaryInstruction(new IrValueId(3), IrBinaryOperator.ADD, left, right);
        var equal = new IrBinaryInstruction(new IrValueId(4), IrBinaryOperator.EQUAL, concatenated, left);
        var scalar = new IrStringIndexInstruction(new IrValueId(5), concatenated, index);
        var function = IrFunction.definition(
            new IrFunctionId(0),
            "inspect",
            List.of(left, right, index),
            PrimitiveIrType.CHAR,
            List.of(new IrBasicBlock(
                new IrBlockId(0),
                List.of(concatenated, equal, scalar),
                IrReturnTerminator.returning(scalar)
            ))
        );
        var program = IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("strings")), List.of(function))));

        try (var module = LlvmBackend.generate(program, "sol.string-operations")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("define internal { ptr, i64, i64 } @sol.runtime.string.concat"));
            assertTrue(text.contains("define internal i1 @sol.runtime.string.equal"));
            assertTrue(text.contains("define internal i32 @sol.runtime.string.index"));
            assertTrue(text.contains("define internal i64 @sol.runtime.string.byte_offset"));
            assertTrue(text.contains("call ptr @malloc(i64"));
            assertTrue(text.contains("call i32 @memcmp"));
            assertTrue(text.contains("Sol runtime error: string index out of bounds."));
            module.verify();
        }
    }

    @Test
    void lowersStandardStringFunctionsToUnicodeScalarRuntime() {
        var value = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.STRING);
        var length = IrFunction.declaration(new IrFunctionId(0), "length", List.of(value), PrimitiveIrType.INT);
        var sliceValue = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.STRING);
        var sliceStart = new IrParameter(new IrValueId(1), "start", PrimitiveIrType.INT);
        var sliceEnd = new IrParameter(new IrValueId(2), "end_index", PrimitiveIrType.INT);
        var slice = IrFunction.declaration(new IrFunctionId(1), "slice", List.of(sliceValue, sliceStart, sliceEnd), PrimitiveIrType.STRING);
        var substringValue = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.STRING);
        var substringStart = new IrParameter(new IrValueId(1), "start", PrimitiveIrType.INT);
        var substringCount = new IrParameter(new IrValueId(2), "count", PrimitiveIrType.INT);
        var substring = IrFunction.declaration(
            new IrFunctionId(2), "substring", List.of(substringValue, substringStart, substringCount), PrimitiveIrType.STRING
        );
        var program = IrProgram.library(List.of(new IrModule(
            new IrModuleName(List.of("std", "string")),
            List.of(length, slice, substring)
        )));

        try (var module = LlvmBackend.generate(program, "sol.standard-string")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("define i64 @sol.function0.length({ ptr, i64, i64 } %value)"));
            assertTrue(text.contains("define { ptr, i64, i64 } @sol.function1.slice"));
            assertTrue(text.contains("define { ptr, i64, i64 } @sol.function2.substring"));
            assertTrue(text.contains("@sol.runtime.string.slice"));
            assertTrue(text.contains("@sol.runtime.string.substring"));
            module.verify();
        }
    }

    @Test
    void lowersStandardMemorySpecializationsToCheckedHostAllocationCalls() {
        var pointerType = new IrPointerType(PrimitiveIrType.INT);
        var allocateCount = new IrParameter(new IrValueId(0), "count", PrimitiveIrType.INT);
        var allocate = IrFunction.declaration(
            new IrFunctionId(0), "allocate$int", List.of(allocateCount), pointerType
        );
        var reallocatePointer = new IrParameter(new IrValueId(0), "value", pointerType);
        var reallocateCount = new IrParameter(new IrValueId(1), "count", PrimitiveIrType.INT);
        var reallocate = IrFunction.declaration(
            new IrFunctionId(1), "reallocate$int", List.of(reallocatePointer, reallocateCount), pointerType
        );
        var freePointer = new IrParameter(new IrValueId(0), "value", pointerType);
        var free = IrFunction.declaration(
            new IrFunctionId(2), "free$int", List.of(freePointer), PrimitiveIrType.VOID
        );
        var program = IrProgram.library(List.of(new IrModule(
            new IrModuleName(List.of("std", "memory")),
            List.of(allocate, reallocate, free)
        )));

        try (var module = LlvmBackend.generate(program, "sol.memory")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("declare ptr @malloc(i64)"));
            assertTrue(text.contains("declare ptr @realloc(ptr, i64)"));
            assertTrue(text.contains("declare void @free(ptr)"));
            assertTrue(text.contains("allocate$int") && text.contains("i64 %count"));
            assertTrue(text.contains("reallocate$int") && text.contains("ptr %value, i64 %count"));
            assertTrue(text.contains("free$int") && text.contains("ptr %value"));
            assertTrue(text.contains("memory.size_check:"));
            assertTrue(text.contains("allocation_fits"));
            assertTrue(text.contains("reallocation_fits"));
            assertTrue(text.contains("ret ptr null"));
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
