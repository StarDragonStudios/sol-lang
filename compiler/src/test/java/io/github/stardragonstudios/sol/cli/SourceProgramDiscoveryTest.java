package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.semantics.ModuleName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceProgramDiscoveryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversDirectInjectedModule()
        throws IOException {

        write(
            "main.sol",
            """
            inject helper

            @init
            fn launch() -> int
                return helperValue()
            end
            """
        );

        write(
            "helper.sol",
            """
            fn helperValue() -> int
                return 42
            end
            """
        );

        var program =
            SourceProgramDiscovery.discover(
                temporaryDirectory.resolve(
                    "main.sol"
                )
            );

        assertEquals(
            List.of(
                new ModuleName(
                    List.of(
                        "main"
                    )
                ),
                new ModuleName(
                    List.of(
                        "helper"
                    )
                )
            ),
            program.modules()
                .stream()
                .map(
                    module ->
                        module.name()
                )
                .toList()
        );
    }

    @Test
    void mapsQualifiedModuleNameToDirectories()
        throws IOException {

        write(
            "main.sol",
            """
            inject utilities.math

            @init
            fn launch() -> int
                return addOne(41)
            end
            """
        );

        var directory =
            temporaryDirectory.resolve(
                "utilities"
            );

        Files.createDirectories(
            directory
        );

        Files.writeString(
            directory.resolve(
                "math.sol"
            ),
            """
            fn addOne(value: int) -> int
                return value + 1
            end
            """
        );

        var program =
            SourceProgramDiscovery.discover(
                temporaryDirectory.resolve(
                    "main.sol"
                )
            );

        assertTrue(
            program.modules()
                .stream()
                .anyMatch(
                    module ->
                        module.name()
                            .equals(
                                new ModuleName(
                                    List.of(
                                        "utilities",
                                        "math"
                                    )
                                )
                            )
                )
        );
    }

    @Test
    void discoversTransitiveInjectedModules()
        throws IOException {

        write(
            "main.sol",
            """
            inject first

            @init
            fn launch() -> int
                return firstValue()
            end
            """
        );

        write(
            "first.sol",
            """
            inject second

            fn firstValue() -> int
                return secondValue()
            end
            """
        );

        write(
            "second.sol",
            """
            fn secondValue() -> int
                return 42
            end
            """
        );

        var program =
            SourceProgramDiscovery.discover(
                temporaryDirectory.resolve(
                    "main.sol"
                )
            );

        assertEquals(
            3,
            program.modules()
                .size()
        );
    }

    @Test
    void cyclicInjectionsTerminate()
        throws IOException {

        write(
            "main.sol",
            """
            inject helper

            @init
            fn launch() -> int
                return value()
            end
            """
        );

        write(
            "helper.sol",
            """
            inject main

            fn value() -> int
                return 42
            end
            """
        );

        var program =
            SourceProgramDiscovery.discover(
                temporaryDirectory.resolve(
                    "main.sol"
                )
            );

        assertEquals(
            2,
            program.modules()
                .size()
        );
    }

    @Test
    void missingInjectedModuleIsLeftForSemanticAnalysis()
        throws IOException {

        write(
            "main.sol",
            """
            inject missing

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var program =
            SourceProgramDiscovery.discover(
                temporaryDirectory.resolve(
                    "main.sol"
                )
            );

        assertEquals(
            1,
            program.modules()
                .size()
        );
    }

    @Test
    void syntaxErrorInInjectedModuleUsesInjectedSourcePath()
        throws IOException {

        write(
            "main.sol",
            """
            inject broken

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var broken =
            write(
                "broken.sol",
                "$"
            );

        var exception =
            assertThrows(
                FrontendCompilationException.class,
                () ->
                    SourceProgramDiscovery.discover(
                        temporaryDirectory.resolve(
                            "main.sol"
                        )
                    )
            );

        assertEquals(
            broken.toAbsolutePath()
                .normalize(),
            exception.diagnostics()
                .getFirst()
                .sourceFile()
        );
    }

    private Path write(
        String name,
        String source
    ) throws IOException {
        var path =
            temporaryDirectory.resolve(
                name
            );

        Files.writeString(
            path,
            source
        );

        return path;
    }
}
