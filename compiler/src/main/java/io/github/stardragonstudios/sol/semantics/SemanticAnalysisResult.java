package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

public record SemanticAnalysisResult(SemanticModel model, List<Diagnostic> diagnostics) {
    public SemanticAnalysisResult {
        Objects.requireNonNull(model, "Semantic model must not be null.");
        Objects.requireNonNull(diagnostics, "Semantic diagnostics must not be null.");

        diagnostics = List.copyOf(diagnostics);
    }
}
