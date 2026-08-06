package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeLinkResultTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void normalizesExecutablePaths() {
        var executable =
            temporaryDirectory
                .resolve(
                    "output"
                )
                .resolve(
                    ".."
                )
                .resolve(
                    "program"
                );

        var command =
            new NativeLinkCommand(
                List.of(
                    "clang"
                )
            );

        var result =
            new NativeLinkResult(
                executable,
                command,
                "output",
                "error"
            );

        assertEquals(
            executable
                .toAbsolutePath()
                .normalize(),
            result.executable()
        );
    }

    @Test
    void rejectsInvalidValues() {
        var command =
            new NativeLinkCommand(
                List.of(
                    "clang"
                )
            );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeLinkResult(
                    null,
                    command,
                    "",
                    ""
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeLinkResult(
                    temporaryDirectory.resolve(
                        "program"
                    ),
                    null,
                    "",
                    ""
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeLinkResult(
                    temporaryDirectory.resolve(
                        "program"
                    ),
                    command,
                    null,
                    ""
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeLinkResult(
                    temporaryDirectory.resolve(
                        "program"
                    ),
                    command,
                    "",
                    null
                )
        );
    }
}
