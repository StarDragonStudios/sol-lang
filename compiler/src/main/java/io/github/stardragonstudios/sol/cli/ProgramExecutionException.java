package io.github.stardragonstudios.sol.cli;

public final class ProgramExecutionException extends RuntimeException {

    public ProgramExecutionException(String message) {
        super(message);
    }

    public ProgramExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
