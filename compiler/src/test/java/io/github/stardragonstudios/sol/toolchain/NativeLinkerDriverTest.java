package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeLinkerDriverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsDeterministicCommandsAndPreservesObjectOrder() {
        var linker = temporaryDirectory.resolve("tool chain").resolve("clang");
        var firstObject = temporaryDirectory.resolve("object files").resolve("first.o");
        var secondObject = temporaryDirectory.resolve("object files").resolve("second.o");
        var output = temporaryDirectory.resolve("native output").resolve("application");
        var driver = new NativeLinkerDriver(linker);
        var command = driver.linkCommand(List.of(firstObject, secondObject), output);

        assertEquals(
            List.of(
                linker.toAbsolutePath().normalize().toString(),
                firstObject.toAbsolutePath().normalize().toString(),
                secondObject.toAbsolutePath().normalize().toString(),
                "-o",
                output.toAbsolutePath().normalize().toString()
            ),
            command.arguments()
        );
    }

    @Test
    void normalizesTheDriverExecutable() {
        var executable = temporaryDirectory.resolve("tools").resolve("..").resolve("clang");
        var driver = new NativeLinkerDriver(executable);

        assertEquals(executable.toAbsolutePath().normalize(), driver.executable());
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(
            NullPointerException.class,
            () -> new NativeLinkerDriver(null)
        );

        var driver = new NativeLinkerDriver(temporaryDirectory.resolve("clang"));

        assertThrows(
            NullPointerException.class,
            () -> driver.linkCommand(null, temporaryDirectory.resolve("program"))
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> driver.linkCommand(List.of(), temporaryDirectory.resolve("program"))
        );

        assertThrows(
            NullPointerException.class,
            () -> driver.linkCommand(List.of(temporaryDirectory.resolve("program.o")), null)
        );

        var objects = new ArrayList<Path>();
        objects.add(temporaryDirectory.resolve("first.o"));
        objects.add(null);

        assertThrows(
            NullPointerException.class,
            () -> driver.linkCommand(objects, temporaryDirectory.resolve("program"))
        );
    }
}
