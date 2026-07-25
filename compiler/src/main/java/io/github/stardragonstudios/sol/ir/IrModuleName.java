package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrModuleName(List<String> segments) {
    public IrModuleName {
        Objects.requireNonNull(segments, "IR module name segments must not be null.");

        segments = List.copyOf(segments);

        if (segments.isEmpty()) throw new IllegalArgumentException("IR module name must contain at least one segment.");

        for (var segment : segments) {
            Objects.requireNonNull(segment, "IR module name segments must not contain null values.");

            if (segment.isBlank()) throw new IllegalArgumentException("IR module name segments must not be blank.");
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
