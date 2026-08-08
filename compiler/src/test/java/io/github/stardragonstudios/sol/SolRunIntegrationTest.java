package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.cli.CompilerExitCode;
import io.github.stardragonstudios.sol.cli.SolProgramRunner;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolRunIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void runCommandCompilesAndExecutesSolProgram() throws IOException {

        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("run.sol");

        Files.writeString(
            source,
            """
            @init
            fn launch() -> int
                return 23
            end
            """
        );

        var errorBytes = new ByteArrayOutputStream();
        var exitCode = Sol.run(new String[] {"run", source.toString()}, new PrintStream(errorBytes), new SolProgramRunner()::run);

        assertEquals(23, exitCode, errorBytes::toString);
        assertEquals("", errorBytes.toString());

        /*
         * `sol run` must not use CompilerOutputPath.defaultFor(source)
         * and therefore must not leave the normal sibling executable.
         */
        assertFalse(Files.exists(temporaryDirectory.resolve(executableName("run"))));
        assertFalse(Files.exists(temporaryDirectory.resolve(executableName("run") + temporaryObjectSuffix())));
    }

    @Test
    void invalidRunProgramReturnsFrontendFailure()
        throws IOException {

        var source = temporaryDirectory.resolve("invalid-run.sol");

        Files.writeString(
            source,
            """
            @init
            fn launch() -> int
                return missing
            end
            """
        );

        var errorBytes = new ByteArrayOutputStream();

        var exitCode = Sol.run(
            new String[] {"run", source.toString()},
            new PrintStream(errorBytes),
            new SolProgramRunner()::run
        );

        var diagnostics = errorBytes.toString();

        assertEquals(CompilerExitCode.FRONTEND_ERROR.value(), exitCode);
        assertTrue(diagnostics.contains("SOL-S002"), diagnostics);
        assertTrue(diagnostics.contains("return missing"), diagnostics);
        assertTrue(diagnostics.contains("^^^^^^^"), diagnostics);
        assertFalse(Files.exists(temporaryDirectory.resolve(executableName("invalid-run"))));
    }

    private static void assumeNativeLinkerAvailable() {
        try {
            NativeLinkerDiscovery.discover();
        } catch (NativeToolchainException exception) {
            Assumptions.assumeTrue(false, exception.getMessage());
        }
    }

    private static String executableName(String baseName) {
        return isWindows() ? baseName + ".exe" : baseName;
    }

    private static String temporaryObjectSuffix() {
        return isWindows() ? ".sol-link.obj" : ".sol-link.o";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
