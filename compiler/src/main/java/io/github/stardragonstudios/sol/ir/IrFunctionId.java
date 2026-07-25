package io.github.stardragonstudios.sol.ir;

public record IrFunctionId(int index) {
    public IrFunctionId {
        if (index < 0) throw new IllegalArgumentException("IR function index must not be negative.");
    }

    @Override
    public String toString() {
        return "function" + index;
    }
}
