package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeProcessResultTest {
    @Test
    void exposesProcessSuccess() {
        assertTrue(
            new NativeProcessResult(
                0,
                "",
                ""
            ).succeeded()
        );

        assertFalse(
            new NativeProcessResult(
                1,
                "",
                ""
            ).succeeded()
        );
    }

    @Test
    void rejectsNullOutputs() {
        assertThrows(
            NullPointerException.class,
            () ->
                new NativeProcessResult(
                    0,
                    null,
                    ""
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new NativeProcessResult(
                    0,
                    "",
                    null
                )
        );
    }
}
