package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.InjectionDeclaration;
import io.github.stardragonstudios.sol.syntax.InjectionKind;
import io.github.stardragonstudios.sol.syntax.ModulePath;

import java.util.Objects;

public record ImportedNameSymbol(
    String name,
    InjectionDeclaration declaration
) implements Symbol {
    public ImportedNameSymbol {
        Objects.requireNonNull(
            name,
            "Imported symbol name must not be null."
        );

        Objects.requireNonNull(
            declaration,
            "Imported symbol declaration must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Imported symbol name must not be blank."
            );
        }

        if (
            declaration.kind()
                != InjectionKind.DIRECT
        ) {
            throw new IllegalArgumentException(
                "Imported name symbols require "
                    + "a direct injection declaration."
            );
        }
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.IMPORTED_NAME;
    }

    public ModulePath modulePath() {
        return declaration.modulePath();
    }

    @Override
    public SourceSpan span() {
        return declaration.span();
    }
}
