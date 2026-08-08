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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SolCompilerIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesAndRunsNativeExecutableThroughCli()
        throws IOException, InterruptedException {

        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("answer.sol");

        Files.writeString(
            source,
            """
            @init
            fn launch() -> int
                return 42
            end
            """
        );

        var errorBytes = new ByteArrayOutputStream();
        var exitCode = SolCompiler.run(new String[] {source.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);
        var executable = temporaryDirectory.resolve(executableName("answer"));

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode, errorBytes::toString);
        assertEquals("", errorBytes.toString());
        assertTrue(Files.isRegularFile(executable));
        assertTrue(Files.size(executable) > 0);
        assertFalse(Files.exists(temporaryObjectFor(executable)));

        var process = new ProcessBuilder(executable.toString()).redirectErrorStream(true).start();
        var processOutput = new String(process.getInputStream().readAllBytes());
        var processExitCode = process.waitFor();

        assertEquals(42, processExitCode, processOutput);
    }

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
    void compilesExecutableWithDiscoveredInjectedModule()
        throws IOException, InterruptedException {

        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("main.sol");
        var helper = temporaryDirectory.resolve("helper.sol");

        Files.writeString(
            source,
            """
            inject helper

            @init
            fn launch() -> int
                return answer()
            end
            """
        );

        Files.writeString(
            helper,
            """
            fn answer() -> int
                return 42
            end
            """
        );

        var errorBytes = new ByteArrayOutputStream();
        var exitCode = SolCompiler.run(new String[] {source.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);
        var executable = temporaryDirectory.resolve(executableName("main"));

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode, errorBytes::toString);

        var process = new ProcessBuilder(executable.toString()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes());

        assertEquals(42, process.waitFor(), output);
    }

    @Test
    void invalidProgramProducesNoExecutable() throws IOException {

        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("invalid.sol");

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
        var exitCode = SolCompiler.run(new String[] {source.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);
        var executable = temporaryDirectory.resolve(executableName("invalid"));

        assertEquals(CompilerExitCode.FRONTEND_ERROR.value(), exitCode);
        assertFalse(Files.exists(executable));

        var diagnosticOutput = errorBytes.toString();

        assertTrue(diagnosticOutput.contains("%s:3:12: error [SOL-S002]: Unresolved name 'missing'.".formatted(source.toAbsolutePath().normalize())), diagnosticOutput);
        assertTrue(diagnosticOutput.contains("3 |     return missing"), diagnosticOutput);
        assertTrue(diagnosticOutput.contains("|            ^^^^^^^"), diagnosticOutput);
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

    @Test
    void compilesAndRunsProgramUsingBundledConsoleNamespace()
        throws IOException, InterruptedException {

        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("console.sol");

        Files.writeString(
            source,
            """
            inject namespace std.console as csl

            @init
            fn launch() -> int
                csl::print("Hello ")
                csl::print_line("Sol ñ")
                return 23
            end
            """
        );

        var errorBytes = new ByteArrayOutputStream();
        var exitCode = SolCompiler.run(new String[] {source.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);

        var executable = temporaryDirectory.resolve(executableName("console"));

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode, errorBytes::toString);
        assertEquals("", errorBytes.toString());
        assertTrue(Files.isRegularFile(executable));

        var process = new ProcessBuilder(executable.toString()).start();
        var standardOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var standardError = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        var processExitCode = process.waitFor();

        assertEquals(23, processExitCode, standardError);
        assertEquals("Hello Sol ñ\n", standardOutput);
        assertEquals("", standardError);
    }

    @Test
    void compilesAndRunsProgramUsingBundledFileExists()
        throws IOException, InterruptedException {

        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("file-exists.sol");
        var existingFile = temporaryDirectory.resolve("present.txt");

        Files.writeString(existingFile, "Sol");

        Files.writeString(
            source,
            """
            inject namespace std.file as file

            @init
            fn launch() -> int
                if file::exists("present.txt") then
                    if file::exists("missing.txt") then
                        return 12
                    else
                        return 23
                    end
                else
                    return 11
                end
            end
            """
        );

        var errorBytes = new ByteArrayOutputStream();
        var exitCode = SolCompiler.run(new String[] {source.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);
        var executable = temporaryDirectory.resolve(executableName("file-exists"));

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode, errorBytes::toString);
        assertEquals("", errorBytes.toString());
        assertTrue(Files.isRegularFile(executable));

        /*
         * std.file paths are resolved by the native process,
         * so run it from the test directory containing present.txt.
         */
        var process = new ProcessBuilder(executable.toString()).directory(temporaryDirectory.toFile()).start();
        var standardOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var standardError = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        var processExitCode = process.waitFor();

        assertEquals(23, processExitCode, standardError);
        assertEquals("", standardOutput);
        assertEquals("", standardError);
    }

    @Test
    void compilesAndRunsProgramUsingBundledFileWriteFunctions() throws IOException, InterruptedException {
        assumeNativeLinkerAvailable();

        var source = temporaryDirectory.resolve("file-write.sol");
        var outputFile = temporaryDirectory.resolve("output.txt");

        /*
         * Verifies that write_text uses replacement/truncation semantics
         * rather than appending to an existing file.
         */
        Files.writeString(outputFile, "stale content", StandardCharsets.UTF_8);

        Files.writeString(
            source,
            """
            inject namespace std.file as file

            @init
            fn launch() -> int
                if file::write_text("output.txt", "Hola ñ") then
                    if file::append_text("output.txt", " Sol") then
                        return 23
                    else
                        return 12
                    end
                else
                    return 11
                end
            end
            """
        );

        var errorBytes = new ByteArrayOutputStream();
        var exitCode = SolCompiler.run(new String[] {source.toString()}, new PrintStream(errorBytes), new CompilerPipeline()::compile);
        var executable = temporaryDirectory.resolve(executableName("file-write"));

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode, errorBytes::toString);
        assertEquals("", errorBytes.toString());
        assertTrue(Files.isRegularFile(executable));

        var process = new ProcessBuilder(executable.toString()).directory(temporaryDirectory.toFile()).start();
        var standardOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var standardError = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        var processExitCode = process.waitFor();

        assertEquals(23, processExitCode, standardError);
        assertEquals("", standardOutput);
        assertEquals("", standardError);
        assertEquals("Hola ñ Sol", Files.readString(outputFile, StandardCharsets.UTF_8));
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
