package io.github.stardragonstudios.sol.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record CompilerCommandLine(Path sourceFile, Optional<Path> output, boolean keepIntermediates) {
    public CompilerCommandLine {
        Objects.requireNonNull(sourceFile, "Compiler source file must not be null.");
        Objects.requireNonNull(output, "Compiler output path must not be null.");
    }
}
