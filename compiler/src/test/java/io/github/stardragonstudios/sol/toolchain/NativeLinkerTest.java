package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLinkerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void linksAndReturnsCapturedOutput() {
        var objectFile =
            temporaryDirectory.resolve(
                "program.o"
            );

        var output =
            temporaryDirectory.resolve(
                "program"
            );

        var driver =
            new NativeLinkerDriver(
                temporaryDirectory.resolve(
                    "clang"
                )
            );

        var linker =
            new NativeLinker(
                driver,
                command -> {
                    write(
                        output,
                        "native executable"
                    );

                    return new NativeProcessResult(
                        0,
                        "linker output",
                        "linker warning"
                    );
                }
            );

        var result =
            linker.link(
                List.of(
                    objectFile
                ),
                output
            );

        assertEquals(
            output
                .toAbsolutePath()
                .normalize(),
            result.executable()
        );

        assertEquals(
            "linker output",
            result.standardOutput()
        );

        assertEquals(
            "linker warning",
            result.standardError()
        );

        assertEquals(
            output
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.command()
                .arguments()
                .getLast()
        );
    }

    @Test
    void retainsDiagnosticsAndRemovesFailedOutputs() {
        var output =
            temporaryDirectory.resolve(
                "failed program"
            );

        var linker =
            new NativeLinker(
                new NativeLinkerDriver(
                    temporaryDirectory.resolve(
                        "clang"
                    )
                ),
                command -> {
                    write(
                        output,
                        "partial executable"
                    );

                    return new NativeProcessResult(
                        17,
                        "original stdout",
                        "original stderr"
                    );
                }
            );

        var exception =
            assertThrows(
                NativeToolchainException.class,
                () ->
                    linker.link(
                        List.of(
                            temporaryDirectory.resolve(
                                "program.o"
                            )
                        ),
                        output
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "exited with code 17"
                )
        );

        assertTrue(
            exception.getMessage()
                .contains(
                    "original stdout"
                )
        );

        assertTrue(
            exception.getMessage()
                .contains(
                    "original stderr"
                )
        );

        assertFalse(
            Files.exists(
                output
            )
        );
    }

    @Test
    void doesNotAcceptStaleExecutableOutputs() {
        var output =
            temporaryDirectory.resolve(
                "stale program"
            );

        write(
            output,
            "old executable"
        );

        var linker =
            new NativeLinker(
                new NativeLinkerDriver(
                    temporaryDirectory.resolve(
                        "clang"
                    )
                ),
                command ->
                    new NativeProcessResult(
                        0,
                        "",
                        ""
                    )
            );

        var exception =
            assertThrows(
                NativeToolchainException.class,
                () ->
                    linker.link(
                        List.of(
                            temporaryDirectory.resolve(
                                "program.o"
                            )
                        ),
                        output
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "did not produce"
                )
        );

        assertFalse(
            Files.exists(
                output
            )
        );
    }

    @Test
    void rejectsEmptyExecutableOutputs() {
        var output =
            temporaryDirectory.resolve(
                "empty program"
            );

        var linker =
            new NativeLinker(
                new NativeLinkerDriver(
                    temporaryDirectory.resolve(
                        "clang"
                    )
                ),
                command -> {
                    write(
                        output,
                        ""
                    );

                    return new NativeProcessResult(
                        0,
                        "",
                        ""
                    );
                }
            );

        var exception =
            assertThrows(
                NativeToolchainException.class,
                () ->
                    linker.link(
                        List.of(
                            temporaryDirectory.resolve(
                                "program.o"
                            )
                        ),
                        output
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "empty executable"
                )
        );

        assertFalse(
            Files.exists(
                output
            )
        );
    }

    @Test
    void rejectsNonFileOutputDestinations() {
        var output =
            temporaryDirectory.resolve(
                "existing directory"
            );

        createDirectory(
            output
        );

        var linker =
            new NativeLinker(
                new NativeLinkerDriver(
                    temporaryDirectory.resolve(
                        "clang"
                    )
                ),
                command ->
                    new NativeProcessResult(
                        0,
                        "",
                        ""
                    )
            );

        var exception =
            assertThrows(
                NativeToolchainException.class,
                () ->
                    linker.link(
                        List.of(
                            temporaryDirectory.resolve(
                                "program.o"
                            )
                        ),
                        output
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "is not a regular file"
                )
        );

        assertTrue(
            Files.isDirectory(
                output
            )
        );
    }

    @Test
    void rejectsInvalidConstruction() {
        var driver =
            new NativeLinkerDriver(
                temporaryDirectory.resolve(
                    "clang"
                )
            );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeLinker(
                    null
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeLinker(
                    driver,
                    null
                )
        );
    }

    private static void write(
        Path destination,
        String content
    ) {
        try {
            Files.writeString(
                destination,
                content
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(
                exception
            );
        }
    }

    private static void createDirectory(
        Path directory
    ) {
        try {
            Files.createDirectory(
                directory
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(
                exception
            );
        }
    }
}
