package io.github.stardragonstudios.sol.semantics.types;

import io.github.stardragonstudios.sol.semantics.TypeParameterSymbol;

import java.util.Objects;

public final class TypeParameterType implements TypeSymbol {
    private final TypeParameterSymbol symbol;

    public TypeParameterType(TypeParameterSymbol symbol) {
        this.symbol = Objects.requireNonNull(symbol, "Type parameter type symbol must not be null.");
    }

    public TypeParameterSymbol symbol() {
        return symbol;
    }

    @Override
    public TypeKind kind() {
        return TypeKind.TYPE_PARAMETER;
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
