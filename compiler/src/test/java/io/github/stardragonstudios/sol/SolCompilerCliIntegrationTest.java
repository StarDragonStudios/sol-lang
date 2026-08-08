package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.cli.CompilerExitCode;
import io.github.stardragonstudios.sol.cli.CompilerPipeline;
import io.github.stardragonstudios.sol.toolchain.NativeLinkerDiscovery;
import io.github.stardragonstudios.sol.toolchain.NativeToolchainException;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SolCompilerCliIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void explicitOutputPathIsUsedByRealCompilation()
        throws IOException {

        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("explicit.sol");

        Files.writeString(
            source,
            """
            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var output = temporaryDirectory.resolve("output with spaces").resolve("custom-program");
        var errorBytes = new ByteArrayOutputStream();
        var exitCode = SolCompiler.run(new String[] {source.toString(), "-o", output.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);
        var executable = hostExecutablePath(output);

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode, errorBytes::toString);
        assertTrue(Files.isRegularFile(executable));
        assertTrue(Files.size(executable) > 0);
    }

    @Test
    void keepIntermediatesRetainsNativeObject()
        throws IOException {

        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("retained.sol");

        Files.writeString(
            source,
            """
            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var errorBytes = new ByteArrayOutputStream();
        var exitCode = SolCompiler.run(new String[] {"--keep-intermediates", source.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);
        var executable = temporaryDirectory.resolve(executableName("retained"));
        var objectFile = temporaryObjectFor(executable);

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode, errorBytes::toString);
        assertTrue(Files.isRegularFile(executable));
        assertTrue(Files.isRegularFile(objectFile));
        assertTrue(Files.size(objectFile) > 0);
    }

    private static void assumeNativeLinkerAvailable() {
        try {
            NativeLinkerDiscovery.discover();
        } catch (NativeToolchainException exception) {
            Assumptions.assumeTrue(false, exception.getMessage());
        }
    }

    private static Path temporaryObjectFor(Path executable) {
        var fileName = executable.getFileName().toString();
        var suffix = isWindows() ? ".sol-link.obj" : ".sol-link.o";

        return executable.resolveSibling(fileName + suffix);
    }

    private static Path hostExecutablePath(Path requested) {
        var normalized = requested.toAbsolutePath().normalize();

        if (isWindows() && !normalized.getFileName().toString().toLowerCase().endsWith(".exe")) return normalized.resolveSibling(normalized.getFileName() + ".exe");


        return normalized;
    }

    private static String executableName(String baseName) {
        return isWindows() ? baseName + ".exe" : baseName;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
