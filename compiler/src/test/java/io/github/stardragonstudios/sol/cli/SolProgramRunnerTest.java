package io.github.stardragonstudios.sol.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolProgramRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesRunsAndRemovesTemporaryExecutable()
        throws IOException {

        var runDirectory = temporaryDirectory.resolve("run");
        var compiledCommand = new AtomicReference<CompilerCommandLine>();
        var executed = new AtomicBoolean();

        var runner = new SolProgramRunner(
            commandLine -> {
                compiledCommand.set(commandLine);

                var executable = commandLine.output().orElseThrow();

                try {
                    Files.writeString(executable, "temporary executable");
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }

                return new CompilerPipelineResult(commandLine.sourceFile(), executable, List.of());
            },
            executable -> {
                executed.set(true);

                assertTrue(Files.isRegularFile(executable));

                return 23;
            },
            () -> {
                Files.createDirectory(runDirectory);

                return runDirectory;
            }
        );

        Path path = Path.of("program.sol");

        var result = runner.run(new RunCommandLine(path));

        assertEquals(23, result.exitCode());
        assertTrue(result.diagnostics().isEmpty());
        assertTrue(executed.get());

        var commandLine = compiledCommand.get();

        assertEquals(path, commandLine.sourceFile());
        assertTrue(commandLine.output().isPresent());
        assertFalse(commandLine.keepIntermediates());
        assertFalse(Files.exists(runDirectory));
    }

    @Test
    void compilationFailureDoesNotExecuteProgram()
        throws IOException {

        var runDirectory = temporaryDirectory.resolve("failed-run");
        var executed = new AtomicBoolean();

        var runner =
            new SolProgramRunner(
                commandLine -> {
                    throw new CompilerPipelineException("Compilation failed.");
                },
                executable -> {
                    executed.set(true);

                    return 0;
                },
                () -> {
                    Files.createDirectory(runDirectory);

                    return runDirectory;
                }
            );

        assertThrows(
            CompilerPipelineException.class,
            () -> runner.run(new RunCommandLine(Path.of("invalid.sol")))
        );

        assertFalse(executed.get());
        assertFalse(Files.exists(runDirectory));
    }

    @Test
    void executionFailureStillCleansTemporaryDirectory()
        throws IOException {

        var runDirectory = temporaryDirectory.resolve("execution-failure");

        var runner =
            new SolProgramRunner(
                commandLine -> {
                    var executable =
                        commandLine.output()
                            .orElseThrow();

                    try {
                        Files.writeString(
                            executable,
                            "temporary executable"
                        );
                    } catch (IOException exception) {
                        throw new RuntimeException(
                            exception
                        );
                    }

                    return new CompilerPipelineResult(commandLine.sourceFile(), executable, List.of());
                },
                _ -> {
                    throw new ProgramExecutionException("Could not execute program.");
                },
                () -> {
                    Files.createDirectory(runDirectory);

                    return runDirectory;
                }
            );

        assertThrows(
            ProgramExecutionException.class,
            () -> runner.run(new RunCommandLine(Path.of("program.sol")))
        );

        assertFalse(Files.exists(runDirectory));
    }

    @Test
    void preservesCompilerDiagnostics() {
        var runDirectory = temporaryDirectory.resolve("diagnostic-run");
        var source = temporaryDirectory.resolve("program.sol");

        var runner = new SolProgramRunner(
            commandLine -> new CompilerPipelineResult(source, commandLine.output().orElseThrow(), List.of()),
            _ -> 7,
            () -> {
                Files.createDirectory(runDirectory);

                return runDirectory;
            }
        );

        var result = runner.run(new RunCommandLine(source));

        assertEquals(7, result.exitCode());
        assertTrue(result.diagnostics().isEmpty());
        assertFalse(Files.exists(runDirectory));
    }
}
