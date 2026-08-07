package io.github.stardragonstudios.sol.toolchain;

import java.util.Objects;

public final class NativeToolchainException
    extends RuntimeException {

    public NativeToolchainException(String message) {
        super(validateMessage(message));
    }

    public NativeToolchainException(String message, Throwable cause) {
        super(validateMessage(message), Objects.requireNonNull(cause, "Native toolchain failure cause must not be null."));
    }

    private static String validateMessage(String message) {
        Objects.requireNonNull(message, "Native toolchain failure message must not be null.");

        if (message.isBlank()) throw new IllegalArgumentException("Native toolchain failure message must not be blank.");

        return message;
    }
}
