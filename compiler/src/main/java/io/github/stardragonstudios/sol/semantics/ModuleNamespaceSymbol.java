package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.InjectionDeclaration;
import io.github.stardragonstudios.sol.syntax.InjectionKind;
import io.github.stardragonstudios.sol.syntax.ModulePath;

import java.util.Objects;
import java.util.Optional;

public record ModuleNamespaceSymbol(
    String name,
    InjectionDeclaration declaration
) implements Symbol {
    public ModuleNamespaceSymbol {
        Objects.requireNonNull(
            name,
            "Module namespace name must not be null."
        );

        Objects.requireNonNull(
            declaration,
            "Module namespace declaration must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Module namespace name must not be blank."
            );
        }

        if (
            declaration.kind()
                != InjectionKind.NAMESPACE
        ) {
            throw new IllegalArgumentException(
                "Module namespace symbols require "
                    + "a namespace injection declaration."
            );
        }
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.MODULE_NAMESPACE;
    }

    public ModulePath modulePath() {
        return declaration.modulePath();
    }

    public Optional<String> explicitAlias() {
        return declaration.alias();
    }

    @Override
    public SourceSpan span() {
        return declaration.span();
    }
}
