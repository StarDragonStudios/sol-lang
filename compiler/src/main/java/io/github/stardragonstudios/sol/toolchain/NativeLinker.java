package io.github.stardragonstudios.sol.toolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class NativeLinker {
    private final NativeLinkerDriver driver;

    private final Function<NativeLinkCommand, NativeProcessResult> executor;

    public NativeLinker(NativeLinkerDriver driver) {
        this(driver, NativeProcessExecutor::execute);
    }

    NativeLinker(NativeLinkerDriver driver, Function<NativeLinkCommand, NativeProcessResult> executor) {
        this.driver = Objects.requireNonNull(driver, "Native linker driver must not be null.");
        this.executor = Objects.requireNonNull(executor, "Native linker process executor must not be null.");
    }

    public NativeLinkResult link(List<Path> objectFiles, Path output) {
        var normalizedOutput = normalizeOutput(output);
        var command = driver.linkCommand(objectFiles, normalizedOutput);

        removePreviousOutput(normalizedOutput);

        var processResult = Objects.requireNonNull(executor.apply(command), "Native linker process executor must not return null.");

        if (!processResult.succeeded()) {
            var failure = new NativeToolchainException(failureMessage(command, processResult));

            removeFailedOutput(normalizedOutput, failure);

            throw failure;
        }

        validateOutput(normalizedOutput);

        return new NativeLinkResult(normalizedOutput, command, processResult.standardOutput(), processResult.standardError());
    }

    private static Path normalizeOutput(Path output) {
        return Objects.requireNonNull(output, "Native executable output path must not be null.").toAbsolutePath().normalize();
    }

    private static void removePreviousOutput(Path output) {
        try {
            if (Files.exists(output) && !Files.isRegularFile(output))
                throw new NativeToolchainException("Native executable output '%s' already exists and is not a regular file.".formatted(output));

            Files.deleteIfExists(output);
        } catch (IOException exception) {
            throw new NativeToolchainException("Failed to remove previous native executable output '%s'.".formatted(output), exception);
        }
    }

    private static void validateOutput(Path output) {
        if (!Files.isRegularFile(output))
            throw new NativeToolchainException("Native linker reported success but did not produce the executable output '%s'.".formatted(output));

        try {
            if (Files.size(output) == 0) {
                var failure = new NativeToolchainException("Native linker produced an empty executable output '%s'.".formatted(output));

                removeFailedOutput(output, failure);

                throw failure;
            }
        } catch (IOException exception) {
            throw new NativeToolchainException("Failed to inspect native executable output '%s'.".formatted(output), exception);
        }
    }

    private static void removeFailedOutput(Path output, RuntimeException failure) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    private static String failureMessage(
        NativeLinkCommand command,
        NativeProcessResult result
    ) {
        return """
            Native linker exited with code %d.
            Command arguments: %s
            Standard output:
            %s
            Standard error:
            %s
            """.formatted(
                result.exitCode(),
                command.arguments(),
                result.standardOutput(),
                result.standardError()
        );
    }
}
