inject namespace std.memory as memory
inject std.collections.vector
inject frontend.source only SourceSpan
inject frontend.syntax
inject semantics.types

struct SemanticSymbol
    kind: int
    name: string
    span: SourceSpan
    declaration: pointer<SyntaxNode>
    owner: pointer<SemanticSymbol>
    type: pointer<SemanticType>
    index: int
    mutable: boolean
    constant: boolean
    children: pointer<Vector<pointer<SemanticSymbol>>>
end

fn create_function_symbol(
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    if !semantic_symbol_declaration_has_kind(
        declaration,
        syntax_kind_function_declaration()
    ) then
        return null
    end

    let symbol: pointer<SemanticSymbol> = create_semantic_symbol(
        semantic_symbol_kind_function(),
        declaration->text,
        declaration,
        null,
        -1
    )

    if symbol == null then
        return null
    end

    if !populate_type_parameter_symbols(symbol, declaration) then
        destroy_semantic_symbol(symbol)
        return null
    end

    return symbol
end

fn create_parameter_symbol(
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    if !semantic_symbol_declaration_has_kind(
        declaration,
        syntax_kind_parameter()
    ) then
        return null
    end

    return create_semantic_symbol(
        semantic_symbol_kind_parameter(),
        declaration->text,
        declaration,
        null,
        -1
    )
end

fn create_local_variable_symbol(
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    if !semantic_symbol_declaration_has_kind(
        declaration,
        syntax_kind_variable_declaration_statement()
    ) then
        return null
    end

    let symbol: pointer<SemanticSymbol> = create_semantic_symbol(
        semantic_symbol_kind_local_variable(),
        declaration->text,
        declaration,
        null,
        -1
    )

    if symbol == null then
        return null
    end

    symbol->mutable = declaration->variant == syntax_variable_mutable_let()
    symbol->constant = declaration->variant == syntax_variable_const()
    return symbol
end

fn create_imported_name_symbol(
    name: string,
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    if name == "" || !semantic_symbol_declaration_has_kind(
        declaration,
        syntax_kind_injection_declaration()
    ) then
        return null
    end

    if declaration->variant != syntax_injection_direct() then
        return null
    end

    return create_semantic_symbol(
        semantic_symbol_kind_imported_name(),
        name,
        declaration,
        null,
        -1
    )
end

fn create_module_namespace_symbol(
    name: string,
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    if name == "" || !semantic_symbol_declaration_has_kind(
        declaration,
        syntax_kind_injection_declaration()
    ) then
        return null
    end

    if declaration->variant != syntax_injection_namespace() then
        return null
    end

    return create_semantic_symbol(
        semantic_symbol_kind_module_namespace(),
        name,
        declaration,
        null,
        -1
    )
end

fn create_struct_symbol(
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    if !semantic_symbol_declaration_has_kind(
        declaration,
        syntax_kind_struct_declaration()
    ) then
        return null
    end

    let symbol: pointer<SemanticSymbol> = create_semantic_symbol(
        semantic_symbol_kind_struct(),
        declaration->text,
        declaration,
        null,
        -1
    )

    if symbol == null then
        return null
    end

    if !populate_type_parameter_symbols(symbol, declaration) then
        destroy_semantic_symbol(symbol)
        return null
    end

    @mut let index: int = 0
    @mut let field_index: int = 0
    let count: int = syntax_child_count(declaration)

    while index < count do
        let child: pointer<SyntaxNode> = syntax_child(declaration, index)

        if child->kind == syntax_kind_struct_field_declaration() then
            let field: pointer<SemanticSymbol> = create_struct_field_symbol(
                symbol,
                child,
                field_index
            )

            if field == null then
                destroy_semantic_symbol(symbol)
                return null
            end

            vector_push<pointer<SemanticSymbol>>(symbol->children, field)
            field_index = field_index + 1
        end

        index = index + 1
    end

    let arguments: pointer<Vector<pointer<SemanticType>>> = create_vector<pointer<SemanticType>>()
    index = 0

    while index < semantic_symbol_type_parameter_count(symbol) do
        vector_push<pointer<SemanticType>>(
            arguments,
            semantic_symbol_type_parameter(symbol, index)->type
        )
        index = index + 1
    end

    symbol->type = create_struct_type(declaration, arguments)
    destroy_vector<pointer<SemanticType>>(arguments)

    if symbol->type == null then
        destroy_semantic_symbol(symbol)
        return null
    end

    return symbol
end

fn create_struct_field_symbol(
    owner: pointer<SemanticSymbol>,
    declaration: pointer<SyntaxNode>,
    index: int
) -> pointer<SemanticSymbol>
    if owner == null || declaration == null || index < 0 then
        return null
    end

    if owner->kind != semantic_symbol_kind_struct() || declaration->kind != syntax_kind_struct_field_declaration() then
        return null
    end

    return create_semantic_symbol(
        semantic_symbol_kind_struct_field(),
        declaration->text,
        declaration,
        owner,
        index
    )
end

fn create_type_parameter_symbol(
    owner: pointer<SemanticSymbol>,
    declaration: pointer<SyntaxNode>,
    index: int
) -> pointer<SemanticSymbol>
    if owner == null || declaration == null || index < 0 then
        return null
    end

    if declaration->kind != syntax_kind_type_parameter() then
        return null
    end

    let symbol: pointer<SemanticSymbol> = create_semantic_symbol(
        semantic_symbol_kind_type_parameter(),
        declaration->text,
        declaration,
        owner,
        index
    )

    if symbol == null then
        return null
    end

    symbol->type = create_type_parameter_type(declaration)

    if symbol->type == null then
        destroy_semantic_symbol(symbol)
        return null
    end

    return symbol
end

fn populate_type_parameter_symbols(
    owner: pointer<SemanticSymbol>,
    declaration: pointer<SyntaxNode>
) -> boolean
    @mut let index: int = 0
    @mut let parameter_index: int = 0
    let count: int = syntax_child_count(declaration)

    while index < count do
        let child: pointer<SyntaxNode> = syntax_child(declaration, index)

        if child->kind == syntax_kind_type_parameter() then
            let parameter: pointer<SemanticSymbol> = create_type_parameter_symbol(
                owner,
                child,
                parameter_index
            )

            if parameter == null then
                return false
            end

            vector_push<pointer<SemanticSymbol>>(owner->children, parameter)
            parameter_index = parameter_index + 1
        end

        index = index + 1
    end

    return true
end

fn create_semantic_symbol(
    kind: int,
    name: string,
    declaration: pointer<SyntaxNode>,
    owner: pointer<SemanticSymbol>,
    index: int
) -> pointer<SemanticSymbol>
    if name == "" || declaration == null then
        return null
    end

    let symbol: pointer<SemanticSymbol> = memory::allocate<SemanticSymbol>(1)

    if symbol == null then
        return null
    end

    symbol->kind = kind
    symbol->name = name
    symbol->span = declaration->span
    symbol->declaration = declaration
    symbol->owner = owner
    symbol->type = null
    symbol->index = index
    symbol->mutable = false
    symbol->constant = false
    symbol->children = create_vector<pointer<SemanticSymbol>>()
    return symbol
end

fn destroy_semantic_symbol(symbol: pointer<SemanticSymbol>) -> void
    if symbol == null then
        return
    end

    destroy_semantic_type(symbol->type)
    symbol->type = null

    @mut let index: int = 0
    let count: int = vector_length<pointer<SemanticSymbol>>(symbol->children)

    while index < count do
        destroy_semantic_symbol(
            vector_get<pointer<SemanticSymbol>>(symbol->children, index)
        )
        index = index + 1
    end

    destroy_vector<pointer<SemanticSymbol>>(symbol->children)
    symbol->declaration = null
    symbol->owner = null
    symbol->children = null
    memory::free<SemanticSymbol>(symbol)
    return
end

fn semantic_symbol_child_count(symbol: pointer<SemanticSymbol>) -> int
    if symbol == null then
        return 0
    end

    return vector_length<pointer<SemanticSymbol>>(symbol->children)
end

fn semantic_symbol_child(
    symbol: pointer<SemanticSymbol>,
    index: int
) -> pointer<SemanticSymbol>
    if symbol == null || index < 0 || index >= semantic_symbol_child_count(symbol) then
        return null
    end

    return vector_get<pointer<SemanticSymbol>>(symbol->children, index)
end

fn semantic_symbol_type_parameter_count(symbol: pointer<SemanticSymbol>) -> int
    if symbol == null then
        return 0
    end

    @mut let count: int = 0
    @mut let index: int = 0
    let child_count: int = semantic_symbol_child_count(symbol)

    while index < child_count do
        if semantic_symbol_child(symbol, index)->kind == semantic_symbol_kind_type_parameter() then
            count = count + 1
        end

        index = index + 1
    end

    return count
end

fn semantic_symbol_type_parameter(
    symbol: pointer<SemanticSymbol>,
    requested_index: int
) -> pointer<SemanticSymbol>
    if symbol == null || requested_index < 0 then
        return null
    end

    @mut let found: int = 0
    @mut let index: int = 0
    let count: int = semantic_symbol_child_count(symbol)

    while index < count do
        let child: pointer<SemanticSymbol> = semantic_symbol_child(symbol, index)

        if child->kind == semantic_symbol_kind_type_parameter() then
            if found == requested_index then
                return child
            end

            found = found + 1
        end

        index = index + 1
    end

    return null
end

fn semantic_struct_field_count(symbol: pointer<SemanticSymbol>) -> int
    if symbol == null then
        return 0
    end

    if symbol->kind != semantic_symbol_kind_struct() then
        return 0
    end

    @mut let count: int = 0
    @mut let index: int = 0
    let child_count: int = semantic_symbol_child_count(symbol)

    while index < child_count do
        if semantic_symbol_child(symbol, index)->kind == semantic_symbol_kind_struct_field() then
            count = count + 1
        end

        index = index + 1
    end

    return count
end

fn semantic_struct_field(
    symbol: pointer<SemanticSymbol>,
    requested_index: int
) -> pointer<SemanticSymbol>
    if symbol == null || requested_index < 0 then
        return null
    end

    @mut let found: int = 0
    @mut let index: int = 0
    let count: int = semantic_symbol_child_count(symbol)

    while index < count do
        let child: pointer<SemanticSymbol> = semantic_symbol_child(symbol, index)

        if child->kind == semantic_symbol_kind_struct_field() then
            if found == requested_index then
                return child
            end

            found = found + 1
        end

        index = index + 1
    end

    return null
end

fn semantic_struct_field_by_name(
    symbol: pointer<SemanticSymbol>,
    name: string
) -> pointer<SemanticSymbol>
    if symbol == null || name == "" then
        return null
    end

    @mut let index: int = 0
    let count: int = semantic_struct_field_count(symbol)

    while index < count do
        let field: pointer<SemanticSymbol> = semantic_struct_field(symbol, index)

        if field->name == name then
            return field
        end

        index = index + 1
    end

    return null
end

fn semantic_symbol_declared_type_reference(
    symbol: pointer<SemanticSymbol>
) -> pointer<SyntaxNode>
    if symbol == null then
        return null
    end

    if symbol->kind == semantic_symbol_kind_parameter() || symbol->kind == semantic_symbol_kind_local_variable() || symbol->kind == semantic_symbol_kind_struct_field() then
        return syntax_child(symbol->declaration, 1)
    end

    return null
end

fn semantic_symbol_module_path(
    symbol: pointer<SemanticSymbol>
) -> pointer<SyntaxNode>
    if symbol == null then
        return null
    end

    if symbol->kind != semantic_symbol_kind_imported_name() && symbol->kind != semantic_symbol_kind_module_namespace() then
        return null
    end

    return syntax_child(symbol->declaration, 0)
end

fn semantic_symbol_declaration_has_kind(
    declaration: pointer<SyntaxNode>,
    expected_kind: int
) -> boolean
    if declaration == null then
        return false
    end

    return declaration->kind == expected_kind
end

fn semantic_symbol_kind_function() -> int
    return 1
end

fn semantic_symbol_kind_parameter() -> int
    return 2
end

fn semantic_symbol_kind_local_variable() -> int
    return 3
end

fn semantic_symbol_kind_struct() -> int
    return 4
end

fn semantic_symbol_kind_struct_field() -> int
    return 5
end

fn semantic_symbol_kind_type_parameter() -> int
    return 6
end

fn semantic_symbol_kind_imported_name() -> int
    return 7
end

fn semantic_symbol_kind_module_namespace() -> int
    return 8
end
