package io.github.stardragonstudios.sol.backend.c;

import java.util.Objects;

public final class CCodeGenerationException
    extends IllegalStateException {

    public enum Reason {
        MISSING_ENTRY_POINT,
        PROGRAM_SEMANTIC_ERRORS,
        MODULE_SEMANTIC_ERRORS,
        UNFROZEN_SEMANTIC_SCOPE
    }

    private final Reason reason;

    public CCodeGenerationException(Reason reason, String message) {
        super(Objects.requireNonNull(message, "C code generation failure message must not be null."));

        this.reason = Objects.requireNonNull(reason, "C code generation failure reason must not be null.");
    }

    public Reason reason() {
        return reason;
    }
}
