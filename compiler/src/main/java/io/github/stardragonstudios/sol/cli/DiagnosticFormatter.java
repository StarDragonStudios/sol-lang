package io.github.stardragonstudios.sol.cli;

import java.util.Locale;
import java.util.Objects;

public final class DiagnosticFormatter {
    private DiagnosticFormatter() {}

    public static String format(SourceDiagnostic sourceDiagnostic) {
        Objects.requireNonNull(sourceDiagnostic, "Formatted source diagnostic must not be null.");

        var diagnostic = sourceDiagnostic.diagnostic();
        var position = diagnostic.span().start();
        var severity = diagnostic.severity().name().toLowerCase(Locale.ROOT);

        return "%s:%d:%d: %s [%s]: %s".formatted(sourceDiagnostic.sourceFile(), position.line(), position.column(), severity, diagnostic.code(), diagnostic.message());
    }
}
