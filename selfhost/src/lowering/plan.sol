inject namespace std.memory as memory
inject std.collections.vector
inject frontend.syntax
inject semantics.types
inject semantics.symbol
inject semantics.model
inject ir.model
inject lowering.model

struct LoweringPlan
    functions: pointer<Vector<pointer<LoweringInstantiation>>>
    structs: pointer<Vector<pointer<LoweringType>>>
end

fn create_lowering_plan(context: pointer<LoweringContext>) -> pointer<LoweringPlan>
    let plan: pointer<LoweringPlan> = memory::allocate<LoweringPlan>(1)
    if plan == null then
        lowering_fail(context, "lowering plan allocation failed")
        return null
    end
    plan->functions = create_vector<pointer<LoweringInstantiation>>()
    plan->structs = create_vector<pointer<LoweringType>>()

    let empty: pointer<Vector<pointer<LoweringType>>> = create_vector<pointer<LoweringType>>()
    @mut let owner_index: int = 0
    while owner_index < vector_length<LoweringOwner>(context->function_owners) do
        let owner: LoweringOwner = vector_get<LoweringOwner>(context->function_owners, owner_index)
        if semantic_symbol_type_parameter_count(owner.symbol) == 0 then
            let root: pointer<LoweringInstantiation> = create_lowering_instantiation(context, owner.symbol, empty)
            if root == null then
                destroy_vector<pointer<LoweringType>>(empty)
                destroy_lowering_plan(plan)
                return null
            end
            vector_push<pointer<LoweringInstantiation>>(plan->functions, root)
        end
        owner_index = owner_index + 1
    end
    destroy_vector<pointer<LoweringType>>(empty)
    let active: pointer<Vector<pointer<LoweringInstantiation>>> = create_vector<pointer<LoweringInstantiation>>()
    let completed: pointer<Vector<pointer<LoweringInstantiation>>> = create_vector<pointer<LoweringInstantiation>>()
    @mut let root_index: int = 0
    while root_index < vector_length<pointer<LoweringInstantiation>>(plan->functions) && !lowering_failed(context) do
        lowering_validate_finite(context, vector_get<pointer<LoweringInstantiation>>(plan->functions, root_index), active, completed)
        root_index = root_index + 1
    end
    destroy_vector<pointer<LoweringInstantiation>>(completed)
    destroy_vector<pointer<LoweringInstantiation>>(active)
    if lowering_failed(context) then
        destroy_lowering_plan(plan)
        return null
    end
    @mut let function_index: int = 0
    while function_index < vector_length<pointer<LoweringInstantiation>>(plan->functions) do
        let calls: pointer<Vector<pointer<LoweringInstantiation>>> = lowering_called_instantiations(context, vector_get<pointer<LoweringInstantiation>>(plan->functions, function_index))
        if calls == null then
            destroy_lowering_plan(plan)
            return null
        end
        @mut let call_index: int = 0
        while call_index < vector_length<pointer<LoweringInstantiation>>(calls) do
            let called: pointer<LoweringInstantiation> = vector_get<pointer<LoweringInstantiation>>(calls, call_index)
            if !lowering_instantiation_in(plan->functions, called) then
                vector_push<pointer<LoweringInstantiation>>(plan->functions, called)
            end
            call_index = call_index + 1
        end
        destroy_vector<pointer<LoweringInstantiation>>(calls)
        function_index = function_index + 1
    end

    if !lowering_discover_structs(context, plan) then
        destroy_lowering_plan(plan)
        return null
    end
    return plan
end

fn destroy_lowering_plan(plan: pointer<LoweringPlan>) -> void
    if plan == null then
        return
    end
    destroy_vector<pointer<LoweringInstantiation>>(plan->functions)
    destroy_vector<pointer<LoweringType>>(plan->structs)
    memory::free<LoweringPlan>(plan)
    return
end

fn lowering_validate_finite(context: pointer<LoweringContext>, current: pointer<LoweringInstantiation>, active: pointer<Vector<pointer<LoweringInstantiation>>>, completed: pointer<Vector<pointer<LoweringInstantiation>>>) -> boolean
    if lowering_instantiation_in(completed, current) then
        return true
    end
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringInstantiation>>(active) do
        let ancestor: pointer<LoweringInstantiation> = vector_get<pointer<LoweringInstantiation>>(active, index)
        if ancestor == current then
            return true
        end
        if ancestor->function == current->function then
            return lowering_fail(context, "generic function '" + current->function->name + "' recursively requests expanding specialization '" + lowering_instantiation_name(context, current) + "'")
        end
        index = index + 1
    end

    vector_push<pointer<LoweringInstantiation>>(active, current)
    let calls: pointer<Vector<pointer<LoweringInstantiation>>> = lowering_called_instantiations(context, current)
    if calls == null then
        vector_pop<pointer<LoweringInstantiation>>(active)
        return false
    end
    index = 0
    while index < vector_length<pointer<LoweringInstantiation>>(calls) do
        if !lowering_validate_finite(context, vector_get<pointer<LoweringInstantiation>>(calls, index), active, completed) then
            destroy_vector<pointer<LoweringInstantiation>>(calls)
            vector_pop<pointer<LoweringInstantiation>>(active)
            return false
        end
        index = index + 1
    end
    destroy_vector<pointer<LoweringInstantiation>>(calls)
    vector_pop<pointer<LoweringInstantiation>>(active)
    vector_push<pointer<LoweringInstantiation>>(completed, current)
    return true
end

fn lowering_called_instantiations(context: pointer<LoweringContext>, caller: pointer<LoweringInstantiation>) -> pointer<Vector<pointer<LoweringInstantiation>>>
    let result: pointer<Vector<pointer<LoweringInstantiation>>> = create_vector<pointer<LoweringInstantiation>>()
    let body: pointer<SyntaxNode> = lowering_function_body(caller->function->declaration)
    if body == null then
        return result
    end
    let calls: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()
    lowering_collect_calls(body, calls)
    @mut let index: int = 0
    while index < vector_length<pointer<SyntaxNode>>(calls) do
        let call: pointer<SyntaxNode> = vector_get<pointer<SyntaxNode>>(calls, index)
        let target: pointer<SemanticSymbol> = semantic_model_called_function(context->semantic, call)
        if target == null then
            lowering_fail(context, "call in function '" + caller->function->name + "' has no canonical semantic target")
            destroy_vector<pointer<SyntaxNode>>(calls)
            destroy_vector<pointer<LoweringInstantiation>>(result)
            return null
        end
        let arguments: pointer<Vector<pointer<LoweringType>>> = create_vector<pointer<LoweringType>>()
        @mut let argument_index: int = 0
        while argument_index < semantic_model_call_type_argument_count(context->semantic, call) do
            let argument: pointer<LoweringType> = lowering_type(
                context,
                semantic_model_call_type_argument(context->semantic, call, argument_index),
                caller->function,
                caller->arguments
            )
            if argument == null then
                destroy_vector<pointer<LoweringType>>(arguments)
                destroy_vector<pointer<SyntaxNode>>(calls)
                destroy_vector<pointer<LoweringInstantiation>>(result)
                return null
            end
            vector_push<pointer<LoweringType>>(arguments, argument)
            argument_index = argument_index + 1
        end
        let instantiation: pointer<LoweringInstantiation> = create_lowering_instantiation(context, target, arguments)
        destroy_vector<pointer<LoweringType>>(arguments)
        if instantiation == null then
            destroy_vector<pointer<SyntaxNode>>(calls)
            destroy_vector<pointer<LoweringInstantiation>>(result)
            return null
        end
        vector_push<pointer<LoweringInstantiation>>(result, instantiation)
        index = index + 1
    end
    destroy_vector<pointer<SyntaxNode>>(calls)
    return result
end

fn lowering_collect_calls(node: pointer<SyntaxNode>, calls: pointer<Vector<pointer<SyntaxNode>>>) -> void
    if node == null then
        return
    end
    if node->kind == syntax_kind_call_expression() then
        vector_push<pointer<SyntaxNode>>(calls, node)
    end
    @mut let index: int = 0
    while index < syntax_child_count(node) do
        lowering_collect_calls(syntax_child(node, index), calls)
        index = index + 1
    end
    return
end

fn lowering_discover_structs(context: pointer<LoweringContext>, plan: pointer<LoweringPlan>) -> boolean
    let no_arguments: pointer<Vector<pointer<LoweringType>>> = create_vector<pointer<LoweringType>>()
    @mut let owner_index: int = 0
    while owner_index < vector_length<LoweringOwner>(context->struct_owners) do
        let owner: LoweringOwner = vector_get<LoweringOwner>(context->struct_owners, owner_index)
        if semantic_symbol_type_parameter_count(owner.symbol) == 0 then
            let type: pointer<LoweringType> = lowering_type(context, owner.symbol->type, owner.symbol, no_arguments)
            if type == null then
                destroy_vector<pointer<LoweringType>>(no_arguments)
                return false
            end
            lowering_add_reachable_type(plan->structs, type)
        end
        owner_index = owner_index + 1
    end
    destroy_vector<pointer<LoweringType>>(no_arguments)
    @mut let function_index: int = 0
    while function_index < vector_length<pointer<LoweringInstantiation>>(plan->functions) do
        let instantiation: pointer<LoweringInstantiation> = vector_get<pointer<LoweringInstantiation>>(plan->functions, function_index)
        @mut let parameter_index: int = 0
        while parameter_index < lowering_function_parameter_count(instantiation->function->declaration) do
            let parameter: pointer<SyntaxNode> = lowering_function_parameter(instantiation->function->declaration, parameter_index)
            let semantic: pointer<SemanticType> = semantic_model_type_of_reference(context->semantic, syntax_child(parameter, 1))
            let type: pointer<LoweringType> = lowering_type(context, semantic, instantiation->function, instantiation->arguments)
            if type == null then
                return false
            end
            lowering_add_reachable_type(plan->structs, type)
            parameter_index = parameter_index + 1
        end
        let return_reference: pointer<SyntaxNode> = lowering_function_return_type(instantiation->function->declaration)
        let return_type: pointer<LoweringType> = lowering_type(context, semantic_model_type_of_reference(context->semantic, return_reference), instantiation->function, instantiation->arguments)
        if return_type == null then
            return false
        end
        lowering_add_reachable_type(plan->structs, return_type)
        let body: pointer<SyntaxNode> = lowering_function_body(instantiation->function->declaration)
        if body != null then
            if !lowering_discover_expression_types(context, plan, body, instantiation) then
                return false
            end
        end
        function_index = function_index + 1
    end
    @mut let struct_index: int = 0
    while struct_index < vector_length<pointer<LoweringType>>(plan->structs) do
        let instance: pointer<LoweringType> = vector_get<pointer<LoweringType>>(plan->structs, struct_index)
        let symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(context->semantic, instance->identity)
        if symbol == null then
            return lowering_fail(context, "concrete struct has no canonical semantic symbol")
        end
        @mut let field_index: int = 0
        while field_index < semantic_struct_field_count(symbol) do
            let field: pointer<SemanticSymbol> = semantic_struct_field(symbol, field_index)
            let semantic: pointer<SemanticType> = semantic_model_type_of_reference(context->semantic, semantic_symbol_declared_type_reference(field))
            let type: pointer<LoweringType> = lowering_type(context, semantic, symbol, instance->arguments)
            if type == null then
                return false
            end
            lowering_add_reachable_type(plan->structs, type)
            field_index = field_index + 1
        end
        struct_index = struct_index + 1
    end
    return true
end

fn lowering_discover_expression_types(context: pointer<LoweringContext>, plan: pointer<LoweringPlan>, node: pointer<SyntaxNode>, instantiation: pointer<LoweringInstantiation>) -> boolean
    let semantic: pointer<SemanticType> = semantic_model_type_of_expression(context->semantic, node)
    if lowering_is_expression_node(node) then
        if semantic != null then
            if semantic->kind != semantic_type_kind_error() then
                let type: pointer<LoweringType> = lowering_type(context, semantic, instantiation->function, instantiation->arguments)
                if type == null then
                    return false
                end
                lowering_add_reachable_type(plan->structs, type)
            end
        end
    end
    @mut let index: int = 0
    while index < syntax_child_count(node) do
        if !lowering_discover_expression_types(context, plan, syntax_child(node, index), instantiation) then
            return false
        end
        index = index + 1
    end
    return true
end

fn lowering_is_expression_node(node: pointer<SyntaxNode>) -> boolean
    if node == null then
        return false
    end
    if node->kind == syntax_kind_name_expression() || node->kind == syntax_kind_qualified_name_expression() || node->kind == syntax_kind_literal_expression() || node->kind == syntax_kind_null_expression() then
        return true
    end
    if node->kind == syntax_kind_parenthesized_expression() || node->kind == syntax_kind_unary_expression() || node->kind == syntax_kind_binary_expression() || node->kind == syntax_kind_call_expression() then
        return true
    end
    return node->kind == syntax_kind_field_access_expression() || node->kind == syntax_kind_pointer_field_access_expression() || node->kind == syntax_kind_index_expression() || node->kind == syntax_kind_struct_construction_expression()
end

fn lowering_add_reachable_type(structs: pointer<Vector<pointer<LoweringType>>>, type: pointer<LoweringType>) -> void
    if type->kind == lowering_type_pointer() then
        lowering_add_reachable_type(structs, type->element)
        return
    end
    if type->kind != lowering_type_struct() then
        return
    end
    if !lowering_type_in(structs, type) then
        vector_push<pointer<LoweringType>>(structs, type)
    end
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(type->arguments) do
        lowering_add_reachable_type(structs, vector_get<pointer<LoweringType>>(type->arguments, index))
        index = index + 1
    end
    return
end

fn lowering_assign_structs(context: pointer<LoweringContext>, plan: pointer<LoweringPlan>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(plan->structs) do
        let type: pointer<LoweringType> = vector_get<pointer<LoweringType>>(plan->structs, index)
        let ir_type: pointer<IrType> = create_ir_struct_type(context->arena, lowering_type_qualified_name(context, type))
        if ir_type == null then
            return lowering_fail(context, context->arena->error)
        end
        let entry: pointer<LoweringStructEntry> = memory::allocate<LoweringStructEntry>(1)
        if entry == null then
            return lowering_fail(context, "lowering allocation failed")
        end
        entry->type = type
        entry->ir_type = ir_type
        vector_push<pointer<LoweringStructEntry>>(context->structs, entry)
        index = index + 1
    end

    index = 0
    while index < vector_length<pointer<LoweringType>>(plan->structs) do
        let type: pointer<LoweringType> = vector_get<pointer<LoweringType>>(plan->structs, index)
        let entry: pointer<LoweringStructEntry> = lowering_struct_entry(context, type)
        let symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(context->semantic, type->identity)
        let fields: pointer<Vector<pointer<IrStructField>>> = create_vector<pointer<IrStructField>>()
        @mut let field_index: int = 0
        while field_index < semantic_struct_field_count(symbol) do
            let semantic_field: pointer<SemanticSymbol> = semantic_struct_field(symbol, field_index)
            let semantic_type: pointer<SemanticType> = semantic_model_type_of_reference(context->semantic, semantic_symbol_declared_type_reference(semantic_field))
            let concrete: pointer<LoweringType> = lowering_type(context, semantic_type, symbol, type->arguments)
            let ir_field_type: pointer<IrType> = lowering_ir_type(context, concrete)
            if ir_field_type == null then
                destroy_vector<pointer<IrStructField>>(fields)
                return false
            end
            let field: pointer<IrStructField> = create_ir_struct_field(context->arena, field_index, semantic_field->name, ir_field_type)
            if field == null then
                destroy_vector<pointer<IrStructField>>(fields)
                return lowering_fail(context, context->arena->error)
            end
            vector_push<pointer<IrStructField>>(fields, field)
            field_index = field_index + 1
        end
        if !define_ir_struct_type(context->arena, entry->ir_type, fields) then
            destroy_vector<pointer<IrStructField>>(fields)
            return lowering_fail(context, context->arena->error)
        end
        destroy_vector<pointer<IrStructField>>(fields)
        index = index + 1
    end
    return true
end

fn lowering_assign_functions(context: pointer<LoweringContext>, plan: pointer<LoweringPlan>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringInstantiation>>(plan->functions) do
        let instantiation: pointer<LoweringInstantiation> = vector_get<pointer<LoweringInstantiation>>(plan->functions, index)
        let entry: pointer<LoweringFunctionEntry> = memory::allocate<LoweringFunctionEntry>(1)
        if entry == null then
            return lowering_fail(context, "lowering allocation failed")
        end
        entry->instantiation = instantiation
        entry->id = index
        entry->reference = null
        entry->function = null
        vector_push<pointer<LoweringFunctionEntry>>(context->functions, entry)
        index = index + 1
    end

    index = 0
    while index < vector_length<pointer<LoweringFunctionEntry>>(context->functions) do
        let entry: pointer<LoweringFunctionEntry> = vector_get<pointer<LoweringFunctionEntry>>(context->functions, index)
        let parameter_types: pointer<Vector<pointer<IrType>>> = create_vector<pointer<IrType>>()
        @mut let parameter_index: int = 0
        while parameter_index < lowering_function_parameter_count(entry->instantiation->function->declaration) do
            let parameter: pointer<SyntaxNode> = lowering_function_parameter(entry->instantiation->function->declaration, parameter_index)
            let semantic: pointer<SemanticType> = semantic_model_type_of_reference(context->semantic, syntax_child(parameter, 1))
            let concrete: pointer<LoweringType> = lowering_type(context, semantic, entry->instantiation->function, entry->instantiation->arguments)
            let ir_type: pointer<IrType> = lowering_ir_type(context, concrete)
            if ir_type == null then
                destroy_vector<pointer<IrType>>(parameter_types)
                return false
            end
            vector_push<pointer<IrType>>(parameter_types, ir_type)
            parameter_index = parameter_index + 1
        end
        let return_reference: pointer<SyntaxNode> = lowering_function_return_type(entry->instantiation->function->declaration)
        let return_concrete: pointer<LoweringType> = lowering_type(context, semantic_model_type_of_reference(context->semantic, return_reference), entry->instantiation->function, entry->instantiation->arguments)
        let return_type: pointer<IrType> = lowering_ir_type(context, return_concrete)
        if return_type == null then
            destroy_vector<pointer<IrType>>(parameter_types)
            return false
        end
        entry->reference = create_ir_function_reference(context->arena, entry->id, lowering_instantiation_name(context, entry->instantiation), parameter_types, return_type)
        destroy_vector<pointer<IrType>>(parameter_types)
        if entry->reference == null then
            return lowering_fail(context, context->arena->error)
        end
        index = index + 1
    end
    return true
end

fn lowering_ir_type(context: pointer<LoweringContext>, type: pointer<LoweringType>) -> pointer<IrType>
    if type == null then
        lowering_fail(context, "concrete lowering type must not be null")
        return null
    end
    if type->kind == lowering_type_struct() then
        let entry: pointer<LoweringStructEntry> = lowering_struct_entry(context, type)
        if entry == null then
            lowering_fail(context, "concrete struct type has no canonical IR type")
            return null
        end
        return entry->ir_type
    end
    if type->kind == lowering_type_pointer() then
        let element: pointer<IrType> = lowering_ir_type(context, type->element)
        if element == null then
            return null
        end
        let pointer_type: pointer<IrType> = create_ir_pointer_type(context->arena, element)
        if pointer_type == null then
            lowering_fail(context, context->arena->error)
        end
        return pointer_type
    end
    if type->name == "int" then
        return context->arena->integer_type
    end
    if type->name == "float" then
        return context->arena->float_type
    end
    if type->name == "boolean" then
        return context->arena->boolean_type
    end
    if type->name == "char" then
        return context->arena->char_type
    end
    if type->name == "string" then
        return context->arena->string_type
    end
    if type->name == "void" then
        return context->arena->void_type
    end
    lowering_fail(context, "unsupported primitive lowering type '" + type->name + "'")
    return null
end

fn lowering_function_body(declaration: pointer<SyntaxNode>) -> pointer<SyntaxNode>
    @mut let index: int = 0
    while index < syntax_child_count(declaration) do
        let child: pointer<SyntaxNode> = syntax_child(declaration, index)
        if child->kind == syntax_kind_block() then
            return child
        end
        index = index + 1
    end
    return null
end

fn lowering_function_return_type(declaration: pointer<SyntaxNode>) -> pointer<SyntaxNode>
    @mut let index: int = syntax_child_count(declaration)
    while index > 0 do
        index = index - 1
        let child: pointer<SyntaxNode> = syntax_child(declaration, index)
        if child->kind == syntax_kind_type_reference() then
            return child
        end
    end
    return null
end

fn lowering_function_parameter_count(declaration: pointer<SyntaxNode>) -> int
    @mut let count: int = 0
    @mut let index: int = 0
    while index < syntax_child_count(declaration) do
        if syntax_child(declaration, index)->kind == syntax_kind_parameter() then
            count = count + 1
        end
        index = index + 1
    end
    return count
end

fn lowering_function_parameter(declaration: pointer<SyntaxNode>, requested: int) -> pointer<SyntaxNode>
    @mut let found: int = 0
    @mut let index: int = 0
    while index < syntax_child_count(declaration) do
        let child: pointer<SyntaxNode> = syntax_child(declaration, index)
        if child->kind == syntax_kind_parameter() then
            if found == requested then
                return child
            end
            found = found + 1
        end
        index = index + 1
    end
    return null
end

fn lowering_instantiation_in(values: pointer<Vector<pointer<LoweringInstantiation>>>, value: pointer<LoweringInstantiation>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringInstantiation>>(values) do
        if vector_get<pointer<LoweringInstantiation>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end

fn lowering_type_in(values: pointer<Vector<pointer<LoweringType>>>, value: pointer<LoweringType>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringType>>(values) do
        if vector_get<pointer<LoweringType>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end
