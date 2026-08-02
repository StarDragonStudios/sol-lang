package io.github.stardragonstudios.sol.ir;

public enum IrLocalKind {
    CONSTANT(false, true),
    IMMUTABLE(false, false),
    MUTABLE(true, false);

    private final boolean mutable;
    private final boolean constant;

    IrLocalKind(boolean mutable, boolean constant) {
        this.mutable = mutable;
        this.constant = constant;
    }

    public boolean isMutable() {
        return mutable;
    }

    public boolean isConstant() {
        return constant;
    }
}
