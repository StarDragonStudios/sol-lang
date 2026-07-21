package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.InjectionDeclaration;

import java.util.Objects;

public record NamespaceSymbol(String name, ModuleSymbol targetModule, InjectionDeclaration declaration) implements Symbol {
    public NamespaceSymbol {
        Objects.requireNonNull(name, "Namespace symbol name must not be null.");
        Objects.requireNonNull(targetModule, "Namespace target module must not be null.");
        Objects.requireNonNull(declaration, "Namespace injection declaration must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Namespace symbol name must not be blank.");
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.MODULE_NAMESPACE;
    }

    @Override
    public SourceSpan span() {
        return declaration.span();
    }
}
