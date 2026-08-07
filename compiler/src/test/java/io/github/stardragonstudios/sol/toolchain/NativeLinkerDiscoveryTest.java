package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLinkerDiscoveryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void givesPriorityToConfiguredExecutablePaths() {
        var configured = normalize(temporaryDirectory.resolve("configured tools").resolve("custom-clang"));
        var pathLinker = normalize(temporaryDirectory.resolve("path tools").resolve("clang"));
        var available = Set.of(configured, pathLinker);

        var driver = discover(
            Map.of(NativeLinkerDiscovery.LINKER_ENVIRONMENT_VARIABLE, configured.toString(), "PATH", pathLinker.getParent().toString()),
            List.of("clang", "cc"),
            available,
            available
        );

        assertEquals(
            configured,
            driver.executable()
        );
    }

    @Test
    void resolvesConfiguredCommandNamesThroughPath() {
        var linker = normalize(temporaryDirectory.resolve("custom tools").resolve("sol-linker"));
        var available = Set.of(linker);
        var driver = discover(
            Map.of(NativeLinkerDiscovery.LINKER_ENVIRONMENT_VARIABLE, "sol-linker", "PATH", linker.getParent().toString()),
            List.of("clang", "cc"),
            available,
            available
        );

        assertEquals(linker, driver.executable());
    }

    @Test
    void searchesCandidatesAndPathDeterministically() {
        var firstDirectory = normalize(temporaryDirectory.resolve("first tools"));
        var secondDirectory = normalize(temporaryDirectory.resolve("second tools"));
        var firstCc = firstDirectory.resolve("cc");
        var secondClang = secondDirectory.resolve("clang");
        var available = Set.of(firstCc, secondClang);

        var driver = discover(
            Map.of("PATH", String.join(File.pathSeparator, firstDirectory.toString(), secondDirectory.toString())),
            List.of("clang", "cc"),
            available,
            available
        );

        /*
         * clang has candidate priority over cc, even when
         * cc appears in an earlier PATH directory.
         */
        assertEquals(secondClang, driver.executable());
    }

    @Test
    void ignoresFilesThatAreNotExecutable() {
        var linker = normalize(temporaryDirectory.resolve("tools").resolve("clang"));

        var exception =
            assertThrows(
                NativeToolchainException.class,
                () -> discover(
                    Map.of("PATH", linker.getParent().toString()),
                    List.of("clang"),
                    Set.of(linker),
                    Set.of()
                )
            );

        assertTrue(exception.getMessage().contains("No native linker driver was found"));
    }

    @Test
    void reportsInvalidConfiguredLinkers() {
        var blankException =
            assertThrows(
                NativeToolchainException.class,
                () -> discover(
                    Map.of(NativeLinkerDiscovery.LINKER_ENVIRONMENT_VARIABLE, "   "),
                    List.of("clang"),
                    Set.of(),
                    Set.of()
                )
            );

        assertTrue(blankException.getMessage().contains("must not be blank"));

        var missing = normalize(temporaryDirectory.resolve("missing").resolve("clang"));

        var missingException =
            assertThrows(
                NativeToolchainException.class,
                () -> discover(
                    Map.of(NativeLinkerDiscovery.LINKER_ENVIRONMENT_VARIABLE, missing.toString()),
                    List.of("clang"),
                    Set.of(),
                    Set.of()
                )
            );

        assertTrue(missingException.getMessage().contains("is not a regular executable file"));
    }

    @Test
    void rejectsInvalidDiscoveryInputs() {
        assertThrows(
            NullPointerException.class,
            () -> NativeLinkerDiscovery.discover(null, File.pathSeparator, List.of("clang"), _ -> true, _ -> true)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> NativeLinkerDiscovery.discover(Map.of(), "", List.of("clang"), _ -> true, _ -> true)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> NativeLinkerDiscovery.discover(Map.of(), File.pathSeparator, List.of(), _ -> true, _ -> true)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> NativeLinkerDiscovery.discover(Map.of(), File.pathSeparator, List.of("tools/clang"), _ -> true, _ -> true)
        );
    }

    private NativeLinkerDriver discover(
        Map<String, String> environment,
        List<String> candidates,
        Set<Path> regularFiles,
        Set<Path> executables
    ) {
        return NativeLinkerDiscovery.discover(
            environment,
            File.pathSeparator,
            candidates,
            path -> regularFiles.contains(normalize(path)),
            path -> executables.contains(normalize(path))
        );
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
