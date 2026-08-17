inject namespace std.console as console
inject namespace std.string as strings
inject std.collections.vector
inject ir.model
inject ir.validation
inject backend.llvm

@init
fn launch() -> int
    let invalid: LlvmGenerationResult = generate_llvm_ir(null, "invalid")
    if invalid.error != "LLVM generation requires an IR program" then
        console::print_line("self-host LLVM generation test failed: null input")
        return 1
    end

    let unsealed: pointer<IrProgram> = create_ir_program(create_ir_arena())
    let unsealed_result: LlvmGenerationResult = generate_llvm_ir(unsealed, "invalid")
    if unsealed_result.error != "LLVM generation requires a sealed IR program" then
        destroy_ir_program(unsealed)
        console::print_line("self-host LLVM generation test failed: unsealed input")
        return 2
    end
    destroy_ir_program(unsealed)

    let empty_program: pointer<IrProgram> = create_ir_program(create_ir_arena())
    seal_ir_program(empty_program)
    let empty_name: LlvmGenerationResult = generate_llvm_ir(empty_program, "")
    if empty_name.error != "LLVM module name must not be empty" || empty_name.text != "" then
        destroy_ir_program(empty_program)
        console::print_line("self-host LLVM generation test failed: empty module name")
        return 3
    end
    destroy_ir_program(empty_program)

    let unsupported: pointer<IrProgram> = create_unsupported_llvm_program()
    let unsupported_result: LlvmGenerationResult = generate_llvm_ir(unsupported, "unsupported")
    if unsupported_result.error != "LLVM generation encountered an unsupported primitive type" || unsupported_result.text != "" then
        destroy_ir_program(unsupported)
        console::print_line("self-host LLVM generation test failed: stable rejection")
        return 4
    end
    destroy_ir_program(unsupported)

    let program: pointer<IrProgram> = create_llvm_test_program()
    if program == null then
        console::print_line("self-host LLVM generation test failed: fixture construction")
        return 5
    end

    let generated: LlvmGenerationResult = generate_llvm_ir(program, "selfhost-test")
    @mut let failure: int = 0
    if !llvm_generation_succeeded(generated) then
        console::print_line("self-host LLVM generation test failed: " + generated.error)
        failure = 4
    end
    if failure == 0 && !text_contains(generated.text, "%sol.string = type { ptr, i64, i64 }") then
        failure = 5
    end
    if failure == 0 && !text_contains(generated.text, "%sol.type0 = type { i64, ptr }") then
        failure = 6
    end
    if failure == 0 && !text_contains(generated.text, "define i64 @sol.function0()") then
        failure = 7
    end
    if failure == 0 && !text_contains(generated.text, "%value2 = add i64 40, 2") then
        failure = 8
    end
    if failure == 0 && !text_contains(generated.text, "define i32 @main()") then
        failure = 9
    end
    let repeated: LlvmGenerationResult = generate_llvm_ir(program, "selfhost-test")
    if failure == 0 && repeated.text != generated.text then
        failure = 10
    end

    destroy_ir_program(program)
    if failure != 0 then
        console::print_line("self-host LLVM generation test failed: textual contract")
    end
    return failure
end

fn create_unsupported_llvm_program() -> pointer<IrProgram>
    let arena: pointer<IrArena> = create_ir_arena()
    let unsupported: pointer<IrType> = create_ir_primitive_type(arena, "unsupported", true, false, false)
    let wrapper: pointer<IrType> = create_ir_struct_type(arena, "fixture.Unsupported")
    let fields: pointer<Vector<pointer<IrStructField>>> = create_vector<pointer<IrStructField>>()
    vector_push<pointer<IrStructField>>(fields, create_ir_struct_field(arena, 0, "value", unsupported))
    define_ir_struct_type(arena, wrapper, fields)
    destroy_vector<pointer<IrStructField>>(fields)
    let module: pointer<IrModule> = create_ir_module(arena, "unsupported")
    ir_module_add_struct(arena, module, wrapper)
    seal_ir_module(arena, module)
    let program: pointer<IrProgram> = create_ir_program(arena)
    ir_program_add_module(program, module)
    seal_ir_program(program)
    return program
end

fn create_llvm_test_program() -> pointer<IrProgram>
    let arena: pointer<IrArena> = create_ir_arena()
    if arena == null then
        return null
    end

    let node: pointer<IrType> = create_ir_struct_type(arena, "fixture.Node")
    let node_pointer: pointer<IrType> = create_ir_pointer_type(arena, node)
    let fields: pointer<Vector<pointer<IrStructField>>> = create_vector<pointer<IrStructField>>()
    vector_push<pointer<IrStructField>>(fields, create_ir_struct_field(arena, 0, "value", arena->integer_type))
    vector_push<pointer<IrStructField>>(fields, create_ir_struct_field(arena, 1, "next", node_pointer))
    define_ir_struct_type(arena, node, fields)
    destroy_vector<pointer<IrStructField>>(fields)

    let function: pointer<IrFunction> = create_ir_function(arena, 0, "launch", arena->integer_type, true)
    let block: pointer<IrBasicBlock> = create_ir_basic_block(arena, create_ir_block_target(arena, 0))
    let forty: pointer<IrValue> = create_ir_int_constant(arena, 0, 40)
    let two: pointer<IrValue> = create_ir_int_constant(arena, 1, 2)
    let sum: pointer<IrInstruction> = create_ir_binary_instruction(arena, 2, ir_binary_add(), forty, two)
    ir_block_add_instruction(arena, block, sum)
    ir_block_terminate(arena, block, create_ir_return(arena, sum->result))
    ir_function_add_block(arena, function, block)
    seal_ir_function(arena, function)

    let module: pointer<IrModule> = create_ir_module(arena, "fixture")
    ir_module_add_struct(arena, module, node)
    ir_module_add_function(arena, module, function)
    seal_ir_module(arena, module)

    let program: pointer<IrProgram> = create_ir_program(arena)
    ir_program_add_module(program, module)
    ir_program_set_entry(program, module, function)
    if !seal_ir_program(program) then
        destroy_ir_program(program)
        return null
    end
    return program
end

fn text_contains(text: string, expected: string) -> boolean
    let text_length: int = strings::length(text)
    let expected_length: int = strings::length(expected)
    if expected_length == 0 then
        return true
    end
    if expected_length > text_length then
        return false
    end
    @mut let index: int = 0
    while index <= text_length - expected_length do
        if strings::substring(text, index, expected_length) == expected then
            return true
        end
        index = index + 1
    end
    return false
end
