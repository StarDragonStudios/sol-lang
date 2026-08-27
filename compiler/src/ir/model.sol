inject namespace std.memory as memory
inject std.collections.vector

struct IrType
    kind: int
    name: string
    value_type: boolean
    numeric: boolean
    integral: boolean
    element_type: pointer<IrType>
    fields: pointer<Vector<pointer<IrStructField>>>
    defined: boolean
end

struct IrStructField
    index: int
    name: string
    type: pointer<IrType>
end

struct IrBlockTarget
    id: int
end

struct IrLocal
    id: int
    name: string
    type: pointer<IrType>
    kind: int
end

struct IrFunctionReference
    id: int
    name: string
    parameter_types: pointer<Vector<pointer<IrType>>>
    return_type: pointer<IrType>
end

struct IrValue
    kind: int
    id: int
    type: pointer<IrType>
    int_value: int
    float_value: float
    boolean_value: boolean
    char_value: char
    string_value: string
    producer: pointer<IrInstruction>
end

struct IrInstruction
    kind: int
    operator: int
    result: pointer<IrValue>
    operands: pointer<Vector<pointer<IrValue>>>
    local: pointer<IrLocal>
    target: pointer<IrFunctionReference>
    field: pointer<IrStructField>
    field_path: pointer<Vector<pointer<IrStructField>>>
end

struct IrTerminator
    kind: int
    value: pointer<IrValue>
    condition: pointer<IrValue>
    true_target: pointer<IrBlockTarget>
    false_target: pointer<IrBlockTarget>
end

struct IrBasicBlock
    target: pointer<IrBlockTarget>
    instructions: pointer<Vector<pointer<IrInstruction>>>
    terminator: pointer<IrTerminator>
    sealed: boolean
end

struct IrParameter
    value: pointer<IrValue>
    name: string
end

struct IrFunction
    id: int
    name: string
    parameters: pointer<Vector<pointer<IrParameter>>>
    return_type: pointer<IrType>
    blocks: pointer<Vector<pointer<IrBasicBlock>>>
    has_body: boolean
    sealed: boolean
end

struct IrModule
    name: string
    structs: pointer<Vector<pointer<IrType>>>
    functions: pointer<Vector<pointer<IrFunction>>>
    sealed: boolean
end

struct IrProgram
    arena: pointer<IrArena>
    modules: pointer<Vector<pointer<IrModule>>>
    entry_module: pointer<IrModule>
    entry_function: pointer<IrFunction>
    sealed: boolean
end

struct IrArena
    types: pointer<Vector<pointer<IrType>>>
    fields: pointer<Vector<pointer<IrStructField>>>
    targets: pointer<Vector<pointer<IrBlockTarget>>>
    locals: pointer<Vector<pointer<IrLocal>>>
    references: pointer<Vector<pointer<IrFunctionReference>>>
    values: pointer<Vector<pointer<IrValue>>>
    instructions: pointer<Vector<pointer<IrInstruction>>>
    terminators: pointer<Vector<pointer<IrTerminator>>>
    blocks: pointer<Vector<pointer<IrBasicBlock>>>
    parameters: pointer<Vector<pointer<IrParameter>>>
    functions: pointer<Vector<pointer<IrFunction>>>
    modules: pointer<Vector<pointer<IrModule>>>
    integer_type: pointer<IrType>
    float_type: pointer<IrType>
    boolean_type: pointer<IrType>
    char_type: pointer<IrType>
    string_type: pointer<IrType>
    void_type: pointer<IrType>
    error: string
end

fn create_ir_arena() -> pointer<IrArena>
    let arena: pointer<IrArena> = memory::allocate<IrArena>(1)

    if arena == null then
        return null
    end

    arena->types = create_vector<pointer<IrType>>()
    arena->fields = create_vector<pointer<IrStructField>>()
    arena->targets = create_vector<pointer<IrBlockTarget>>()
    arena->locals = create_vector<pointer<IrLocal>>()
    arena->references = create_vector<pointer<IrFunctionReference>>()
    arena->values = create_vector<pointer<IrValue>>()
    arena->instructions = create_vector<pointer<IrInstruction>>()
    arena->terminators = create_vector<pointer<IrTerminator>>()
    arena->blocks = create_vector<pointer<IrBasicBlock>>()
    arena->parameters = create_vector<pointer<IrParameter>>()
    arena->functions = create_vector<pointer<IrFunction>>()
    arena->modules = create_vector<pointer<IrModule>>()
    arena->integer_type = null
    arena->float_type = null
    arena->boolean_type = null
    arena->char_type = null
    arena->string_type = null
    arena->void_type = null
    arena->error = ""

    arena->integer_type = create_ir_primitive_type(arena, "int", true, true, true)
    arena->float_type = create_ir_primitive_type(arena, "float", true, true, false)
    arena->boolean_type = create_ir_primitive_type(arena, "boolean", true, false, false)
    arena->char_type = create_ir_primitive_type(arena, "char", true, false, false)
    arena->string_type = create_ir_primitive_type(arena, "string", true, false, false)
    arena->void_type = create_ir_primitive_type(arena, "void", false, false, false)

    if arena->integer_type == null || arena->float_type == null || arena->boolean_type == null || arena->char_type == null || arena->string_type == null || arena->void_type == null then
        destroy_ir_arena(arena)
        return null
    end

    arena->error = ""
    return arena
end

fn destroy_ir_arena(arena: pointer<IrArena>) -> void
    if arena == null then
        return
    end

    @mut let index: int = vector_length<pointer<IrModule>>(arena->modules)

    while index > 0 do
        index = index - 1
        destroy_ir_module_storage(vector_get<pointer<IrModule>>(arena->modules, index))
    end

    index = vector_length<pointer<IrFunction>>(arena->functions)
    while index > 0 do
        index = index - 1
        destroy_ir_function_storage(vector_get<pointer<IrFunction>>(arena->functions, index))
    end

    index = vector_length<pointer<IrParameter>>(arena->parameters)
    while index > 0 do
        index = index - 1
        memory::free<IrParameter>(vector_get<pointer<IrParameter>>(arena->parameters, index))
    end

    index = vector_length<pointer<IrBasicBlock>>(arena->blocks)
    while index > 0 do
        index = index - 1
        destroy_ir_block_storage(vector_get<pointer<IrBasicBlock>>(arena->blocks, index))
    end

    index = vector_length<pointer<IrTerminator>>(arena->terminators)
    while index > 0 do
        index = index - 1
        memory::free<IrTerminator>(vector_get<pointer<IrTerminator>>(arena->terminators, index))
    end

    index = vector_length<pointer<IrInstruction>>(arena->instructions)
    while index > 0 do
        index = index - 1
        destroy_ir_instruction_storage(vector_get<pointer<IrInstruction>>(arena->instructions, index))
    end

    index = vector_length<pointer<IrValue>>(arena->values)
    while index > 0 do
        index = index - 1
        memory::free<IrValue>(vector_get<pointer<IrValue>>(arena->values, index))
    end

    index = vector_length<pointer<IrFunctionReference>>(arena->references)
    while index > 0 do
        index = index - 1
        destroy_ir_reference_storage(vector_get<pointer<IrFunctionReference>>(arena->references, index))
    end

    index = vector_length<pointer<IrLocal>>(arena->locals)
    while index > 0 do
        index = index - 1
        memory::free<IrLocal>(vector_get<pointer<IrLocal>>(arena->locals, index))
    end

    index = vector_length<pointer<IrBlockTarget>>(arena->targets)
    while index > 0 do
        index = index - 1
        memory::free<IrBlockTarget>(vector_get<pointer<IrBlockTarget>>(arena->targets, index))
    end

    index = vector_length<pointer<IrStructField>>(arena->fields)
    while index > 0 do
        index = index - 1
        memory::free<IrStructField>(vector_get<pointer<IrStructField>>(arena->fields, index))
    end

    index = vector_length<pointer<IrType>>(arena->types)
    while index > 0 do
        index = index - 1
        destroy_ir_type_storage(vector_get<pointer<IrType>>(arena->types, index))
    end

    destroy_vector<pointer<IrModule>>(arena->modules)
    destroy_vector<pointer<IrFunction>>(arena->functions)
    destroy_vector<pointer<IrParameter>>(arena->parameters)
    destroy_vector<pointer<IrBasicBlock>>(arena->blocks)
    destroy_vector<pointer<IrTerminator>>(arena->terminators)
    destroy_vector<pointer<IrInstruction>>(arena->instructions)
    destroy_vector<pointer<IrValue>>(arena->values)
    destroy_vector<pointer<IrFunctionReference>>(arena->references)
    destroy_vector<pointer<IrLocal>>(arena->locals)
    destroy_vector<pointer<IrBlockTarget>>(arena->targets)
    destroy_vector<pointer<IrStructField>>(arena->fields)
    destroy_vector<pointer<IrType>>(arena->types)
    memory::free<IrArena>(arena)
    return
end

fn ir_error(arena: pointer<IrArena>, message: string) -> boolean
    if arena != null then
        arena->error = message
    end

    return false
end

fn ir_clear_error(arena: pointer<IrArena>) -> void
    if arena != null then
        arena->error = ""
    end

    return
end

fn ir_has_error(arena: pointer<IrArena>) -> boolean
    if arena == null then
        return false
    end
    return arena->error != ""
end

fn create_ir_primitive_type(arena: pointer<IrArena>, name: string, value_type: boolean, numeric: boolean, integral: boolean) -> pointer<IrType>
    if arena == null || name == "" || (integral && !numeric) then
        ir_error(arena, "invalid primitive IR type")
        return null
    end

    let type: pointer<IrType> = allocate_ir_type(arena)
    if type == null then
        return null
    end

    type->kind = ir_type_primitive()
    type->name = name
    type->value_type = value_type
    type->numeric = numeric
    type->integral = integral
    type->defined = true
    return type
end

fn create_ir_pointer_type(arena: pointer<IrArena>, element_type: pointer<IrType>) -> pointer<IrType>
    if arena == null then
        return null
    end
    if element_type == null then
        ir_error(arena, "IR pointer element must be a value type")
        return null
    end
    if !element_type->value_type then
        ir_error(arena, "IR pointer element must be a value type")
        return null
    end

    let existing: pointer<IrType> = find_ir_pointer_type(arena, element_type)
    if existing != null then
        return existing
    end

    let type: pointer<IrType> = allocate_ir_type(arena)
    if type == null then
        return null
    end

    type->kind = ir_type_pointer()
    type->name = "pointer<" + element_type->name + ">"
    type->value_type = true
    type->numeric = false
    type->integral = false
    type->element_type = element_type
    type->defined = true
    return type
end

fn create_ir_struct_type(arena: pointer<IrArena>, name: string) -> pointer<IrType>
    if arena == null || name == "" then
        ir_error(arena, "IR struct type name must not be empty")
        return null
    end

    let type: pointer<IrType> = allocate_ir_type(arena)
    if type == null then
        return null
    end

    type->kind = ir_type_struct()
    type->name = name
    type->value_type = true
    type->numeric = false
    type->integral = false
    type->defined = false
    return type
end

fn define_ir_struct_type(arena: pointer<IrArena>, type: pointer<IrType>, fields: pointer<Vector<pointer<IrStructField>>>) -> boolean
    if arena == null || type == null || fields == null then
        return ir_error(arena, "invalid IR struct definition")
    end
    if type->kind != ir_type_struct() then
        return ir_error(arena, "invalid IR struct definition")
    end

    if type->defined then
        return ir_error(arena, "IR struct type is already defined")
    end

    @mut let index: int = 0
    let count: int = vector_length<pointer<IrStructField>>(fields)

    while index < count do
        let field: pointer<IrStructField> = vector_get<pointer<IrStructField>>(fields, index)
        if field == null then
            return ir_error(arena, "invalid ordered IR struct field")
        end
        if field->type == null then
            return ir_error(arena, "invalid ordered IR struct field")
        end
        if field->index != index || field->name == "" || !field->type->value_type then
            return ir_error(arena, "invalid ordered IR struct field")
        end

        @mut let previous: int = 0
        while previous < index do
            if vector_get<pointer<IrStructField>>(fields, previous)->name == field->name then
                return ir_error(arena, "duplicate IR struct field name")
            end
            previous = previous + 1
        end

        if ir_type_contains_by_value(field->type, type, null) then
            return ir_error(arena, "recursive by-value IR struct layout")
        end

        index = index + 1
    end

    index = 0
    while index < count do
        vector_push<pointer<IrStructField>>(
            type->fields,
            vector_get<pointer<IrStructField>>(fields, index)
        )
        index = index + 1
    end

    type->defined = true
    return true
end

fn create_ir_struct_field(arena: pointer<IrArena>, index: int, name: string, type: pointer<IrType>) -> pointer<IrStructField>
    if arena == null then
        return null
    end
    if type == null then
        ir_error(arena, "invalid IR struct field")
        return null
    end
    if index < 0 || name == "" || !type->value_type then
        ir_error(arena, "invalid IR struct field")
        return null
    end

    let field: pointer<IrStructField> = memory::allocate<IrStructField>(1)
    if field == null then
        ir_error(arena, "IR allocation failed")
        return null
    end

    field->index = index
    field->name = name
    field->type = type
    vector_push<pointer<IrStructField>>(arena->fields, field)
    return field
end

fn ir_type_equals(left: pointer<IrType>, right: pointer<IrType>) -> boolean
    if left == right then
        return true
    end
    if left == null || right == null then
        return false
    end
    if left->kind != right->kind then
        return false
    end
    if left->kind == ir_type_pointer() then
        return ir_type_equals(left->element_type, right->element_type)
    end
    if left->kind == ir_type_struct() then
        return false
    end
    return left->name == right->name
end

fn ir_struct_field(type: pointer<IrType>, name: string) -> pointer<IrStructField>
    if type == null then
        return null
    end
    if type->kind != ir_type_struct() || !type->defined || name == "" then
        return null
    end

    @mut let index: int = 0
    let count: int = vector_length<pointer<IrStructField>>(type->fields)
    while index < count do
        let field: pointer<IrStructField> = vector_get<pointer<IrStructField>>(type->fields, index)
        if field->name == name then
            return field
        end
        index = index + 1
    end
    return null
end

fn create_ir_block_target(arena: pointer<IrArena>, id: int) -> pointer<IrBlockTarget>
    if arena == null || id < 0 then
        ir_error(arena, "IR block identifier must not be negative")
        return null
    end
    let target: pointer<IrBlockTarget> = memory::allocate<IrBlockTarget>(1)
    if target == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    target->id = id
    vector_push<pointer<IrBlockTarget>>(arena->targets, target)
    return target
end

fn create_ir_local(arena: pointer<IrArena>, id: int, name: string, type: pointer<IrType>, kind: int) -> pointer<IrLocal>
    if arena == null then
        return null
    end
    if type == null then
        ir_error(arena, "invalid IR local")
        return null
    end
    if id < 0 || name == "" || !type->value_type || kind < ir_local_constant() || kind > ir_local_mutable() then
        ir_error(arena, "invalid IR local")
        return null
    end
    let local: pointer<IrLocal> = memory::allocate<IrLocal>(1)
    if local == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    local->id = id
    local->name = name
    local->type = type
    local->kind = kind
    vector_push<pointer<IrLocal>>(arena->locals, local)
    return local
end

fn create_ir_function_reference(arena: pointer<IrArena>, id: int, name: string, parameter_types: pointer<Vector<pointer<IrType>>>, return_type: pointer<IrType>) -> pointer<IrFunctionReference>
    if arena == null || id < 0 || name == "" || parameter_types == null || return_type == null then
        ir_error(arena, "invalid IR function reference")
        return null
    end
    let reference: pointer<IrFunctionReference> = memory::allocate<IrFunctionReference>(1)
    if reference == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    reference->id = id
    reference->name = name
    reference->parameter_types = create_vector<pointer<IrType>>()
    reference->return_type = return_type
    @mut let index: int = 0
    while index < vector_length<pointer<IrType>>(parameter_types) do
        let type: pointer<IrType> = vector_get<pointer<IrType>>(parameter_types, index)
        if type == null then
            destroy_vector<pointer<IrType>>(reference->parameter_types)
            memory::free<IrFunctionReference>(reference)
            ir_error(arena, "IR function reference parameter must be a value type")
            return null
        end
        if !type->value_type then
            destroy_vector<pointer<IrType>>(reference->parameter_types)
            memory::free<IrFunctionReference>(reference)
            ir_error(arena, "IR function reference parameter must be a value type")
            return null
        end
        vector_push<pointer<IrType>>(reference->parameter_types, type)
        index = index + 1
    end
    vector_push<pointer<IrFunctionReference>>(arena->references, reference)
    return reference
end

fn ir_function_reference_returns_value(reference: pointer<IrFunctionReference>) -> boolean
    if reference == null then
        return false
    end
    if reference->return_type == null then
        return false
    end
    return reference->return_type->value_type
end

fn create_ir_parameter(arena: pointer<IrArena>, id: int, name: string, type: pointer<IrType>) -> pointer<IrParameter>
    if arena == null then
        return null
    end
    if type == null then
        ir_error(arena, "invalid IR parameter")
        return null
    end
    if id < 0 || name == "" || !type->value_type then
        ir_error(arena, "invalid IR parameter")
        return null
    end
    let value: pointer<IrValue> = create_ir_value(arena, ir_value_parameter(), id, type)
    if value == null then
        return null
    end
    let parameter: pointer<IrParameter> = memory::allocate<IrParameter>(1)
    if parameter == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    parameter->value = value
    parameter->name = name
    vector_push<pointer<IrParameter>>(arena->parameters, parameter)
    return parameter
end

fn create_ir_int_constant(arena: pointer<IrArena>, id: int, value: int) -> pointer<IrValue>
    if arena == null then
        return null
    end
    let result: pointer<IrValue> = create_ir_value(arena, ir_value_int_constant(), id, arena->integer_type)
    if result != null then
        result->int_value = value
    end
    return result
end

fn create_ir_float_constant(arena: pointer<IrArena>, id: int, value: float, text: string) -> pointer<IrValue>
    if arena == null then
        return null
    end
    if text == "" then
        ir_error(arena, "IR float constant text must not be empty")
        return null
    end
    let result: pointer<IrValue> = create_ir_value(arena, ir_value_float_constant(), id, arena->float_type)
    if result != null then
        result->float_value = value
        result->string_value = text
    end
    return result
end

fn create_ir_boolean_constant(arena: pointer<IrArena>, id: int, value: boolean) -> pointer<IrValue>
    if arena == null then
        return null
    end
    let result: pointer<IrValue> = create_ir_value(arena, ir_value_boolean_constant(), id, arena->boolean_type)
    if result != null then
        result->boolean_value = value
    end
    return result
end

fn create_ir_char_constant(arena: pointer<IrArena>, id: int, value: char, text: string) -> pointer<IrValue>
    if arena == null then
        return null
    end
    if text == "" then
        ir_error(arena, "IR char constant text must not be empty")
        return null
    end
    let result: pointer<IrValue> = create_ir_value(arena, ir_value_char_constant(), id, arena->char_type)
    if result != null then
        result->char_value = value
        result->string_value = text
    end
    return result
end

fn create_ir_string_constant(arena: pointer<IrArena>, id: int, value: string) -> pointer<IrValue>
    if arena == null then
        return null
    end
    let result: pointer<IrValue> = create_ir_value(arena, ir_value_string_constant(), id, arena->string_type)
    if result != null then
        result->string_value = value
    end
    return result
end

fn create_ir_null_constant(arena: pointer<IrArena>, id: int, type: pointer<IrType>) -> pointer<IrValue>
    if type == null then
        ir_error(arena, "IR null constant must have pointer type")
        return null
    end
    if type->kind != ir_type_pointer() then
        ir_error(arena, "IR null constant must have pointer type")
        return null
    end
    return create_ir_value(arena, ir_value_null_constant(), id, type)
end

fn create_ir_value(arena: pointer<IrArena>, kind: int, id: int, type: pointer<IrType>) -> pointer<IrValue>
    if arena == null then
        return null
    end
    if type == null then
        ir_error(arena, "invalid IR value")
        return null
    end
    if id < 0 || !type->value_type then
        ir_error(arena, "invalid IR value")
        return null
    end
    let value: pointer<IrValue> = memory::allocate<IrValue>(1)
    if value == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    value->kind = kind
    value->id = id
    value->type = type
    value->int_value = 0
    value->float_value = 0.0
    value->boolean_value = false
    value->char_value = ' '
    value->string_value = ""
    value->producer = null
    vector_push<pointer<IrValue>>(arena->values, value)
    return value
end

fn create_ir_unary_instruction(arena: pointer<IrArena>, id: int, operator: int, operand: pointer<IrValue>) -> pointer<IrInstruction>
    let result_type: pointer<IrType> = ir_unary_result_type(arena, operator, operand)
    if result_type == null then
        return null
    end
    let operands: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(operands, operand)
    let instruction: pointer<IrInstruction> = create_ir_value_instruction(arena, ir_instruction_unary(), id, result_type, operands)
    destroy_vector<pointer<IrValue>>(operands)
    if instruction != null then
        instruction->operator = operator
    end
    return instruction
end

fn create_ir_binary_instruction(arena: pointer<IrArena>, id: int, operator: int, left: pointer<IrValue>, right: pointer<IrValue>) -> pointer<IrInstruction>
    let result_type: pointer<IrType> = ir_binary_result_type(arena, operator, left, right)
    if result_type == null then
        return null
    end
    let operands: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(operands, left)
    vector_push<pointer<IrValue>>(operands, right)
    let instruction: pointer<IrInstruction> = create_ir_value_instruction(arena, ir_instruction_binary(), id, result_type, operands)
    destroy_vector<pointer<IrValue>>(operands)
    if instruction != null then
        instruction->operator = operator
    end
    return instruction
end

fn create_ir_local_initialize(arena: pointer<IrArena>, local: pointer<IrLocal>, initializer: pointer<IrValue>) -> pointer<IrInstruction>
    if local == null || initializer == null then
        ir_error(arena, "IR local initializer type mismatch")
        return null
    end
    if !ir_type_equals(local->type, initializer->type) then
        ir_error(arena, "IR local initializer type mismatch")
        return null
    end
    let instruction: pointer<IrInstruction> = allocate_ir_instruction(arena, ir_instruction_local_initialize())
    if instruction != null then
        instruction->local = local
        vector_push<pointer<IrValue>>(instruction->operands, initializer)
    end
    return instruction
end

fn create_ir_local_load(arena: pointer<IrArena>, id: int, local: pointer<IrLocal>) -> pointer<IrInstruction>
    if local == null then
        ir_error(arena, "IR local load requires a local")
        return null
    end
    let operands: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    let instruction: pointer<IrInstruction> = create_ir_value_instruction(arena, ir_instruction_local_load(), id, local->type, operands)
    destroy_vector<pointer<IrValue>>(operands)
    if instruction != null then
        instruction->local = local
    end
    return instruction
end

fn create_ir_local_store(arena: pointer<IrArena>, local: pointer<IrLocal>, value: pointer<IrValue>) -> pointer<IrInstruction>
    if local == null || value == null then
        ir_error(arena, "invalid IR local store")
        return null
    end
    if local->kind != ir_local_mutable() || !ir_type_equals(local->type, value->type) then
        ir_error(arena, "invalid IR local store")
        return null
    end
    let instruction: pointer<IrInstruction> = allocate_ir_instruction(arena, ir_instruction_local_store())
    if instruction != null then
        instruction->local = local
        vector_push<pointer<IrValue>>(instruction->operands, value)
    end
    return instruction
end

fn create_ir_value_call_instruction(arena: pointer<IrArena>, id: int, target: pointer<IrFunctionReference>, arguments: pointer<Vector<pointer<IrValue>>>) -> pointer<IrInstruction>
    if !validate_ir_call_arguments(arena, target, arguments) then
        return null
    end
    if !target->return_type->value_type then
        ir_error(arena, "value-producing IR call target must return a value")
        return null
    end
    let instruction: pointer<IrInstruction> = create_ir_value_instruction(arena, ir_instruction_value_call(), id, target->return_type, arguments)
    if instruction != null then
        instruction->target = target
    end
    return instruction
end

fn create_ir_void_call_instruction(arena: pointer<IrArena>, target: pointer<IrFunctionReference>, arguments: pointer<Vector<pointer<IrValue>>>) -> pointer<IrInstruction>
    if !validate_ir_call_arguments(arena, target, arguments) then
        return null
    end
    if target->return_type->value_type then
        ir_error(arena, "void IR call target must return void")
        return null
    end
    let instruction: pointer<IrInstruction> = allocate_ir_instruction(arena, ir_instruction_void_call())
    if instruction == null then
        return null
    end
    instruction->target = target
    copy_ir_values(instruction->operands, arguments)
    return instruction
end

fn create_ir_struct_construct(arena: pointer<IrArena>, id: int, type: pointer<IrType>, fields: pointer<Vector<pointer<IrValue>>>) -> pointer<IrInstruction>
    if type == null || fields == null then
        ir_error(arena, "invalid IR struct construction")
        return null
    end
    if type->kind != ir_type_struct() || !type->defined || vector_length<pointer<IrStructField>>(type->fields) != vector_length<pointer<IrValue>>(fields) then
        ir_error(arena, "invalid IR struct construction")
        return null
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(fields) do
        if !ir_type_equals(vector_get<pointer<IrValue>>(fields, index)->type, vector_get<pointer<IrStructField>>(type->fields, index)->type) then
            ir_error(arena, "IR struct construction field type mismatch")
            return null
        end
        index = index + 1
    end
    return create_ir_value_instruction(arena, ir_instruction_struct_construct(), id, type, fields)
end

fn create_ir_struct_extract(arena: pointer<IrArena>, id: int, target: pointer<IrValue>, field: pointer<IrStructField>) -> pointer<IrInstruction>
    if target == null || field == null then
        ir_error(arena, "invalid IR struct field extraction")
        return null
    end
    if target->type->kind != ir_type_struct() || !ir_type_owns_field(target->type, field) then
        ir_error(arena, "invalid IR struct field extraction")
        return null
    end
    let operands: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(operands, target)
    let instruction: pointer<IrInstruction> = create_ir_value_instruction(arena, ir_instruction_struct_extract(), id, field->type, operands)
    destroy_vector<pointer<IrValue>>(operands)
    if instruction != null then
        instruction->field = field
    end
    return instruction
end

fn create_ir_struct_field_store(arena: pointer<IrArena>, local: pointer<IrLocal>, path: pointer<Vector<pointer<IrStructField>>>, value: pointer<IrValue>) -> pointer<IrInstruction>
    if local == null || path == null || value == null then
        ir_error(arena, "invalid IR struct field store")
        return null
    end
    if local->kind != ir_local_mutable() || vector_length<pointer<IrStructField>>(path) == 0 then
        ir_error(arena, "invalid IR struct field store")
        return null
    end
    @mut let current: pointer<IrType> = local->type
    @mut let index: int = 0
    while index < vector_length<pointer<IrStructField>>(path) do
        let field: pointer<IrStructField> = vector_get<pointer<IrStructField>>(path, index)
        if current == null then
            ir_error(arena, "invalid IR struct field path")
            return null
        end
        if current->kind != ir_type_struct() || !ir_type_owns_field(current, field) then
            ir_error(arena, "invalid IR struct field path")
            return null
        end
        current = field->type
        index = index + 1
    end
    if !ir_type_equals(current, value->type) then
        ir_error(arena, "IR struct field store type mismatch")
        return null
    end
    let instruction: pointer<IrInstruction> = allocate_ir_instruction(arena, ir_instruction_struct_field_store())
    if instruction != null then
        instruction->local = local
        copy_ir_fields(instruction->field_path, path)
        vector_push<pointer<IrValue>>(instruction->operands, value)
    end
    return instruction
end

fn create_ir_pointer_load(arena: pointer<IrArena>, id: int, pointer_value: pointer<IrValue>) -> pointer<IrInstruction>
    if pointer_value == null then
        ir_error(arena, "IR pointer load requires a pointer")
        return null
    end
    if pointer_value->type->kind != ir_type_pointer() then
        ir_error(arena, "IR pointer load requires a pointer")
        return null
    end
    return create_ir_single_operand_value_instruction(arena, ir_instruction_pointer_load(), id, pointer_value->type->element_type, pointer_value)
end

fn create_ir_pointer_store(arena: pointer<IrArena>, pointer_value: pointer<IrValue>, value: pointer<IrValue>) -> pointer<IrInstruction>
    if pointer_value == null || value == null then
        ir_error(arena, "invalid IR pointer store")
        return null
    end
    if pointer_value->type->kind != ir_type_pointer() || !ir_type_equals(pointer_value->type->element_type, value->type) then
        ir_error(arena, "invalid IR pointer store")
        return null
    end
    return create_ir_two_operand_instruction(arena, ir_instruction_pointer_store(), pointer_value, value)
end

fn create_ir_pointer_index_load(arena: pointer<IrArena>, id: int, pointer_value: pointer<IrValue>, index: pointer<IrValue>) -> pointer<IrInstruction>
    if !validate_ir_pointer_index(arena, pointer_value, index) then
        return null
    end
    let operands: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(operands, pointer_value)
    vector_push<pointer<IrValue>>(operands, index)
    let instruction: pointer<IrInstruction> = create_ir_value_instruction(arena, ir_instruction_pointer_index_load(), id, pointer_value->type->element_type, operands)
    destroy_vector<pointer<IrValue>>(operands)
    return instruction
end

fn create_ir_pointer_index_store(arena: pointer<IrArena>, pointer_value: pointer<IrValue>, index: pointer<IrValue>, value: pointer<IrValue>) -> pointer<IrInstruction>
    if !validate_ir_pointer_index(arena, pointer_value, index) then
        return null
    end
    if value == null then
        ir_error(arena, "invalid IR indexed pointer store")
        return null
    end
    if !ir_type_equals(pointer_value->type->element_type, value->type) then
        ir_error(arena, "invalid IR indexed pointer store")
        return null
    end
    let instruction: pointer<IrInstruction> = allocate_ir_instruction(arena, ir_instruction_pointer_index_store())
    if instruction != null then
        vector_push<pointer<IrValue>>(instruction->operands, pointer_value)
        vector_push<pointer<IrValue>>(instruction->operands, index)
        vector_push<pointer<IrValue>>(instruction->operands, value)
    end
    return instruction
end

fn create_ir_pointer_field_load(arena: pointer<IrArena>, id: int, pointer_value: pointer<IrValue>, field: pointer<IrStructField>) -> pointer<IrInstruction>
    if !validate_ir_pointer_field(arena, pointer_value, field) then
        return null
    end
    let instruction: pointer<IrInstruction> = create_ir_single_operand_value_instruction(arena, ir_instruction_pointer_field_load(), id, field->type, pointer_value)
    if instruction != null then
        instruction->field = field
    end
    return instruction
end

fn create_ir_pointer_field_store(arena: pointer<IrArena>, pointer_value: pointer<IrValue>, field: pointer<IrStructField>, value: pointer<IrValue>) -> pointer<IrInstruction>
    if !validate_ir_pointer_field(arena, pointer_value, field) then
        return null
    end
    if value == null then
        ir_error(arena, "invalid IR pointer field store")
        return null
    end
    if !ir_type_equals(field->type, value->type) then
        ir_error(arena, "invalid IR pointer field store")
        return null
    end
    let instruction: pointer<IrInstruction> = create_ir_two_operand_instruction(arena, ir_instruction_pointer_field_store(), pointer_value, value)
    if instruction != null then
        instruction->field = field
    end
    return instruction
end

fn create_ir_string_index(arena: pointer<IrArena>, id: int, value: pointer<IrValue>, index: pointer<IrValue>) -> pointer<IrInstruction>
    if arena == null then
        return null
    end
    if value == null || index == null then
        ir_error(arena, "IR string index requires string and int operands")
        return null
    end
    if value->type != arena->string_type || index->type != arena->integer_type then
        ir_error(arena, "IR string index requires string and int operands")
        return null
    end
    let operands: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(operands, value)
    vector_push<pointer<IrValue>>(operands, index)
    let instruction: pointer<IrInstruction> = create_ir_value_instruction(arena, ir_instruction_string_index(), id, arena->char_type, operands)
    destroy_vector<pointer<IrValue>>(operands)
    return instruction
end

fn create_ir_return(arena: pointer<IrArena>, value: pointer<IrValue>) -> pointer<IrTerminator>
    let terminator: pointer<IrTerminator> = allocate_ir_terminator(arena, ir_terminator_return())
    if terminator != null then
        terminator->value = value
    end
    return terminator
end

fn create_ir_branch(arena: pointer<IrArena>, target: pointer<IrBlockTarget>) -> pointer<IrTerminator>
    if target == null then
        ir_error(arena, "IR branch target must not be null")
        return null
    end
    let terminator: pointer<IrTerminator> = allocate_ir_terminator(arena, ir_terminator_branch())
    if terminator != null then
        terminator->true_target = target
    end
    return terminator
end

fn create_ir_conditional_branch(arena: pointer<IrArena>, condition: pointer<IrValue>, true_target: pointer<IrBlockTarget>, false_target: pointer<IrBlockTarget>) -> pointer<IrTerminator>
    if arena == null then
        return null
    end
    if condition == null || true_target == null || false_target == null then
        ir_error(arena, "invalid IR conditional branch")
        return null
    end
    if condition->type != arena->boolean_type then
        ir_error(arena, "invalid IR conditional branch")
        return null
    end
    let terminator: pointer<IrTerminator> = allocate_ir_terminator(arena, ir_terminator_conditional_branch())
    if terminator != null then
        terminator->condition = condition
        terminator->true_target = true_target
        terminator->false_target = false_target
    end
    return terminator
end

fn create_ir_basic_block(arena: pointer<IrArena>, target: pointer<IrBlockTarget>) -> pointer<IrBasicBlock>
    if arena == null || target == null then
        ir_error(arena, "invalid IR basic block")
        return null
    end
    let block: pointer<IrBasicBlock> = memory::allocate<IrBasicBlock>(1)
    if block == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    block->target = target
    block->instructions = create_vector<pointer<IrInstruction>>()
    block->terminator = null
    block->sealed = false
    vector_push<pointer<IrBasicBlock>>(arena->blocks, block)
    return block
end

fn ir_block_add_instruction(arena: pointer<IrArena>, block: pointer<IrBasicBlock>, instruction: pointer<IrInstruction>) -> boolean
    if block == null || instruction == null then
        return ir_error(arena, "cannot append IR instruction to sealed block")
    end
    if block->sealed || block->terminator != null then
        return ir_error(arena, "cannot append IR instruction to sealed block")
    end
    if ir_pointer_in_instructions(block->instructions, instruction) then
        return ir_error(arena, "duplicate IR instruction instance in block")
    end
    vector_push<pointer<IrInstruction>>(block->instructions, instruction)
    return true
end

fn ir_block_terminate(arena: pointer<IrArena>, block: pointer<IrBasicBlock>, terminator: pointer<IrTerminator>) -> boolean
    if block == null || terminator == null then
        return ir_error(arena, "invalid or duplicate IR block terminator")
    end
    if block->sealed || block->terminator != null then
        return ir_error(arena, "invalid or duplicate IR block terminator")
    end
    block->terminator = terminator
    block->sealed = true
    return true
end

fn create_ir_function(arena: pointer<IrArena>, id: int, name: string, return_type: pointer<IrType>, has_body: boolean) -> pointer<IrFunction>
    if arena == null || id < 0 || name == "" || return_type == null then
        ir_error(arena, "invalid IR function")
        return null
    end
    let function: pointer<IrFunction> = memory::allocate<IrFunction>(1)
    if function == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    function->id = id
    function->name = name
    function->parameters = create_vector<pointer<IrParameter>>()
    function->return_type = return_type
    function->blocks = create_vector<pointer<IrBasicBlock>>()
    function->has_body = has_body
    function->sealed = false
    vector_push<pointer<IrFunction>>(arena->functions, function)
    return function
end

fn ir_function_add_parameter(arena: pointer<IrArena>, function: pointer<IrFunction>, parameter: pointer<IrParameter>) -> boolean
    if function == null || parameter == null then
        return ir_error(arena, "cannot append IR function parameter")
    end
    if function->sealed then
        return ir_error(arena, "cannot append IR function parameter")
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrParameter>>(function->parameters) do
        let current: pointer<IrParameter> = vector_get<pointer<IrParameter>>(function->parameters, index)
        if current == parameter || current->value->id == parameter->value->id || current->name == parameter->name then
            return ir_error(arena, "duplicate IR function parameter")
        end
        index = index + 1
    end
    vector_push<pointer<IrParameter>>(function->parameters, parameter)
    return true
end

fn ir_function_add_block(arena: pointer<IrArena>, function: pointer<IrFunction>, block: pointer<IrBasicBlock>) -> boolean
    if function == null || block == null then
        return ir_error(arena, "cannot append IR function block")
    end
    if function->sealed || !function->has_body || !block->sealed then
        return ir_error(arena, "cannot append IR function block")
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrBasicBlock>>(function->blocks) do
        let current: pointer<IrBasicBlock> = vector_get<pointer<IrBasicBlock>>(function->blocks, index)
        if current == block || current->target == block->target || current->target->id == block->target->id then
            return ir_error(arena, "duplicate IR function block")
        end
        index = index + 1
    end
    vector_push<pointer<IrBasicBlock>>(function->blocks, block)
    return true
end

fn create_ir_module(arena: pointer<IrArena>, name: string) -> pointer<IrModule>
    if arena == null || name == "" then
        ir_error(arena, "IR module name must not be empty")
        return null
    end
    let module: pointer<IrModule> = memory::allocate<IrModule>(1)
    if module == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    module->name = name
    module->structs = create_vector<pointer<IrType>>()
    module->functions = create_vector<pointer<IrFunction>>()
    module->sealed = false
    vector_push<pointer<IrModule>>(arena->modules, module)
    return module
end

fn ir_module_add_struct(arena: pointer<IrArena>, module: pointer<IrModule>, type: pointer<IrType>) -> boolean
    if module == null || type == null then
        return ir_error(arena, "cannot append IR struct to module")
    end
    if module->sealed || type->kind != ir_type_struct() || !type->defined then
        return ir_error(arena, "cannot append IR struct to module")
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrType>>(module->structs) do
        let current: pointer<IrType> = vector_get<pointer<IrType>>(module->structs, index)
        if current == type || current->name == type->name then
            return ir_error(arena, "duplicate IR module struct")
        end
        index = index + 1
    end
    vector_push<pointer<IrType>>(module->structs, type)
    return true
end

fn ir_module_add_function(arena: pointer<IrArena>, module: pointer<IrModule>, function: pointer<IrFunction>) -> boolean
    if module == null || function == null then
        return ir_error(arena, "cannot append IR function to module")
    end
    if module->sealed || !function->sealed then
        return ir_error(arena, "cannot append IR function to module")
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrFunction>>(module->functions) do
        let current: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, index)
        if current == function || current->id == function->id || current->name == function->name then
            return ir_error(arena, "duplicate IR module function")
        end
        index = index + 1
    end
    vector_push<pointer<IrFunction>>(module->functions, function)
    return true
end

fn create_ir_program(arena: pointer<IrArena>) -> pointer<IrProgram>
    if arena == null then
        return null
    end
    let program: pointer<IrProgram> = memory::allocate<IrProgram>(1)
    if program == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    program->arena = arena
    program->modules = create_vector<pointer<IrModule>>()
    program->entry_module = null
    program->entry_function = null
    program->sealed = false
    return program
end

fn destroy_ir_program(program: pointer<IrProgram>) -> void
    if program == null then
        return
    end
    let arena: pointer<IrArena> = program->arena
    destroy_vector<pointer<IrModule>>(program->modules)
    memory::free<IrProgram>(program)
    destroy_ir_arena(arena)
    return
end

fn ir_program_add_module(program: pointer<IrProgram>, module: pointer<IrModule>) -> boolean
    if program == null then
        return false
    end
    if module == null then
        return ir_error(program->arena, "cannot append IR program module")
    end
    if program->sealed || !module->sealed then
        if program != null then
            return ir_error(program->arena, "cannot append IR program module")
        end
        return false
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrModule>>(program->modules) do
        let current: pointer<IrModule> = vector_get<pointer<IrModule>>(program->modules, index)
        if current == module || current->name == module->name then
            return ir_error(program->arena, "duplicate IR program module")
        end
        index = index + 1
    end
    vector_push<pointer<IrModule>>(program->modules, module)
    return true
end

fn ir_program_set_entry(program: pointer<IrProgram>, module: pointer<IrModule>, function: pointer<IrFunction>) -> boolean
    if program == null then
        return false
    end
    if module == null || function == null then
        return ir_error(program->arena, "invalid IR entry point")
    end
    if program->sealed || program->entry_module != null || !function->has_body || function->return_type != program->arena->integer_type || !ir_module_contains_function(module, function) then
        if program != null then
            return ir_error(program->arena, "invalid IR entry point")
        end
        return false
    end
    program->entry_module = module
    program->entry_function = function
    return true
end

fn ir_type_kind_name(kind: int) -> string
    if kind == ir_type_primitive() then
        return "primitive"
    end
    if kind == ir_type_struct() then
        return "struct"
    end
    if kind == ir_type_pointer() then
        return "pointer"
    end
    return "unknown"
end

fn ir_type_primitive() -> int
    return 1
end
fn ir_type_struct() -> int
    return 2
end
fn ir_type_pointer() -> int
    return 3
end

fn ir_local_constant() -> int
    return 1
end
fn ir_local_immutable() -> int
    return 2
end
fn ir_local_mutable() -> int
    return 3
end

fn ir_value_parameter() -> int
    return 1
end
fn ir_value_int_constant() -> int
    return 2
end
fn ir_value_float_constant() -> int
    return 3
end
fn ir_value_boolean_constant() -> int
    return 4
end
fn ir_value_char_constant() -> int
    return 5
end
fn ir_value_string_constant() -> int
    return 6
end
fn ir_value_null_constant() -> int
    return 7
end
fn ir_value_instruction() -> int
    return 8
end

fn ir_instruction_unary() -> int
    return 1
end
fn ir_instruction_binary() -> int
    return 2
end
fn ir_instruction_value_call() -> int
    return 3
end
fn ir_instruction_void_call() -> int
    return 4
end
fn ir_instruction_local_initialize() -> int
    return 5
end
fn ir_instruction_local_load() -> int
    return 6
end
fn ir_instruction_local_store() -> int
    return 7
end
fn ir_instruction_struct_construct() -> int
    return 8
end
fn ir_instruction_struct_extract() -> int
    return 9
end
fn ir_instruction_struct_field_store() -> int
    return 10
end
fn ir_instruction_pointer_load() -> int
    return 11
end
fn ir_instruction_pointer_store() -> int
    return 12
end
fn ir_instruction_pointer_index_load() -> int
    return 13
end
fn ir_instruction_pointer_index_store() -> int
    return 14
end
fn ir_instruction_pointer_field_load() -> int
    return 15
end
fn ir_instruction_pointer_field_store() -> int
    return 16
end
fn ir_instruction_string_index() -> int
    return 17
end

fn ir_unary_logical_not() -> int
    return 1
end
fn ir_unary_negate() -> int
    return 2
end
fn ir_unary_positive() -> int
    return 3
end

fn ir_binary_multiply() -> int
    return 1
end
fn ir_binary_divide() -> int
    return 2
end
fn ir_binary_remainder() -> int
    return 3
end
fn ir_binary_add() -> int
    return 4
end
fn ir_binary_subtract() -> int
    return 5
end
fn ir_binary_less_than() -> int
    return 6
end
fn ir_binary_less_than_or_equal() -> int
    return 7
end
fn ir_binary_greater_than() -> int
    return 8
end
fn ir_binary_greater_than_or_equal() -> int
    return 9
end
fn ir_binary_equal() -> int
    return 10
end
fn ir_binary_not_equal() -> int
    return 11
end
fn ir_binary_logical_and() -> int
    return 12
end
fn ir_binary_logical_or() -> int
    return 13
end

fn ir_terminator_return() -> int
    return 1
end
fn ir_terminator_branch() -> int
    return 2
end
fn ir_terminator_conditional_branch() -> int
    return 3
end

fn allocate_ir_type(arena: pointer<IrArena>) -> pointer<IrType>
    let type: pointer<IrType> = memory::allocate<IrType>(1)
    if type == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    type->kind = 0
    type->name = ""
    type->value_type = false
    type->numeric = false
    type->integral = false
    type->element_type = null
    type->fields = create_vector<pointer<IrStructField>>()
    type->defined = false
    vector_push<pointer<IrType>>(arena->types, type)
    return type
end

fn allocate_ir_instruction(arena: pointer<IrArena>, kind: int) -> pointer<IrInstruction>
    if arena == null then
        return null
    end
    let instruction: pointer<IrInstruction> = memory::allocate<IrInstruction>(1)
    if instruction == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    instruction->kind = kind
    instruction->operator = 0
    instruction->result = null
    instruction->operands = create_vector<pointer<IrValue>>()
    instruction->local = null
    instruction->target = null
    instruction->field = null
    instruction->field_path = create_vector<pointer<IrStructField>>()
    vector_push<pointer<IrInstruction>>(arena->instructions, instruction)
    return instruction
end

fn create_ir_value_instruction(arena: pointer<IrArena>, kind: int, id: int, type: pointer<IrType>, operands: pointer<Vector<pointer<IrValue>>>) -> pointer<IrInstruction>
    if operands == null then
        ir_error(arena, "IR instruction operands must not be null")
        return null
    end
    let instruction: pointer<IrInstruction> = allocate_ir_instruction(arena, kind)
    if instruction == null then
        return null
    end
    let result: pointer<IrValue> = create_ir_value(arena, ir_value_instruction(), id, type)
    if result == null then
        return null
    end
    instruction->result = result
    result->producer = instruction
    copy_ir_values(instruction->operands, operands)
    return instruction
end

fn create_ir_single_operand_value_instruction(arena: pointer<IrArena>, kind: int, id: int, type: pointer<IrType>, operand: pointer<IrValue>) -> pointer<IrInstruction>
    let operands: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(operands, operand)
    let instruction: pointer<IrInstruction> = create_ir_value_instruction(arena, kind, id, type, operands)
    destroy_vector<pointer<IrValue>>(operands)
    return instruction
end

fn create_ir_two_operand_instruction(arena: pointer<IrArena>, kind: int, first: pointer<IrValue>, second: pointer<IrValue>) -> pointer<IrInstruction>
    let instruction: pointer<IrInstruction> = allocate_ir_instruction(arena, kind)
    if instruction != null then
        vector_push<pointer<IrValue>>(instruction->operands, first)
        vector_push<pointer<IrValue>>(instruction->operands, second)
    end
    return instruction
end

fn allocate_ir_terminator(arena: pointer<IrArena>, kind: int) -> pointer<IrTerminator>
    if arena == null then
        return null
    end
    let terminator: pointer<IrTerminator> = memory::allocate<IrTerminator>(1)
    if terminator == null then
        ir_error(arena, "IR allocation failed")
        return null
    end
    terminator->kind = kind
    terminator->value = null
    terminator->condition = null
    terminator->true_target = null
    terminator->false_target = null
    vector_push<pointer<IrTerminator>>(arena->terminators, terminator)
    return terminator
end

fn ir_unary_result_type(arena: pointer<IrArena>, operator: int, operand: pointer<IrValue>) -> pointer<IrType>
    if operand == null then
        ir_error(arena, "IR unary operand must not be null")
        return null
    end
    if operator == ir_unary_logical_not() && operand->type == arena->boolean_type then
        return arena->boolean_type
    end
    if (operator == ir_unary_negate() || operator == ir_unary_positive()) && operand->type->numeric then
        return operand->type
    end
    ir_error(arena, "invalid IR unary operand type")
    return null
end

fn ir_binary_result_type(arena: pointer<IrArena>, operator: int, left: pointer<IrValue>, right: pointer<IrValue>) -> pointer<IrType>
    if left == null || right == null then
        ir_error(arena, "IR binary operands must have matching types")
        return null
    end
    if !ir_type_equals(left->type, right->type) then
        ir_error(arena, "IR binary operands must have matching types")
        return null
    end
    if operator == ir_binary_add() && left->type == arena->string_type then
        return arena->string_type
    end
    if operator == ir_binary_equal() || operator == ir_binary_not_equal() then
        return arena->boolean_type
    end
    if operator == ir_binary_logical_and() || operator == ir_binary_logical_or() then
        if left->type == arena->boolean_type then
            return arena->boolean_type
        end
        ir_error(arena, "logical IR operands must be boolean")
        return null
    end
    if operator >= ir_binary_less_than() && operator <= ir_binary_greater_than_or_equal() then
        if left->type->numeric then
            return arena->boolean_type
        end
        ir_error(arena, "relational IR operands must be numeric")
        return null
    end
    if operator == ir_binary_remainder() then
        if left->type == arena->integer_type then
            return arena->integer_type
        end
        ir_error(arena, "remainder IR operands must be int")
        return null
    end
    if operator == ir_binary_multiply() || operator == ir_binary_divide() || operator == ir_binary_add() || operator == ir_binary_subtract() then
        if left->type->numeric then
            return left->type
        end
    end
    ir_error(arena, "invalid IR binary operand types")
    return null
end

fn validate_ir_call_arguments(arena: pointer<IrArena>, target: pointer<IrFunctionReference>, arguments: pointer<Vector<pointer<IrValue>>>) -> boolean
    if target == null || arguments == null then
        return ir_error(arena, "IR call argument count mismatch")
    end
    if vector_length<pointer<IrType>>(target->parameter_types) != vector_length<pointer<IrValue>>(arguments) then
        return ir_error(arena, "IR call argument count mismatch")
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(arguments) do
        let argument: pointer<IrValue> = vector_get<pointer<IrValue>>(arguments, index)
        if argument == null then
            return ir_error(arena, "IR call argument type mismatch")
        end
        if !ir_type_equals(argument->type, vector_get<pointer<IrType>>(target->parameter_types, index)) then
            return ir_error(arena, "IR call argument type mismatch")
        end
        index = index + 1
    end
    return true
end

fn validate_ir_pointer_index(arena: pointer<IrArena>, pointer_value: pointer<IrValue>, index: pointer<IrValue>) -> boolean
    if arena == null then
        return false
    end
    if pointer_value == null || index == null then
        return ir_error(arena, "IR pointer index requires pointer and int operands")
    end
    if pointer_value->type->kind != ir_type_pointer() || index->type != arena->integer_type then
        return ir_error(arena, "IR pointer index requires pointer and int operands")
    end
    return true
end

fn validate_ir_pointer_field(arena: pointer<IrArena>, pointer_value: pointer<IrValue>, field: pointer<IrStructField>) -> boolean
    if pointer_value == null || field == null then
        return ir_error(arena, "invalid IR pointer field")
    end
    if pointer_value->type->kind != ir_type_pointer() then
        return ir_error(arena, "invalid IR pointer field")
    end
    if pointer_value->type->element_type->kind != ir_type_struct() || !ir_type_owns_field(pointer_value->type->element_type, field) then
        return ir_error(arena, "invalid IR pointer field")
    end
    return true
end

fn ir_type_owns_field(type: pointer<IrType>, field: pointer<IrStructField>) -> boolean
    if type == null || field == null then
        return false
    end
    if type->fields == null then
        return false
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrStructField>>(type->fields) do
        if vector_get<pointer<IrStructField>>(type->fields, index) == field then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_type_contains_by_value(type: pointer<IrType>, target: pointer<IrType>, visited: pointer<Vector<pointer<IrType>>>) -> boolean
    if type == null then
        return false
    end
    if type->kind == ir_type_pointer() then
        return false
    end
    if type == target then
        return true
    end
    if type->kind != ir_type_struct() || !type->defined then
        return false
    end
    @mut let owns_visited: boolean = false
    @mut let seen: pointer<Vector<pointer<IrType>>> = visited
    if seen == null then
        seen = create_vector<pointer<IrType>>()
        owns_visited = true
    end
    if ir_pointer_in_types(seen, type) then
        if owns_visited then
            destroy_vector<pointer<IrType>>(seen)
        end
        return false
    end
    vector_push<pointer<IrType>>(seen, type)
    @mut let index: int = 0
    @mut let found: boolean = false
    while index < vector_length<pointer<IrStructField>>(type->fields) && !found do
        found = ir_type_contains_by_value(vector_get<pointer<IrStructField>>(type->fields, index)->type, target, seen)
        index = index + 1
    end
    vector_pop<pointer<IrType>>(seen)
    if owns_visited then
        destroy_vector<pointer<IrType>>(seen)
    end
    return found
end

fn find_ir_pointer_type(arena: pointer<IrArena>, element: pointer<IrType>) -> pointer<IrType>
    @mut let index: int = 0
    while index < vector_length<pointer<IrType>>(arena->types) do
        let type: pointer<IrType> = vector_get<pointer<IrType>>(arena->types, index)
        if type->kind == ir_type_pointer() && type->element_type == element then
            return type
        end
        index = index + 1
    end
    return null
end

fn copy_ir_values(destination: pointer<Vector<pointer<IrValue>>>, source: pointer<Vector<pointer<IrValue>>>) -> void
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(source) do
        vector_push<pointer<IrValue>>(destination, vector_get<pointer<IrValue>>(source, index))
        index = index + 1
    end
    return
end

fn copy_ir_fields(destination: pointer<Vector<pointer<IrStructField>>>, source: pointer<Vector<pointer<IrStructField>>>) -> void
    @mut let index: int = 0
    while index < vector_length<pointer<IrStructField>>(source) do
        vector_push<pointer<IrStructField>>(destination, vector_get<pointer<IrStructField>>(source, index))
        index = index + 1
    end
    return
end

fn ir_pointer_in_types(values: pointer<Vector<pointer<IrType>>>, value: pointer<IrType>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrType>>(values) do
        if vector_get<pointer<IrType>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_pointer_in_instructions(values: pointer<Vector<pointer<IrInstruction>>>, value: pointer<IrInstruction>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrInstruction>>(values) do
        if vector_get<pointer<IrInstruction>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_module_contains_function(module: pointer<IrModule>, function: pointer<IrFunction>) -> boolean
    if module == null || function == null then
        return false
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrFunction>>(module->functions) do
        if vector_get<pointer<IrFunction>>(module->functions, index) == function then
            return true
        end
        index = index + 1
    end
    return false
end

fn destroy_ir_type_storage(type: pointer<IrType>) -> void
    if type != null then
        destroy_vector<pointer<IrStructField>>(type->fields)
        memory::free<IrType>(type)
    end
    return
end

fn destroy_ir_reference_storage(reference: pointer<IrFunctionReference>) -> void
    if reference != null then
        destroy_vector<pointer<IrType>>(reference->parameter_types)
        memory::free<IrFunctionReference>(reference)
    end
    return
end

fn destroy_ir_instruction_storage(instruction: pointer<IrInstruction>) -> void
    if instruction != null then
        destroy_vector<pointer<IrValue>>(instruction->operands)
        destroy_vector<pointer<IrStructField>>(instruction->field_path)
        memory::free<IrInstruction>(instruction)
    end
    return
end

fn destroy_ir_block_storage(block: pointer<IrBasicBlock>) -> void
    if block != null then
        destroy_vector<pointer<IrInstruction>>(block->instructions)
        memory::free<IrBasicBlock>(block)
    end
    return
end

fn destroy_ir_function_storage(function: pointer<IrFunction>) -> void
    if function != null then
        destroy_vector<pointer<IrParameter>>(function->parameters)
        destroy_vector<pointer<IrBasicBlock>>(function->blocks)
        memory::free<IrFunction>(function)
    end
    return
end

fn destroy_ir_module_storage(module: pointer<IrModule>) -> void
    if module != null then
        destroy_vector<pointer<IrType>>(module->structs)
        destroy_vector<pointer<IrFunction>>(module->functions)
        memory::free<IrModule>(module)
    end
    return
end
