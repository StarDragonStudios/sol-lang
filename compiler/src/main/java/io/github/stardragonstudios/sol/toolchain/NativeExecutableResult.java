package io.github.stardragonstudios.sol.toolchain;

import java.nio.file.Path;
import java.util.Objects;

public record NativeExecutableResult(NativeLinkResult linkResult, Path objectFile, TemporaryObjectPolicy objectPolicy) {
    public NativeExecutableResult {
        Objects.requireNonNull(linkResult, "Native executable link result must not be null.");

        objectFile = Objects.requireNonNull(objectFile, "Native executable object-file path must not be null.").toAbsolutePath().normalize();

        Objects.requireNonNull(objectPolicy, "Native executable object policy must not be null.");
    }

    public Path executable() {
        return linkResult.executable();
    }

    public boolean retainedObjectFile() {
        return objectPolicy == TemporaryObjectPolicy.KEEP;
    }
}
