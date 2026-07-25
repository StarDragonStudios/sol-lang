package io.github.stardragonstudios.sol.ir;

public record IrValueId(int index) {
    public IrValueId {
        if (index < 0) throw new IllegalArgumentException("IR value index must not be negative.");
    }

    @Override
    public String toString() {
        return "%" + index;
    }
}
