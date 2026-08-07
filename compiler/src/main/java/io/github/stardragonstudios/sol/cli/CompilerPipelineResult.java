package io.github.stardragonstudios.sol.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record CompilerPipelineResult(Path sourceFile, Path executable, List<SourceDiagnostic> diagnostics) {
    public CompilerPipelineResult {
        sourceFile = normalize(sourceFile, "Compiled source file must not be null.");
        executable = normalize(executable, "Compiled executable path must not be null.");

        Objects.requireNonNull(diagnostics, "Successful compiler diagnostics must not be null.");

        diagnostics = List.copyOf(diagnostics);
    }

    private static Path normalize(Path path, String nullMessage) {
        return Objects.requireNonNull(path, nullMessage).toAbsolutePath().normalize();
    }
}
