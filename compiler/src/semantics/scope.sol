inject namespace std.memory as memory
inject std.collections.vector
inject semantics.symbol only SemanticSymbol, destroy_semantic_symbol, semantic_symbol_kind_class_field, semantic_symbol_kind_method

struct Scope
    kind: int
    parent: pointer<Scope>
    symbols: pointer<Vector<pointer<SemanticSymbol>>>
    frozen: boolean
end

fn create_root_scope(kind: int) -> pointer<Scope>
    if !scope_kind_is_valid(kind) then
        return null
    end

    return create_scope(kind, null)
end

fn create_child_scope(kind: int, parent: pointer<Scope>) -> pointer<Scope>
    if !scope_kind_is_valid(kind) || parent == null then
        return null
    end

    return create_scope(kind, parent)
end

fn create_scope(kind: int, parent: pointer<Scope>) -> pointer<Scope>
    let scope: pointer<Scope> = memory::allocate<Scope>(1)

    if scope == null then
        return null
    end

    scope->kind = kind
    scope->parent = parent
    scope->symbols = create_vector<pointer<SemanticSymbol>>()
    scope->frozen = false
    return scope
end

fn destroy_scope(scope: pointer<Scope>) -> void
    if scope == null then
        return
    end

    @mut let index: int = 0
    let count: int = vector_length<pointer<SemanticSymbol>>(scope->symbols)

    while index < count do
        destroy_semantic_symbol(
            vector_get<pointer<SemanticSymbol>>(scope->symbols, index)
        )
        index = index + 1
    end

    destroy_vector<pointer<SemanticSymbol>>(scope->symbols)
    scope->parent = null
    scope->symbols = null
    memory::free<Scope>(scope)
    return
end

fn scope_declare(
    scope: pointer<Scope>,
    symbol: pointer<SemanticSymbol>
) -> int
    if scope == null || symbol == null then
        return scope_declare_invalid()
    end

    if scope->frozen then
        return scope_declare_frozen()
    end

    if scope_lookup_local(scope, symbol->name) != null then
        return scope_declare_duplicate()
    end

    vector_push<pointer<SemanticSymbol>>(scope->symbols, symbol)
    return scope_declare_success()
end

fn scope_declare_member(
    scope: pointer<Scope>,
    symbol: pointer<SemanticSymbol>
) -> int
    if scope == null || symbol == null || scope->kind != scope_kind_class() then
        return scope_declare_invalid()
    end

    if scope->frozen then
        return scope_declare_frozen()
    end

    @mut let index: int = 0
    let count: int = scope_declared_symbol_count(scope)

    while index < count do
        let existing: pointer<SemanticSymbol> = scope_declared_symbol(scope, index)

        if existing->name == symbol->name && existing->kind == semantic_symbol_kind_class_field() && symbol->kind == semantic_symbol_kind_class_field() then
            return scope_declare_duplicate()
        end

        index = index + 1
    end

    vector_push<pointer<SemanticSymbol>>(scope->symbols, symbol)
    return scope_declare_success()
end

fn scope_lookup_class_field(
    scope: pointer<Scope>,
    name: string
) -> pointer<SemanticSymbol>
    if scope == null || name == "" || scope->kind != scope_kind_class() then
        return null
    end

    @mut let index: int = 0
    let count: int = scope_declared_symbol_count(scope)

    while index < count do
        let symbol: pointer<SemanticSymbol> = scope_declared_symbol(scope, index)

        if symbol->kind == semantic_symbol_kind_class_field() && symbol->name == name then
            return symbol
        end

        index = index + 1
    end

    return null
end

fn scope_class_method_count(
    scope: pointer<Scope>,
    name: string
) -> int
    if scope == null || name == "" || scope->kind != scope_kind_class() then
        return 0
    end

    @mut let found: int = 0
    @mut let index: int = 0
    let count: int = scope_declared_symbol_count(scope)

    while index < count do
        let symbol: pointer<SemanticSymbol> = scope_declared_symbol(scope, index)

        if symbol->kind == semantic_symbol_kind_method() && symbol->name == name then
            found = found + 1
        end

        index = index + 1
    end

    return found
end

fn scope_class_method(
    scope: pointer<Scope>,
    name: string,
    requested_index: int
) -> pointer<SemanticSymbol>
    if scope == null || name == "" || requested_index < 0 || scope->kind != scope_kind_class() then
        return null
    end

    @mut let found: int = 0
    @mut let index: int = 0
    let count: int = scope_declared_symbol_count(scope)

    while index < count do
        let symbol: pointer<SemanticSymbol> = scope_declared_symbol(scope, index)

        if symbol->kind == semantic_symbol_kind_method() && symbol->name == name then
            if found == requested_index then
                return symbol
            end

            found = found + 1
        end

        index = index + 1
    end

    return null
end

fn scope_freeze(scope: pointer<Scope>) -> boolean
    if scope == null then
        return false
    end

    scope->frozen = true
    return true
end

fn scope_declared_symbol_count(scope: pointer<Scope>) -> int
    if scope == null then
        return 0
    end

    return vector_length<pointer<SemanticSymbol>>(scope->symbols)
end

fn scope_declared_symbol(
    scope: pointer<Scope>,
    index: int
) -> pointer<SemanticSymbol>
    if scope == null || index < 0 || index >= scope_declared_symbol_count(scope) then
        return null
    end

    return vector_get<pointer<SemanticSymbol>>(scope->symbols, index)
end

fn scope_lookup_local(
    scope: pointer<Scope>,
    name: string
) -> pointer<SemanticSymbol>
    if scope == null || name == "" then
        return null
    end

    @mut let index: int = 0
    let count: int = scope_declared_symbol_count(scope)

    while index < count do
        let symbol: pointer<SemanticSymbol> = scope_declared_symbol(scope, index)

        if symbol->name == name then
            return symbol
        end

        index = index + 1
    end

    return null
end

fn scope_lookup(
    scope: pointer<Scope>,
    name: string
) -> pointer<SemanticSymbol>
    if scope == null || name == "" then
        return null
    end

    @mut let current: pointer<Scope> = scope

    while current != null do
        let local: pointer<SemanticSymbol> = scope_lookup_local(current, name)

        if local != null then
            return local
        end

        current = current->parent
    end

    return null
end

fn scope_kind_is_valid(kind: int) -> boolean
    return kind == scope_kind_module() || kind == scope_kind_function() || kind == scope_kind_block() || kind == scope_kind_class()
end

fn scope_kind_module() -> int
    return 1
end

fn scope_kind_function() -> int
    return 2
end

fn scope_kind_block() -> int
    return 3
end

fn scope_kind_class() -> int
    return 4
end

fn scope_declare_success() -> int
    return 0
end

fn scope_declare_duplicate() -> int
    return 1
end

fn scope_declare_frozen() -> int
    return 2
end

fn scope_declare_invalid() -> int
    return 3
end
