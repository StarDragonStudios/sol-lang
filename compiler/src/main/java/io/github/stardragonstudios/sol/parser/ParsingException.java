package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;

import java.util.Objects;

public final class ParsingException extends RuntimeException {
    private final Diagnostic diagnostic;

    public ParsingException(Diagnostic diagnostic) {
        super(Objects.requireNonNull(diagnostic, "Parsing diagnostic must not be null.").message());

        this.diagnostic = diagnostic;
    }

    public Diagnostic diagnostic() {
        return diagnostic;
    }
}
