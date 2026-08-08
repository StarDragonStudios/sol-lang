package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.toolchain.NativeLinkerDiscovery;
import io.github.stardragonstudios.sol.toolchain.NativeToolchainException;
import io.github.stardragonstudios.sol.cli.CompilerOutputPath;
import io.github.stardragonstudios.sol.cli.CompilerPipeline;

import org.junit.jupiter.api.Assumptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

final class EndToEndTestSupport {
    private static final String FIXTURE_ROOT = "e2e";

    private EndToEndTestSupport() {}

    static void assumeNativeLinkerAvailable() {
        try {
            NativeLinkerDiscovery.discover();
        } catch (NativeToolchainException exception) {
            Assumptions.assumeTrue(false, exception.getMessage());
        }
    }

    static Path copyFixture(Path workspace, String fixtureName, String entryFile, String... additionalFiles) throws IOException {
        Objects.requireNonNull(workspace, "E2E workspace must not be null.");
        Objects.requireNonNull(additionalFiles, "Additional E2E fixture files must not be null.");

        var normalizedFixtureName = normalizeRelativePath(fixtureName, "E2E fixture name");
        var normalizedEntry = normalizeRelativePath(entryFile, "E2E fixture entry file");
        var projectDirectory = workspace.toAbsolutePath().normalize().resolve(normalizedFixtureName).normalize();

        Files.createDirectories(projectDirectory);

        copyFixtureFile(projectDirectory, normalizedFixtureName, normalizedEntry);

        for (var additionalFile : additionalFiles)
            copyFixtureFile(projectDirectory, normalizedFixtureName, normalizeRelativePath(additionalFile, "Additional E2E fixture file"));

        return projectDirectory.resolve(normalizedEntry).normalize();
    }

    static EndToEndCompilationResult compile(Path source) {
        Objects.requireNonNull(source, "E2E source file must not be null.");

        var errorBytes = new ByteArrayOutputStream();
        var exitCode = SolCompiler.run(new String[] {source.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);
        var requestedExecutable = CompilerOutputPath.defaultFor(source);

        return new EndToEndCompilationResult(exitCode, hostExecutablePath(requestedExecutable), errorBytes.toString(StandardCharsets.UTF_8));
    }

    static NativeExecutionResult execute(Path executable, Path workingDirectory) throws IOException, InterruptedException, ExecutionException {
        Objects.requireNonNull(executable, "E2E executable must not be null.");
        Objects.requireNonNull(workingDirectory, "E2E working directory must not be null.");

        var process = new ProcessBuilder(executable.toString()).directory(workingDirectory.toFile()).start();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var standardOutput = executor.submit(() -> {
                try (var stream = process.getInputStream()) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            });

            var standardError = executor.submit(() -> {
                try (var stream = process.getErrorStream()) {
                            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            });

            final int exitCode;

            try {
                exitCode = process.waitFor();
            } catch (InterruptedException exception) {
                process.destroyForcibly();
                throw exception;
            }

            return new NativeExecutionResult(exitCode, standardOutput.get(), standardError.get());
        }
    }

    static Path hostExecutablePath(Path requested) {
        var normalized = Objects.requireNonNull(requested, "Requested E2E executable path must not be null.").toAbsolutePath().normalize();

        if (isWindows() && !normalized.getFileName().toString().toLowerCase().endsWith(".exe")) return normalized.resolveSibling(normalized.getFileName() + ".exe");

        return normalized;
    }

    static Path temporaryObjectFor(Path executable) {
        Objects.requireNonNull(executable, "E2E executable path must not be null.");

        var suffix = isWindows() ? ".sol-link.obj" : ".sol-link.o";

        return executable.resolveSibling(executable.getFileName() + suffix);
    }

    private static void copyFixtureFile(Path projectDirectory, Path fixtureName, Path relativeFile) throws IOException {
        var resourceName = "%s/%s/%s".formatted(FIXTURE_ROOT, resourcePath(fixtureName), resourcePath(relativeFile));
        var target = projectDirectory.resolve(relativeFile).normalize();

        if (!target.startsWith(projectDirectory)) throw new IllegalArgumentException("E2E fixture file '%s' escapes its project directory.".formatted(relativeFile));

        var parent = target.getParent();

        if (parent != null) Files.createDirectories(parent);

        try (var input = EndToEndTestSupport.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) throw new IllegalArgumentException("E2E fixture resource '%s' does not exist.".formatted(resourceName));

            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path normalizeRelativePath(String value, String description) {
        Objects.requireNonNull(value, "%s must not be null.".formatted(description));

        if (value.isBlank()) throw new IllegalArgumentException("%s must not be blank.".formatted(description));

        var path = Path.of(value).normalize();

        if (path.isAbsolute() || path.startsWith("..")) throw new IllegalArgumentException("%s must remain inside the E2E fixture tree.".formatted(description));

        return path;
    }

    private static String resourcePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}

record NativeExecutionResult(int exitCode, String standardOutput, String standardError) {
    NativeExecutionResult {
        Objects.requireNonNull(standardOutput, "E2E standard output must not be null.");
        Objects.requireNonNull(standardError, "E2E standard error must not be null.");
    }
}

record EndToEndCompilationResult(int exitCode, Path executable, String compilerOutput) {
    EndToEndCompilationResult {
        Objects.requireNonNull(executable, "E2E compiled executable path must not be null.");
        Objects.requireNonNull(compilerOutput, "E2E compiler output must not be null.");
    }
}
