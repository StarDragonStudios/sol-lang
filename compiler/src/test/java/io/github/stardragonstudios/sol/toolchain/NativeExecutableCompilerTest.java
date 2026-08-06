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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeExecutableCompilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void deletesTemporaryObjectsAfterSuccessfulLinks() {
        var output =
            temporaryDirectory
                .resolve(
                    "native output"
                )
                .resolve(
                    "program"
                );

        var layout =
            NativeExecutableLayout.host(
                output
            );

        var compiler =
            compiler(
                0
            );

        var result =
            compiler.compile(
                executableProgram(
                    42
                ),
                "sol.executable",
                output
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

        assertFalse(
            Files.exists(
                layout.objectFile()
            )
        );

        assertFalse(
            result.retainedObjectFile()
        );
    }

    @Test
    void keepsTemporaryObjectsWhenRequested() {
        var output =
            temporaryDirectory.resolve(
                "debug-program"
            );

        var layout =
            NativeExecutableLayout.host(
                output
            );

        var result =
            compiler(
                0
            ).compile(
                executableProgram(
                    0
                ),
                "sol.debug-executable",
                output,
                TemporaryObjectPolicy.KEEP
            );

        assertTrue(
            Files.isRegularFile(
                result.executable()
            )
        );

        assertTrue(
            Files.isRegularFile(
                layout.objectFile()
            )
        );

        assertTrue(
            result.retainedObjectFile()
        );
    }

    @Test
    void deletesTemporaryObjectsAfterLinkFailures() {
        var output =
            temporaryDirectory.resolve(
                "failed-program"
            );

        var layout =
            NativeExecutableLayout.host(
                output
            );

        var exception =
            assertThrows(
                NativeToolchainException.class,
                () ->
                    compiler(
                        9
                    ).compile(
                        executableProgram(
                            0
                        ),
                        "sol.failed-executable",
                        output
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "exited with code 9"
                )
        );

        assertFalse(
            Files.exists(
                layout.objectFile()
            )
        );

        assertFalse(
            Files.exists(
                layout.executable()
            )
        );
    }

    @Test
    void rejectsLibraryPrograms() {
        var compiler =
            compiler(
                0
            );

        var exception =
            assertThrows(
                NativeToolchainException.class,
                () ->
                    compiler.compile(
                        libraryProgram(),
                        "sol.library",
                        temporaryDirectory.resolve(
                            "library"
                        )
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "requires a Sol IR entry point"
                )
        );
    }

    @Test
    void rejectsInvalidConstructionAndArguments() {
        var linker =
            linker(
                0
            );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeExecutableCompiler(
                    null
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeExecutableCompiler(
                    linker,
                    null
                )
        );

        var compiler =
            new NativeExecutableCompiler(
                linker
            );

        assertThrows(
            NullPointerException.class,
            () ->
                compiler.compile(
                    null,
                    "sol.executable",
                    temporaryDirectory.resolve(
                        "program"
                    )
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                compiler.compile(
                    executableProgram(
                        0
                    ),
                    "   ",
                    temporaryDirectory.resolve(
                        "program"
                    )
                )
        );
    }

    private NativeExecutableCompiler compiler(
        int linkerExitCode
    ) {
        return new NativeExecutableCompiler(
            linker(
                linkerExitCode
            ),
            (
                program,
                moduleName,
                destination
            ) -> {
                write(
                    destination,
                    "native object"
                );

                return destination;
            }
        );
    }

    private NativeLinker linker(
        int exitCode
    ) {
        return new NativeLinker(
            new NativeLinkerDriver(
                temporaryDirectory.resolve(
                    "clang"
                )
            ),
            command -> {
                var output =
                    Path.of(
                        command.arguments()
                            .getLast()
                    );

                write(
                    output,
                    "native executable"
                );

                return new NativeProcessResult(
                    exitCode,
                    "linker output",
                    "linker error"
                );
            }
        );
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

    private static IrProgram libraryProgram() {
        var result =
            new IrIntConstant(
                new IrValueId(
                    0
                ),
                0
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
                            result
                        )
                    )
                )
            );

        return IrProgram.library(
            List.of(
                new IrModule(
                    new IrModuleName(
                        List.of(
                            "library"
                        )
                    ),
                    List.of(
                        function
                    )
                )
            )
        );
    }

    private static void write(
        Path destination,
        String content
    ) {
        try {
            var parent =
                destination.getParent();

            if (parent != null) {
                Files.createDirectories(
                    parent
                );
            }

            Files.writeString(
                destination,
                content
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(
                exception
            );
        }
    }
}
