inject namespace std.memory as memory
inject std.collections.vector
inject ir.model

struct IrFunctionValidation
    function: pointer<IrFunction>
    targets: pointer<Vector<pointer<IrBlockTarget>>>
    instructions: pointer<Vector<pointer<IrInstruction>>>
    locals: pointer<Vector<pointer<IrLocal>>>
    values: pointer<Vector<pointer<IrValue>>>
end

fn seal_ir_function(arena: pointer<IrArena>, function: pointer<IrFunction>) -> boolean
    if arena == null || function == null then
        return ir_error(arena, "cannot seal IR function")
    end
    if function->sealed then
        return ir_error(arena, "cannot seal IR function")
    end

    if !function->has_body then
        if vector_length<pointer<IrBasicBlock>>(function->blocks) != 0 then
            return ir_error(arena, "bodyless IR function must not contain blocks")
        end
        function->sealed = true
        return true
    end

    if vector_length<pointer<IrBasicBlock>>(function->blocks) == 0 then
        return ir_error(arena, "defined IR function must contain a basic block")
    end

    let validation: pointer<IrFunctionValidation> = create_ir_function_validation(function)
    if validation == null then
        return ir_error(arena, "IR validation allocation failed")
    end

    @mut let valid: boolean = collect_ir_function_declarations(arena, validation)
    if valid then
        valid = validate_ir_function_graph(arena, validation)
    end

    destroy_ir_function_validation(validation)
    if valid then
        function->sealed = true
    end
    return valid
end

fn seal_ir_module(arena: pointer<IrArena>, module: pointer<IrModule>) -> boolean
    if arena == null || module == null then
        return ir_error(arena, "cannot seal IR module")
    end
    if module->sealed then
        return ir_error(arena, "cannot seal IR module")
    end

    @mut let index: int = 0
    while index < vector_length<pointer<IrType>>(module->structs) do
        let type: pointer<IrType> = vector_get<pointer<IrType>>(module->structs, index)
        if type == null then
            return ir_error(arena, "IR module contains undefined struct")
        end
        if type->kind != ir_type_struct() || !type->defined then
            return ir_error(arena, "IR module contains undefined struct")
        end
        index = index + 1
    end

    index = 0
    while index < vector_length<pointer<IrFunction>>(module->functions) do
        let function: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, index)
        if function == null then
            return ir_error(arena, "IR module contains unsealed function")
        end
        if !function->sealed then
            return ir_error(arena, "IR module contains unsealed function")
        end
        index = index + 1
    end

    module->sealed = true
    return true
end

fn seal_ir_program(program: pointer<IrProgram>) -> boolean
    if program == null then
        return false
    end
    if program->sealed then
        return false
    end

    let arena: pointer<IrArena> = program->arena
    @mut let module_index: int = 0
    let module_count: int = vector_length<pointer<IrModule>>(program->modules)

    while module_index < module_count do
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(program->modules, module_index)
        if module == null then
            return ir_error(arena, "IR program contains unsealed module")
        end
        if !module->sealed then
            return ir_error(arena, "IR program contains unsealed module")
        end

        @mut let previous_module: int = 0
        while previous_module < module_index do
            if vector_get<pointer<IrModule>>(program->modules, previous_module)->name == module->name then
                return ir_error(arena, "duplicate IR program module name")
            end
            previous_module = previous_module + 1
        end

        @mut let function_index: int = 0
        while function_index < vector_length<pointer<IrFunction>>(module->functions) do
            let function: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, function_index)
            if ir_program_function_id_count(program, function->id) != 1 then
                return ir_error(arena, "duplicate global IR function identifier")
            end
            function_index = function_index + 1
        end
        module_index = module_index + 1
    end

    if program->entry_module != null then
        if program->entry_function == null then
            return ir_error(arena, "IR entry point is not canonical")
        end
        if !ir_program_contains_module(program, program->entry_module) || !ir_module_contains_function(program->entry_module, program->entry_function) then
            return ir_error(arena, "IR entry point is not canonical")
        end
    else
        if program->entry_function != null then
            return ir_error(arena, "IR entry function requires an entry module")
        end
    end

    if !validate_ir_program_calls(program) then
        return false
    end

    program->sealed = true
    return true
end

fn create_ir_function_validation(function: pointer<IrFunction>) -> pointer<IrFunctionValidation>
    let validation: pointer<IrFunctionValidation> = memory::allocate<IrFunctionValidation>(1)
    if validation == null then
        return null
    end
    validation->function = function
    validation->targets = create_vector<pointer<IrBlockTarget>>()
    validation->instructions = create_vector<pointer<IrInstruction>>()
    validation->locals = create_vector<pointer<IrLocal>>()
    validation->values = create_vector<pointer<IrValue>>()
    return validation
end

fn destroy_ir_function_validation(validation: pointer<IrFunctionValidation>) -> void
    if validation == null then
        return
    end
    destroy_vector<pointer<IrBlockTarget>>(validation->targets)
    destroy_vector<pointer<IrInstruction>>(validation->instructions)
    destroy_vector<pointer<IrLocal>>(validation->locals)
    destroy_vector<pointer<IrValue>>(validation->values)
    memory::free<IrFunctionValidation>(validation)
    return
end

fn collect_ir_function_declarations(arena: pointer<IrArena>, validation: pointer<IrFunctionValidation>) -> boolean
    @mut let parameter_index: int = 0
    while parameter_index < vector_length<pointer<IrParameter>>(validation->function->parameters) do
        let parameter: pointer<IrParameter> = vector_get<pointer<IrParameter>>(validation->function->parameters, parameter_index)
        if parameter == null then
            return ir_error(arena, "invalid IR function parameter")
        end
        if parameter->value == null then
            return ir_error(arena, "invalid IR function parameter")
        end
        if parameter->value->kind != ir_value_parameter() || !validate_ir_value_identity(arena, validation, parameter->value) then
            return ir_error(arena, "invalid IR function parameter")
        end
        parameter_index = parameter_index + 1
    end

    @mut let block_index: int = 0
    while block_index < vector_length<pointer<IrBasicBlock>>(validation->function->blocks) do
        let block: pointer<IrBasicBlock> = vector_get<pointer<IrBasicBlock>>(validation->function->blocks, block_index)
        if block == null then
            return ir_error(arena, "IR function contains incomplete block")
        end
        if !block->sealed || block->terminator == null || block->target == null then
            return ir_error(arena, "IR function contains incomplete block")
        end
        if ir_target_or_id_exists(validation->targets, block->target) then
            return ir_error(arena, "duplicate IR function block target")
        end
        vector_push<pointer<IrBlockTarget>>(validation->targets, block->target)

        @mut let instruction_index: int = 0
        while instruction_index < vector_length<pointer<IrInstruction>>(block->instructions) do
            let instruction: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, instruction_index)
            if instruction == null then
                return ir_error(arena, "duplicate IR instruction instance")
            end
            if ir_instruction_exists(validation->instructions, instruction) then
                return ir_error(arena, "duplicate IR instruction instance")
            end
            vector_push<pointer<IrInstruction>>(validation->instructions, instruction)
            if instruction->kind == ir_instruction_local_initialize() then
                if instruction->local == null then
                    return ir_error(arena, "duplicate IR local initialization")
                end
                if ir_local_or_id_exists(validation->locals, instruction->local) then
                    return ir_error(arena, "duplicate IR local initialization")
                end
                vector_push<pointer<IrLocal>>(validation->locals, instruction->local)
            end
            instruction_index = instruction_index + 1
        end
        block_index = block_index + 1
    end
    return true
end

fn validate_ir_function_graph(arena: pointer<IrArena>, validation: pointer<IrFunctionValidation>) -> boolean
    @mut let block_index: int = 0
    while block_index < vector_length<pointer<IrBasicBlock>>(validation->function->blocks) do
        let block: pointer<IrBasicBlock> = vector_get<pointer<IrBasicBlock>>(validation->function->blocks, block_index)
        @mut let instruction_index: int = 0
        while instruction_index < vector_length<pointer<IrInstruction>>(block->instructions) do
            if !validate_ir_instruction_graph(arena, validation, vector_get<pointer<IrInstruction>>(block->instructions, instruction_index)) then
                return false
            end
            instruction_index = instruction_index + 1
        end
        if !validate_ir_terminator_graph(arena, validation, block->terminator) then
            return false
        end
        block_index = block_index + 1
    end
    return true
end

fn validate_ir_instruction_graph(arena: pointer<IrArena>, validation: pointer<IrFunctionValidation>, instruction: pointer<IrInstruction>) -> boolean
    if ir_instruction_uses_local(instruction) && !ir_local_exists(validation->locals, instruction->local) then
        return ir_error(arena, "IR instruction references undeclared local")
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(instruction->operands) do
        if !validate_ir_operand_value(arena, validation, vector_get<pointer<IrValue>>(instruction->operands, index)) then
            return false
        end
        index = index + 1
    end
    if instruction->result != null then
        if instruction->result->producer != instruction || instruction->result->kind != ir_value_instruction() then
            return ir_error(arena, "invalid IR instruction result")
        end
        if !validate_ir_value_identity(arena, validation, instruction->result) then
            return false
        end
    end
    return true
end

fn validate_ir_operand_value(arena: pointer<IrArena>, validation: pointer<IrFunctionValidation>, value: pointer<IrValue>) -> boolean
    if value == null then
        return ir_error(arena, "invalid IR value graph")
    end
    if value->type == null then
        return ir_error(arena, "invalid IR value graph")
    end
    if !value->type->value_type || value->id < 0 then
        return ir_error(arena, "invalid IR value graph")
    end
    if ir_value_exists(validation->values, value) then
        return true
    end
    if value->kind == ir_value_instruction() then
        return ir_error(arena, "IR instruction result is used before it is available")
    end
    if value->kind == ir_value_parameter() then
        return ir_error(arena, "IR value graph references foreign parameter")
    end
    return validate_ir_value_identity(arena, validation, value)
end

fn validate_ir_terminator_graph(arena: pointer<IrArena>, validation: pointer<IrFunctionValidation>, terminator: pointer<IrTerminator>) -> boolean
    if terminator->kind == ir_terminator_return() then
        if validation->function->return_type->value_type then
            if terminator->value == null then
                return ir_error(arena, "IR value-returning function requires exact return type")
            end
            if !ir_type_equals(terminator->value->type, validation->function->return_type) then
                return ir_error(arena, "IR value-returning function requires exact return type")
            end
            return validate_ir_operand_value(arena, validation, terminator->value)
        end
        if terminator->value != null then
            return ir_error(arena, "void IR function requires bare return")
        end
        return true
    end

    if terminator->kind == ir_terminator_branch() then
        if !ir_target_exists(validation->targets, terminator->true_target) then
            return ir_error(arena, "IR branch references foreign target")
        end
        return true
    end

    if terminator->kind == ir_terminator_conditional_branch() then
        if terminator->condition == null then
            return ir_error(arena, "invalid IR conditional branch graph")
        end
        if terminator->condition->type != arena->boolean_type || !ir_target_exists(validation->targets, terminator->true_target) || !ir_target_exists(validation->targets, terminator->false_target) then
            return ir_error(arena, "invalid IR conditional branch graph")
        end
        return validate_ir_operand_value(arena, validation, terminator->condition)
    end

    return ir_error(arena, "unknown IR terminator kind")
end

fn validate_ir_program_calls(program: pointer<IrProgram>) -> boolean
    let references: pointer<Vector<pointer<IrFunctionReference>>> = create_vector<pointer<IrFunctionReference>>()
    @mut let module_index: int = 0
    @mut let valid: boolean = true

    while module_index < vector_length<pointer<IrModule>>(program->modules) && valid do
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(program->modules, module_index)
        @mut let function_index: int = 0
        while function_index < vector_length<pointer<IrFunction>>(module->functions) && valid do
            let function: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, function_index)
            @mut let block_index: int = 0
            while block_index < vector_length<pointer<IrBasicBlock>>(function->blocks) && valid do
                let block: pointer<IrBasicBlock> = vector_get<pointer<IrBasicBlock>>(function->blocks, block_index)
                @mut let instruction_index: int = 0
                while instruction_index < vector_length<pointer<IrInstruction>>(block->instructions) && valid do
                    let instruction: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, instruction_index)
                    if instruction->kind == ir_instruction_value_call() || instruction->kind == ir_instruction_void_call() then
                        valid = validate_ir_program_call(program, references, instruction->target)
                    end
                    instruction_index = instruction_index + 1
                end
                block_index = block_index + 1
            end
            function_index = function_index + 1
        end
        module_index = module_index + 1
    end

    destroy_vector<pointer<IrFunctionReference>>(references)
    return valid
end

fn validate_ir_program_call(program: pointer<IrProgram>, references: pointer<Vector<pointer<IrFunctionReference>>>, reference: pointer<IrFunctionReference>) -> boolean
    if reference == null then
        return ir_error(program->arena, "IR call target must not be null")
    end
    let function: pointer<IrFunction> = ir_program_function(program, reference->id)
    if function == null then
        return ir_error(program->arena, "IR call target does not match canonical function")
    end
    if function->name != reference->name || !ir_type_equals(function->return_type, reference->return_type) || vector_length<pointer<IrParameter>>(function->parameters) != vector_length<pointer<IrType>>(reference->parameter_types) then
        return ir_error(program->arena, "IR call target does not match canonical function")
    end
    @mut let index: int = 0
    while index < vector_length<pointer<IrParameter>>(function->parameters) do
        if !ir_type_equals(vector_get<pointer<IrParameter>>(function->parameters, index)->value->type, vector_get<pointer<IrType>>(reference->parameter_types, index)) then
            return ir_error(program->arena, "IR call target parameter type mismatch")
        end
        index = index + 1
    end
    index = 0
    while index < vector_length<pointer<IrFunctionReference>>(references) do
        let current: pointer<IrFunctionReference> = vector_get<pointer<IrFunctionReference>>(references, index)
        if current->id == reference->id then
            if current != reference then
                return ir_error(program->arena, "IR calls must share canonical function reference")
            end
            return true
        end
        index = index + 1
    end
    vector_push<pointer<IrFunctionReference>>(references, reference)
    return true
end

fn ir_program_function(program: pointer<IrProgram>, id: int) -> pointer<IrFunction>
    @mut let module_index: int = 0
    while module_index < vector_length<pointer<IrModule>>(program->modules) do
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(program->modules, module_index)
        @mut let function_index: int = 0
        while function_index < vector_length<pointer<IrFunction>>(module->functions) do
            let function: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, function_index)
            if function->id == id then
                return function
            end
            function_index = function_index + 1
        end
        module_index = module_index + 1
    end
    return null
end

fn ir_program_function_id_count(program: pointer<IrProgram>, id: int) -> int
    @mut let count: int = 0
    @mut let module_index: int = 0
    while module_index < vector_length<pointer<IrModule>>(program->modules) do
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(program->modules, module_index)
        @mut let function_index: int = 0
        while function_index < vector_length<pointer<IrFunction>>(module->functions) do
            if vector_get<pointer<IrFunction>>(module->functions, function_index)->id == id then
                count = count + 1
            end
            function_index = function_index + 1
        end
        module_index = module_index + 1
    end
    return count
end

fn ir_program_contains_module(program: pointer<IrProgram>, module: pointer<IrModule>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrModule>>(program->modules) do
        if vector_get<pointer<IrModule>>(program->modules, index) == module then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_instruction_uses_local(instruction: pointer<IrInstruction>) -> boolean
    return instruction->kind == ir_instruction_local_initialize() || instruction->kind == ir_instruction_local_load() || instruction->kind == ir_instruction_local_store() || instruction->kind == ir_instruction_struct_field_store()
end

fn ir_target_or_id_exists(values: pointer<Vector<pointer<IrBlockTarget>>>, value: pointer<IrBlockTarget>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrBlockTarget>>(values) do
        let current: pointer<IrBlockTarget> = vector_get<pointer<IrBlockTarget>>(values, index)
        if current == value || current->id == value->id then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_target_exists(values: pointer<Vector<pointer<IrBlockTarget>>>, value: pointer<IrBlockTarget>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrBlockTarget>>(values) do
        if vector_get<pointer<IrBlockTarget>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_instruction_exists(values: pointer<Vector<pointer<IrInstruction>>>, value: pointer<IrInstruction>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrInstruction>>(values) do
        if vector_get<pointer<IrInstruction>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_local_or_id_exists(values: pointer<Vector<pointer<IrLocal>>>, value: pointer<IrLocal>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrLocal>>(values) do
        let current: pointer<IrLocal> = vector_get<pointer<IrLocal>>(values, index)
        if current == value || current->id == value->id then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_local_exists(values: pointer<Vector<pointer<IrLocal>>>, value: pointer<IrLocal>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrLocal>>(values) do
        if vector_get<pointer<IrLocal>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_value_exists(values: pointer<Vector<pointer<IrValue>>>, value: pointer<IrValue>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(values) do
        if vector_get<pointer<IrValue>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_value_id_exists(values: pointer<Vector<pointer<IrValue>>>, id: int) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(values) do
        if vector_get<pointer<IrValue>>(values, index)->id == id then
            return true
        end
        index = index + 1
    end
    return false
end

fn ir_parameter_value_exists(parameters: pointer<Vector<pointer<IrParameter>>>, value: pointer<IrValue>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrParameter>>(parameters) do
        if vector_get<pointer<IrParameter>>(parameters, index)->value == value then
            return true
        end
        index = index + 1
    end
    return false
end

fn validate_ir_value_identity(arena: pointer<IrArena>, validation: pointer<IrFunctionValidation>, value: pointer<IrValue>) -> boolean
    if ir_value_id_exists(validation->values, value->id) then
        return ir_error(arena, "duplicate IR value identifier")
    end
    vector_push<pointer<IrValue>>(validation->values, value)
    return true
end
