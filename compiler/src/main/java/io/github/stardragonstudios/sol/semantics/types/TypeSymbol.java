package io.github.stardragonstudios.sol.semantics.types;

public interface TypeSymbol {
    TypeKind kind();

    String name();

    boolean isValue();

    boolean isNumeric();

    boolean isIntegral();
}
