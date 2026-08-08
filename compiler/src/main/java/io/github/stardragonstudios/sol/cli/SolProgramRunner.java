package io.github.stardragonstudios.sol.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class SolProgramRunner {
    private final RunCompilationStage compilation;
    private final ProgramExecutionStage execution;
    private final RunTemporaryDirectoryFactory temporaryDirectories;

    public SolProgramRunner() {
        this(
            new CompilerPipeline()::compile,
            SolProgramRunner::executeProgram,
            () -> Files.createTempDirectory("sol-run-")
        );
    }

    SolProgramRunner(RunCompilationStage compilation, ProgramExecutionStage execution, RunTemporaryDirectoryFactory temporaryDirectories) {
        this.compilation = Objects.requireNonNull(compilation, "Run compilation stage must not be null.");
        this.execution = Objects.requireNonNull(execution, "Program execution stage must not be null.");
        this.temporaryDirectories = Objects.requireNonNull(temporaryDirectories, "Run temporary directory factory must not be null.");
    }

    public ProgramRunResult run(RunCommandLine commandLine) {
        Objects.requireNonNull(commandLine, "Run command line must not be null.");

        var temporaryDirectory = createTemporaryDirectory();

        Throwable primaryFailure = null;

        try {
            var output = temporaryDirectory.resolve("program");
            var compilerCommandLine = new CompilerCommandLine(commandLine.sourceFile(), Optional.of(output), false);
            var compilationResult = compilation.compile(compilerCommandLine);
            var exitCode = execution.execute(compilationResult.executable());

            return new ProgramRunResult(exitCode, compilationResult.diagnostics());
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                deleteTemporaryDirectory(temporaryDirectory);
            } catch (ProgramExecutionException cleanupFailure) {
                /*
                 * Never replace a compilation or execution failure with a
                 * secondary cleanup failure. Preserve the original failure
                 * and attach cleanup information to it instead.
                 */
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }

    private Path createTemporaryDirectory() {
        try {
            return temporaryDirectories.create().toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new ProgramExecutionException("Failed to create temporary directory for Sol program execution.", exception);
        }
    }

    private static int executeProgram(Path executable) {
        Objects.requireNonNull(executable, "Executed Sol program must not be null.");

        final Process process;

        try {
            process = new ProcessBuilder(executable.toString()).inheritIO().start();
        } catch (IOException exception) {
            throw new ProgramExecutionException("Failed to start compiled Sol program '%s'.".formatted(executable), exception);
        }

        try {
            return process.waitFor();
        } catch (InterruptedException exception) {
            terminateInterruptedProcess(process);

            Thread.currentThread().interrupt();

            throw new ProgramExecutionException("Interrupted while waiting for compiled Sol program '%s'.".formatted(executable), exception);
        }
    }

    private static void deleteTemporaryDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) return;

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(SolProgramRunner::deleteTemporaryPath);
        } catch (IOException | UncheckedIOException exception) {
            throw new ProgramExecutionException("Failed to clean temporary Sol run directory '%s'.".formatted(directory), exception);
        }
    }

    private static void deleteTemporaryPath(Path path) {
        try {
            Files.deleteIfExists(
                path
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(
                exception
            );
        }
    }

    private static void terminateInterruptedProcess(Process process) {
        process.destroyForcibly();

        /*
         * destroyForcibly() requests termination but does not guarantee that
         * the process has already exited when it returns. Wait until the child
         * is actually gone before the surrounding finally block removes the
         * temporary executable. This matters especially on hosts that keep a
         * running executable locked.
         *
         * The original interruption is restored by executeProgram after this
         * cleanup completes.
         */
        while (process.isAlive()) {
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {
                /*
                 * Cleanup must finish before restoring the interrupted state.
                 * A subsequent interruption does not change that requirement.
                 */
            }
        }
    }
}

@FunctionalInterface
interface RunCompilationStage {
    CompilerPipelineResult compile(CompilerCommandLine commandLine);
}

@FunctionalInterface
interface ProgramExecutionStage {
    int execute(Path executable);
}

@FunctionalInterface
interface RunTemporaryDirectoryFactory {
    Path create() throws IOException;
}
