package io.github.stardragonstudios.sol.lowering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrLoweringExceptionTest {
    @Test
    void preservesLoweringFailureMessages() {
        var exception = new IrLoweringException("Unsupported syntax.");

        assertEquals("Unsupported syntax.", exception.getMessage());
    }

    @Test
    void rejectsInvalidMessages() {
        assertThrows(
            NullPointerException.class,
            () -> new IrLoweringException(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrLoweringException(" ")
        );
    }
}
