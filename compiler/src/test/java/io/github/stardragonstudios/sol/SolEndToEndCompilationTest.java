package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.cli.CompilerExitCode;
import io.github.stardragonstudios.sol.cli.CompilerPipeline;
import io.github.stardragonstudios.sol.cli.CompilerPipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SolEndToEndCompilationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesAndExecutesBasicFixture() throws Exception {

        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "basic-exit", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());
        assertTrue(Files.isRegularFile(compilation.executable()));
        assertTrue(Files.size(compilation.executable()) > 0);
        assertFalse(Files.exists(EndToEndTestSupport.temporaryObjectFor(compilation.executable())));

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(42, result.exitCode(), result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void compilesAndExecutesInjectedModuleFixture() throws Exception {

        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "multi-module", "main.sol", "helper.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(42, result.exitCode(), result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void compilesAndExecutesStructValueFixture() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "struct-values", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(42, result.exitCode(), result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void compilesAndExecutesConsoleFixture() throws Exception {

        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "console", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(23, result.exitCode(), result.standardError());
        assertEquals("Hello Sol ñ\n", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void compilesAndExecutesFileExistsFixture() throws Exception {

        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "file-exists", "main.sol", "present.txt");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(23, result.exitCode(), result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void compilesAndExecutesFileWriteFixture() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "file-write", "main.sol", "output.txt");
        var outputFile = source.getParent().resolve("output.txt");

        assertEquals("stale content", Files.readString(outputFile));

        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(23, result.exitCode(), result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
        assertEquals("Hola ñ Sol", Files.readString(outputFile));
    }

    @Test
    void invalidFixtureProducesSourceDiagnosticAndNoExecutable()
        throws Exception {

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "invalid-name", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.FRONTEND_ERROR.value(), compilation.exitCode());
        assertFalse(Files.exists(compilation.executable()));

        var diagnostics = compilation.compilerOutput();

        assertTrue(diagnostics.contains("%s:3:12: error [SOL-S002]: Unresolved name 'missing'.".formatted(source.toAbsolutePath().normalize())), diagnostics);
        assertTrue(diagnostics.contains("3 |     return missing"), diagnostics);
        assertTrue(diagnostics.contains("|            ^^^^^^^"), diagnostics);
    }
}
