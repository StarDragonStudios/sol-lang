package io.github.stardragonstudios.sol.cli;

import java.util.List;
import java.util.Objects;

public record ProgramRunResult(int exitCode, List<SourceDiagnostic> diagnostics) {
    public ProgramRunResult {
        Objects.requireNonNull(diagnostics, "Run diagnostics must not be null.");

        diagnostics = List.copyOf(diagnostics);
    }
}
