package io.github.stardragonstudios.sol.ir;

public interface IrType {
    String displayName();

    boolean isValue();

    boolean isNumeric();

    boolean isIntegral();
}
