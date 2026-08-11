package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.semantics.types.TypeParameterType;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.TypeParameter;

import java.util.Objects;

public final class TypeParameterSymbol implements Symbol {
    private final TypeParameter declaration;
    private final Symbol owner;
    private final TypeParameterType type;

    public TypeParameterSymbol(TypeParameter declaration, Symbol owner) {
        this.declaration = Objects.requireNonNull(declaration, "Type parameter declaration must not be null.");
        this.owner = Objects.requireNonNull(owner, "Type parameter owner must not be null.");
        type = new TypeParameterType(this);
    }

    public TypeParameter declaration() {
        return declaration;
    }

    public Symbol owner() {
        return owner;
    }

    public TypeParameterType type() {
        return type;
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.TYPE_PARAMETER;
    }

    @Override
    public String name() {
        return declaration.name();
    }

    @Override
    public SourceSpan span() {
        return declaration.span();
    }
}
