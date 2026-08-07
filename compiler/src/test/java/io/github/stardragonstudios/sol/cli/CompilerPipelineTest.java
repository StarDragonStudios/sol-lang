package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.toolchain.TemporaryObjectPolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerPipelineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesSourceThroughTypedIrToNativeStage()
        throws IOException {

        var source =
            writeSource(
                "program.sol",
                """
                @init
                fn launch() -> int
                    return 42
                end
                """
            );

        var receivedProgram =
            new AtomicReference<IrProgram>();

        var receivedModuleName =
            new AtomicReference<String>();

        var receivedOutput =
            new AtomicReference<Path>();

        var receivedPolicy =
            new AtomicReference<TemporaryObjectPolicy>();

        var pipeline =
            new CompilerPipeline(
                (
                    program,
                    moduleName,
                    output,
                    objectPolicy
                ) -> {
                    receivedProgram.set(
                        program
                    );

                    receivedModuleName.set(
                        moduleName
                    );

                    receivedOutput.set(
                        output
                    );

                    receivedPolicy.set(
                        objectPolicy
                    );

                    return output;
                }
            );

        var result =
            pipeline.compile(
                new CompilerCommandLine(
                    source,
                    Optional.empty(),
                    false
                )
            );

        assertTrue(
            receivedProgram.get()
                .hasEntryPoint()
        );

        assertEquals(
            "program",
            receivedModuleName.get()
        );

        assertEquals(
            temporaryDirectory
                .resolve(
                    "program"
                )
                .toAbsolutePath()
                .normalize(),
            receivedOutput.get()
        );

        assertEquals(
            TemporaryObjectPolicy.DELETE,
            receivedPolicy.get()
        );

        assertEquals(
            source.toAbsolutePath()
                .normalize(),
            result.sourceFile()
        );

        assertEquals(
            receivedOutput.get(),
            result.executable()
        );

        assertTrue(
            result.diagnostics()
                .isEmpty()
        );
    }

    @Test
    void forwardsExplicitOutputAndIntermediateRetention()
        throws IOException {

        var source =
            writeSource(
                "program.sol",
                """
                @init
                fn launch() -> int
                    return 0
                end
                """
            );

        var requestedOutput =
            temporaryDirectory
                .resolve(
                    "native output"
                )
                .resolve(
                    "custom"
                );

        var receivedPolicy =
            new AtomicReference<TemporaryObjectPolicy>();

        var receivedOutput =
            new AtomicReference<Path>();

        var pipeline =
            new CompilerPipeline(
                (
                    program,
                    moduleName,
                    output,
                    objectPolicy
                ) -> {
                    receivedOutput.set(
                        output
                    );

                    receivedPolicy.set(
                        objectPolicy
                    );

                    return output;
                }
            );

        pipeline.compile(
            new CompilerCommandLine(
                source,
                Optional.of(
                    requestedOutput
                ),
                true
            )
        );

        assertEquals(
            requestedOutput
                .toAbsolutePath()
                .normalize(),
            receivedOutput.get()
        );

        assertEquals(
            TemporaryObjectPolicy.KEEP,
            receivedPolicy.get()
        );
    }

    @Test
    void lexicalErrorsPreventNativeCompilation()
        throws IOException {

        var source =
            writeSource(
                "invalid.sol",
                "$"
            );

        var invoked =
            new AtomicBoolean();

        var pipeline =
            pipelineRecordingInvocation(
                invoked
            );

        var exception =
            assertThrows(
                FrontendCompilationException.class,
                () ->
                    pipeline.compile(
                        new CompilerCommandLine(
                            source,
                            Optional.empty(),
                            false
                        )
                    )
            );

        assertFalse(
            invoked.get()
        );

        assertEquals(
            "SOL-L001",
            exception.diagnostics()
                .getFirst()
                .diagnostic()
                .code()
        );

        assertEquals(
            source.toAbsolutePath()
                .normalize(),
            exception.diagnostics()
                .getFirst()
                .sourceFile()
        );
    }

    @Test
    void parsingErrorsPreventNativeCompilation()
        throws IOException {

        var source =
            writeSource(
                "invalid.sol",
                """
                fn launch(
                """
            );

        var invoked =
            new AtomicBoolean();

        var exception =
            assertThrows(
                FrontendCompilationException.class,
                () ->
                    pipelineRecordingInvocation(
                        invoked
                    ).compile(
                        new CompilerCommandLine(
                            source,
                            Optional.empty(),
                            false
                        )
                    )
            );

        assertFalse(
            invoked.get()
        );

        assertTrue(
            exception.diagnostics()
                .getFirst()
                .diagnostic()
                .code()
                .startsWith(
                    "SOL-P"
                )
        );
    }

    @Test
    void semanticErrorsPreventNativeCompilation()
        throws IOException {

        var source =
            writeSource(
                "missing-init.sol",
                """
                fn answer() -> int
                    return 42
                end
                """
            );

        var invoked =
            new AtomicBoolean();

        var exception =
            assertThrows(
                FrontendCompilationException.class,
                () ->
                    pipelineRecordingInvocation(
                        invoked
                    ).compile(
                        new CompilerCommandLine(
                            source,
                            Optional.empty(),
                            false
                        )
                    )
            );

        assertFalse(
            invoked.get()
        );

        assertTrue(
            exception.diagnostics()
                .stream()
                .anyMatch(
                    diagnostic ->
                        diagnostic.diagnostic()
                            .code()
                            .equals(
                                "SOL-S023"
                            )
                )
        );
    }

    @Test
    void rejectsMissingSourceFile() {
        var invoked =
            new AtomicBoolean();

        var source =
            temporaryDirectory.resolve(
                "missing.sol"
            );

        assertThrows(
            CompilerPipelineException.class,
            () ->
                pipelineRecordingInvocation(
                    invoked
                ).compile(
                    new CompilerCommandLine(
                        source,
                        Optional.empty(),
                        false
                    )
                )
        );

        assertFalse(
            invoked.get()
        );
    }

    @Test
    void rejectsNonSolSourceBeforeNativeCompilation()
        throws IOException {

        var source =
            writeSource(
                "program.txt",
                """
                @init
                fn launch() -> int
                    return 0
                end
                """
            );

        var invoked =
            new AtomicBoolean();

        assertThrows(
            CompilerPipelineException.class,
            () ->
                pipelineRecordingInvocation(
                    invoked
                ).compile(
                    new CompilerCommandLine(
                        source,
                        Optional.empty(),
                        false
                    )
                )
        );

        assertFalse(
            invoked.get()
        );
    }

    private CompilerPipeline pipelineRecordingInvocation(
        AtomicBoolean invoked
    ) {
        return new CompilerPipeline(
            (
                program,
                moduleName,
                output,
                objectPolicy
            ) -> {
                invoked.set(
                    true
                );

                return output;
            }
        );
    }

    private Path writeSource(
        String name,
        String source
    ) throws IOException {
        var file =
            temporaryDirectory.resolve(
                name
            );

        Files.writeString(
            file,
            source
        );

        return file;
    }
}
