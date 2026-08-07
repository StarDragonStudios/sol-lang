package io.github.stardragonstudios.sol.cli;

import java.util.Objects;

public final class CompilerPipelineException extends RuntimeException {

    public CompilerPipelineException(String message) {
        super(validateMessage(message));
    }

    public CompilerPipelineException(String message, Throwable cause) {
        super(validateMessage(message), Objects.requireNonNull(cause, "Compiler pipeline failure cause must not be null."));
    }

    private static String validateMessage(String message) {
        Objects.requireNonNull(message, "Compiler pipeline failure message must not be null.");

        if (message.isBlank()) throw new IllegalArgumentException("Compiler pipeline failure message must not be blank.");

        return message;
    }
}
