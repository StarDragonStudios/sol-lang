package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;

import java.nio.file.Path;
import java.util.Objects;

public record SourceDiagnostic(Path sourceFile, Diagnostic diagnostic) {
    public SourceDiagnostic {
        sourceFile = Objects.requireNonNull(sourceFile, "Diagnostic source file must not be null.").toAbsolutePath().normalize();

        Objects.requireNonNull(diagnostic, "Source diagnostic must not be null.");
    }
}
