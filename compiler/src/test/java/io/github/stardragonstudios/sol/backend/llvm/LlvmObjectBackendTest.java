package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmObjectBackendTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesSolIrDirectlyToHostObject() throws Exception {
        var destination =
            temporaryDirectory.resolve(
                    "native"
                )
                .resolve(
                    "answer.o"
                );

        var emitted =
            LlvmObjectBackend.emitHostObject(
                answerProgram(),
                "sol.native-object",
                destination
            );

        assertEquals(
            destination.toAbsolutePath()
                .normalize(),
            emitted
        );

        assertTrue(
            Files.isRegularFile(
                emitted
            )
        );

        assertTrue(
            Files.size(
                emitted
            )
                > 0
        );
    }

    @Test
    void acceptsExplicitTargetConfiguration() throws Exception {
        var destination =
            temporaryDirectory.resolve(
                "explicit.o"
            );

        var configuration =
            LlvmTargetConfiguration.host();

        var emitted =
            LlvmObjectBackend.emitObject(
                answerProgram(),
                "sol.explicit-object",
                configuration,
                destination
            );

        assertEquals(
            destination.toAbsolutePath()
                .normalize(),
            emitted
        );

        assertTrue(
            Files.size(
                emitted
            )
                > 0
        );
    }

    @Test
    void reportsUnsupportedExplicitTargetsWithoutCreatingOutput() {
        var destination =
            temporaryDirectory.resolve(
                "unsupported.o"
            );

        var configuration =
            new LlvmTargetConfiguration(
                "sol-invalid-unknown-none",
                "generic",
                "",
                LlvmTargetConfiguration.OptimizationLevel.DEFAULT,
                LlvmTargetConfiguration.RelocationModel.DEFAULT,
                LlvmTargetConfiguration.CodeModel.DEFAULT
            );

        var exception =
            assertThrows(
                LlvmBackendException.class,
                () ->
                    LlvmObjectBackend.emitObject(
                        answerProgram(),
                        "sol.unsupported-object",
                        configuration,
                        destination
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "sol-invalid-unknown-none"
                )
        );

        assertFalse(
            Files.exists(
                destination
            )
        );
    }

    private static IrProgram answerProgram() {
        var answer =
            new IrIntConstant(
                new IrValueId(
                    0
                ),
                42
            );

        var function =
            IrFunction.definition(
                new IrFunctionId(
                    0
                ),
                "answer",
                List.of(),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(),
                        IrReturnTerminator.returning(
                            answer
                        )
                    )
                )
            );

        return IrProgram.library(
            List.of(
                new IrModule(
                    new IrModuleName(
                        List.of(
                            "object"
                        )
                    ),
                    List.of(
                        function
                    )
                )
            )
        );
    }
}
