package io.github.stardragonstudios.sol.toolchain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class NativeProcessExecutor {
    private NativeProcessExecutor() {}

    public static NativeProcessResult execute(NativeLinkCommand command) {
        Objects.requireNonNull(command, "Executed native link command must not be null.");

        var process = start(command);

        try {
            process.getOutputStream().close();
        } catch (IOException exception) {
            process.destroyForcibly();

            throw new NativeToolchainException("Failed to close the native linker standard input.", exception);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var standardOutput = executor.submit(() -> read(process.getInputStream()));
            var standardError = executor.submit(() -> read(process.getErrorStream()));
            var exitCode = waitFor(process);

            return new NativeProcessResult(
                exitCode,
                await(standardOutput, process, "standard output"),
                await(standardError, process, "standard error")
            );
        }
    }

    private static Process start(NativeLinkCommand command) {
        try {
            return new ProcessBuilder(command.arguments()).start();
        } catch (IOException exception) {
            throw new NativeToolchainException("Failed to start native linker command with executable '%s'.".formatted(command.arguments().getFirst()), exception);
        }
    }

    private static int waitFor(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();

            throw new NativeToolchainException("Interrupted while waiting for the native linker process.", exception);
        }
    }

    private static String await(Future<String> output, Process process, String streamName) {
        try {
            return output.get();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();

            throw new NativeToolchainException("Interrupted while reading native linker %s.".formatted(streamName), exception);
        } catch (ExecutionException exception) {
            process.destroyForcibly();

            var cause = exception.getCause();

            throw new NativeToolchainException("Failed to read native linker %s.".formatted(streamName), cause == null ? exception : cause);
        }
    }

    private static String read(InputStream stream) throws IOException {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
