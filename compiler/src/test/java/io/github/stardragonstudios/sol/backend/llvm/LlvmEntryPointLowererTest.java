package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrEntryPoint;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmEntryPointLowererTest {
    @Test
    void generatesNativeMainForExecutablePrograms() {
        var result = new IrIntConstant(new IrValueId(0), 42);

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "launch",
            List.of(),
            PrimitiveIrType.INT,
            List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(result)))
        );

        var irModule = new IrModule(
            new IrModuleName(List.of("application")),
            List.of(function)
        );

        var program = IrProgram.executable(
            List.of(irModule),
            new IrEntryPoint(irModule, function)
        );

        try (var module = LlvmBackend.generate(program, "sol.executable")) {
            assertEquals(
                """
                ; ModuleID = 'sol.executable'
                source_filename = "sol.executable"

                define i64 @sol.function0.launch() {
                block0:
                  ret i64 42
                }

                define i32 @main() {
                entry:
                  %sol.entry.result = call i64 @sol.function0.launch()
                  %sol.entry.status = trunc i64 %sol.entry.result to i32
                  ret i32 %sol.entry.status
                }
                """,
                normalizeNewlines(module.text())
            );
        }
    }

    @Test
    void leavesLibraryProgramsWithoutNativeMain() {
        var result = new IrIntConstant(new IrValueId(0), 42);

        var function =
            IrFunction.definition(
                new IrFunctionId(0),
                "answer",
                List.of(),
                PrimitiveIrType.INT,
                List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(result)))
            );

        var program = IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("library")), List.of(function))));

        try (var module = LlvmBackend.generate(program, "sol.library")) {
            assertFalse(module.text().contains("@main"));
        }
    }

    @Test
    void reportsUnsupportedStartupArgumentBinding() {
        var parameter = new IrParameter(new IrValueId(0), "argument", PrimitiveIrType.INT);

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "launch",
            List.of(parameter),
            PrimitiveIrType.INT,
            List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(parameter)))
        );

        var irModule = new IrModule(new IrModuleName(List.of("application")), List.of(function));
        var program = IrProgram.executable(List.of(irModule), new IrEntryPoint(irModule, function));

        /*
         * Parameters remain structurally valid in IrEntryPoint.
         * Only the current native startup bridge rejects the
         * unsupported binding operation.
         */
        assertTrue(program.hasEntryPoint());
        assertEquals(List.of(parameter), program.entryFunction().orElseThrow().parameters());

        var exception = assertThrows(
            LlvmBackendException.class,
            () -> LlvmBackend.generate(program, "sol.parameterized-entry")
        );

        assertTrue(exception.getMessage().contains("launch"));
        assertTrue(exception.getMessage().contains("1 parameter(s)"));
        assertTrue(exception.getMessage().contains("remain valid Sol IR"));
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }
}
