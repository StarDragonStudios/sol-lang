inject namespace std.memory as memory
inject std.collections.vector
inject namespace std.string as strings
inject frontend.syntax
inject semantics.types
inject semantics.symbol
inject semantics.model
inject ir.model
inject ir.validation
inject lowering.model
inject lowering.plan

struct LoweringValueBinding
    symbol: pointer<SemanticSymbol>
    parameter: pointer<IrParameter>
    local: pointer<IrLocal>
end

struct LoweringFunctionContext
    global: pointer<LoweringContext>
    entry: pointer<LoweringFunctionEntry>
    function: pointer<IrFunction>
    bindings: pointer<Vector<LoweringValueBinding>>
    targets: pointer<Vector<pointer<IrBlockTarget>>>
    started_targets: pointer<Vector<pointer<IrBlockTarget>>>
    blocks: pointer<Vector<pointer<IrBasicBlock>>>
    current: pointer<IrBasicBlock>
    next_block: int
    next_value: int
    next_local: int
    started: boolean
end

fn lower_function(context: pointer<LoweringContext>, entry: pointer<LoweringFunctionEntry>) -> boolean
    let function_context: pointer<LoweringFunctionContext> = create_lowering_function_context(context, entry)
    if function_context == null then
        return false
    end
    let declaration: pointer<SyntaxNode> = entry->instantiation->function->declaration
    @mut let parameter_index: int = 0
    while parameter_index < lowering_function_parameter_count(declaration) do
        let parameter_declaration: pointer<SyntaxNode> = lowering_function_parameter(declaration, parameter_index)
        let symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(context->semantic, parameter_declaration)
        let type: pointer<IrType> = vector_get<pointer<IrType>>(entry->reference->parameter_types, parameter_index)
        if symbol == null then
            destroy_lowering_function_context(function_context)
            return lowering_fail(context, "function parameter has no canonical semantic symbol")
        end
        if symbol->kind != semantic_symbol_kind_parameter() then
            destroy_lowering_function_context(function_context)
            return lowering_fail(context, "function parameter has no canonical semantic symbol")
        end
        let parameter: pointer<IrParameter> = create_ir_parameter(context->arena, function_context->next_value, symbol->name, type)
        function_context->next_value = function_context->next_value + 1
        if parameter == null then
            destroy_lowering_function_context(function_context)
            return lowering_fail(context, context->arena->error)
        end
        if !ir_function_add_parameter(context->arena, function_context->function, parameter) then
            destroy_lowering_function_context(function_context)
            return lowering_fail(context, context->arena->error)
        end
        vector_push<LoweringValueBinding>(function_context->bindings, LoweringValueBinding { symbol: symbol, parameter: parameter, local: null })
        parameter_index = parameter_index + 1
    end

    let body: pointer<SyntaxNode> = lowering_function_body(declaration)
    if body == null then
        if !seal_ir_function(context->arena, function_context->function) then
            destroy_lowering_function_context(function_context)
            return lowering_fail(context, context->arena->error)
        end
        entry->function = function_context->function
        destroy_lowering_function_context(function_context)
        return true
    end

    lowering_ensure_block(function_context)
    if !lower_block(function_context, body) then
        destroy_lowering_function_context(function_context)
        return false
    end
    if function_context->current != null then
        destroy_lowering_function_context(function_context)
        return lowering_fail(context, "function '" + entry->instantiation->function->name + "' can reach the end of its body without an explicit return")
    end
    @mut let block_index: int = 0
    while block_index < vector_length<pointer<IrBasicBlock>>(function_context->blocks) do
        if !ir_function_add_block(context->arena, function_context->function, vector_get<pointer<IrBasicBlock>>(function_context->blocks, block_index)) then
            destroy_lowering_function_context(function_context)
            return lowering_fail(context, context->arena->error)
        end
        block_index = block_index + 1
    end
    if !seal_ir_function(context->arena, function_context->function) then
        destroy_lowering_function_context(function_context)
        return lowering_fail(context, context->arena->error)
    end
    entry->function = function_context->function
    destroy_lowering_function_context(function_context)
    return true
end

fn create_lowering_function_context(global: pointer<LoweringContext>, entry: pointer<LoweringFunctionEntry>) -> pointer<LoweringFunctionContext>
    let context: pointer<LoweringFunctionContext> = memory::allocate<LoweringFunctionContext>(1)
    if context == null then
        lowering_fail(global, "function lowering allocation failed")
        return null
    end
    context->global = global
    context->entry = entry
    context->function = create_ir_function(global->arena, entry->id, entry->reference->name, entry->reference->return_type, lowering_function_body(entry->instantiation->function->declaration) != null)
    context->bindings = create_vector<LoweringValueBinding>()
    context->targets = create_vector<pointer<IrBlockTarget>>()
    context->started_targets = create_vector<pointer<IrBlockTarget>>()
    context->blocks = create_vector<pointer<IrBasicBlock>>()
    context->current = null
    context->next_block = 0
    context->next_value = 0
    context->next_local = 0
    context->started = false
    if context->function == null then
        destroy_lowering_function_context(context)
        lowering_fail(global, global->arena->error)
        return null
    end
    return context
end

fn destroy_lowering_function_context(context: pointer<LoweringFunctionContext>) -> void
    if context == null then
        return
    end
    destroy_vector<LoweringValueBinding>(context->bindings)
    destroy_vector<pointer<IrBlockTarget>>(context->targets)
    destroy_vector<pointer<IrBlockTarget>>(context->started_targets)
    destroy_vector<pointer<IrBasicBlock>>(context->blocks)
    memory::free<LoweringFunctionContext>(context)
    return
end

fn lowering_new_target(context: pointer<LoweringFunctionContext>) -> pointer<IrBlockTarget>
    let target: pointer<IrBlockTarget> = create_ir_block_target(context->global->arena, context->next_block)
    context->next_block = context->next_block + 1
    if target == null then
        lowering_fail(context->global, context->global->arena->error)
        return null
    end
    vector_push<pointer<IrBlockTarget>>(context->targets, target)
    return target
end

fn lowering_begin_block(context: pointer<LoweringFunctionContext>, target: pointer<IrBlockTarget>) -> boolean
    if context->current != null || target == null || !lowering_target_in(context->targets, target) || lowering_target_in(context->started_targets, target) then
        return lowering_fail(context->global, "invalid IR block transition in function '" + context->entry->instantiation->function->name + "'")
    end
    let block: pointer<IrBasicBlock> = create_ir_basic_block(context->global->arena, target)
    if block == null then
        return lowering_fail(context->global, context->global->arena->error)
    end
    context->current = block
    context->started = true
    vector_push<pointer<IrBlockTarget>>(context->started_targets, target)
    return true
end

fn lowering_ensure_block(context: pointer<LoweringFunctionContext>) -> boolean
    if context->current != null then
        return true
    end
    if context->started then
        return lowering_fail(context->global, "function has no active IR block")
    end
    return lowering_begin_block(context, lowering_new_target(context))
end

fn lowering_emit(context: pointer<LoweringFunctionContext>, instruction: pointer<IrInstruction>) -> boolean
    if instruction == null then
        return false
    end
    if !lowering_ensure_block(context) then
        return false
    end
    if !ir_block_add_instruction(context->global->arena, context->current, instruction) then
        return lowering_fail(context->global, context->global->arena->error)
    end
    return true
end

fn lowering_finish_block(context: pointer<LoweringFunctionContext>, terminator: pointer<IrTerminator>) -> boolean
    if context->current == null || terminator == null then
        return lowering_fail(context->global, "cannot terminate inactive IR block")
    end
    if !ir_block_terminate(context->global->arena, context->current, terminator) then
        return lowering_fail(context->global, context->global->arena->error)
    end
    vector_push<pointer<IrBasicBlock>>(context->blocks, context->current)
    context->current = null
    return true
end

fn lowering_next_value(context: pointer<LoweringFunctionContext>) -> int
    let id: int = context->next_value
    context->next_value = context->next_value + 1
    return id
end

fn lowering_declare_local(context: pointer<LoweringFunctionContext>, symbol: pointer<SemanticSymbol>, type: pointer<IrType>) -> pointer<IrLocal>
    let existing: pointer<LoweringValueBinding> = lowering_binding(context, symbol)
    if existing != null then
        destroy_lowering_value_binding(existing)
        lowering_fail(context->global, "local variable '" + symbol->name + "' has already been lowered")
        return null
    end
    @mut let kind: int = ir_local_immutable()
    if symbol->constant then
        kind = ir_local_constant()
    else
        if symbol->mutable then
            kind = ir_local_mutable()
        end
    end
    let local: pointer<IrLocal> = create_ir_local(context->global->arena, context->next_local, symbol->name, type, kind)
    context->next_local = context->next_local + 1
    if local == null then
        lowering_fail(context->global, context->global->arena->error)
        return null
    end
    vector_push<LoweringValueBinding>(context->bindings, LoweringValueBinding { symbol: symbol, parameter: null, local: local })
    return local
end

fn lowering_binding(context: pointer<LoweringFunctionContext>, symbol: pointer<SemanticSymbol>) -> pointer<LoweringValueBinding>
    @mut let index: int = 0
    while index < vector_length<LoweringValueBinding>(context->bindings) do
        let binding: pointer<LoweringValueBinding> = memory::allocate<LoweringValueBinding>(1)
        if binding == null then
            lowering_fail(context->global, "lowering allocation failed")
            return null
        end
        binding->symbol = vector_get<LoweringValueBinding>(context->bindings, index).symbol
        binding->parameter = vector_get<LoweringValueBinding>(context->bindings, index).parameter
        binding->local = vector_get<LoweringValueBinding>(context->bindings, index).local
        if binding->symbol == symbol then
            return binding
        end
        memory::free<LoweringValueBinding>(binding)
        index = index + 1
    end
    return null
end

fn destroy_lowering_value_binding(binding: pointer<LoweringValueBinding>) -> void
    if binding != null then
        memory::free<LoweringValueBinding>(binding)
    end
    return
end

fn lower_block(context: pointer<LoweringFunctionContext>, block: pointer<SyntaxNode>) -> boolean
    @mut let index: int = 0
    while index < syntax_child_count(block) do
        let statement: pointer<SyntaxNode> = syntax_child(block, index)
        if context->current == null then
            return lowering_fail(context->global, "semantically validated block contains unreachable statement kind")
        end
        if !lower_statement(context, statement) then
            return false
        end
        index = index + 1
    end
    return true
end

fn lower_statement(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    if statement->kind == syntax_kind_variable_declaration_statement() then
        return lower_variable_declaration(context, statement)
    end
    if statement->kind == syntax_kind_assignment_statement() then
        return lower_assignment(context, statement)
    end
    if statement->kind == syntax_kind_field_assignment_statement() then
        return lower_field_assignment(context, statement)
    end
    if statement->kind == syntax_kind_pointer_field_assignment_statement() then
        return lower_pointer_field_assignment(context, statement)
    end
    if statement->kind == syntax_kind_index_assignment_statement() then
        return lower_index_assignment(context, statement)
    end
    if statement->kind == syntax_kind_call_statement() then
        return lower_call_statement(context, statement)
    end
    if statement->kind == syntax_kind_return_statement() then
        return lower_return_statement(context, statement)
    end
    if statement->kind == syntax_kind_conditional_statement() then
        return lower_conditional_statement(context, statement)
    end
    if statement->kind == syntax_kind_while_statement() then
        return lower_while_statement(context, statement)
    end
    if statement->kind == syntax_kind_block() then
        return lower_block(context, statement)
    end
    return lowering_fail(context->global, "unsupported statement syntax during IR lowering")
end

fn lower_variable_declaration(context: pointer<LoweringFunctionContext>, declaration: pointer<SyntaxNode>) -> boolean
    let symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(context->global->semantic, declaration)
    if symbol == null then
        return lowering_fail(context->global, "variable declaration has no canonical local symbol")
    end
    if symbol->kind != semantic_symbol_kind_local_variable() then
        return lowering_fail(context->global, "variable declaration has no canonical local symbol")
    end
    let initializer: pointer<IrValue> = lower_expression(context, syntax_child(declaration, 2))
    if initializer == null then
        return false
    end
    let semantic: pointer<SemanticType> = semantic_model_type_of_reference(context->global->semantic, syntax_child(declaration, 1))
    let concrete: pointer<LoweringType> = lowering_type(context->global, semantic, context->entry->instantiation->function, context->entry->instantiation->arguments)
    let type: pointer<IrType> = lowering_ir_type(context->global, concrete)
    if type == null then
        return false
    end
    let local: pointer<IrLocal> = lowering_declare_local(context, symbol, type)
    if local == null then
        return false
    end
    let instruction: pointer<IrInstruction> = create_ir_local_initialize(context->global->arena, local, initializer)
    if instruction == null then
        return lowering_fail(context->global, context->global->arena->error)
    end
    return lowering_emit(context, instruction)
end

fn lower_assignment(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    let symbol: pointer<SemanticSymbol> = semantic_model_assignment_target(context->global->semantic, statement)
    let binding: pointer<LoweringValueBinding> = lowering_binding(context, symbol)
    if symbol == null || binding == null then
        destroy_lowering_value_binding(binding)
        return lowering_fail(context->global, "assignment target has no lowered local")
    end
    if binding->local == null then
        destroy_lowering_value_binding(binding)
        return lowering_fail(context->global, "assignment target has no lowered local")
    end
    let value: pointer<IrValue> = lower_expression(context, syntax_child(statement, 1))
    if value == null then
        destroy_lowering_value_binding(binding)
        return false
    end
    let instruction: pointer<IrInstruction> = create_ir_local_store(context->global->arena, binding->local, value)
    destroy_lowering_value_binding(binding)
    if instruction == null then
        return lowering_fail(context->global, context->global->arena->error)
    end
    return lowering_emit(context, instruction)
end

fn lower_field_assignment(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    let symbol: pointer<SemanticSymbol> = semantic_model_field_assignment_target(context->global->semantic, statement)
    let binding: pointer<LoweringValueBinding> = lowering_binding(context, symbol)
    if symbol == null || binding == null then
        destroy_lowering_value_binding(binding)
        return lowering_fail(context->global, "field assignment root has no lowered local")
    end
    if binding->local == null then
        destroy_lowering_value_binding(binding)
        return lowering_fail(context->global, "field assignment root has no lowered local")
    end
    let path: pointer<Vector<pointer<IrStructField>>> = create_vector<pointer<IrStructField>>()
    if !lower_field_path(context, syntax_child(statement, 0), path) then
        destroy_vector<pointer<IrStructField>>(path)
        destroy_lowering_value_binding(binding)
        return false
    end
    let value: pointer<IrValue> = lower_expression(context, syntax_child(statement, 1))
    if value == null then
        destroy_vector<pointer<IrStructField>>(path)
        destroy_lowering_value_binding(binding)
        return false
    end
    let instruction: pointer<IrInstruction> = create_ir_struct_field_store(context->global->arena, binding->local, path, value)
    destroy_vector<pointer<IrStructField>>(path)
    destroy_lowering_value_binding(binding)
    if instruction == null then
        return lowering_fail(context->global, context->global->arena->error)
    end
    return lowering_emit(context, instruction)
end

fn lower_field_path(context: pointer<LoweringFunctionContext>, access: pointer<SyntaxNode>, path: pointer<Vector<pointer<IrStructField>>>) -> boolean
    let target: pointer<SyntaxNode> = syntax_child(access, 0)
    if target->kind == syntax_kind_field_access_expression() then
        if !lower_field_path(context, target, path) then
            return false
        end
    end
    let semantic_field: pointer<SemanticSymbol> = semantic_model_accessed_field(context->global->semantic, access)
    let semantic_type: pointer<SemanticType> = semantic_model_type_of_expression(context->global->semantic, target)
    let concrete: pointer<LoweringType> = lowering_type(context->global, semantic_type, context->entry->instantiation->function, context->entry->instantiation->arguments)
    let type: pointer<IrType> = lowering_ir_type(context->global, concrete)
    if semantic_field == null || type == null then
        return lowering_fail(context->global, "field assignment path has invalid semantic type or field")
    end
    if type->kind != ir_type_struct() || semantic_field->index < 0 || semantic_field->index >= vector_length<pointer<IrStructField>>(type->fields) then
        return lowering_fail(context->global, "field assignment path has invalid semantic type or field")
    end
    vector_push<pointer<IrStructField>>(path, vector_get<pointer<IrStructField>>(type->fields, semantic_field->index))
    return true
end

fn lower_pointer_field_assignment(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    let access: pointer<SyntaxNode> = syntax_child(statement, 0)
    let pointer_value: pointer<IrValue> = lower_expression(context, syntax_child(access, 0))
    if pointer_value == null then
        return false
    end
    let semantic_field: pointer<SemanticSymbol> = semantic_model_accessed_pointer_field(context->global->semantic, access)
    if semantic_field == null then
        return lowering_fail(context->global, "pointer-field assignment lowered a non-pointer-to-struct target")
    end
    if pointer_value->type->kind != ir_type_pointer() || pointer_value->type->element_type->kind != ir_type_struct() then
        return lowering_fail(context->global, "pointer-field assignment lowered a non-pointer-to-struct target")
    end
    let field: pointer<IrStructField> = vector_get<pointer<IrStructField>>(pointer_value->type->element_type->fields, semantic_field->index)
    let value: pointer<IrValue> = lower_expression(context, syntax_child(statement, 1))
    if value == null then
        return false
    end
    let instruction: pointer<IrInstruction> = create_ir_pointer_field_store(context->global->arena, pointer_value, field, value)
    if instruction == null then
        return lowering_fail(context->global, context->global->arena->error)
    end
    return lowering_emit(context, instruction)
end

fn lower_index_assignment(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    let access: pointer<SyntaxNode> = syntax_child(statement, 0)
    let target: pointer<IrValue> = lower_expression(context, syntax_child(access, 0))
    let index: pointer<IrValue> = lower_expression(context, syntax_child(access, 1))
    let value: pointer<IrValue> = lower_expression(context, syntax_child(statement, 1))
    if target == null || index == null || value == null then
        return false
    end
    let instruction: pointer<IrInstruction> = create_ir_pointer_index_store(context->global->arena, target, index, value)
    if instruction == null then
        return lowering_fail(context->global, context->global->arena->error)
    end
    return lowering_emit(context, instruction)
end

fn lower_call_statement(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    let instruction: pointer<IrInstruction> = lower_call(context, syntax_child(statement, 0))
    return instruction != null
end

fn lower_return_statement(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    @mut let value: pointer<IrValue> = null
    if syntax_child_count(statement) > 0 then
        value = lower_expression(context, syntax_child(statement, 0))
        if value == null then
            return false
        end
    end
    let terminator: pointer<IrTerminator> = create_ir_return(context->global->arena, value)
    return lowering_finish_block(context, terminator)
end

fn lower_conditional_statement(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    let condition: pointer<IrValue> = lower_expression(context, syntax_child(statement, 0))
    if condition == null then
        return false
    end
    let then_target: pointer<IrBlockTarget> = lowering_new_target(context)
    if syntax_child_count(statement) == 2 then
        let continuation: pointer<IrBlockTarget> = lowering_new_target(context)
        if !lowering_finish_block(context, create_ir_conditional_branch(context->global->arena, condition, then_target, continuation)) then
            return false
        end
        if !lowering_begin_block(context, then_target) then
            return false
        end
        if !lower_block(context, syntax_child(statement, 1)) then
            return false
        end
        if context->current != null then
            if !lowering_finish_block(context, create_ir_branch(context->global->arena, continuation)) then
                return false
            end
        end
        return lowering_begin_block(context, continuation)
    end

    let else_target: pointer<IrBlockTarget> = lowering_new_target(context)
    if !lowering_finish_block(context, create_ir_conditional_branch(context->global->arena, condition, then_target, else_target)) then
        return false
    end
    @mut let continuation: pointer<IrBlockTarget> = null
    if !lowering_begin_block(context, then_target) then
        return false
    end
    if !lower_block(context, syntax_child(statement, 1)) then
        return false
    end
    if context->current != null then
        continuation = lowering_new_target(context)
        if !lowering_finish_block(context, create_ir_branch(context->global->arena, continuation)) then
            return false
        end
    end
    if !lowering_begin_block(context, else_target) then
        return false
    end
    if !lower_block(context, syntax_child(statement, 2)) then
        return false
    end
    if context->current != null then
        if continuation == null then
            continuation = lowering_new_target(context)
        end
        if !lowering_finish_block(context, create_ir_branch(context->global->arena, continuation)) then
            return false
        end
    end
    if continuation != null then
        return lowering_begin_block(context, continuation)
    end
    return true
end

fn lower_while_statement(context: pointer<LoweringFunctionContext>, statement: pointer<SyntaxNode>) -> boolean
    let condition_target: pointer<IrBlockTarget> = lowering_new_target(context)
    let body_target: pointer<IrBlockTarget> = lowering_new_target(context)
    let continuation: pointer<IrBlockTarget> = lowering_new_target(context)
    if !lowering_finish_block(context, create_ir_branch(context->global->arena, condition_target)) then
        return false
    end
    if !lowering_begin_block(context, condition_target) then
        return false
    end
    let condition: pointer<IrValue> = lower_expression(context, syntax_child(statement, 0))
    if condition == null then
        return false
    end
    if !lowering_finish_block(context, create_ir_conditional_branch(context->global->arena, condition, body_target, continuation)) then
        return false
    end
    if !lowering_begin_block(context, body_target) then
        return false
    end
    if !lower_block(context, syntax_child(statement, 1)) then
        return false
    end
    if context->current != null then
        if !lowering_finish_block(context, create_ir_branch(context->global->arena, condition_target)) then
            return false
        end
    end
    return lowering_begin_block(context, continuation)
end

fn lower_expression(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    if expression == null then
        lowering_fail(context->global, "lowered expression must not be null")
        return null
    end
    @mut let value: pointer<IrValue> = null
    if expression->kind == syntax_kind_literal_expression() then
        value = lower_literal(context, expression)
    else
        if expression->kind == syntax_kind_null_expression() then
            let type: pointer<IrType> = lower_expression_type(context, expression)
            value = create_ir_null_constant(context->global->arena, lowering_next_value(context), type)
        else
            if expression->kind == syntax_kind_name_expression() then
                value = lower_name(context, expression)
            else
                if expression->kind == syntax_kind_parenthesized_expression() then
                    value = lower_expression(context, syntax_child(expression, 0))
                else
                    if expression->kind == syntax_kind_unary_expression() then
                        value = lower_unary(context, expression)
                    else
                        if expression->kind == syntax_kind_binary_expression() then
                            value = lower_binary(context, expression)
                        else
                            if expression->kind == syntax_kind_call_expression() then
                                let call: pointer<IrInstruction> = lower_call(context, expression)
                                if call != null then
                                    value = call->result
                                    if value == null then
                                        lowering_fail(context->global, "void function call cannot be used as an IR value")
                                    end
                                end
                            else
                                if expression->kind == syntax_kind_struct_construction_expression() then
                                    value = lower_struct_construction(context, expression)
                                else
                                    if expression->kind == syntax_kind_field_access_expression() then
                                        value = lower_field_access(context, expression)
                                    else
                                        if expression->kind == syntax_kind_pointer_field_access_expression() then
                                            value = lower_pointer_field_access(context, expression)
                                        else
                                            if expression->kind == syntax_kind_index_expression() then
                                                value = lower_index_expression(context, expression)
                                            else
                                                lowering_fail(context->global, "unsupported expression syntax during IR lowering")
                                            end
                                        end
                                    end
                                end
                            end
                        end
                    end
                end
            end
        end
    end
    if value == null then
        return null
    end
    let expected: pointer<IrType> = lower_expression_type(context, expression)
    if expected == null || !ir_type_equals(expected, value->type) then
        lowering_fail(context->global, "lowered expression type does not match semantic type")
        return null
    end
    return value
end

fn lower_expression_type(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrType>
    let semantic: pointer<SemanticType> = semantic_model_type_of_expression(context->global->semantic, expression)
    let concrete: pointer<LoweringType> = lowering_type(context->global, semantic, context->entry->instantiation->function, context->entry->instantiation->arguments)
    return lowering_ir_type(context->global, concrete)
end

fn lower_literal(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    if expression->variant == syntax_literal_integer() then
        return lower_integer_literal(context, expression->text)
    end
    if expression->variant == syntax_literal_float() then
        return lower_float_literal(context, expression->text)
    end
    if expression->variant == syntax_literal_boolean() then
        if expression->text == "true" then
            return create_ir_boolean_constant(context->global->arena, lowering_next_value(context), true)
        end
        if expression->text == "false" then
            return create_ir_boolean_constant(context->global->arena, lowering_next_value(context), false)
        end
        lowering_fail(context->global, "invalid boolean literal during IR lowering")
        return null
    end
    if expression->variant == syntax_literal_char() then
        let character: char = decode_character_literal(context, expression->text)
        if lowering_failed(context->global) then
            return null
        end
        return create_ir_char_constant(context->global->arena, lowering_next_value(context), character, expression->text)
    end
    if expression->variant == syntax_literal_string() then
        let string_value: string = decode_string_literal(context, expression->text)
        if lowering_failed(context->global) then
            return null
        end
        return create_ir_string_constant(context->global->arena, lowering_next_value(context), string_value)
    end
    lowering_fail(context->global, "unknown literal kind during IR lowering")
    return null
end

fn lower_integer_literal(context: pointer<LoweringFunctionContext>, text: string) -> pointer<IrValue>
    if strings::length(text) == 0 then
        lowering_fail(context->global, "empty integer literal during IR lowering")
        return null
    end
    @mut let value: int = 0
    @mut let index: int = 0
    while index < strings::length(text) do
        let digit: int = decimal_digit(text[index])
        if digit < 0 || value > 922337203685477580 || (value == 922337203685477580 && digit > 7) then
            lowering_fail(context->global, "integer literal cannot be represented as a Sol int")
            return null
        end
        value = value * 10 + digit
        index = index + 1
    end
    return create_ir_int_constant(context->global->arena, lowering_next_value(context), value)
end

fn lower_float_literal(context: pointer<LoweringFunctionContext>, text: string) -> pointer<IrValue>
    @mut let value: float = 0.0
    @mut let divisor: float = 1.0
    @mut let fractional: boolean = false
    @mut let index: int = 0
    while index < strings::length(text) do
        if text[index] == '.' then
            if fractional then
                lowering_fail(context->global, "invalid floating-point literal during IR lowering")
                return null
            end
            fractional = true
        else
            let digit: float = decimal_digit_float(text[index])
            if digit < 0.0 then
                lowering_fail(context->global, "invalid floating-point literal during IR lowering")
                return null
            end
            if fractional then
                divisor = divisor * 10.0
                value = value + digit / divisor
            else
                value = value * 10.0 + digit
            end
        end
        index = index + 1
    end
    if !fractional || value - value != 0.0 then
        lowering_fail(context->global, "floating-point literal cannot be represented as a finite Sol float")
        return null
    end
    return create_ir_float_constant(context->global->arena, lowering_next_value(context), value, text)
end

fn decode_character_literal(context: pointer<LoweringFunctionContext>, text: string) -> char
    if strings::length(text) < 3 then
        lowering_fail(context->global, "invalid character literal during IR lowering")
        return ' '
    end
    if text[0] != '\'' || text[strings::length(text) - 1] != '\'' then
        lowering_fail(context->global, "invalid character literal during IR lowering")
        return ' '
    end
    if strings::length(text) == 3 && text[1] != '\\' then
        return text[1]
    end
    if strings::length(text) == 4 && text[1] == '\\' then
        return decode_escape(context, text[2])
    end
    lowering_fail(context->global, "invalid character literal during IR lowering")
    return ' '
end

fn decode_string_literal(context: pointer<LoweringFunctionContext>, text: string) -> string
    if strings::length(text) < 2 then
        lowering_fail(context->global, "invalid string literal during IR lowering")
        return ""
    end
    if text[0] != '"' || text[strings::length(text) - 1] != '"' then
        lowering_fail(context->global, "invalid string literal during IR lowering")
        return ""
    end
    @mut let result: string = ""
    @mut let index: int = 1
    while index < strings::length(text) - 1 do
        if text[index] != '\\' then
            result = result + strings::slice(text, index, index + 1)
        else
            index = index + 1
            if index >= strings::length(text) - 1 then
                lowering_fail(context->global, "invalid string literal during IR lowering")
                return ""
            end
            let escaped: char = decode_escape(context, text[index])
            if lowering_failed(context->global) then
                return ""
            end
            if escaped == '\n' then
                result = result + "\n"
            else
                if escaped == '\r' then
                    result = result + "\r"
                else
                    if escaped == '\t' then
                        result = result + "\t"
                    else
                        result = result + strings::slice(text, index, index + 1)
                    end
                end
            end
        end
        index = index + 1
    end
    return result
end

fn decode_escape(context: pointer<LoweringFunctionContext>, value: char) -> char
    if value == 'n' then
        return '\n'
    end
    if value == 'r' then
        return '\r'
    end
    if value == 't' then
        return '\t'
    end
    if value == '\\' then
        return '\\'
    end
    if value == '"' then
        return '"'
    end
    if value == '\'' then
        return '\''
    end
    lowering_fail(context->global, "invalid literal escape during IR lowering")
    return ' '
end

fn decimal_digit(value: char) -> int
    if value == '0' then
        return 0
    end
    if value == '1' then
        return 1
    end
    if value == '2' then
        return 2
    end
    if value == '3' then
        return 3
    end
    if value == '4' then
        return 4
    end
    if value == '5' then
        return 5
    end
    if value == '6' then
        return 6
    end
    if value == '7' then
        return 7
    end
    if value == '8' then
        return 8
    end
    if value == '9' then
        return 9
    end
    return -1
end

fn decimal_digit_float(value: char) -> float
    if value == '0' then
        return 0.0
    end
    if value == '1' then
        return 1.0
    end
    if value == '2' then
        return 2.0
    end
    if value == '3' then
        return 3.0
    end
    if value == '4' then
        return 4.0
    end
    if value == '5' then
        return 5.0
    end
    if value == '6' then
        return 6.0
    end
    if value == '7' then
        return 7.0
    end
    if value == '8' then
        return 8.0
    end
    if value == '9' then
        return 9.0
    end
    return -1.0
end

fn lower_name(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    let symbol: pointer<SemanticSymbol> = semantic_model_resolved_name(context->global->semantic, expression)
    let binding: pointer<LoweringValueBinding> = lowering_binding(context, symbol)
    if symbol == null || binding == null then
        destroy_lowering_value_binding(binding)
        lowering_fail(context->global, "name expression has no lowered semantic value")
        return null
    end
    if binding->parameter != null then
        let value: pointer<IrValue> = binding->parameter->value
        destroy_lowering_value_binding(binding)
        return value
    end
    if binding->local != null then
        let instruction: pointer<IrInstruction> = create_ir_local_load(context->global->arena, lowering_next_value(context), binding->local)
        destroy_lowering_value_binding(binding)
        if instruction == null || !lowering_emit(context, instruction) then
            return null
        end
        return instruction->result
    end
    destroy_lowering_value_binding(binding)
    lowering_fail(context->global, "unsupported semantic name value")
    return null
end

fn lower_unary(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    let operand: pointer<IrValue> = lower_expression(context, syntax_child(expression, 0))
    if operand == null then
        return null
    end
    @mut let operator: int = 0
    if expression->variant == syntax_unary_not() then
        operator = ir_unary_logical_not()
    else
        if expression->variant == syntax_unary_negative() then
            operator = ir_unary_negate()
        else
            if expression->variant == syntax_unary_positive() then
                operator = ir_unary_positive()
            end
        end
    end
    let instruction: pointer<IrInstruction> = create_ir_unary_instruction(context->global->arena, lowering_next_value(context), operator, operand)
    if instruction == null || !lowering_emit(context, instruction) then
        return null
    end
    return instruction->result
end

fn lower_binary(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    let left: pointer<IrValue> = lower_expression(context, syntax_child(expression, 0))
    let right: pointer<IrValue> = lower_expression(context, syntax_child(expression, 1))
    if left == null || right == null then
        return null
    end
    let operator: int = lower_binary_operator(expression->variant)
    let instruction: pointer<IrInstruction> = create_ir_binary_instruction(context->global->arena, lowering_next_value(context), operator, left, right)
    if instruction == null || !lowering_emit(context, instruction) then
        return null
    end
    return instruction->result
end

fn lower_binary_operator(variant: int) -> int
    if variant == syntax_binary_multiply() then
        return ir_binary_multiply()
    end
    if variant == syntax_binary_divide() then
        return ir_binary_divide()
    end
    if variant == syntax_binary_remainder() then
        return ir_binary_remainder()
    end
    if variant == syntax_binary_add() then
        return ir_binary_add()
    end
    if variant == syntax_binary_subtract() then
        return ir_binary_subtract()
    end
    if variant == syntax_binary_less() then
        return ir_binary_less_than()
    end
    if variant == syntax_binary_less_equal() then
        return ir_binary_less_than_or_equal()
    end
    if variant == syntax_binary_greater() then
        return ir_binary_greater_than()
    end
    if variant == syntax_binary_greater_equal() then
        return ir_binary_greater_than_or_equal()
    end
    if variant == syntax_binary_equal() then
        return ir_binary_equal()
    end
    if variant == syntax_binary_not_equal() then
        return ir_binary_not_equal()
    end
    if variant == syntax_binary_and() then
        return ir_binary_logical_and()
    end
    return ir_binary_logical_or()
end

fn lower_call(context: pointer<LoweringFunctionContext>, call: pointer<SyntaxNode>) -> pointer<IrInstruction>
    let target: pointer<SemanticSymbol> = semantic_model_called_function(context->global->semantic, call)
    if target == null then
        lowering_fail(context->global, "call expression has no canonical semantic function")
        return null
    end
    let type_arguments: pointer<Vector<pointer<LoweringType>>> = create_vector<pointer<LoweringType>>()
    @mut let index: int = 0
    while index < semantic_model_call_type_argument_count(context->global->semantic, call) do
        let argument: pointer<LoweringType> = lowering_type(context->global, semantic_model_call_type_argument(context->global->semantic, call, index), context->entry->instantiation->function, context->entry->instantiation->arguments)
        if argument == null then
            destroy_vector<pointer<LoweringType>>(type_arguments)
            return null
        end
        vector_push<pointer<LoweringType>>(type_arguments, argument)
        index = index + 1
    end
    let instantiation: pointer<LoweringInstantiation> = lowering_find_instantiation(context->global, target, type_arguments)
    destroy_vector<pointer<LoweringType>>(type_arguments)
    let entry: pointer<LoweringFunctionEntry> = lowering_function_entry(context->global, instantiation)
    if instantiation == null || entry == null then
        lowering_fail(context->global, "called function has no canonical IR reference")
        return null
    end
    if entry->reference == null then
        lowering_fail(context->global, "called function has no canonical IR reference")
        return null
    end
    let arguments: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    index = 0
    while index < lowering_call_value_count(call) do
        let value: pointer<IrValue> = lower_expression(context, lowering_call_value(call, index))
        if value == null then
            destroy_vector<pointer<IrValue>>(arguments)
            return null
        end
        vector_push<pointer<IrValue>>(arguments, value)
        index = index + 1
    end
    @mut let instruction: pointer<IrInstruction> = null
    if entry->reference->return_type->value_type then
        instruction = create_ir_value_call_instruction(context->global->arena, lowering_next_value(context), entry->reference, arguments)
    else
        instruction = create_ir_void_call_instruction(context->global->arena, entry->reference, arguments)
    end
    destroy_vector<pointer<IrValue>>(arguments)
    if instruction == null || !lowering_emit(context, instruction) then
        lowering_fail(context->global, context->global->arena->error)
        return null
    end
    return instruction
end

fn lower_struct_construction(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    let type: pointer<IrType> = lower_expression_type(context, expression)
    let symbol: pointer<SemanticSymbol> = semantic_model_constructed_struct(context->global->semantic, expression)
    if type == null || symbol == null then
        lowering_fail(context->global, "struct construction has invalid semantic type")
        return null
    end
    if type->kind != ir_type_struct() then
        lowering_fail(context->global, "struct construction has invalid semantic type")
        return null
    end
    let values: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    @mut let index: int = 0
    while index < vector_length<pointer<IrStructField>>(type->fields) do
        vector_push<pointer<IrValue>>(values, null)
        index = index + 1
    end
    index = 1
    while index < syntax_child_count(expression) do
        let initializer: pointer<SyntaxNode> = syntax_child(expression, index)
        let field: pointer<SemanticSymbol> = semantic_model_initialized_field(context->global->semantic, initializer)
        let value: pointer<IrValue> = lower_expression(context, syntax_child(initializer, 1))
        if field == null || value == null then
            destroy_vector<pointer<IrValue>>(values)
            lowering_fail(context->global, "struct initializer has no canonical semantic field")
            return null
        end
        vector_set<pointer<IrValue>>(values, field->index, value)
        index = index + 1
    end
    let instruction: pointer<IrInstruction> = create_ir_struct_construct(context->global->arena, lowering_next_value(context), type, values)
    destroy_vector<pointer<IrValue>>(values)
    if instruction == null || !lowering_emit(context, instruction) then
        return null
    end
    return instruction->result
end

fn lower_field_access(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    let target: pointer<IrValue> = lower_expression(context, syntax_child(expression, 0))
    let semantic_field: pointer<SemanticSymbol> = semantic_model_accessed_field(context->global->semantic, expression)
    if target == null || semantic_field == null then
        lowering_fail(context->global, "field access lowered a non-struct target")
        return null
    end
    if target->type->kind != ir_type_struct() then
        lowering_fail(context->global, "field access lowered a non-struct target")
        return null
    end
    let field: pointer<IrStructField> = vector_get<pointer<IrStructField>>(target->type->fields, semantic_field->index)
    let instruction: pointer<IrInstruction> = create_ir_struct_extract(context->global->arena, lowering_next_value(context), target, field)
    if instruction == null || !lowering_emit(context, instruction) then
            return null
        end
    return instruction->result
end

fn lower_pointer_field_access(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    let target: pointer<IrValue> = lower_expression(context, syntax_child(expression, 0))
    let semantic_field: pointer<SemanticSymbol> = semantic_model_accessed_pointer_field(context->global->semantic, expression)
    if target == null || semantic_field == null then
        lowering_fail(context->global, "pointer-field access lowered a non-pointer-to-struct target")
        return null
    end
    if target->type->kind != ir_type_pointer() || target->type->element_type->kind != ir_type_struct() then
        lowering_fail(context->global, "pointer-field access lowered a non-pointer-to-struct target")
        return null
    end
    let field: pointer<IrStructField> = vector_get<pointer<IrStructField>>(target->type->element_type->fields, semantic_field->index)
    let instruction: pointer<IrInstruction> = create_ir_pointer_field_load(context->global->arena, lowering_next_value(context), target, field)
    if instruction == null || !lowering_emit(context, instruction) then
            return null
        end
    return instruction->result
end

fn lower_index_expression(context: pointer<LoweringFunctionContext>, expression: pointer<SyntaxNode>) -> pointer<IrValue>
    let target: pointer<IrValue> = lower_expression(context, syntax_child(expression, 0))
    let index: pointer<IrValue> = lower_expression(context, syntax_child(expression, 1))
    if target == null || index == null then
            return null
        end
    @mut let instruction: pointer<IrInstruction> = null
    if target->type == context->global->arena->string_type then
        instruction = create_ir_string_index(context->global->arena, lowering_next_value(context), target, index)
    else
        if target->type->kind == ir_type_pointer() then
            instruction = create_ir_pointer_index_load(context->global->arena, lowering_next_value(context), target, index)
        end
    end
    if instruction == null || !lowering_emit(context, instruction) then
        lowering_fail(context->global, "index expression lowered an unsupported target")
        return null
    end
    return instruction->result
end

fn lowering_call_value_count(call: pointer<SyntaxNode>) -> int
    @mut let count: int = 0
    @mut let index: int = 1
    while index < syntax_child_count(call) do
        if syntax_child(call, index)->kind != syntax_kind_type_reference() then
            count = count + 1
        end
        index = index + 1
    end
    return count
end

fn lowering_call_value(call: pointer<SyntaxNode>, requested: int) -> pointer<SyntaxNode>
    @mut let found: int = 0
    @mut let index: int = 1
    while index < syntax_child_count(call) do
        let child: pointer<SyntaxNode> = syntax_child(call, index)
        if child->kind != syntax_kind_type_reference() then
            if found == requested then
                return child
            end
            found = found + 1
        end
        index = index + 1
    end
    return null
end

fn lowering_target_in(values: pointer<Vector<pointer<IrBlockTarget>>>, value: pointer<IrBlockTarget>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrBlockTarget>>(values) do
        if vector_get<pointer<IrBlockTarget>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end
