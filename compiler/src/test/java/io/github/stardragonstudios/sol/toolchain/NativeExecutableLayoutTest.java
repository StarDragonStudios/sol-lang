package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeExecutableLayoutTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsUnixExecutableLayouts() {
        var requested =
            temporaryDirectory
                .resolve(
                    "native output"
                )
                .resolve(
                    "program"
                );

        var layout =
            NativeExecutableLayout.forOperatingSystem(
                requested,
                "Mac OS X"
            );

        assertEquals(
            requested
                .toAbsolutePath()
                .normalize(),
            layout.executable()
        );

        assertEquals(
            requested
                .resolveSibling(
                    "program.sol-link.o"
                )
                .toAbsolutePath()
                .normalize(),
            layout.objectFile()
        );
    }

    @Test
    void appendsWindowsExecutableExtensions() {
        var requested =
            temporaryDirectory.resolve(
                "program"
            );

        var layout =
            NativeExecutableLayout.forOperatingSystem(
                requested,
                "Windows 11"
            );

        assertEquals(
            requested
                .resolveSibling(
                    "program.exe"
                )
                .toAbsolutePath()
                .normalize(),
            layout.executable()
        );

        assertEquals(
            requested
                .resolveSibling(
                    "program.exe.sol-link.obj"
                )
                .toAbsolutePath()
                .normalize(),
            layout.objectFile()
        );
    }

    @Test
    void preservesExistingWindowsExtensionsCaseInsensitively() {
        var requested =
            temporaryDirectory.resolve(
                "PROGRAM.EXE"
            );

        var layout =
            NativeExecutableLayout.forOperatingSystem(
                requested,
                "Windows 10"
            );

        assertEquals(
            requested
                .toAbsolutePath()
                .normalize(),
            layout.executable()
        );
    }

    @Test
    void keepsExecutableAndObjectPathsDifferent() {
        var layout =
            NativeExecutableLayout.host(
                temporaryDirectory.resolve(
                    "program"
                )
            );

        assertNotEquals(
            layout.executable(),
            layout.objectFile()
        );
    }

    @Test
    void rejectsInvalidLayouts() {
        assertThrows(
            NullPointerException.class,
            () ->
                NativeExecutableLayout.host(
                    null
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                NativeExecutableLayout.forOperatingSystem(
                    temporaryDirectory.resolve(
                        "program"
                    ),
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new NativeExecutableLayout(
                    temporaryDirectory.resolve(
                        "same"
                    ),
                    temporaryDirectory.resolve(
                        "same"
                    )
                )
        );
    }
}
