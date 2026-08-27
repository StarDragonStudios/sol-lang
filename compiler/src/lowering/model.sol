inject namespace std.memory as memory
inject std.collections.vector
inject frontend.syntax only SyntaxNode, syntax_child_count, syntax_child
inject semantics.types
inject semantics.symbol
inject semantics.model
inject ir.model

struct LoweringType
    kind: int
    name: string
    identity: pointer<SyntaxNode>
    element: pointer<LoweringType>
    arguments: pointer<Vector<pointer<LoweringType>>>
end

struct LoweringInstantiation
    function: pointer<SemanticSymbol>
    arguments: pointer<Vector<pointer<LoweringType>>>
end

struct LoweringOwner
    symbol: pointer<SemanticSymbol>
    module: pointer<SemanticModule>
end

struct LoweringFunctionEntry
    instantiation: pointer<LoweringInstantiation>
    id: int
    reference: pointer<IrFunctionReference>
    function: pointer<IrFunction>
end

struct LoweringStructEntry
    type: pointer<LoweringType>
    ir_type: pointer<IrType>
end

struct LoweringContext
    semantic: pointer<SemanticProgram>
    arena: pointer<IrArena>
    types: pointer<Vector<pointer<LoweringType>>>
    instantiations: pointer<Vector<pointer<LoweringInstantiation>>>
    function_owners: pointer<Vector<LoweringOwner>>
    struct_owners: pointer<Vector<LoweringOwner>>
    functions: pointer<Vector<pointer<LoweringFunctionEntry>>>
    structs: pointer<Vector<pointer<LoweringStructEntry>>>
    error: string
end

struct IrLoweringResult
    program: pointer<IrProgram>
    error: string
end

fn create_lowering_context(semantic: pointer<SemanticProgram>) -> pointer<LoweringContext>
    if semantic == null then
        return null
    end
    let context: pointer<LoweringContext> = memory::allocate<LoweringContext>(1)
    if context == null then
        return null
    end
    context->semantic = semantic
    context->arena = create_ir_arena()
    context->types = create_vector<pointer<LoweringType>>()
    context->instantiations = create_vector<pointer<LoweringInstantiation>>()
    context->function_owners = create_vector<LoweringOwner>()
    context->struct_owners = create_vector<LoweringOwner>()
    context->functions = create_vector<pointer<LoweringFunctionEntry>>()
    context->structs = create_vector<pointer<LoweringStructEntry>>()
    context->error = ""
    if context->arena == null then
        destroy_lowering_context(context, false)
        return null
    end
    return context
end

fn destroy_lowering_context(context: pointer<LoweringContext>, keep_arena: boolean) -> void
    if context == null then
        return
    end
    @mut let index: int = vector_length<pointer<LoweringStructEntry>>(context->structs)
    while index > 0 do
        index = index - 1
        memory::free<LoweringStructEntry>(vector_get<pointer<LoweringStructEntry>>(context->structs, index))
    end
    index = vector_length<pointer<LoweringFunctionEntry>>(context->functions)
    while index > 0 do
        index = index - 1
        memory::free<LoweringFunctionEntry>(vector_get<pointer<LoweringFunctionEntry>>(context->functions, index))
    end
    index = vector_length<pointer<LoweringInstantiation>>(context->instantiations)
    while index > 0 do
        index = index - 1
        let instantiation: pointer<LoweringInstantiation> = vector_get<pointer<LoweringInstantiation>>(context->instantiations, index)
        destroy_vector<pointer<LoweringType>>(instantiation->arguments)
        memory::free<LoweringInstantiation>(instantiation)
    end
    index = vector_length<pointer<LoweringType>>(context->types)
    while index > 0 do
        index = index - 1
        let type: pointer<LoweringType> = vector_get<pointer<LoweringType>>(context->types, index)
        destroy_vector<pointer<LoweringType>>(type->arguments)
        memory::free<LoweringType>(type)
    end
    destroy_vector<pointer<LoweringStructEntry>>(context->structs)
    destroy_vector<pointer<LoweringFunctionEntry>>(context->functions)
    destroy_vector<LoweringOwner>(context->struct_owners)
    destroy_vector<LoweringOwner>(context->function_owners)
    destroy_vector<pointer<LoweringInstantiation>>(context->instantiations)
    destroy_vector<pointer<LoweringType>>(context->types)
    if !keep_arena then
        destroy_ir_arena(context->arena)
    end
    memory::free<LoweringContext>(context)
    return
end

fn lowering_fail(context: pointer<LoweringContext>, message: string) -> boolean
    if context != null then
        if context->error == "" then
            context->error = message
        end
    end
    return false
end

fn lowering_failed(context: pointer<LoweringContext>) -> boolean
    if context == null then
        return true
    end
    return context->error != ""
end

fn lowering_result_failure(message: string) -> IrLoweringResult
    return IrLoweringResult {
        program: null,
        error: message
    }
end

fn lowering_result_success(program: pointer<IrProgram>) -> IrLoweringResult
    return IrLoweringResult {
        program: program,
        error: ""
    }
end

fn lowering_collect_owners(context: pointer<LoweringContext>) -> boolean
    @mut let module_index: int = 0
    while module_index < semantic_program_module_count(context->semantic) do
        let module: pointer<SemanticModule> = semantic_program_module_at(context->semantic, module_index)
        @mut let declaration_index: int = 0
        while declaration_index < syntax_child_count(module->unit) do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, declaration_index)
            let symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(context->semantic, declaration)
            if symbol != null then
                if symbol->kind == semantic_symbol_kind_function() then
                    vector_push<LoweringOwner>(context->function_owners, LoweringOwner { symbol: symbol, module: module })
                else
                    if symbol->kind == semantic_symbol_kind_struct() then
                        vector_push<LoweringOwner>(context->struct_owners, LoweringOwner { symbol: symbol, module: module })
                    end
                end
            end
            declaration_index = declaration_index + 1
        end
        module_index = module_index + 1
    end
    return true
end

fn lowering_function_owner(context: pointer<LoweringContext>, function: pointer<SemanticSymbol>) -> pointer<SemanticModule>
    @mut let index: int = 0
    while index < vector_length<LoweringOwner>(context->function_owners) do
        let owner: LoweringOwner = vector_get<LoweringOwner>(context->function_owners, index)
        if owner.symbol == function then
            return owner.module
        end
        index = index + 1
    end
    return null
end

fn lowering_struct_owner(context: pointer<LoweringContext>, symbol: pointer<SemanticSymbol>) -> pointer<SemanticModule>
    @mut let index: int = 0
    while index < vector_length<LoweringOwner>(context->struct_owners) do
        let owner: LoweringOwner = vector_get<LoweringOwner>(context->struct_owners, index)
        if owner.symbol == symbol then
            return owner.module
        end
        index = index + 1
    end
    return null
end

fn lowering_type(context: pointer<LoweringContext>, semantic: pointer<SemanticType>, owner: pointer<SemanticSymbol>, arguments: pointer<Vector<pointer<LoweringType>>>) -> pointer<LoweringType>
    if context == null || semantic == null then
        lowering_fail(context, "lowered semantic type must not be null")
        return null
    end
    if semantic->kind == semantic_type_kind_type_parameter() then
        if owner == null || arguments == null then
            lowering_fail(context, "unresolved semantic type parameter during IR lowering")
            return null
        end
        @mut let index: int = 0
        while index < semantic_symbol_type_parameter_count(owner) && index < vector_length<pointer<LoweringType>>(arguments) do
            if semantic_symbol_type_parameter(owner, index)->declaration == semantic->identity then
                return vector_get<pointer<LoweringType>>(arguments, index)
            end
            index = index + 1
        end
        lowering_fail(context, "semantic type parameter is not owned by the active specialization")
        return null
    end

    if semantic->kind == semantic_type_kind_pointer() then
        let element: pointer<LoweringType> = lowering_type(context, semantic->element_type, owner, arguments)
        if element == null then
            return null
        end
        return intern_lowering_pointer_type(context, element)
    end

    if semantic->kind == semantic_type_kind_struct() then
        let lowered_arguments: pointer<Vector<pointer<LoweringType>>> = create_vector<pointer<LoweringType>>()
        @mut let index: int = 0
        while index < semantic_type_argument_count(semantic) do
            let argument: pointer<LoweringType> = lowering_type(context, semantic_type_argument(semantic, index), owner, arguments)
            if argument == null then
                destroy_vector<pointer<LoweringType>>(lowered_arguments)
                return null
            end
            vector_push<pointer<LoweringType>>(lowered_arguments, argument)
            index = index + 1
        end
        let result: pointer<LoweringType> = intern_lowering_struct_type(context, semantic->identity, semantic->name, lowered_arguments)
        destroy_vector<pointer<LoweringType>>(lowered_arguments)
        return result
    end

    if semantic->kind != semantic_type_kind_primitive() then
        lowering_fail(context, "unsupported semantic type '" + semantic->name + "' during IR lowering")
        return null
    end
    return intern_lowering_primitive_type(context, semantic->name)
end

fn intern_lowering_primitive_type(context: pointer<LoweringContext>, name: string) -> pointer<LoweringType>
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(context->types) do
        let type: pointer<LoweringType> = vector_get<pointer<LoweringType>>(context->types, index)
        if type->kind == lowering_type_primitive() && type->name == name then
            return type
        end
        index = index + 1
    end
    return allocate_lowering_type(context, lowering_type_primitive(), name, null, null, null)
end

fn intern_lowering_pointer_type(context: pointer<LoweringContext>, element: pointer<LoweringType>) -> pointer<LoweringType>
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(context->types) do
        let type: pointer<LoweringType> = vector_get<pointer<LoweringType>>(context->types, index)
        if type->kind == lowering_type_pointer() && type->element == element then
            return type
        end
        index = index + 1
    end
    return allocate_lowering_type(context, lowering_type_pointer(), "pointer<" + element->name + ">", null, element, null)
end

fn intern_lowering_struct_type(context: pointer<LoweringContext>, identity: pointer<SyntaxNode>, name: string, arguments: pointer<Vector<pointer<LoweringType>>>) -> pointer<LoweringType>
    if identity == null || arguments == null then
        lowering_fail(context, "invalid concrete semantic struct type")
        return null
    end
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(context->types) do
        let type: pointer<LoweringType> = vector_get<pointer<LoweringType>>(context->types, index)
        if type->kind == lowering_type_struct() && type->identity == identity && lowering_type_vector_equals(type->arguments, arguments) then
            return type
        end
        index = index + 1
    end
    return allocate_lowering_type(context, lowering_type_struct(), name, identity, null, arguments)
end

fn allocate_lowering_type(context: pointer<LoweringContext>, kind: int, name: string, identity: pointer<SyntaxNode>, element: pointer<LoweringType>, arguments: pointer<Vector<pointer<LoweringType>>>) -> pointer<LoweringType>
    let type: pointer<LoweringType> = memory::allocate<LoweringType>(1)
    if type == null then
        lowering_fail(context, "lowering allocation failed")
        return null
    end
    type->kind = kind
    type->name = name
    type->identity = identity
    type->element = element
    type->arguments = create_vector<pointer<LoweringType>>()
    if arguments != null then
        @mut let index: int = 0
        while index < vector_length<pointer<LoweringType>>(arguments) do
            vector_push<pointer<LoweringType>>(type->arguments, vector_get<pointer<LoweringType>>(arguments, index))
            index = index + 1
        end
    end
    vector_push<pointer<LoweringType>>(context->types, type)
    return type
end

fn lowering_type_vector_equals(left: pointer<Vector<pointer<LoweringType>>>, right: pointer<Vector<pointer<LoweringType>>>) -> boolean
    if left == null || right == null then
        return false
    end
    if vector_length<pointer<LoweringType>>(left) != vector_length<pointer<LoweringType>>(right) then
        return false
    end
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(left) do
        if vector_get<pointer<LoweringType>>(left, index) != vector_get<pointer<LoweringType>>(right, index) then
            return false
        end
        index = index + 1
    end
    return true
end

fn create_lowering_instantiation(context: pointer<LoweringContext>, function: pointer<SemanticSymbol>, arguments: pointer<Vector<pointer<LoweringType>>>) -> pointer<LoweringInstantiation>
    if function == null || arguments == null then
        lowering_fail(context, "invalid function specialization")
        return null
    end
    if function->kind != semantic_symbol_kind_function() then
        lowering_fail(context, "invalid function specialization")
        return null
    end
    if semantic_symbol_type_parameter_count(function) != vector_length<pointer<LoweringType>>(arguments) then
        lowering_fail(context, "function '" + function->name + "' received the wrong number of lowering type arguments")
        return null
    end
    let existing: pointer<LoweringInstantiation> = lowering_find_instantiation(context, function, arguments)
    if existing != null then
        return existing
    end
    let instantiation: pointer<LoweringInstantiation> = memory::allocate<LoweringInstantiation>(1)
    if instantiation == null then
        lowering_fail(context, "lowering allocation failed")
        return null
    end
    instantiation->function = function
    instantiation->arguments = create_vector<pointer<LoweringType>>()
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(arguments) do
        vector_push<pointer<LoweringType>>(instantiation->arguments, vector_get<pointer<LoweringType>>(arguments, index))
        index = index + 1
    end
    vector_push<pointer<LoweringInstantiation>>(context->instantiations, instantiation)
    return instantiation
end

fn lowering_find_instantiation(context: pointer<LoweringContext>, function: pointer<SemanticSymbol>, arguments: pointer<Vector<pointer<LoweringType>>>) -> pointer<LoweringInstantiation>
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringInstantiation>>(context->instantiations) do
        let current: pointer<LoweringInstantiation> = vector_get<pointer<LoweringInstantiation>>(context->instantiations, index)
        if current->function == function && lowering_type_vector_equals(current->arguments, arguments) then
            return current
        end
        index = index + 1
    end
    return null
end

fn lowering_instantiation_name(context: pointer<LoweringContext>, instantiation: pointer<LoweringInstantiation>) -> string
    @mut let name: string = instantiation->function->name
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(instantiation->arguments) do
        name = name + "$" + lowering_type_qualified_name(context, vector_get<pointer<LoweringType>>(instantiation->arguments, index))
        index = index + 1
    end
    return name
end

fn lowering_type_qualified_name(context: pointer<LoweringContext>, type: pointer<LoweringType>) -> string
    if type->kind == lowering_type_pointer() then
        return "pointer<" + lowering_type_qualified_name(context, type->element) + ">"
    end
    if type->kind != lowering_type_struct() then
        return type->name
    end
    let symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(context->semantic, type->identity)
    let module: pointer<SemanticModule> = lowering_struct_owner(context, symbol)
    if symbol == null || module == null then
        lowering_fail(context, "concrete struct type has no semantic owner")
        return "<missing-struct>"
    end
    @mut let name: string = module->name + "::" + symbol->name
    if vector_length<pointer<LoweringType>>(type->arguments) > 0 then
        name = name + "<"
        @mut let index: int = 0
        while index < vector_length<pointer<LoweringType>>(type->arguments) do
            if index > 0 then
                name = name + ", "
            end
            name = name + lowering_type_qualified_name(context, vector_get<pointer<LoweringType>>(type->arguments, index))
            index = index + 1
        end
        name = name + ">"
    end
    return name
end

fn lowering_function_entry(context: pointer<LoweringContext>, instantiation: pointer<LoweringInstantiation>) -> pointer<LoweringFunctionEntry>
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringFunctionEntry>>(context->functions) do
        let entry: pointer<LoweringFunctionEntry> = vector_get<pointer<LoweringFunctionEntry>>(context->functions, index)
        if entry->instantiation == instantiation then
            return entry
        end
        index = index + 1
    end
    return null
end

fn lowering_struct_entry(context: pointer<LoweringContext>, type: pointer<LoweringType>) -> pointer<LoweringStructEntry>
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringStructEntry>>(context->structs) do
        let entry: pointer<LoweringStructEntry> = vector_get<pointer<LoweringStructEntry>>(context->structs, index)
        if entry->type == type then
            return entry
        end
        index = index + 1
    end
    return null
end

fn lowering_type_primitive() -> int
    return 1
end
fn lowering_type_struct() -> int
    return 2
end
fn lowering_type_pointer() -> int
    return 3
end
