package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import java.util.Objects;

public record FunctionSymbol(FunctionDeclaration declaration) implements Symbol {
    public FunctionSymbol {
        Objects.requireNonNull(
            declaration,
            "Function symbol declaration must not be null."
        );
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.FUNCTION;
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
