package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeProcessExecutorTest {
    @Test
    void capturesOutputAndExitCodeSeparately() {
        var result =
            NativeProcessExecutor.execute(
                fixtureCommand(
                    "simple",
                    "7",
                    "standard output",
                    "standard error"
                )
            );

        assertEquals(
            7,
            result.exitCode()
        );

        assertEquals(
            "standard output",
            result.standardOutput()
        );

        assertEquals(
            "standard error",
            result.standardError()
        );
    }

    @Test
    void drainsLargeOutputStreamsConcurrently() {
        var result =
            NativeProcessExecutor.execute(
                fixtureCommand(
                    "bulk",
                    "200000"
                )
            );

        assertEquals(
            0,
            result.exitCode()
        );

        assertEquals(
            200000,
            result.standardOutput()
                .length()
        );

        assertEquals(
            200000,
            result.standardError()
                .length()
        );

        assertTrue(
            result.standardOutput()
                .chars()
                .allMatch(
                    character ->
                        character == 'o'
                )
        );

        assertTrue(
            result.standardError()
                .chars()
                .allMatch(
                    character ->
                        character == 'e'
                )
        );
    }

    @Test
    void reportsProcessStartFailures() {
        var command =
            new NativeLinkCommand(
                List.of(
                    Path.of(
                            "missing",
                            "native",
                            "linker"
                        )
                        .toAbsolutePath()
                        .toString()
                )
            );

        var exception =
            assertThrows(
                NativeToolchainException.class,
                () ->
                    NativeProcessExecutor.execute(
                        command
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "Failed to start native linker command"
                )
        );
    }

    @Test
    void rejectsNullCommands() {
        assertThrows(
            NullPointerException.class,
            () ->
                NativeProcessExecutor.execute(
                    null
                )
        );
    }

    private static NativeLinkCommand fixtureCommand(
        String... arguments
    ) {
        var command =
            new java.util.ArrayList<String>();

        command.add(
            javaExecutable()
                .toString()
        );

        command.add(
            "-cp"
        );

        command.add(
            System.getProperty(
                "java.class.path"
            )
        );

        command.add(
            NativeProcessFixture.class
                .getName()
        );

        command.addAll(
            List.of(
                arguments
            )
        );

        return new NativeLinkCommand(
            command
        );
    }

    private static Path javaExecutable() {
        var operatingSystem =
            System.getProperty(
                    "os.name",
                    ""
                )
                .toLowerCase(
                    Locale.ROOT
                );

        var executableName =
            operatingSystem.contains(
                "win"
            )
                ? "java.exe"
                : "java";

        return Path.of(
                System.getProperty(
                    "java.home"
                ),
                "bin",
                executableName
            )
            .toAbsolutePath()
            .normalize();
    }
}
