package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.StructFieldDeclaration;
import io.github.stardragonstudios.sol.syntax.TypeReference;

import java.util.Objects;

public record StructFieldSymbol(StructSymbol owner, StructFieldDeclaration declaration, int index) implements Symbol {
    public StructFieldSymbol {
        Objects.requireNonNull(owner, "Struct field owner must not be null.");
        Objects.requireNonNull(declaration, "Struct field declaration must not be null.");

        if (index < 0) throw new IllegalArgumentException("Struct field index must not be negative.");
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.STRUCT_FIELD;
    }

    @Override
    public String name() {
        return declaration.name();
    }

    public TypeReference type() {
        return declaration.type();
    }

    @Override
    public SourceSpan span() {
        return declaration.span();
    }
}
