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
    void installedDistributionCompilesAndRunsSelfHostFoundationsProgram() throws Exception {
        var distribution = Path.of(System.getProperty("sol.distributionDir"));
        var expectedVersion = System.getProperty("sol.expectedVersion");
        var sol = launcher(distribution, "sol");
        var solc = launcher(distribution, "solc");

        assertProcess("sol --version", run(temporaryDirectory, sol, "--version"), 0, "Sol " + expectedVersion);
        assertProcess("solc --version", run(temporaryDirectory, solc, "--version"), 0, "Sol " + expectedVersion);

        var source = temporaryDirectory.resolve("foundations.sol");
        var input = "Sol 🐉\n";

        Files.writeString(temporaryDirectory.resolve("input.txt"), "Aé🐉Z", StandardCharsets.UTF_8);

        Files.writeString(
            source,
            """
            inject std.collections.vector
            inject namespace std.console as console
            inject namespace std.file as file
            inject namespace std.memory as memory
            inject namespace std.string as strings

            struct Span
                start: int
                end_index: int
            end

            struct Token
                kind: int
                span: Span
            end

            @init
            fn launch() -> int
                let text: string = file::read_text("input.txt")
                let line: string = console::read_line()
                let raw: pointer<int> = memory::allocate<int>(1)

                if raw == null then
                    return 1
                end

                memory::store<int>(raw, 19)

                let tokens: pointer<Vector<Token>> = create_vector<Token>()
                vector_push<Token>(tokens, Token { kind: 23, span: Span { start: 1, end_index: 3 } })
                let token: Token = vector_pop<Token>(tokens)

                let valid: boolean = strings::length(text) == 4 && text[1] == 'é' && strings::slice(text, 1, 3) == "é🐉" && line == "Sol 🐉" && token.kind == 23 && token.span.start == 1 && memory::load<int>(raw) == 19

                destroy_vector<Token>(tokens)
                memory::free<int>(raw)

                if valid then
                    console::print_line("Sol 0.1.1 foundations smoke test")
                    return 42
                end

                return 2
            end
            """,
            StandardCharsets.UTF_8
        );

        var output = temporaryDirectory.resolve("foundations");

        assertProcess("solc compile", run(temporaryDirectory, solc, source.toString(), "-o", output.toString()), 0, "");

        var executable = Files.exists(output) ? output : Path.of(output + ".exe");

        assertProcess("native executable", run(temporaryDirectory, input, executable), 42, "Sol 0.1.1 foundations smoke test");
        assertProcess("sol run", run(temporaryDirectory, input, sol, "run", source.toString()), 42, "Sol 0.1.1 foundations smoke test");
    }

    private static Path launcher(Path distribution, String name) {
        var suffix = isWindows() ? ".bat" : "";

        return distribution.resolve("bin").resolve(name + suffix).toAbsolutePath();
    }

    private static ProcessResult run(Path workingDirectory, Path executable, String... arguments) throws IOException, InterruptedException {
        return run(workingDirectory, "", executable, arguments);
    }

    private static ProcessResult run(Path workingDirectory, String standardInput, Path executable, String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<String>();

        if (isWindows() && executable.getFileName().toString().endsWith(".bat")) {
            command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
            command.add("/d");
            command.add("/c");
        }

        command.add(executable.toString());
        command.addAll(Arrays.asList(arguments));

        var process = new ProcessBuilder(command).directory(workingDirectory.toFile()).redirectErrorStream(true).start();

        try (var input = process.getOutputStream()) {
            input.write(standardInput.getBytes(StandardCharsets.UTF_8));
        }

        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();

        return new ProcessResult(exitCode, output);
    }

    private static void assertProcess(String operation, ProcessResult result, int expectedExitCode, String expectedOutput) {
        var diagnostic = "%s failed with exit code %d.%n%s".formatted(operation, result.exitCode(), result.output());

        assertEquals(expectedExitCode, result.exitCode(), diagnostic);

        if (!expectedOutput.isEmpty()) assertTrue(result.output().contains(expectedOutput), diagnostic);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private record ProcessResult(int exitCode, String output) {}
}
