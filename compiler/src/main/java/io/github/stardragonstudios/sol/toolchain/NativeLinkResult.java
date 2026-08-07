package io.github.stardragonstudios.sol.toolchain;

import java.nio.file.Path;
import java.util.Objects;

public record NativeLinkResult(Path executable, NativeLinkCommand command, String standardOutput, String standardError) {
    public NativeLinkResult {
        executable = Objects.requireNonNull(executable, "Linked native executable path must not be null.").toAbsolutePath().normalize();

        Objects.requireNonNull(command, "Successful native link command must not be null.");
        Objects.requireNonNull(standardOutput, "Native linker standard output must not be null.");
        Objects.requireNonNull(standardError, "Native linker standard error must not be null.");
    }
}
