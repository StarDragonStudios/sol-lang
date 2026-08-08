package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.cli.CompilerExitCode;
import io.github.stardragonstudios.sol.cli.SolProgramRunner;

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

class SolRunEndToEndTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void runCommandCompilesAndExecutesFixture()
        throws IOException {

        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "basic-exit", "main.sol");
        var errorBytes = new ByteArrayOutputStream();
        var exitCode = Sol.run(new String[] {"run", source.toString()}, new PrintStream(errorBytes), new SolProgramRunner()::run);

        assertEquals(42, exitCode, errorBytes::toString);
        assertEquals("", errorBytes.toString());

        /*
         * `sol run` compiles into private temporary storage and must not
         * leave the normal compile-only output beside the source fixture.
         */
        assertFalse(Files.exists(EndToEndTestSupport.hostExecutablePath(source.resolveSibling("main"))));
    }

    @Test
    void invalidFixtureReturnsFrontendFailure() throws IOException {
        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "invalid-name", "main.sol");
        var errorBytes = new ByteArrayOutputStream();
        var exitCode = Sol.run(new String[] {"run", source.toString()}, new PrintStream(errorBytes), new SolProgramRunner()::run);
        var diagnostics = errorBytes.toString();

        assertEquals(CompilerExitCode.FRONTEND_ERROR.value(), exitCode);
        assertTrue(diagnostics.contains("%s:3:12: error [SOL-S002]: Unresolved name 'missing'.".formatted(source.toAbsolutePath().normalize())), diagnostics);
        assertTrue(diagnostics.contains("3 |     return missing"), diagnostics);
        assertTrue(diagnostics.contains(  "|            ^^^^^^^"), diagnostics);
        assertFalse(Files.exists(EndToEndTestSupport.hostExecutablePath(source.resolveSibling("main"))));
    }
}
