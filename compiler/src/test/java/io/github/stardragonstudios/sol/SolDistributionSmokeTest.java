package io.github.stardragonstudios.sol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SolDistributionSmokeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void installedDistributionCompilesAndRunsSolProgram() throws Exception {
        var distribution = Path.of(System.getProperty("sol.distributionDir"));
        var expectedVersion = System.getProperty("sol.expectedVersion");
        var sol = launcher(distribution, "sol");
        var solc = launcher(distribution, "solc");

        assertProcess(run(temporaryDirectory, sol, "--version"), 0, "Sol " + expectedVersion);
        assertProcess(run(temporaryDirectory, solc, "--version"), 0, "Sol " + expectedVersion);

        var source = temporaryDirectory.resolve("hello.sol");

        Files.writeString(
            source,
            """
            inject namespace std.console as csl

            @init
            fn launch() -> int
                csl::print_line("Sol distribution smoke test")
                return 23
            end
            """,
            StandardCharsets.UTF_8
        );

        var output = temporaryDirectory.resolve("hello");

        assertProcess(run(temporaryDirectory, solc, source.toString(), "-o", output.toString()), 0, "");

        var executable = Files.exists(output) ? output : Path.of(output + ".exe");

        assertProcess(run(temporaryDirectory, executable), 23, "Sol distribution smoke test");
        assertProcess(run(temporaryDirectory, sol, "run", source.toString()), 23, "Sol distribution smoke test");
    }

    private static Path launcher(Path distribution, String name) {
        var suffix = isWindows() ? ".bat" : "";

        return distribution.resolve("bin").resolve(name + suffix).toAbsolutePath();
    }

    private static ProcessResult run(Path workingDirectory, Path executable, String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<String>();

        if (isWindows() && executable.getFileName().toString().endsWith(".bat")) {
            command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
            command.add("/d");
            command.add("/c");
        }

        command.add(executable.toString());
        command.addAll(Arrays.asList(arguments));

        var process = new ProcessBuilder(command).directory(workingDirectory.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();

        return new ProcessResult(exitCode, output);
    }

    private static void assertProcess(ProcessResult result, int expectedExitCode, String expectedOutput) {
        assertEquals(expectedExitCode, result.exitCode(), result.output());

        if (!expectedOutput.isEmpty()) assertTrue(result.output().contains(expectedOutput), result.output());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private record ProcessResult(int exitCode, String output) {}
}
