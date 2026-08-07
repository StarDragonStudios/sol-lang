package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeLinkCommandTest {
    @Test
    void preservesArgumentsImmutably() {
        var arguments = new ArrayList<>(List.of("clang", "program.o", "-o", "program"));
        var command = new NativeLinkCommand(arguments);

        arguments.clear();

        assertEquals(
            List.of("clang", "program.o", "-o", "program"),
            command.arguments()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> command.arguments().add("another.o")
        );
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(
            NullPointerException.class,
            () -> new NativeLinkCommand(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new NativeLinkCommand(List.of())
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new NativeLinkCommand(List.of("clang", "   "))
        );

        var arguments = new ArrayList<String>();
        arguments.add("clang");
        arguments.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new NativeLinkCommand(arguments)
        );
    }
}
