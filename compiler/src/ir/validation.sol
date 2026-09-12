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
    end

    if function->has_body && vector_length<pointer<IrBasicBlock>>(function->blocks) == 0 then
        return ir_error(arena, "defined IR function must contain a basic block")
    end
    if function->kind != ir_function_kind_function() then
        if !validate_ir_callable_owner(arena, function->kind, function->owner, function->dispatch, function->overridden) then
            return false
        end
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
    while index < vector_length<pointer<IrType>>(module->objects) do
        let type: pointer<IrType> = vector_get<pointer<IrType>>(module->objects, index)
        @mut let invalid: boolean = type == null
        if !invalid then
            invalid = !type->defined
        end
        if !invalid then
            invalid = (type->kind != ir_type_class() && type->kind != ir_type_interface())
        end
        if invalid then
            return ir_error(arena, "IR module contains undefined object type")
        end
        @mut let previous: int = 0
        while previous < index do
            if vector_get<pointer<IrType>>(module->objects, previous)->name == type->name then
                return ir_error(arena, "duplicate IR module object type")
            end
            previous = previous + 1
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
    if validation->function->kind != ir_function_kind_function() then
        @mut let invalid: boolean = validation->function->receiver == null
        if !invalid then
            invalid = validation->function->owner == null
        end
        if !invalid then
            invalid = validation->function->receiver->value == null
        end
        if !invalid then
            invalid = validation->function->receiver->value->kind != ir_value_parameter()
        end
        if invalid then
            return ir_error(arena, "invalid IR callable receiver")
        end
        if !validate_ir_value_identity(arena, validation, validation->function->receiver->value) then
            return false
        end
    else
        @mut let invalid_2: boolean = validation->function->receiver != null
        if !invalid_2 then
            invalid_2 = validation->function->owner != null
        end
        if invalid_2 then
            return ir_error(arena, "ordinary IR function cannot have a receiver")
        end
    end
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
            if instruction->kind == ir_instruction_local_initialize() || instruction->kind == ir_instruction_object_initialize() then
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
    if !validate_ir_object_instruction(arena, instruction) then
        return false
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

fn validate_ir_object_result(arena: pointer<IrArena>, instruction: pointer<IrInstruction>, expected: pointer<IrType>) -> boolean
    if expected == null then
        if instruction->result != null then
            return ir_error(arena, "side-effecting IR object instruction has a result")
        end
        return true
    end
    if instruction->result == null then
        return ir_error(arena, "IR object instruction requires a result")
    end
    if !ir_type_equals(instruction->result->type, expected) then
        return ir_error(arena, "IR object instruction result type mismatch")
    end
    return true
end

fn validate_ir_object_instruction(arena: pointer<IrArena>, instruction: pointer<IrInstruction>) -> boolean
    let kind: int = instruction->kind
    if kind < ir_instruction_object_initialize() then
        return true
    end
    let count: int = vector_length<pointer<IrValue>>(instruction->operands)
    if kind == ir_instruction_object_address() then
        if instruction->local == null || count != 0 then
            return ir_error(arena, "invalid IR object address")
        end
        if instruction->local->type->kind != ir_type_class() then
            return ir_error(arena, "IR object address requires class storage")
        end
        return validate_ir_object_result(arena, instruction, create_ir_pointer_type(arena, instruction->local->type))
    end
    if kind == ir_instruction_object_field_load() || kind == ir_instruction_object_field_store() || kind == ir_instruction_object_field_address() then
        @mut let expected_count: int = 1
        if kind == ir_instruction_object_field_store() then
            expected_count = 2
        end
        if count != expected_count then
            return ir_error(arena, "IR object field operand count mismatch")
        end
        if !validate_ir_object_field(arena, vector_get<pointer<IrValue>>(instruction->operands, 0), instruction->object_field) then
            return false
        end
        let type: pointer<IrType> = instruction->object_field->type
        if kind == ir_instruction_object_field_address() then
            if type->kind != ir_type_class() then
                return ir_error(arena, "IR object field address requires class storage")
            end
            return validate_ir_object_result(arena, instruction, create_ir_pointer_type(arena, type))
        end
        if !type->value_type then
            return ir_error(arena, "IR object field cannot be copied")
        end
        if kind == ir_instruction_object_field_store() then
            if !ir_type_equals(type, vector_get<pointer<IrValue>>(instruction->operands, 1)->type) then
                return ir_error(arena, "IR object field store type mismatch")
            end
            return validate_ir_object_result(arena, instruction, null)
        end
        return validate_ir_object_result(arena, instruction, type)
    end
    if kind == ir_instruction_object_delete() || kind == ir_instruction_object_view() then
        if count != 1 then
            return ir_error(arena, "IR object view/delete requires one operand")
        end
        let value: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
        if value->type->kind != ir_type_pointer() then
            return ir_error(arena, "IR object view/delete requires a pointer")
        end
        if kind == ir_instruction_object_delete() then
            if value->type->element_type->kind != ir_type_class() || instruction->object_type != value->type->element_type then
                return ir_error(arena, "IR object deletion type mismatch")
            end
            return validate_ir_object_result(arena, instruction, null)
        end
        if instruction->result == null then
            return ir_error(arena, "IR object view requires a result")
        end
        let result_type: pointer<IrType> = instruction->result->type
        if result_type == null then
            return ir_error(arena, "IR object view result must have a type")
        end
        if result_type->kind != ir_type_pointer() then
            return ir_error(arena, "IR object view result must be a pointer")
        end
        if !ir_object_is_subtype(value->type->element_type, result_type->element_type) then
            return ir_error(arena, "IR object view is not an upcast")
        end
        return true
    end
    let target: pointer<IrFunctionReference> = instruction->target
    if target == null then
        return ir_error(arena, "IR object call requires a canonical target")
    end
    @mut let offset: int = 0
    if kind == ir_instruction_method_call() || kind == ir_instruction_void_method_call() || kind == ir_instruction_constructor_call() || kind == ir_instruction_object_field_construct() then
        offset = 1
    end
    if count < offset then
        return ir_error(arena, "IR object call requires a receiver")
    end
    let arguments: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    @mut let index: int = offset
    while index < count do
        vector_push<pointer<IrValue>>(arguments, vector_get<pointer<IrValue>>(instruction->operands, index))
        index = index + 1
    end
    @mut let valid: boolean = validate_ir_call_arguments(arena, target, arguments)
    if valid && (kind == ir_instruction_method_call() || kind == ir_instruction_void_method_call() || kind == ir_instruction_constructor_call()) then
        @mut let dispatch: int = instruction->dispatch
        if kind == ir_instruction_constructor_call() then
            dispatch = ir_dispatch_direct()
        end
        valid = validate_ir_method_call(arena, target, vector_get<pointer<IrValue>>(instruction->operands, 0), arguments, dispatch)
    end
    destroy_vector<pointer<IrValue>>(arguments)
    if !valid then
        return false
    end
    if kind == ir_instruction_method_call() || kind == ir_instruction_void_method_call() then
        if target->kind != ir_function_kind_method() then
            return ir_error(arena, "IR method call targets a non-method")
        end
        if kind == ir_instruction_method_call() then
            return validate_ir_object_result(arena, instruction, target->return_type)
        end
        if target->return_type != arena->void_type then
            return ir_error(arena, "void IR method call must return void")
        end
        return validate_ir_object_result(arena, instruction, null)
    end
    if target->kind != ir_function_kind_constructor() then
        return ir_error(arena, "IR construction targets a non-constructor")
    end
    if kind == ir_instruction_constructor_call() then
        return validate_ir_object_result(arena, instruction, null)
    end
    @mut let destination: pointer<IrType> = null
    if kind == ir_instruction_object_field_construct() then
        if !validate_ir_object_field(arena, vector_get<pointer<IrValue>>(instruction->operands, 0), instruction->object_field) then
            return false
        end
        destination = instruction->object_field->type
    else
        if kind == ir_instruction_object_new() then
            destination = instruction->object_type
        else
            if kind != ir_instruction_object_initialize() && kind != ir_instruction_object_reconstruct() then
                return ir_error(arena, "unknown IR object instruction")
            end
            if instruction->local == null then
                return ir_error(arena, "IR object construction requires local storage")
            end
            if kind == ir_instruction_object_reconstruct() && instruction->local->kind != ir_local_mutable() then
                return ir_error(arena, "IR object reconstruction requires mutable storage")
            end
            destination = instruction->local->type
        end
    end
    if destination == null then
        return ir_error(arena, "IR construction has no destination type")
    end
    if destination != target->owner || destination->kind != ir_type_class() || destination->abstract_type || !destination->defined then
        return ir_error(arena, "IR construction requires the exact concrete class")
    end
    if kind == ir_instruction_object_new() then
        return validate_ir_object_result(arena, instruction, create_ir_pointer_type(arena, destination))
    end
    return validate_ir_object_result(arena, instruction, null)
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
        @mut let object_index: int = 0
        while object_index < vector_length<pointer<IrType>>(module->objects) && valid do
            let object: pointer<IrType> = vector_get<pointer<IrType>>(module->objects, object_index)
            @mut let requirement_index: int = 0
            while requirement_index < vector_length<pointer<IrRequirementImplementation>>(object->requirements) && valid do
                let mapping: pointer<IrRequirementImplementation> = vector_get<pointer<IrRequirementImplementation>>(object->requirements, requirement_index)
                valid = validate_ir_program_call(program, references, mapping->requirement)
                @mut let matches: boolean = valid
                if matches then
                    matches = mapping->implementation != null
                end
                if matches then
                    valid = validate_ir_program_call(program, references, mapping->implementation)
                end
                requirement_index = requirement_index + 1
            end
            object_index = object_index + 1
        end
        @mut let function_index: int = 0
        while function_index < vector_length<pointer<IrFunction>>(module->functions) && valid do
            let function: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, function_index)
            @mut let block_index: int = 0
            while block_index < vector_length<pointer<IrBasicBlock>>(function->blocks) && valid do
                let block: pointer<IrBasicBlock> = vector_get<pointer<IrBasicBlock>>(function->blocks, block_index)
                @mut let instruction_index: int = 0
                while instruction_index < vector_length<pointer<IrInstruction>>(block->instructions) && valid do
                    let instruction: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, instruction_index)
                    if instruction->kind == ir_instruction_value_call() || instruction->kind == ir_instruction_void_call() || instruction->kind == ir_instruction_method_call() || instruction->kind == ir_instruction_void_method_call() || instruction->kind == ir_instruction_constructor_call() || instruction->kind == ir_instruction_object_initialize() || instruction->kind == ir_instruction_object_reconstruct() || instruction->kind == ir_instruction_object_field_construct() || instruction->kind == ir_instruction_object_new() then
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
    if function->name != reference->name || function->kind != reference->kind || function->owner != reference->owner || function->dispatch != reference->dispatch || function->overridden != reference->overridden || !ir_type_equals(function->return_type, reference->return_type) || vector_length<pointer<IrParameter>>(function->parameters) != vector_length<pointer<IrType>>(reference->parameter_types) then
        return ir_error(program->arena, "IR call target does not match canonical function")
    end
    if function->kind != ir_function_kind_function() then
        @mut let invalid: boolean = function->receiver == null
        if !invalid then
            invalid = !ir_type_equals(function->receiver->value->type, reference->receiver_type)
        end
        if invalid then
            return ir_error(program->arena, "IR call target receiver type mismatch")
        end
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
    return instruction->kind == ir_instruction_local_initialize() || instruction->kind == ir_instruction_local_load() || instruction->kind == ir_instruction_local_store() || instruction->kind == ir_instruction_struct_field_store() || instruction->kind == ir_instruction_object_initialize() || instruction->kind == ir_instruction_object_reconstruct() || instruction->kind == ir_instruction_object_address()
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
