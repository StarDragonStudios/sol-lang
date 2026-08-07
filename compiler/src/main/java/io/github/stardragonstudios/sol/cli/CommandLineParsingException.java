package io.github.stardragonstudios.sol.cli;

import java.util.Objects;

public final class CommandLineParsingException extends RuntimeException {

    public CommandLineParsingException(String message) {
        super(validateMessage(message));
    }

    public CommandLineParsingException(String message, Throwable cause) {
        super(
            validateMessage(message),
            Objects.requireNonNull(cause, "Command-line parsing failure cause must not be null.")
        );
    }

    private static String validateMessage(String message) {
        Objects.requireNonNull(message, "Command-line parsing failure message must not be null.");

        if (message.isBlank()) throw new IllegalArgumentException("Command-line parsing failure message must not be blank.");

        return message;
    }
}
