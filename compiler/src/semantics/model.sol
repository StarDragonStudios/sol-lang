inject namespace std.memory as memory
inject std.collections.vector
inject frontend.diagnostic only Diagnostic
inject frontend.source only SourceSpan
inject frontend.syntax only SyntaxNode
inject semantics.types
inject semantics.symbol only SemanticSymbol, destroy_semantic_symbol
inject semantics.scope

struct SourceModule
    name: string
    unit: pointer<SyntaxNode>
end

struct SemanticModule
    name: string
    unit: pointer<SyntaxNode>
    scope: pointer<Scope>
end

struct SemanticBinding
    kind: int
    node: pointer<SyntaxNode>
    scope: pointer<Scope>
    symbol: pointer<SemanticSymbol>
    type: pointer<SemanticType>
    module: pointer<SemanticModule>
    symbols: pointer<Vector<pointer<SemanticSymbol>>>
    types: pointer<Vector<pointer<SemanticType>>>
end

struct SemanticDiagnostic
    module_name: string
    diagnostic: Diagnostic
end

struct SemanticProgram
    catalog: pointer<TypeCatalog>
    modules: pointer<Vector<pointer<SemanticModule>>>
    scopes: pointer<Vector<pointer<Scope>>>
    owned_symbols: pointer<Vector<pointer<SemanticSymbol>>>
    owned_types: pointer<Vector<pointer<SemanticType>>>
    bindings: pointer<Vector<pointer<SemanticBinding>>>
    binding_buckets: pointer<Vector<pointer<Vector<pointer<SemanticBinding>>>>>
    diagnostics: pointer<Vector<SemanticDiagnostic>>
    entry_module: pointer<SemanticModule>
    entry_function: pointer<SemanticSymbol>
    require_entry_point: boolean
    complete: boolean
end

fn source_module(name: string, unit: pointer<SyntaxNode>) -> SourceModule
    return SourceModule {
        name: name,
        unit: unit
    }
end

fn create_semantic_program(require_entry_point: boolean) -> pointer<SemanticProgram>
    let program: pointer<SemanticProgram> = memory::allocate<SemanticProgram>(1)

    if program == null then
        return null
    end

    program->catalog = create_type_catalog()
    program->modules = create_vector<pointer<SemanticModule>>()
    program->scopes = create_vector<pointer<Scope>>()
    program->owned_symbols = create_vector<pointer<SemanticSymbol>>()
    program->owned_types = create_vector<pointer<SemanticType>>()
    program->bindings = create_vector<pointer<SemanticBinding>>()
    program->binding_buckets = create_vector<pointer<Vector<pointer<SemanticBinding>>>>()
    @mut let bucket_index: int = 0
    while bucket_index < semantic_binding_bucket_count() do
        vector_push<pointer<Vector<pointer<SemanticBinding>>>>(
            program->binding_buckets,
            null
        )
        bucket_index = bucket_index + 1
    end
    program->diagnostics = create_vector<SemanticDiagnostic>()
    program->entry_module = null
    program->entry_function = null
    program->require_entry_point = require_entry_point
    program->complete = false

    if program->catalog == null then
        destroy_semantic_program(program)
        return null
    end

    return program
end

fn destroy_semantic_program(program: pointer<SemanticProgram>) -> void
    if program == null then
        return
    end

    @mut let index: int = vector_length<pointer<SemanticBinding>>(program->bindings)

    while index > 0 do
        index = index - 1
        destroy_semantic_binding(
            vector_get<pointer<SemanticBinding>>(program->bindings, index)
        )
    end

    destroy_vector<pointer<SemanticBinding>>(program->bindings)

    index = vector_length<pointer<Vector<pointer<SemanticBinding>>>>(
        program->binding_buckets
    )
    while index > 0 do
        index = index - 1
        destroy_vector<pointer<SemanticBinding>>(
            vector_get<pointer<Vector<pointer<SemanticBinding>>>>(
                program->binding_buckets,
                index
            )
        )
    end
    destroy_vector<pointer<Vector<pointer<SemanticBinding>>>>(
        program->binding_buckets
    )

    index = vector_length<pointer<Scope>>(program->scopes)

    while index > 0 do
        index = index - 1
        destroy_scope(vector_get<pointer<Scope>>(program->scopes, index))
    end

    destroy_vector<pointer<Scope>>(program->scopes)

    index = vector_length<pointer<SemanticSymbol>>(program->owned_symbols)

    while index > 0 do
        index = index - 1
        destroy_semantic_symbol(
            vector_get<pointer<SemanticSymbol>>(program->owned_symbols, index)
        )
    end

    destroy_vector<pointer<SemanticSymbol>>(program->owned_symbols)

    index = vector_length<pointer<SemanticType>>(program->owned_types)

    while index > 0 do
        index = index - 1
        destroy_semantic_type(
            vector_get<pointer<SemanticType>>(program->owned_types, index)
        )
    end

    destroy_vector<pointer<SemanticType>>(program->owned_types)

    index = vector_length<pointer<SemanticModule>>(program->modules)

    while index > 0 do
        index = index - 1
        destroy_semantic_module(
            vector_get<pointer<SemanticModule>>(program->modules, index)
        )
    end

    destroy_vector<pointer<SemanticModule>>(program->modules)
    destroy_vector<SemanticDiagnostic>(program->diagnostics)
    destroy_type_catalog(program->catalog)
    program->catalog = null
    program->modules = null
    program->scopes = null
    program->owned_symbols = null
    program->owned_types = null
    program->bindings = null
    program->binding_buckets = null
    program->diagnostics = null
    program->entry_module = null
    program->entry_function = null
    memory::free<SemanticProgram>(program)
    return
end

fn create_semantic_module(
    program: pointer<SemanticProgram>,
    source: SourceModule
) -> pointer<SemanticModule>
    if program == null || source.name == "" || source.unit == null then
        return null
    end

    if semantic_program_module(program, source.name) != null then
        return null
    end

    let module_scope: pointer<Scope> = create_root_scope(scope_kind_module())

    if module_scope == null then
        return null
    end

    let module: pointer<SemanticModule> = memory::allocate<SemanticModule>(1)

    if module == null then
        destroy_scope(module_scope)
        return null
    end

    module->name = source.name
    module->unit = source.unit
    module->scope = module_scope
    vector_push<pointer<SemanticModule>>(program->modules, module)
    vector_push<pointer<Scope>>(program->scopes, module_scope)
    return module
end

fn destroy_semantic_module(module: pointer<SemanticModule>) -> void
    if module == null then
        return
    end

    module->unit = null
    module->scope = null
    memory::free<SemanticModule>(module)
    return
end

fn semantic_program_module_count(program: pointer<SemanticProgram>) -> int
    if program == null then
        return 0
    end

    return vector_length<pointer<SemanticModule>>(program->modules)
end

fn semantic_program_module_at(
    program: pointer<SemanticProgram>,
    index: int
) -> pointer<SemanticModule>
    if program == null || index < 0 || index >= semantic_program_module_count(program) then
        return null
    end

    return vector_get<pointer<SemanticModule>>(program->modules, index)
end

fn semantic_program_module(
    program: pointer<SemanticProgram>,
    name: string
) -> pointer<SemanticModule>
    if program == null || name == "" then
        return null
    end

    @mut let index: int = 0
    let count: int = semantic_program_module_count(program)

    while index < count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            index
        )

        if module->name == name then
            return module
        end

        index = index + 1
    end

    return null
end

fn semantic_program_create_scope(
    program: pointer<SemanticProgram>,
    kind: int,
    parent: pointer<Scope>
) -> pointer<Scope>
    if program == null || parent == null then
        return null
    end

    let scope: pointer<Scope> = create_child_scope(kind, parent)

    if scope != null then
        vector_push<pointer<Scope>>(program->scopes, scope)
    end

    return scope
end

fn semantic_program_own_type(
    program: pointer<SemanticProgram>,
    type: pointer<SemanticType>
) -> pointer<SemanticType>
    if program == null || type == null then
        return null
    end

    vector_push<pointer<SemanticType>>(program->owned_types, type)
    return type
end

fn semantic_program_own_symbol(
    program: pointer<SemanticProgram>,
    symbol: pointer<SemanticSymbol>
) -> pointer<SemanticSymbol>
    if program == null || symbol == null then
        return null
    end

    vector_push<pointer<SemanticSymbol>>(program->owned_symbols, symbol)
    return symbol
end

fn semantic_program_owned_type_count(program: pointer<SemanticProgram>) -> int
    if program == null then
        return 0
    end

    return vector_length<pointer<SemanticType>>(program->owned_types)
end

fn create_semantic_binding(
    kind: int,
    node: pointer<SyntaxNode>
) -> pointer<SemanticBinding>
    if node == null then
        return null
    end

    let binding: pointer<SemanticBinding> = memory::allocate<SemanticBinding>(1)

    if binding == null then
        return null
    end

    binding->kind = kind
    binding->node = node
    binding->scope = null
    binding->symbol = null
    binding->type = null
    binding->module = null
    binding->symbols = create_vector<pointer<SemanticSymbol>>()
    binding->types = create_vector<pointer<SemanticType>>()
    return binding
end

fn destroy_semantic_binding(binding: pointer<SemanticBinding>) -> void
    if binding == null then
        return
    end

    destroy_vector<pointer<SemanticSymbol>>(binding->symbols)
    destroy_vector<pointer<SemanticType>>(binding->types)
    binding->node = null
    binding->scope = null
    binding->symbol = null
    binding->type = null
    binding->module = null
    binding->symbols = null
    binding->types = null
    memory::free<SemanticBinding>(binding)
    return
end

fn semantic_program_add_binding(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>
) -> pointer<SemanticBinding>
    if program == null || node == null || kind < 1 || kind >= 20 then
        return null
    end

    let existing: pointer<SemanticBinding> = semantic_program_binding(
        program,
        kind,
        node
    )

    if existing != null then
        return existing
    end

    let binding: pointer<SemanticBinding> = create_semantic_binding(kind, node)

    if binding != null then
        let bucket_index: int = semantic_binding_bucket_index(kind, node)
        @mut let bucket: pointer<Vector<pointer<SemanticBinding>>> = vector_get<pointer<Vector<pointer<SemanticBinding>>>>(
            program->binding_buckets,
            bucket_index
        )

        if bucket == null then
            bucket = create_vector<pointer<SemanticBinding>>()
            vector_set<pointer<Vector<pointer<SemanticBinding>>>>(
                program->binding_buckets,
                bucket_index,
                bucket
            )
        end

        vector_push<pointer<SemanticBinding>>(program->bindings, binding)
        vector_push<pointer<SemanticBinding>>(bucket, binding)
    end

    return binding
end

fn semantic_binding_bucket_count() -> int
    return 4093
end

fn semantic_binding_bucket_index(kind: int, node: pointer<SyntaxNode>) -> int
    // Semantic analysis consumes finalized syntax trees: source offsets stay
    // fixed while bindings are alive. Offsets only select a bucket; pointer
    // identity below still distinguishes nodes and modules with equal spans.
    let count: int = semantic_binding_bucket_count()
    @mut let offset: int = node->span.start.offset % count

    if offset < 0 then
        offset = offset + count
    end

    return (offset * 20 + kind) % count
end

fn semantic_program_binding(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>
) -> pointer<SemanticBinding>
    if program == null || node == null then
        return null
    end

    if kind < 1 || kind >= 20 then
        return null
    end

    let bucket: pointer<Vector<pointer<SemanticBinding>>> = vector_get<pointer<Vector<pointer<SemanticBinding>>>>(
        program->binding_buckets,
        semantic_binding_bucket_index(kind, node)
    )

    if bucket == null then
        return null
    end

    @mut let index: int = 0
    let count: int = vector_length<pointer<SemanticBinding>>(bucket)

    while index < count do
        let binding: pointer<SemanticBinding> = vector_get<pointer<SemanticBinding>>(
            bucket,
            index
        )

        if binding->kind == kind && binding->node == node then
            return binding
        end

        index = index + 1
    end

    return null
end

fn semantic_program_record_scope(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>,
    scope: pointer<Scope>
) -> boolean
    let binding: pointer<SemanticBinding> = semantic_program_add_binding(
        program,
        kind,
        node
    )

    if binding == null || scope == null then
        return false
    end

    binding->scope = scope
    return true
end

fn semantic_program_record_symbol(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>,
    symbol: pointer<SemanticSymbol>
) -> boolean
    let binding: pointer<SemanticBinding> = semantic_program_add_binding(
        program,
        kind,
        node
    )

    if binding == null || symbol == null then
        return false
    end

    binding->symbol = symbol
    return true
end

fn semantic_program_record_type(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>,
    type: pointer<SemanticType>
) -> boolean
    let binding: pointer<SemanticBinding> = semantic_program_add_binding(
        program,
        kind,
        node
    )

    if binding == null || type == null then
        return false
    end

    binding->type = type
    return true
end

fn semantic_program_record_module(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>,
    module: pointer<SemanticModule>
) -> boolean
    let binding: pointer<SemanticBinding> = semantic_program_add_binding(
        program,
        kind,
        node
    )

    if binding == null || module == null then
        return false
    end

    binding->module = module
    return true
end

fn semantic_program_scope_of(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>
) -> pointer<Scope>
    let binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        kind,
        node
    )

    if binding == null then
        return null
    end

    return binding->scope
end

fn semantic_program_symbol_of(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    let binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        kind,
        node
    )

    if binding == null then
        return null
    end

    return binding->symbol
end

fn semantic_program_type_of(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>
) -> pointer<SemanticType>
    let binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        kind,
        node
    )

    if binding == null then
        return null
    end

    return binding->type
end

fn semantic_program_module_of(
    program: pointer<SemanticProgram>,
    kind: int,
    node: pointer<SyntaxNode>
) -> pointer<SemanticModule>
    let binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        kind,
        node
    )

    if binding == null then
        return null
    end

    return binding->module
end

fn semantic_binding_add_symbol(
    binding: pointer<SemanticBinding>,
    symbol: pointer<SemanticSymbol>
) -> boolean
    if binding == null || symbol == null then
        return false
    end

    vector_push<pointer<SemanticSymbol>>(binding->symbols, symbol)
    return true
end

fn semantic_binding_add_type(
    binding: pointer<SemanticBinding>,
    type: pointer<SemanticType>
) -> boolean
    if binding == null || type == null then
        return false
    end

    vector_push<pointer<SemanticType>>(binding->types, type)
    return true
end

fn semantic_binding_symbol_count(binding: pointer<SemanticBinding>) -> int
    if binding == null then
        return 0
    end

    return vector_length<pointer<SemanticSymbol>>(binding->symbols)
end

fn semantic_binding_symbol(
    binding: pointer<SemanticBinding>,
    index: int
) -> pointer<SemanticSymbol>
    if binding == null || index < 0 || index >= semantic_binding_symbol_count(binding) then
        return null
    end

    return vector_get<pointer<SemanticSymbol>>(binding->symbols, index)
end

fn semantic_binding_type_count(binding: pointer<SemanticBinding>) -> int
    if binding == null then
        return 0
    end

    return vector_length<pointer<SemanticType>>(binding->types)
end

fn semantic_binding_type(
    binding: pointer<SemanticBinding>,
    index: int
) -> pointer<SemanticType>
    if binding == null || index < 0 || index >= semantic_binding_type_count(binding) then
        return null
    end

    return vector_get<pointer<SemanticType>>(binding->types, index)
end

fn semantic_program_add_diagnostic(
    program: pointer<SemanticProgram>,
    module_name: string,
    code: string,
    message: string,
    span: SourceSpan
) -> boolean
    if program == null || code == "" then
        return false
    end

    vector_push<SemanticDiagnostic>(
        program->diagnostics,
        SemanticDiagnostic {
            module_name: module_name,
            diagnostic: Diagnostic {
                code: code,
                message: message,
                span: span
            }
        }
    )
    return true
end

fn semantic_program_diagnostic_count(program: pointer<SemanticProgram>) -> int
    if program == null then
        return 0
    end

    return vector_length<SemanticDiagnostic>(program->diagnostics)
end

fn semantic_program_diagnostic(
    program: pointer<SemanticProgram>,
    index: int
) -> SemanticDiagnostic
    return vector_get<SemanticDiagnostic>(program->diagnostics, index)
end

fn semantic_program_successful(program: pointer<SemanticProgram>) -> boolean
    if program == null then
        return false
    end

    return program->complete && semantic_program_diagnostic_count(program) == 0
end

fn semantic_model_function_scope(
    program: pointer<SemanticProgram>,
    declaration: pointer<SyntaxNode>
) -> pointer<Scope>
    return semantic_program_scope_of(
        program,
        semantic_binding_kind_function_scope(),
        declaration
    )
end

fn semantic_model_block_scope(
    program: pointer<SemanticProgram>,
    block: pointer<SyntaxNode>
) -> pointer<Scope>
    return semantic_program_scope_of(
        program,
        semantic_binding_kind_block_scope(),
        block
    )
end

fn semantic_model_class_scope(
    program: pointer<SemanticProgram>,
    declaration: pointer<SyntaxNode>
) -> pointer<Scope>
    return semantic_program_scope_of(
        program,
        semantic_binding_kind_class_scope(),
        declaration
    )
end

fn semantic_model_declared_symbol(
    program: pointer<SemanticProgram>,
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_declared_symbol(),
        declaration
    )
end

fn semantic_model_resolved_name(
    program: pointer<SemanticProgram>,
    expression: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_resolved_name(),
        expression
    )
end

fn semantic_model_assignment_target(
    program: pointer<SemanticProgram>,
    statement: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_assignment_target(),
        statement
    )
end

fn semantic_model_field_assignment_target(
    program: pointer<SemanticProgram>,
    statement: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_field_assignment_target(),
        statement
    )
end

fn semantic_model_constructed_struct(
    program: pointer<SemanticProgram>,
    expression: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_constructed_struct(),
        expression
    )
end

fn semantic_model_initialized_field(
    program: pointer<SemanticProgram>,
    initializer: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_initialized_field(),
        initializer
    )
end

fn semantic_model_accessed_field(
    program: pointer<SemanticProgram>,
    expression: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_accessed_field(),
        expression
    )
end

fn semantic_model_accessed_pointer_field(
    program: pointer<SemanticProgram>,
    expression: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_accessed_pointer_field(),
        expression
    )
end

fn semantic_model_type_of_reference(
    program: pointer<SemanticProgram>,
    reference: pointer<SyntaxNode>
) -> pointer<SemanticType>
    return semantic_program_type_of(
        program,
        semantic_binding_kind_resolved_type(),
        reference
    )
end

fn semantic_model_type_of_expression(
    program: pointer<SemanticProgram>,
    expression: pointer<SyntaxNode>
) -> pointer<SemanticType>
    return semantic_program_type_of(
        program,
        semantic_binding_kind_expression_type(),
        expression
    )
end

fn semantic_model_called_function(
    program: pointer<SemanticProgram>,
    call: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_called_function(),
        call
    )
end

fn semantic_model_call_type_argument_count(
    program: pointer<SemanticProgram>,
    call: pointer<SyntaxNode>
) -> int
    return semantic_binding_type_count(
        semantic_program_binding(
            program,
            semantic_binding_kind_called_function(),
            call
        )
    )
end

fn semantic_model_call_type_argument(
    program: pointer<SemanticProgram>,
    call: pointer<SyntaxNode>,
    index: int
) -> pointer<SemanticType>
    return semantic_binding_type(
        semantic_program_binding(
            program,
            semantic_binding_kind_called_function(),
            call
        ),
        index
    )
end

fn semantic_model_qualified_function(
    program: pointer<SemanticProgram>,
    expression: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_qualified_function(),
        expression
    )
end

fn semantic_model_injected_module(
    program: pointer<SemanticProgram>,
    injection: pointer<SyntaxNode>
) -> pointer<SemanticModule>
    return semantic_program_module_of(
        program,
        semantic_binding_kind_injected_module(),
        injection
    )
end

fn semantic_model_injected_namespace(
    program: pointer<SemanticProgram>,
    injection: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_injected_namespace(),
        injection
    )
end

fn semantic_model_direct_injected_symbol_count(
    program: pointer<SemanticProgram>,
    injection: pointer<SyntaxNode>
) -> int
    let binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        semantic_binding_kind_direct_injection(),
        injection
    )
    return semantic_binding_symbol_count(binding) / 2
end

fn semantic_model_direct_injected_symbol(
    program: pointer<SemanticProgram>,
    injection: pointer<SyntaxNode>,
    index: int
) -> pointer<SemanticSymbol>
    let binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        semantic_binding_kind_direct_injection(),
        injection
    )
    return semantic_binding_symbol(binding, index * 2 + 1)
end

fn semantic_binding_kind_function_scope() -> int
    return 1
end

fn semantic_binding_kind_block_scope() -> int
    return 2
end

fn semantic_binding_kind_declared_symbol() -> int
    return 3
end

fn semantic_binding_kind_resolved_name() -> int
    return 4
end

fn semantic_binding_kind_assignment_target() -> int
    return 5
end

fn semantic_binding_kind_field_assignment_target() -> int
    return 6
end

fn semantic_binding_kind_constructed_struct() -> int
    return 7
end

fn semantic_binding_kind_initialized_field() -> int
    return 8
end

fn semantic_binding_kind_accessed_field() -> int
    return 9
end

fn semantic_binding_kind_accessed_pointer_field() -> int
    return 10
end

fn semantic_binding_kind_resolved_type() -> int
    return 11
end

fn semantic_binding_kind_expression_type() -> int
    return 12
end

fn semantic_binding_kind_called_function() -> int
    return 13
end

fn semantic_binding_kind_qualified_function() -> int
    return 14
end

fn semantic_binding_kind_injected_module() -> int
    return 15
end

fn semantic_binding_kind_direct_injection() -> int
    return 16
end

fn semantic_binding_kind_injected_namespace() -> int
    return 17
end

fn semantic_binding_kind_import_target() -> int
    return 18
end

fn semantic_binding_kind_class_scope() -> int
    return 19
end
