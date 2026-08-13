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
    void compilesAndExecutesCrossModuleGenericFixture() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(
            temporaryDirectory,
            "generic-multi-module",
            "main.sol",
            "helper.sol"
        );
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(42, result.exitCode(), result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void compilesAndExecutesRawMemoryFixture() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "raw-memory", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(42, result.exitCode(), result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void compilesAndExecutesGenericVectorFixture() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "generic-vector", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(42, result.exitCode(), result.standardOutput() + result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void reportsGenericVectorBoundsFailure() throws Exception {
        assertVectorFailure("vector-bounds", "vector index out of bounds");
    }

    @Test
    void reportsGenericVectorEmptyPopFailure() throws Exception {
        assertVectorFailure("vector-empty-pop", "cannot pop an empty vector");
    }

    @Test
    void reportsGenericVectorCapacityFailure() throws Exception {
        assertVectorFailure("vector-capacity", "invalid or overflowing vector capacity");
    }

    @Test
    void reportsGenericVectorGrowthOverflow() throws Exception {
        assertVectorFailure("vector-overflow", "invalid or overflowing vector capacity");
    }

    private void assertVectorFailure(String fixture, String message) throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, fixture, "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(70, result.exitCode());
        assertTrue(result.standardOutput().contains(message), result.standardOutput());
    }

    @Test
    void readsUtf8FileTextAndConsoleLines() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "text-input", "main.sol", "input.txt");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent(), "\r\nSol 🐉\nfinal\r");

        assertEquals(42, result.exitCode(), result.standardOutput() + result.standardError());
        assertEquals("", result.standardError());
    }

    @Test
    void reportsConsoleEofBeforeALine() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "console-eof", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);
        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(70, result.exitCode());
        assertTrue(result.standardOutput().contains("std.console.read_line reached EOF"));
    }

    @Test
    void rejectsMalformedUtf8FileInput() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "invalid-text-input", "main.sol", "invalid.txt");
        Files.write(source.getParent().resolve("invalid.txt"), new byte[] {(byte) 0xC3, 0x28});
        var compilation = EndToEndTestSupport.compile(source);
        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(70, result.exitCode());
        assertTrue(result.standardOutput().contains("text input is not valid UTF-8"));
    }

    @Test
    void readsEmptyFilesAndGrowsConsoleInputStorage() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "empty-and-long-input", "main.sol", "empty.txt");
        var compilation = EndToEndTestSupport.compile(source);
        var longLine = "x".repeat(600);
        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent(), longLine + "\n");

        assertEquals(42, result.exitCode(), result.standardOutput() + result.standardError());
        assertEquals("", result.standardError());
    }

    @Test
    void reportsMissingFileInput() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "missing-text-input", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);
        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(70, result.exitCode());
        assertTrue(result.standardOutput().contains("std.file.read_text could not open the file"));
    }

    @Test
    void rejectsMalformedUtf8ConsoleInput() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "console-eof", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);
        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent(), new byte[] {(byte) 0xC3, 0x28, '\n'});

        assertEquals(70, result.exitCode());
        assertTrue(result.standardOutput().contains("text input is not valid UTF-8"));
    }

    @Test
    void compilesAndExecutesBootstrapStringOperations() throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, "bootstrap-strings", "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());
        assertEquals("", compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(42, result.exitCode(), result.standardOutput() + result.standardError());
        assertEquals("", result.standardOutput());
        assertEquals("", result.standardError());
    }

    @Test
    void stringIndexBoundsFailureIsDeterministicAndDiagnosed() throws Exception {
        assertStringRuntimeFailure(
            "string-index-bounds",
            "Sol runtime error: string index out of bounds."
        );
    }

    @Test
    void stringSliceBoundsFailureIsDeterministicAndDiagnosed() throws Exception {
        assertStringRuntimeFailure(
            "string-slice-bounds",
            "Sol runtime error: invalid string slice range."
        );
    }

    @Test
    void stringSubstringBoundsFailureIsDeterministicAndDiagnosed() throws Exception {
        assertStringRuntimeFailure(
            "string-substring-bounds",
            "Sol runtime error: invalid string substring range."
        );
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

    private void assertStringRuntimeFailure(String fixture, String diagnostic) throws Exception {
        EndToEndTestSupport.assumeNativeLinkerAvailable();

        var source = EndToEndTestSupport.copyFixture(temporaryDirectory, fixture, "main.sol");
        var compilation = EndToEndTestSupport.compile(source);

        assertEquals(CompilerExitCode.SUCCESS.value(), compilation.exitCode(), compilation.compilerOutput());

        var result = EndToEndTestSupport.execute(compilation.executable(), source.getParent());

        assertEquals(70, result.exitCode(), result.standardOutput() + result.standardError());
        assertEquals(diagnostic + System.lineSeparator(), result.standardOutput());
        assertEquals("", result.standardError());
    }
}
