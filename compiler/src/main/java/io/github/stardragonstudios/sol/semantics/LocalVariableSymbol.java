package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.TypeReference;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationKind;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import java.util.Objects;

public record LocalVariableSymbol(
    VariableDeclarationStatement declaration
) implements Symbol {
    public LocalVariableSymbol {
        Objects.requireNonNull(
            declaration,
            "Local variable symbol declaration must not be null."
        );
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.LOCAL_VARIABLE;
    }

    @Override
    public String name() {
        return declaration.name();
    }

    public TypeReference type() {
        return declaration.type();
    }

    public VariableDeclarationKind declarationKind() {
        return declaration.kind();
    }

    public boolean isMutable() {
        return declaration.kind() == VariableDeclarationKind.MUTABLE_LET;
    }

    public boolean isConstant() {
        return declaration.kind() == VariableDeclarationKind.CONST;
    }

    @Override
    public SourceSpan span() {
        return declaration.span();
    }
}
