package io.github.stardragonstudios.sol.lexer;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;

import java.util.Objects;

public final class LexicalException extends RuntimeException {
    private final Diagnostic diagnostic;

    public LexicalException(Diagnostic diagnostic) {
        super(
            Objects.requireNonNull(
                diagnostic,
                "Lexical diagnostic must not be null."
            ).message()
        );

        this.diagnostic = diagnostic;
    }

    public Diagnostic diagnostic() {
        return diagnostic;
    }
}
