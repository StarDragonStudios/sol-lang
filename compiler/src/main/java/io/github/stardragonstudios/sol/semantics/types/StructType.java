package io.github.stardragonstudios.sol.semantics.types;

import io.github.stardragonstudios.sol.semantics.StructSymbol;

import java.util.Objects;

public final class StructType implements TypeSymbol {
    private final StructSymbol symbol;

    public StructType(StructSymbol symbol) {
        this.symbol = Objects.requireNonNull(symbol, "Struct type symbol must not be null.");
    }

    public StructSymbol symbol() {
        return symbol;
    }

    @Override
    public TypeKind kind() {
        return TypeKind.STRUCT;
    }

    @Override
    public String name() {
        return symbol.name();
    }

    @Override
    public boolean isValue() {
        return true;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isIntegral() {
        return false;
    }

    @Override
    public String toString() {
        return name();
    }
}
