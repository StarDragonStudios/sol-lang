package io.github.stardragonstudios.sol.toolchain;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrEntryPoint;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeExecutableIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void emitsLinksAndRunsHostNativeExecutables()
        throws IOException, InterruptedException {

        var driver =
            discoverLinkerOrSkip();

        var requestedOutput =
            temporaryDirectory
                .resolve(
                    "directory containing spaces"
                )
                .resolve(
                    "sol-answer"
                );

        var layout =
            NativeExecutableLayout.host(
                requestedOutput
            );

        var compiler =
            new NativeExecutableCompiler(
                new NativeLinker(
                    driver
                )
            );

        var result =
            compiler.compile(
                executableProgram(
                    42
                ),
                "sol.native-executable",
                requestedOutput
            );

        assertEquals(
            layout.executable(),
            result.executable()
        );

        assertTrue(
            Files.isRegularFile(
                result.executable()
            )
        );

        assertTrue(
            Files.size(
                result.executable()
            )
                > 0
        );

        assertFalse(
            Files.exists(
                layout.objectFile()
            )
        );

        var process =
            new ProcessBuilder(
                result.executable()
                    .toString()
            ).start();

        process.getOutputStream()
            .close();

        var exitCode =
            process.waitFor();

        assertEquals(
            42,
            exitCode
        );
    }

    private static NativeLinkerDriver discoverLinkerOrSkip() {
        try {
            return NativeLinkerDiscovery.discover();
        } catch (NativeToolchainException exception) {
            Assumptions.assumeTrue(
                false,
                exception.getMessage()
            );

            throw new AssertionError(
                exception
            );
        }
    }

    private static IrProgram executableProgram(
        int exitCode
    ) {
        var result =
            new IrIntConstant(
                new IrValueId(
                    0
                ),
                exitCode
            );

        var function =
            IrFunction.definition(
                new IrFunctionId(
                    0
                ),
                "launch",
                List.of(),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(),
                        IrReturnTerminator.returning(
                            result
                        )
                    )
                )
            );

        var module =
            new IrModule(
                new IrModuleName(
                    List.of(
                        "application"
                    )
                ),
                List.of(
                    function
                )
            );

        return IrProgram.executable(
            List.of(
                module
            ),
            new IrEntryPoint(
                module,
                function
            )
        );
    }
}
