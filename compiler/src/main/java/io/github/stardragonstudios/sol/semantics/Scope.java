package io.github.stardragonstudios.sol.semantics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Scope {
    private final ScopeKind kind;
    private final Optional<Scope> parent;
    private final LinkedHashMap<String, Symbol> symbols = new LinkedHashMap<>();

    private boolean frozen;

    public Scope(ScopeKind kind) {
        this.kind = Objects.requireNonNull(kind, "Scope kind must not be null.");

        parent = Optional.empty();
    }

    public Scope(ScopeKind kind, Scope parent) {
        this.kind = Objects.requireNonNull(kind, "Scope kind must not be null.");
        this.parent = Optional.of(Objects.requireNonNull(parent, "Parent scope must not be null."));
    }

    public ScopeKind kind() {
        return kind;
    }

    public Optional<Scope> parent() {
        return parent;
    }

    public boolean isFrozen() {
        return frozen;
    }

    void freeze() {
        frozen = true;
    }

    public boolean declare(Symbol symbol) {
        Objects.requireNonNull(symbol, "Declared symbol must not be null.");

        if (frozen) throw new IllegalStateException("Cannot declare symbols in a frozen scope.");
        if (symbols.containsKey(symbol.name())) return false;

        symbols.put(symbol.name(), symbol);

        return true;
    }

    public List<Symbol> declaredSymbols() {
        return List.copyOf(symbols.values());
    }

    public Optional<Symbol> lookupLocal(String name) {
        validateLookupName(name);

        return Optional.ofNullable(symbols.get(name));
    }

    public Optional<Symbol> lookup(String name) {
        validateLookupName(name);

        var localSymbol =
            Optional.ofNullable(symbols.get(name));

        if (localSymbol.isPresent()) {
            return localSymbol;
        }

        return parent.flatMap(
            scope -> scope.lookup(name)
        );
    }

    private static void validateLookupName(String name) {
        Objects.requireNonNull(name, "Symbol lookup name must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("Symbol lookup name must not be blank.");
    }
}
