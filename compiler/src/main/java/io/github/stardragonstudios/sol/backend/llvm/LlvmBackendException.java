package io.github.stardragonstudios.sol.backend.llvm;

import java.util.Objects;

public final class LlvmBackendException
    extends RuntimeException {

    public LlvmBackendException(String message) {
        super(validateMessage(message));
    }

    public LlvmBackendException(String message, Throwable cause) {
        super(validateMessage(message), Objects.requireNonNull(cause, "LLVM backend failure cause must not be null."));
    }

    private static String validateMessage(String message) {
        Objects.requireNonNull(message, "LLVM backend failure message must not be null.");

        if (message.isBlank()) throw new IllegalArgumentException("LLVM backend failure message must not be blank.");

        return message;
    }
}