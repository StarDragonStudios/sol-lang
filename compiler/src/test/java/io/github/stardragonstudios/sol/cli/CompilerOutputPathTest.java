package io.github.stardragonstudios.sol.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompilerOutputPathTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void removesSolExtensionForDefaultOutput() {
        var source =
            temporaryDirectory
                .resolve(
                    "hello.sol"
                );

        assertEquals(
            temporaryDirectory
                .resolve(
                    "hello"
                )
                .toAbsolutePath()
                .normalize(),
            CompilerOutputPath.defaultFor(
                source
            )
        );
    }

    @Test
    void acceptsSolExtensionCaseInsensitively() {
        var source =
            temporaryDirectory.resolve(
                "HELLO.SOL"
            );

        assertEquals(
            "HELLO",
            CompilerOutputPath.moduleName(
                source
            )
        );
    }

    @Test
    void rejectsNonSolSources() {
        assertThrows(
            CompilerPipelineException.class,
            () ->
                CompilerOutputPath.defaultFor(
                    temporaryDirectory.resolve(
                        "hello.txt"
                    )
                )
        );
    }

    @Test
    void rejectsEmptySourceName() {
        assertThrows(
            CompilerPipelineException.class,
            () ->
                CompilerOutputPath.defaultFor(
                    temporaryDirectory.resolve(
                        ".sol"
                    )
                )
        );
    }
}
