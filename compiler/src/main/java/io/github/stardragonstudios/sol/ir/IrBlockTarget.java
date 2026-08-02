package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrBlockTarget(IrBlockId id) {
    public IrBlockTarget {
        Objects.requireNonNull(id, "IR block target identifier must not be null.");
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
