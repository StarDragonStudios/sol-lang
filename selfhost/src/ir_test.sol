inject namespace std.console as console
inject std.collections.vector
inject ir.model
inject ir.validation
inject ir.formatter

@init
fn launch() -> int
    let types: int = test_ir_types_and_identifiers()
    if types != 0 then
        console::print_line("self-host IR test failed: types and identifiers")
        return 10 + types
    end

    let operations: int = test_ir_values_and_operations()
    if operations != 0 then
        console::print_line("self-host IR test failed: values and operations")
        return 30 + operations
    end

    let program: int = test_ir_program_and_formatter()
    if program != 0 then
        console::print_line("self-host IR test failed: program and formatter")
        return 50 + program
    end

    let invalid: int = test_ir_rejection_paths()
    if invalid != 0 then
        console::print_line("self-host IR test failed: rejection paths")
        return 70 + invalid
    end

    let returns: int = test_ir_return_rejection()
    if returns != 0 then
        console::print_line("self-host IR test failed: return rejection")
        return 90 + returns
    end

    let graph: int = test_ir_graph_rejection()
    if graph != 0 then
        console::print_line("self-host IR test failed: graph rejection")
        return 100 + graph
    end

    return 0
end

fn test_ir_types_and_identifiers() -> int
    let arena: pointer<IrArena> = create_ir_arena()
    if arena == null then
        return 1
    end
    @mut let failure: int = 0

    if vector_length<pointer<IrType>>(arena->types) != 6 || !arena->integer_type->numeric || !arena->integer_type->integral || !arena->float_type->numeric || arena->float_type->integral || arena->void_type->value_type then
        failure = 2
    end

    let node: pointer<IrType> = create_ir_struct_type(arena, "tree.Node<int>")
    let node_pointer: pointer<IrType> = create_ir_pointer_type(arena, node)
    let same_pointer: pointer<IrType> = create_ir_pointer_type(arena, node)
    let fields: pointer<Vector<pointer<IrStructField>>> = create_vector<pointer<IrStructField>>()
    let value_field: pointer<IrStructField> = create_ir_struct_field(arena, 0, "value", arena->integer_type)
    let next_field: pointer<IrStructField> = create_ir_struct_field(arena, 1, "next", node_pointer)
    vector_push<pointer<IrStructField>>(fields, value_field)
    vector_push<pointer<IrStructField>>(fields, next_field)

    if failure == 0 && (node == null || node_pointer == null || node_pointer != same_pointer || node->defined || !define_ir_struct_type(arena, node, fields)) then
        failure = 3
    end
    if failure == 0 && (!node->defined || node->name != "tree.Node<int>" || vector_length<pointer<IrStructField>>(node->fields) != 2 || ir_struct_field(node, "next") != next_field || ir_type_equals(node, create_ir_struct_type(arena, "tree.Node<int>"))) then
        failure = 4
    end

    let block_zero: pointer<IrBlockTarget> = create_ir_block_target(arena, 0)
    let local_zero: pointer<IrLocal> = create_ir_local(arena, 0, "item", node, ir_local_mutable())
    if failure == 0 && (block_zero == null || block_zero->id != 0 || local_zero == null || local_zero->id != 0 || local_zero->kind != ir_local_mutable()) then
        failure = 5
    end

    ir_clear_error(arena)
    if failure == 0 && (create_ir_block_target(arena, -1) != null || !ir_has_error(arena)) then
        failure = 6
    end

    destroy_vector<pointer<IrStructField>>(fields)
    destroy_ir_arena(arena)
    return failure
end

fn test_ir_values_and_operations() -> int
    let arena: pointer<IrArena> = create_ir_arena()
    if arena == null then
        return 1
    end
    @mut let failure: int = 0

    let integer: pointer<IrValue> = create_ir_int_constant(arena, 0, 42)
    let floating: pointer<IrValue> = create_ir_float_constant(arena, 1, 2.5, "2.5")
    let truth: pointer<IrValue> = create_ir_boolean_constant(arena, 2, true)
    let character: pointer<IrValue> = create_ir_char_constant(arena, 3, 'λ', "'λ'")
    let string_value: pointer<IrValue> = create_ir_string_constant(arena, 4, "Sol\nλ")
    let int_pointer: pointer<IrType> = create_ir_pointer_type(arena, arena->integer_type)
    let null_value: pointer<IrValue> = create_ir_null_constant(arena, 5, int_pointer)

    if integer == null || floating == null || truth == null || character == null || string_value == null || null_value == null then
        failure = 2
    end

    let other: pointer<IrValue> = create_ir_int_constant(arena, 6, 8)
    let sum: pointer<IrInstruction> = create_ir_binary_instruction(arena, 7, ir_binary_add(), integer, other)
    let negative: pointer<IrInstruction> = create_ir_unary_instruction(arena, 8, ir_unary_negate(), integer)
    let not_truth: pointer<IrInstruction> = create_ir_unary_instruction(arena, 9, ir_unary_logical_not(), truth)
    let index: pointer<IrValue> = create_ir_int_constant(arena, 10, 1)
    let indexed: pointer<IrInstruction> = create_ir_string_index(arena, 11, string_value, index)

    if failure == 0 && (sum == null || sum->result->type != arena->integer_type || negative == null || not_truth->result->type != arena->boolean_type || indexed->result->type != arena->char_type) then
        failure = 3
    end

    let pair: pointer<IrType> = create_ir_struct_type(arena, "Pair<int, string>")
    let pair_fields: pointer<Vector<pointer<IrStructField>>> = create_vector<pointer<IrStructField>>()
    let first: pointer<IrStructField> = create_ir_struct_field(arena, 0, "first", arena->integer_type)
    let second: pointer<IrStructField> = create_ir_struct_field(arena, 1, "second", arena->string_type)
    vector_push<pointer<IrStructField>>(pair_fields, first)
    vector_push<pointer<IrStructField>>(pair_fields, second)
    define_ir_struct_type(arena, pair, pair_fields)
    let values: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(values, integer)
    vector_push<pointer<IrValue>>(values, string_value)
    let construction: pointer<IrInstruction> = create_ir_struct_construct(arena, 12, pair, values)
    let extraction: pointer<IrInstruction> = create_ir_struct_extract(arena, 13, construction->result, second)
    let pair_pointer: pointer<IrType> = create_ir_pointer_type(arena, pair)
    let pointer_parameter: pointer<IrParameter> = create_ir_parameter(arena, 14, "pair", pair_pointer)
    let field_load: pointer<IrInstruction> = create_ir_pointer_field_load(arena, 15, pointer_parameter->value, first)
    let field_store: pointer<IrInstruction> = create_ir_pointer_field_store(arena, pointer_parameter->value, first, integer)
    let pair_local: pointer<IrLocal> = create_ir_local(arena, 0, "pair", pair, ir_local_mutable())
    let path: pointer<Vector<pointer<IrStructField>>> = create_vector<pointer<IrStructField>>()
    vector_push<pointer<IrStructField>>(path, second)
    let local_field_store: pointer<IrInstruction> = create_ir_struct_field_store(arena, pair_local, path, string_value)

    if failure == 0 && (construction == null || extraction->result->type != arena->string_type || field_load->result->type != arena->integer_type || field_store == null || local_field_store == null) then
        failure = 4
    end

    let raw_pointer: pointer<IrParameter> = create_ir_parameter(arena, 16, "data", int_pointer)
    let pointer_load: pointer<IrInstruction> = create_ir_pointer_load(arena, 17, raw_pointer->value)
    let pointer_store: pointer<IrInstruction> = create_ir_pointer_store(arena, raw_pointer->value, integer)
    let pointer_index_load: pointer<IrInstruction> = create_ir_pointer_index_load(arena, 18, raw_pointer->value, index)
    let pointer_index_store: pointer<IrInstruction> = create_ir_pointer_index_store(arena, raw_pointer->value, index, integer)
    let call_types: pointer<Vector<pointer<IrType>>> = create_vector<pointer<IrType>>()
    vector_push<pointer<IrType>>(call_types, arena->integer_type)
    let identity: pointer<IrFunctionReference> = create_ir_function_reference(arena, 0, "identity", call_types, arena->integer_type)
    let call_values: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(call_values, integer)
    let value_call: pointer<IrInstruction> = create_ir_value_call_instruction(arena, 19, identity, call_values)
    if failure == 0 && (pointer_load == null || pointer_store == null || pointer_index_load == null || pointer_index_store == null || value_call == null || value_call->result->type != arena->integer_type) then
        failure = 5
    end

    ir_clear_error(arena)
    if failure == 0 && (create_ir_binary_instruction(arena, 20, ir_binary_add(), integer, truth) != null || !ir_has_error(arena)) then
        failure = 6
    end
    ir_clear_error(arena)
    if failure == 0 && create_ir_pointer_store(arena, null_value, string_value) != null then
        failure = 7
    end

    destroy_vector<pointer<IrValue>>(call_values)
    destroy_vector<pointer<IrType>>(call_types)
    destroy_vector<pointer<IrStructField>>(path)
    destroy_vector<pointer<IrValue>>(values)
    destroy_vector<pointer<IrStructField>>(pair_fields)
    destroy_ir_arena(arena)
    return failure
end

fn test_ir_program_and_formatter() -> int
    let program: pointer<IrProgram> = create_valid_ir_program()
    if program == null then
        return 1
    end
    @mut let failure: int = 0

    if !program->sealed || vector_length<pointer<IrModule>>(program->modules) != 1 || program->entry_function == null || program->entry_function->name != "launch" then
        failure = 2
    end

    let actual: string = format_ir_program(program)
    let expected: string = "program {\n  entry @application::function1\n\n  module @application {\n    struct application.Marker {\n    }\n\n    declare @function0 output(%0 value: int) -> void\n\n    define @function1 launch() -> int {\n      block0:\n        %0: int = const 40\n        initialize local0 mut answer: int, %0\n        %1: int = load local0\n        call @function0 output(%1)\n        %2: int = const 2\n        %3: int = add %1, %2\n        return %3\n    }\n\n    define @function2 spin(%0 running: boolean) -> void {\n      block0:\n        branch_if %0, block1, block2\n\n      block1:\n        branch block0\n\n      block2:\n        return\n    }\n  }\n}\n"
    if failure == 0 && actual != expected then
        console::print_line(actual)
        failure = 3
    end

    if failure == 0 && (format_ir_int(-9223372036854775807 - 1) != "-9223372036854775808" || quote_ir_string("a\n\"b") != "\"a\\n\\\"b\"") then
        failure = 4
    end

    destroy_ir_program(program)
    return failure
end

fn create_valid_ir_program() -> pointer<IrProgram>
    let arena: pointer<IrArena> = create_ir_arena()
    if arena == null then
        return null
    end

    let output: pointer<IrFunction> = create_ir_function(arena, 0, "output", arena->void_type, false)
    let output_parameter: pointer<IrParameter> = create_ir_parameter(arena, 0, "value", arena->integer_type)
    ir_function_add_parameter(arena, output, output_parameter)
    if !seal_ir_function(arena, output) then
        destroy_ir_arena(arena)
        return null
    end

    let launch_function: pointer<IrFunction> = create_ir_function(arena, 1, "launch", arena->integer_type, true)
    let launch_target: pointer<IrBlockTarget> = create_ir_block_target(arena, 0)
    let launch_block: pointer<IrBasicBlock> = create_ir_basic_block(arena, launch_target)
    let forty: pointer<IrValue> = create_ir_int_constant(arena, 0, 40)
    let answer: pointer<IrLocal> = create_ir_local(arena, 0, "answer", arena->integer_type, ir_local_mutable())
    let initialize: pointer<IrInstruction> = create_ir_local_initialize(arena, answer, forty)
    let load: pointer<IrInstruction> = create_ir_local_load(arena, 1, answer)
    let parameter_types: pointer<Vector<pointer<IrType>>> = create_vector<pointer<IrType>>()
    vector_push<pointer<IrType>>(parameter_types, arena->integer_type)
    let output_reference: pointer<IrFunctionReference> = create_ir_function_reference(arena, 0, "output", parameter_types, arena->void_type)
    let call_arguments: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    vector_push<pointer<IrValue>>(call_arguments, load->result)
    let call: pointer<IrInstruction> = create_ir_void_call_instruction(arena, output_reference, call_arguments)
    let two: pointer<IrValue> = create_ir_int_constant(arena, 2, 2)
    let sum: pointer<IrInstruction> = create_ir_binary_instruction(arena, 3, ir_binary_add(), load->result, two)
    ir_block_add_instruction(arena, launch_block, initialize)
    ir_block_add_instruction(arena, launch_block, load)
    ir_block_add_instruction(arena, launch_block, call)
    ir_block_add_instruction(arena, launch_block, sum)
    ir_block_terminate(arena, launch_block, create_ir_return(arena, sum->result))
    ir_function_add_block(arena, launch_function, launch_block)
    destroy_vector<pointer<IrValue>>(call_arguments)
    destroy_vector<pointer<IrType>>(parameter_types)
    if !seal_ir_function(arena, launch_function) then
        destroy_ir_arena(arena)
        return null
    end

    let spin: pointer<IrFunction> = create_ir_function(arena, 2, "spin", arena->void_type, true)
    let running: pointer<IrParameter> = create_ir_parameter(arena, 0, "running", arena->boolean_type)
    ir_function_add_parameter(arena, spin, running)
    let condition_target: pointer<IrBlockTarget> = create_ir_block_target(arena, 0)
    let body_target: pointer<IrBlockTarget> = create_ir_block_target(arena, 1)
    let exit_target: pointer<IrBlockTarget> = create_ir_block_target(arena, 2)
    let condition_block: pointer<IrBasicBlock> = create_ir_basic_block(arena, condition_target)
    let body_block: pointer<IrBasicBlock> = create_ir_basic_block(arena, body_target)
    let exit_block: pointer<IrBasicBlock> = create_ir_basic_block(arena, exit_target)
    ir_block_terminate(arena, condition_block, create_ir_conditional_branch(arena, running->value, body_target, exit_target))
    ir_block_terminate(arena, body_block, create_ir_branch(arena, condition_target))
    ir_block_terminate(arena, exit_block, create_ir_return(arena, null))
    ir_function_add_block(arena, spin, condition_block)
    ir_function_add_block(arena, spin, body_block)
    ir_function_add_block(arena, spin, exit_block)
    if !seal_ir_function(arena, spin) then
        destroy_ir_arena(arena)
        return null
    end

    let module: pointer<IrModule> = create_ir_module(arena, "application")
    let marker: pointer<IrType> = create_ir_struct_type(arena, "application.Marker")
    let marker_fields: pointer<Vector<pointer<IrStructField>>> = create_vector<pointer<IrStructField>>()
    define_ir_struct_type(arena, marker, marker_fields)
    ir_module_add_struct(arena, module, marker)
    destroy_vector<pointer<IrStructField>>(marker_fields)
    ir_module_add_function(arena, module, output)
    ir_module_add_function(arena, module, launch_function)
    ir_module_add_function(arena, module, spin)
    if !seal_ir_module(arena, module) then
        destroy_ir_arena(arena)
        return null
    end

    let program: pointer<IrProgram> = create_ir_program(arena)
    ir_program_add_module(program, module)
    ir_program_set_entry(program, module, launch_function)
    if !seal_ir_program(program) then
        destroy_ir_program(program)
        return null
    end
    return program
end

fn test_ir_rejection_paths() -> int
    let constructor_arena: pointer<IrArena> = create_ir_arena()
    if create_ir_pointer_type(constructor_arena, null) != null || format_ir_program(null) != "<invalid IR program>\n" then
        destroy_ir_arena(constructor_arena)
        return 1
    end
    ir_clear_error(constructor_arena)
    let immutable: pointer<IrLocal> = create_ir_local(constructor_arena, 0, "fixed", constructor_arena->integer_type, ir_local_immutable())
    let value: pointer<IrValue> = create_ir_int_constant(constructor_arena, 0, 1)
    if create_ir_local_store(constructor_arena, immutable, value) != null then
        destroy_ir_arena(constructor_arena)
        return 2
    end
    destroy_ir_arena(constructor_arena)

    let target_arena: pointer<IrArena> = create_ir_arena()
    let target_function: pointer<IrFunction> = create_ir_function(target_arena, 0, "foreign_target", target_arena->void_type, true)
    let declared: pointer<IrBlockTarget> = create_ir_block_target(target_arena, 0)
    let foreign: pointer<IrBlockTarget> = create_ir_block_target(target_arena, 0)
    let target_block: pointer<IrBasicBlock> = create_ir_basic_block(target_arena, declared)
    ir_block_terminate(target_arena, target_block, create_ir_branch(target_arena, foreign))
    ir_function_add_block(target_arena, target_function, target_block)
    if seal_ir_function(target_arena, target_function) || target_arena->error != "IR branch references foreign target" then
        destroy_ir_arena(target_arena)
        return 3
    end
    destroy_ir_arena(target_arena)

    let value_arena: pointer<IrArena> = create_ir_arena()
    let value_function: pointer<IrFunction> = create_ir_function(value_arena, 0, "duplicate_values", value_arena->integer_type, true)
    let value_target: pointer<IrBlockTarget> = create_ir_block_target(value_arena, 0)
    let value_block: pointer<IrBasicBlock> = create_ir_basic_block(value_arena, value_target)
    let left: pointer<IrValue> = create_ir_int_constant(value_arena, 0, 1)
    let right: pointer<IrValue> = create_ir_int_constant(value_arena, 0, 2)
    let sum: pointer<IrInstruction> = create_ir_binary_instruction(value_arena, 1, ir_binary_add(), left, right)
    ir_block_add_instruction(value_arena, value_block, sum)
    ir_block_terminate(value_arena, value_block, create_ir_return(value_arena, sum->result))
    ir_function_add_block(value_arena, value_function, value_block)
    if seal_ir_function(value_arena, value_function) || value_arena->error != "duplicate IR value identifier" then
        destroy_ir_arena(value_arena)
        return 4
    end
    destroy_ir_arena(value_arena)
    return 0

end

fn test_ir_return_rejection() -> int
    let return_arena: pointer<IrArena> = create_ir_arena()
    let return_function: pointer<IrFunction> = create_ir_function(return_arena, 0, "wrong_return", return_arena->integer_type, true)
    let return_target: pointer<IrBlockTarget> = create_ir_block_target(return_arena, 0)
    let return_block: pointer<IrBasicBlock> = create_ir_basic_block(return_arena, return_target)
    ir_block_terminate(return_arena, return_block, create_ir_return(return_arena, null))
    ir_function_add_block(return_arena, return_function, return_block)
    if seal_ir_function(return_arena, return_function) || return_arena->error != "IR value-returning function requires exact return type" then
        destroy_ir_arena(return_arena)
        return 1
    end
    destroy_ir_arena(return_arena)

    return 0
end

fn test_ir_graph_rejection() -> int
    let order_arena: pointer<IrArena> = create_ir_arena()
    let order_function: pointer<IrFunction> = create_ir_function(order_arena, 0, "use_before_definition", order_arena->integer_type, true)
    let order_target: pointer<IrBlockTarget> = create_ir_block_target(order_arena, 0)
    let order_block: pointer<IrBasicBlock> = create_ir_basic_block(order_arena, order_target)
    let local: pointer<IrLocal> = create_ir_local(order_arena, 0, "value", order_arena->integer_type, ir_local_mutable())
    let one: pointer<IrValue> = create_ir_int_constant(order_arena, 0, 1)
    let initialize: pointer<IrInstruction> = create_ir_local_initialize(order_arena, local, one)
    let later_load: pointer<IrInstruction> = create_ir_local_load(order_arena, 1, local)
    let early_sum: pointer<IrInstruction> = create_ir_binary_instruction(order_arena, 2, ir_binary_add(), later_load->result, one)
    ir_block_add_instruction(order_arena, order_block, initialize)
    ir_block_add_instruction(order_arena, order_block, early_sum)
    ir_block_add_instruction(order_arena, order_block, later_load)
    ir_block_terminate(order_arena, order_block, create_ir_return(order_arena, early_sum->result))
    ir_function_add_block(order_arena, order_function, order_block)
    if seal_ir_function(order_arena, order_function) || order_arena->error != "IR instruction result is used before it is available" then
        destroy_ir_arena(order_arena)
        return 1
    end
    destroy_ir_arena(order_arena)

    let call_arena: pointer<IrArena> = create_ir_arena()
    let caller: pointer<IrFunction> = create_ir_function(call_arena, 0, "caller", call_arena->void_type, true)
    let call_target: pointer<IrBlockTarget> = create_ir_block_target(call_arena, 0)
    let call_block: pointer<IrBasicBlock> = create_ir_basic_block(call_arena, call_target)
    let no_types: pointer<Vector<pointer<IrType>>> = create_vector<pointer<IrType>>()
    let missing: pointer<IrFunctionReference> = create_ir_function_reference(call_arena, 99, "missing", no_types, call_arena->void_type)
    let no_arguments: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    let missing_call: pointer<IrInstruction> = create_ir_void_call_instruction(call_arena, missing, no_arguments)
    ir_block_add_instruction(call_arena, call_block, missing_call)
    ir_block_terminate(call_arena, call_block, create_ir_return(call_arena, null))
    ir_function_add_block(call_arena, caller, call_block)
    seal_ir_function(call_arena, caller)
    let module: pointer<IrModule> = create_ir_module(call_arena, "calls")
    ir_module_add_function(call_arena, module, caller)
    seal_ir_module(call_arena, module)
    let program: pointer<IrProgram> = create_ir_program(call_arena)
    ir_program_add_module(program, module)
    ir_clear_error(call_arena)
    if seal_ir_program(program) || call_arena->error != "IR call target does not match canonical function" then
        destroy_vector<pointer<IrValue>>(no_arguments)
        destroy_vector<pointer<IrType>>(no_types)
        destroy_ir_program(program)
        return 2
    end
    destroy_vector<pointer<IrValue>>(no_arguments)
    destroy_vector<pointer<IrType>>(no_types)
    destroy_ir_program(program)

    let reference_arena: pointer<IrArena> = create_ir_arena()
    let callee: pointer<IrFunction> = create_ir_function(reference_arena, 0, "callee", reference_arena->void_type, false)
    seal_ir_function(reference_arena, callee)
    let reference_caller: pointer<IrFunction> = create_ir_function(reference_arena, 1, "caller", reference_arena->void_type, true)
    let reference_target: pointer<IrBlockTarget> = create_ir_block_target(reference_arena, 0)
    let reference_block: pointer<IrBasicBlock> = create_ir_basic_block(reference_arena, reference_target)
    let reference_types: pointer<Vector<pointer<IrType>>> = create_vector<pointer<IrType>>()
    let reference_arguments: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    let first_reference: pointer<IrFunctionReference> = create_ir_function_reference(reference_arena, 0, "callee", reference_types, reference_arena->void_type)
    let second_reference: pointer<IrFunctionReference> = create_ir_function_reference(reference_arena, 0, "callee", reference_types, reference_arena->void_type)
    ir_block_add_instruction(reference_arena, reference_block, create_ir_void_call_instruction(reference_arena, first_reference, reference_arguments))
    ir_block_add_instruction(reference_arena, reference_block, create_ir_void_call_instruction(reference_arena, second_reference, reference_arguments))
    ir_block_terminate(reference_arena, reference_block, create_ir_return(reference_arena, null))
    ir_function_add_block(reference_arena, reference_caller, reference_block)
    seal_ir_function(reference_arena, reference_caller)
    let reference_module: pointer<IrModule> = create_ir_module(reference_arena, "references")
    ir_module_add_function(reference_arena, reference_module, callee)
    ir_module_add_function(reference_arena, reference_module, reference_caller)
    seal_ir_module(reference_arena, reference_module)
    let reference_program: pointer<IrProgram> = create_ir_program(reference_arena)
    ir_program_add_module(reference_program, reference_module)
    ir_clear_error(reference_arena)
    if seal_ir_program(reference_program) || reference_arena->error != "IR calls must share canonical function reference" then
        destroy_vector<pointer<IrValue>>(reference_arguments)
        destroy_vector<pointer<IrType>>(reference_types)
        destroy_ir_program(reference_program)
        return 3
    end
    destroy_vector<pointer<IrValue>>(reference_arguments)
    destroy_vector<pointer<IrType>>(reference_types)
    destroy_ir_program(reference_program)
    return 0
end
