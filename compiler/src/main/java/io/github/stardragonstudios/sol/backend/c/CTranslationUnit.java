package io.github.stardragonstudios.sol.backend.c;

import java.util.Objects;

public record CTranslationUnit(String source) {
    public CTranslationUnit {
        Objects.requireNonNull(source, "C translation unit source must not be null.");

        if (source.indexOf('\r') >= 0)
            throw new IllegalArgumentException("C translation unit source must use '\\n' line endings.");

        if (!source.isEmpty() && (!source.endsWith("\n") || source.endsWith("\n\n")))
            throw new IllegalArgumentException("Non-empty C translation unit source must end with exactly one newline.");
    }
}
