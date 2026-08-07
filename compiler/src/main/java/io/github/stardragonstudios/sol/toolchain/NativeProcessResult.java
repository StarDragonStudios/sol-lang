package io.github.stardragonstudios.sol.toolchain;

import java.util.Objects;

public record NativeProcessResult(int exitCode, String standardOutput, String standardError) {
    public NativeProcessResult {
        Objects.requireNonNull(standardOutput, "Native process standard output must not be null.");
        Objects.requireNonNull(standardError, "Native process standard error must not be null.");
    }

    public boolean succeeded() {
        return exitCode == 0;
    }
}
