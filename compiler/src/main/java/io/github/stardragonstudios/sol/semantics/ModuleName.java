package io.github.stardragonstudios.sol.semantics;

import java.util.List;
import java.util.Objects;

public record ModuleName(List<String> segments) {
    public ModuleName {
        Objects.requireNonNull(segments, "Module name segments must not be null.");

        segments = List.copyOf(segments);

        if (segments.isEmpty())
            throw new IllegalArgumentException("Module name must contain at least one segment.");

        for (var segment : segments) {
            Objects.requireNonNull(segment, "Module name segments must not contain null values.");

            if (segment.isBlank())
                throw new IllegalArgumentException("Module name segments must not be blank.");
        }
    }

    public String simpleName() {
        return segments.getLast();
    }

    public String qualifiedName() {
        return String.join(".", segments);
    }

    @Override
    public String toString() {
        return qualifiedName();
    }
}
