package io.github.stardragonstudios.sol.ir;

public record IrBlockId(int index) {
    public IrBlockId {
        if (index < 0) throw new IllegalArgumentException("IR block index must not be negative.");
    }

    @Override
    public String toString() {
        return "block" + index;
    }
}
