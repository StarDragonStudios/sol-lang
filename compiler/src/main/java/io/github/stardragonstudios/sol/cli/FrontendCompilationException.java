package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FrontendCompilationException
    extends RuntimeException {

    private final List<SourceDiagnostic> diagnostics;

    public FrontendCompilationException(List<SourceDiagnostic> diagnostics) {
        super(failureMessage(diagnostics));

        this.diagnostics = copyAndValidate(diagnostics);
    }

    public List<SourceDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static String failureMessage(List<SourceDiagnostic> diagnostics) {
        var validated = copyAndValidate(diagnostics);
        var errorCount = validated.stream().filter(diagnostic -> diagnostic.diagnostic().severity() == DiagnosticSeverity.ERROR).count();

        return "Frontend compilation failed with %d error diagnostic(s).".formatted(errorCount);
    }

    private static List<SourceDiagnostic> copyAndValidate(List<SourceDiagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "Frontend diagnostics must not be null.");

        if (diagnostics.isEmpty()) throw new IllegalArgumentException("Frontend compilation failure must contain diagnostics.");

        var copy = new ArrayList<SourceDiagnostic>(diagnostics.size());
        var hasError = false;

        for (var diagnostic : diagnostics) {
            var validated = Objects.requireNonNull(diagnostic, "Frontend diagnostics must not contain null values.");

            copy.add(validated);

            if (validated.diagnostic().severity() == DiagnosticSeverity.ERROR) hasError = true;
        }

        if (!hasError) throw new IllegalArgumentException("Frontend compilation failure must contain at least one error diagnostic.");

        return List.copyOf(copy);
    }
}
