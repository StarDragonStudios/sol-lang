package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.Parameter;
import io.github.stardragonstudios.sol.syntax.TypeReference;

import java.util.Objects;

public record ParameterSymbol(
    Parameter declaration
) implements Symbol {
    public ParameterSymbol {
        Objects.requireNonNull(
            declaration,
            "Parameter symbol declaration must not be null."
        );
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.PARAMETER;
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
