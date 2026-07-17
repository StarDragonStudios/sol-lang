package io.github.stardragonstudios.sol.diagnostics;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.Objects;

public record Diagnostic(String code, DiagnosticSeverity severity, String message, SourceSpan span) {
    public Diagnostic {
        Objects.requireNonNull(
            code,
            "Diagnostic code must not be null."
        );

        Objects.requireNonNull(
            severity,
            "Diagnostic severity must not be null."
        );

        Objects.requireNonNull(
            message,
            "Diagnostic message must not be null."
        );

        Objects.requireNonNull(
            span,
            "Diagnostic source span must not be null."
        );

        if (code.isBlank()) {
            throw new IllegalArgumentException(
                "Diagnostic code must not be blank."
            );
        }

        if (message.isBlank()) {
            throw new IllegalArgumentException(
                "Diagnostic message must not be blank."
            );
        }
    }
}
