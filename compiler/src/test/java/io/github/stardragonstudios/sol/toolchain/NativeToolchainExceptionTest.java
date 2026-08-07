package io.github.stardragonstudios.sol.toolchain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeToolchainExceptionTest {
    @Test
    void preservesMessagesAndCauses() {
        var cause = new IllegalStateException("failure");
        var exception = new NativeToolchainException("Native linking failed.", cause);

        assertEquals("Native linking failed.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void rejectsInvalidMessagesAndCauses() {
        assertThrows(
            NullPointerException.class,
            () -> new NativeToolchainException(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new NativeToolchainException("   ")
        );

        assertThrows(
            NullPointerException.class,
            () -> new NativeToolchainException("Native linking failed.", null)
        );
    }
}
