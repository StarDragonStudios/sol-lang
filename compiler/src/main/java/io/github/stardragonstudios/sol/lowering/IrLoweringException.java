package io.github.stardragonstudios.sol.lowering;

import java.util.Objects;

public final class IrLoweringException extends RuntimeException {

    public IrLoweringException(String message) {
        super(validateMessage(message));
    }

    private static String validateMessage(String message) {
        Objects.requireNonNull(message, "IR lowering exception message must not be null.");

        if (message.isBlank()) throw new IllegalArgumentException("IR lowering exception message must not be blank.");

        return message;
    }
}
