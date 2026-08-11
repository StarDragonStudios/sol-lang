package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import java.util.List;
import java.util.Objects;

public final class FunctionSymbol implements Symbol {
    private final FunctionDeclaration declaration;
    private final List<TypeParameterSymbol> typeParameters;

    public FunctionSymbol(FunctionDeclaration declaration) {
        this.declaration = Objects.requireNonNull(declaration, "Function symbol declaration must not be null.");
        typeParameters = declaration.typeParameters().stream().map(parameter -> new TypeParameterSymbol(parameter, this)).toList();
    }

    public FunctionDeclaration declaration() {
        return declaration;
    }

    public List<TypeParameterSymbol> typeParameters() {
        return typeParameters;
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
