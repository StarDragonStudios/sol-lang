package io.github.stardragonstudios.sol.ir;

public record IrLocalId(int index) {
    public IrLocalId {
        if (index < 0) throw new IllegalArgumentException("IR local index must not be negative.");
    }

    @Override
    public String toString() {
        return "local" + index;
    }
}
