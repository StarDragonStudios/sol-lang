package io.github.stardragonstudios.sol.cli;

import java.nio.file.Path;
import java.util.Objects;

public record RunCommandLine(Path sourceFile) {
    public RunCommandLine {
        Objects.requireNonNull(sourceFile, "Run source file must not be null.");
    }
}
