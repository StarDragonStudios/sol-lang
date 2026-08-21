inject namespace std.memory as memory
inject std.collections.vector
inject namespace std.string as strings
inject ir.model
inject ir.formatter only format_ir_int, quote_ir_string

struct LlvmGenerationResult
    text: string
    error: string
end

struct LlvmTypeBinding
    type: pointer<IrType>
    id: int
end

struct LlvmGenerationContext
    program: pointer<IrProgram>
    types: pointer<Vector<pointer<LlvmTypeBinding>>>
    lines: pointer<Vector<string>>
    error: string
end

fn generate_llvm_ir(program: pointer<IrProgram>, module_name: string) -> LlvmGenerationResult
    @mut let invalid: LlvmGenerationResult = LlvmGenerationResult { text: "", error: "" }
    if program == null then
        invalid.error = "LLVM generation requires an IR program"
        return invalid
    end
    if !program->sealed then
        invalid.error = "LLVM generation requires a sealed IR program"
        return invalid
    end
    if module_name == "" then
        invalid.error = "LLVM module name must not be empty"
        return invalid
    end

    let context: pointer<LlvmGenerationContext> = create_llvm_context(program)
    if context == null then
        invalid.error = "LLVM generation allocation failed"
        return invalid
    end

    collect_llvm_types(context)
    if !llvm_failed(context) then
        emit_llvm_program(context, module_name)
    end

    @mut let result: LlvmGenerationResult = LlvmGenerationResult {
        text: join_llvm_lines(context->lines, 0, vector_length<string>(context->lines)),
        error: context->error
    }
    if context->error != "" then
        result.text = ""
    end
    destroy_llvm_context(context)
    return result
end

fn llvm_generation_succeeded(result: LlvmGenerationResult) -> boolean
    return result.error == ""
end

fn create_llvm_context(program: pointer<IrProgram>) -> pointer<LlvmGenerationContext>
    let context: pointer<LlvmGenerationContext> = memory::allocate<LlvmGenerationContext>(1)
    if context == null then
        return null
    end
    context->program = program
    context->types = create_vector<pointer<LlvmTypeBinding>>()
    context->lines = create_vector<string>()
    context->error = ""
    return context
end

fn destroy_llvm_context(context: pointer<LlvmGenerationContext>) -> void
    if context == null then
        return
    end
    @mut let index: int = vector_length<pointer<LlvmTypeBinding>>(context->types)
    while index > 0 do
        index = index - 1
        memory::free<LlvmTypeBinding>(vector_get<pointer<LlvmTypeBinding>>(context->types, index))
    end
    destroy_vector<pointer<LlvmTypeBinding>>(context->types)
    destroy_vector<string>(context->lines)
    memory::free<LlvmGenerationContext>(context)
    return
end

fn llvm_fail(context: pointer<LlvmGenerationContext>, message: string) -> void
    if context != null && context->error == "" then
        context->error = message
    end
    return
end

fn llvm_failed(context: pointer<LlvmGenerationContext>) -> boolean
    return context == null || context->error != ""
end

fn llvm_line(context: pointer<LlvmGenerationContext>, text: string) -> void
    vector_push<string>(context->lines, text + "\n")
    return
end

fn join_llvm_lines(lines: pointer<Vector<string>>, start: int, end_index: int) -> string
    if start >= end_index then
        return ""
    end
    if end_index - start == 1 then
        return vector_get<string>(lines, start)
    end
    let middle: int = start + (end_index - start) / 2
    return join_llvm_lines(lines, start, middle) + join_llvm_lines(lines, middle, end_index)
end

fn collect_llvm_types(context: pointer<LlvmGenerationContext>) -> void
    @mut let module_index: int = 0
    while module_index < vector_length<pointer<IrModule>>(context->program->modules) do
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(context->program->modules, module_index)
        @mut let type_index: int = 0
        while type_index < vector_length<pointer<IrType>>(module->structs) do
            let binding: pointer<LlvmTypeBinding> = memory::allocate<LlvmTypeBinding>(1)
            if binding == null then
                llvm_fail(context, "LLVM type catalog allocation failed")
                return
            end
            binding->type = vector_get<pointer<IrType>>(module->structs, type_index)
            binding->id = vector_length<pointer<LlvmTypeBinding>>(context->types)
            vector_push<pointer<LlvmTypeBinding>>(context->types, binding)
            type_index = type_index + 1
        end
        module_index = module_index + 1
    end
    return
end

fn llvm_type_id(context: pointer<LlvmGenerationContext>, type: pointer<IrType>) -> int
    @mut let index: int = 0
    while index < vector_length<pointer<LlvmTypeBinding>>(context->types) do
        let binding: pointer<LlvmTypeBinding> = vector_get<pointer<LlvmTypeBinding>>(context->types, index)
        if binding->type == type then
            return binding->id
        end
        index = index + 1
    end
    llvm_fail(context, "LLVM generation encountered an unregistered struct type")
    return -1
end

fn llvm_type(context: pointer<LlvmGenerationContext>, type: pointer<IrType>) -> string
    if type == null then
        llvm_fail(context, "LLVM generation encountered a missing type")
        return "void"
    end
    if type->kind == ir_type_pointer() then
        return "ptr"
    end
    if type->kind == ir_type_struct() then
        return "%sol.type" + format_ir_int(llvm_type_id(context, type))
    end
    if type == context->program->arena->integer_type then
        return "i64"
    end
    if type == context->program->arena->float_type then
        return "double"
    end
    if type == context->program->arena->boolean_type then
        return "i1"
    end
    if type == context->program->arena->char_type then
        return "i32"
    end
    if type == context->program->arena->string_type then
        return "%sol.string"
    end
    if type == context->program->arena->void_type then
        return "void"
    end
    llvm_fail(context, "LLVM generation encountered an unsupported primitive type")
    return "void"
end

fn emit_llvm_program(context: pointer<LlvmGenerationContext>, module_name: string) -> void
    llvm_line(context, "; Sol self-host textual LLVM module: " + llvm_comment_text(module_name))
    llvm_line(context, "source_filename = \"sol-selfhost\"")
    llvm_line(context, "")
    llvm_line(context, "%sol.string = type { ptr, i64, i64 }")
    emit_llvm_structs(context)
    llvm_line(context, "")
    emit_llvm_runtime_declarations(context)
    llvm_line(context, "")
    emit_llvm_functions(context)
    if context->program->entry_function != null then
        llvm_line(context, "")
        emit_llvm_entry(context)
    end
    return
end

fn emit_llvm_structs(context: pointer<LlvmGenerationContext>) -> void
    @mut let index: int = 0
    while index < vector_length<pointer<LlvmTypeBinding>>(context->types) do
        let binding: pointer<LlvmTypeBinding> = vector_get<pointer<LlvmTypeBinding>>(context->types, index)
        @mut let fields: string = ""
        @mut let field_index: int = 0
        while field_index < vector_length<pointer<IrStructField>>(binding->type->fields) do
            if field_index > 0 then
                fields = fields + ", "
            end
            fields = fields + llvm_type(context, vector_get<pointer<IrStructField>>(binding->type->fields, field_index)->type)
            field_index = field_index + 1
        end
        llvm_line(context, "%sol.type" + format_ir_int(binding->id) + " = type { " + fields + " } ; " + llvm_comment_text(binding->type->name))
        index = index + 1
    end
    return
end

fn emit_llvm_runtime_declarations(context: pointer<LlvmGenerationContext>) -> void
    llvm_line(context, "; Runtime boundary. Literal storage and host operations are provided by the self-host native runtime.")
    llvm_line(context, "declare i32 @sol_runtime_char_literal(i64, i64)")
    llvm_line(context, "declare void @sol_runtime_string_literal(ptr, i64, i64)")
    llvm_line(context, "declare void @sol_runtime_string_concat(ptr, ptr, i64, i64, ptr, i64, i64)")
    llvm_line(context, "declare i1 @sol_runtime_string_equal(ptr, i64, ptr, i64)")
    llvm_line(context, "declare i32 @sol_runtime_string_index(ptr, i64, i64, i64)")
    llvm_line(context, "declare void @sol_runtime_string_slice(ptr, ptr, i64, i64, i64, i64)")
    llvm_line(context, "declare void @sol_runtime_string_substring(ptr, ptr, i64, i64, i64, i64)")
    llvm_line(context, "declare void @sol_runtime_console_print(ptr, i64)")
    llvm_line(context, "declare void @sol_runtime_console_print_line(ptr, i64)")
    llvm_line(context, "declare void @sol_runtime_console_read_line(ptr)")
    llvm_line(context, "declare i1 @sol_runtime_file_exists(ptr, i64)")
    llvm_line(context, "declare void @sol_runtime_file_read_text(ptr, ptr, i64)")
    llvm_line(context, "declare i1 @sol_runtime_file_write_text(ptr, i64, ptr, i64)")
    llvm_line(context, "declare i1 @sol_runtime_file_append_text(ptr, i64, ptr, i64)")
    llvm_line(context, "declare void @sol_runtime_vector_fail_allocation()")
    llvm_line(context, "declare void @sol_runtime_vector_fail_bounds()")
    llvm_line(context, "declare void @sol_runtime_vector_fail_capacity()")
    llvm_line(context, "declare void @sol_runtime_vector_fail_empty_pop()")
    llvm_line(context, "declare ptr @malloc(i64)")
    llvm_line(context, "declare ptr @realloc(ptr, i64)")
    llvm_line(context, "declare void @free(ptr)")
    return
end

fn emit_llvm_functions(context: pointer<LlvmGenerationContext>) -> void
    @mut let module_index: int = 0
    while module_index < vector_length<pointer<IrModule>>(context->program->modules) do
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(context->program->modules, module_index)
        @mut let function_index: int = 0
        while function_index < vector_length<pointer<IrFunction>>(module->functions) do
            emit_llvm_function(context, module, vector_get<pointer<IrFunction>>(module->functions, function_index))
            if !llvm_failed(context) then
                llvm_line(context, "")
            end
            function_index = function_index + 1
        end
        module_index = module_index + 1
    end
    return
end

fn llvm_function_symbol(context: pointer<LlvmGenerationContext>, function_id: int) -> string
    @mut let module_index: int = 0
    while module_index < vector_length<pointer<IrModule>>(context->program->modules) do
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(context->program->modules, module_index)
        @mut let function_index: int = 0
        while function_index < vector_length<pointer<IrFunction>>(module->functions) do
            let function: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, function_index)
            if function->id == function_id then
                return "@sol.function" + format_ir_int(function_id)
            end
            function_index = function_index + 1
        end
        module_index = module_index + 1
    end
    llvm_fail(context, "LLVM generation cannot resolve a canonical function symbol")
    return "@sol.function.invalid"
end

fn llvm_standard_runtime_symbol(module_name: string, function_name: string) -> string
    if module_name == "std.console" then
        if function_name == "print" then
            return "sol_runtime_console_print"
        end
        if function_name == "print_line" then
            return "sol_runtime_console_print_line"
        end
        if function_name == "read_line" then
            return "sol_runtime_console_read_line"
        end
    end
    if module_name == "std.file" then
        if function_name == "exists" then
            return "sol_runtime_file_exists"
        end
        if function_name == "read_text" then
            return "sol_runtime_file_read_text"
        end
        if function_name == "write_text" then
            return "sol_runtime_file_write_text"
        end
        if function_name == "append_text" then
            return "sol_runtime_file_append_text"
        end
    end
    if module_name == "std.string" then
        if function_name == "length" then
            return "sol_runtime_string_length"
        end
        if function_name == "slice" then
            return "sol_runtime_string_slice"
        end
        if function_name == "substring" then
            return "sol_runtime_string_substring"
        end
    end
    if module_name == "std.collections.vector" then
        if function_name == "_vector_fail_allocation" then
            return "sol_runtime_vector_fail_allocation"
        end
        if function_name == "_vector_fail_bounds" then
            return "sol_runtime_vector_fail_bounds"
        end
        if function_name == "_vector_fail_capacity" then
            return "sol_runtime_vector_fail_capacity"
        end
        if function_name == "_vector_fail_empty_pop" then
            return "sol_runtime_vector_fail_empty_pop"
        end
    end
    return ""
end

fn llvm_function_signature(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>, names: boolean) -> string
    @mut let parameters: string = ""
    @mut let index: int = 0
    while index < vector_length<pointer<IrParameter>>(function->parameters) do
        if index > 0 then
            parameters = parameters + ", "
        end
        let parameter: pointer<IrParameter> = vector_get<pointer<IrParameter>>(function->parameters, index)
        parameters = parameters + llvm_type(context, parameter->value->type)
        if names then
            parameters = parameters + " %value" + format_ir_int(parameter->value->id)
        end
        index = index + 1
    end
    return llvm_type(context, function->return_type) + " " + llvm_function_symbol(context, function->id) + "(" + parameters + ")"
end

fn emit_llvm_function(context: pointer<LlvmGenerationContext>, module: pointer<IrModule>, function: pointer<IrFunction>) -> void
    llvm_line(context, "; " + llvm_comment_text(module->name + "::" + function->name))
    if !function->has_body then
        if module->name == "std.memory" then
            emit_llvm_memory_function(context, function)
            return
        end
        if module->name == "std.console" || module->name == "std.file" || module->name == "std.string" || module->name == "std.collections.vector" then
            emit_llvm_standard_function(context, module, function)
            return
        end
        llvm_line(context, "declare " + llvm_function_signature(context, function, false))
        return
    end
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    @mut let block_index: int = 0
    while block_index < vector_length<pointer<IrBasicBlock>>(function->blocks) do
        emit_llvm_block(context, function, vector_get<pointer<IrBasicBlock>>(function->blocks, block_index), block_index == 0)
        block_index = block_index + 1
    end
    llvm_line(context, "}")
    return
end

fn emit_llvm_standard_function(context: pointer<LlvmGenerationContext>, module: pointer<IrModule>, function: pointer<IrFunction>) -> void
    if module->name == "std.console" then
        emit_llvm_console_function(context, function)
        return
    end
    if module->name == "std.file" then
        emit_llvm_file_function(context, function)
        return
    end
    if module->name == "std.string" then
        emit_llvm_standard_string_function(context, function)
        return
    end
    if module->name == "std.collections.vector" then
        emit_llvm_vector_failure(context, function)
        return
    end
    llvm_fail(context, "LLVM generation encountered an unsupported standard-library module")
    return
end

fn emit_llvm_standard_string_fields(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>, parameter_index: int, prefix: string) -> void
    let parameter: string = llvm_parameter_name(function, parameter_index)
    llvm_line(context, "  %" + prefix + ".data = extractvalue %sol.string " + parameter + ", 0")
    llvm_line(context, "  %" + prefix + ".bytes = extractvalue %sol.string " + parameter + ", 1")
    llvm_line(context, "  %" + prefix + ".scalars = extractvalue %sol.string " + parameter + ", 2")
    return
end

fn emit_llvm_console_function(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "runtime.entry:")
    if function->name == "read_line" then
        llvm_line(context, "  %runtime.result.address = alloca %sol.string")
        llvm_line(context, "  call void @sol_runtime_console_read_line(ptr %runtime.result.address)")
        llvm_line(context, "  %runtime.result = load %sol.string, ptr %runtime.result.address")
        llvm_line(context, "  ret %sol.string %runtime.result")
        llvm_line(context, "}")
        return
    end
    emit_llvm_standard_string_fields(context, function, 0, "runtime.value")
    if function->name == "print" then
        llvm_line(context, "  call void @sol_runtime_console_print(ptr %runtime.value.data, i64 %runtime.value.bytes)")
    else
        if function->name == "print_line" then
            llvm_line(context, "  call void @sol_runtime_console_print_line(ptr %runtime.value.data, i64 %runtime.value.bytes)")
        else
            llvm_fail(context, "LLVM generation encountered an unsupported std.console function")
        end
    end
    llvm_line(context, "  ret void")
    llvm_line(context, "}")
    return
end

fn emit_llvm_standard_string_function(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "runtime.entry:")
    emit_llvm_standard_string_fields(context, function, 0, "runtime.value")
    if function->name == "length" then
        llvm_line(context, "  ret i64 %runtime.value.scalars")
        llvm_line(context, "}")
        return
    end
    llvm_line(context, "  %runtime.result.address = alloca %sol.string")
    if function->name == "slice" then
        llvm_line(context, "  call void @sol_runtime_string_slice(ptr %runtime.result.address, ptr %runtime.value.data, i64 %runtime.value.bytes, i64 %runtime.value.scalars, i64 " + llvm_parameter_name(function, 1) + ", i64 " + llvm_parameter_name(function, 2) + ")")
    else
        if function->name == "substring" then
            llvm_line(context, "  call void @sol_runtime_string_substring(ptr %runtime.result.address, ptr %runtime.value.data, i64 %runtime.value.bytes, i64 %runtime.value.scalars, i64 " + llvm_parameter_name(function, 1) + ", i64 " + llvm_parameter_name(function, 2) + ")")
        else
            llvm_fail(context, "LLVM generation encountered an unsupported std.string function")
        end
    end
    llvm_line(context, "  %runtime.result = load %sol.string, ptr %runtime.result.address")
    llvm_line(context, "  ret %sol.string %runtime.result")
    llvm_line(context, "}")
    return
end

fn emit_llvm_file_function(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "runtime.entry:")
    emit_llvm_standard_string_fields(context, function, 0, "runtime.path")
    if function->name == "exists" then
        llvm_line(context, "  %runtime.result = call i1 @sol_runtime_file_exists(ptr %runtime.path.data, i64 %runtime.path.bytes)")
        llvm_line(context, "  ret i1 %runtime.result")
        llvm_line(context, "}")
        return
    end
    if function->name == "read_text" then
        llvm_line(context, "  %runtime.result.address = alloca %sol.string")
        llvm_line(context, "  call void @sol_runtime_file_read_text(ptr %runtime.result.address, ptr %runtime.path.data, i64 %runtime.path.bytes)")
        llvm_line(context, "  %runtime.result = load %sol.string, ptr %runtime.result.address")
        llvm_line(context, "  ret %sol.string %runtime.result")
        llvm_line(context, "}")
        return
    end
    emit_llvm_standard_string_fields(context, function, 1, "runtime.content")
    if function->name == "write_text" then
        llvm_line(context, "  %runtime.result = call i1 @sol_runtime_file_write_text(ptr %runtime.path.data, i64 %runtime.path.bytes, ptr %runtime.content.data, i64 %runtime.content.bytes)")
    else
        if function->name == "append_text" then
            llvm_line(context, "  %runtime.result = call i1 @sol_runtime_file_append_text(ptr %runtime.path.data, i64 %runtime.path.bytes, ptr %runtime.content.data, i64 %runtime.content.bytes)")
        else
            llvm_fail(context, "LLVM generation encountered an unsupported std.file function")
        end
    end
    llvm_line(context, "  ret i1 %runtime.result")
    llvm_line(context, "}")
    return
end

fn emit_llvm_vector_failure(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    let runtime: string = llvm_standard_runtime_symbol("std.collections.vector", function->name)
    if runtime == "" then
        llvm_fail(context, "LLVM generation encountered an unsupported vector runtime function")
        return
    end
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "runtime.entry:")
    llvm_line(context, "  call void @" + runtime + "()")
    llvm_line(context, "  unreachable")
    llvm_line(context, "}")
    return
end

fn llvm_function_name_is(name: string, expected: string) -> boolean
    if name == expected then
        return true
    end
    let expected_length: int = strings::length(expected)
    if strings::length(name) <= expected_length then
        return false
    end
    return strings::slice(name, 0, expected_length + 1) == expected + "$"
end

fn emit_llvm_memory_function(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    if llvm_function_name_is(function->name, "allocate") then
        emit_llvm_memory_allocate(context, function)
        return
    end
    if llvm_function_name_is(function->name, "reallocate") then
        emit_llvm_memory_reallocate(context, function)
        return
    end
    if llvm_function_name_is(function->name, "free") then
        emit_llvm_memory_free(context, function)
        return
    end
    if llvm_function_name_is(function->name, "load_at") then
        emit_llvm_memory_load(context, function, true)
        return
    end
    if llvm_function_name_is(function->name, "load") then
        emit_llvm_memory_load(context, function, false)
        return
    end
    if llvm_function_name_is(function->name, "store_at") then
        emit_llvm_memory_store(context, function, true)
        return
    end
    if llvm_function_name_is(function->name, "store") then
        emit_llvm_memory_store(context, function, false)
        return
    end
    llvm_fail(context, "LLVM generation encountered an unsupported std.memory function")
    return
end

fn llvm_parameter_name(function: pointer<IrFunction>, index: int) -> string
    return "%value" + format_ir_int(vector_get<pointer<IrParameter>>(function->parameters, index)->value->id)
end

fn llvm_memory_element_type(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> pointer<IrType>
    if llvm_function_name_is(function->name, "allocate") || llvm_function_name_is(function->name, "reallocate") then
        if function->return_type->kind != ir_type_pointer() then
            llvm_fail(context, "LLVM allocation function does not return a pointer")
            return null
        end
        return function->return_type->element_type
    end
    if llvm_function_name_is(function->name, "load") || llvm_function_name_is(function->name, "load_at") then
        return function->return_type
    end
    let first: pointer<IrType> = vector_get<pointer<IrParameter>>(function->parameters, 0)->value->type
    if first->kind == ir_type_pointer() then
        return first->element_type
    end
    llvm_fail(context, "LLVM std.memory function has no concrete element type")
    return null
end

fn emit_llvm_memory_size(context: pointer<LlvmGenerationContext>, element: pointer<IrType>) -> void
    let type: string = llvm_type(context, element)
    llvm_line(context, "  %element.size = ptrtoint ptr getelementptr (" + type + ", ptr null, i32 1) to i64")
    return
end

fn emit_llvm_memory_allocate(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    let count: string = llvm_parameter_name(function, 0)
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "memory.entry:")
    llvm_line(context, "  %count.positive = icmp sgt i64 " + count + ", 0")
    llvm_line(context, "  br i1 %count.positive, label %memory.size, label %memory.invalid")
    llvm_line(context, "memory.size:")
    emit_llvm_memory_size(context, llvm_memory_element_type(context, function))
    llvm_line(context, "  %size.positive = icmp sgt i64 %element.size, 0")
    llvm_line(context, "  br i1 %size.positive, label %memory.bounds, label %memory.invalid")
    llvm_line(context, "memory.bounds:")
    llvm_line(context, "  %maximum.count = udiv i64 9223372036854775807, %element.size")
    llvm_line(context, "  %count.fits = icmp sle i64 " + count + ", %maximum.count")
    llvm_line(context, "  br i1 %count.fits, label %memory.allocate, label %memory.invalid")
    llvm_line(context, "memory.allocate:")
    llvm_line(context, "  %allocation.bytes = mul i64 " + count + ", %element.size")
    llvm_line(context, "  %allocation.result = call ptr @malloc(i64 %allocation.bytes)")
    llvm_line(context, "  ret ptr %allocation.result")
    llvm_line(context, "memory.invalid:")
    llvm_line(context, "  ret ptr null")
    llvm_line(context, "}")
    return
end

fn emit_llvm_memory_reallocate(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    let pointer_value: string = llvm_parameter_name(function, 0)
    let count: string = llvm_parameter_name(function, 1)
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "memory.entry:")
    llvm_line(context, "  %count.zero = icmp eq i64 " + count + ", 0")
    llvm_line(context, "  br i1 %count.zero, label %memory.release, label %memory.positive")
    llvm_line(context, "memory.release:")
    llvm_line(context, "  call void @free(ptr " + pointer_value + ")")
    llvm_line(context, "  ret ptr null")
    llvm_line(context, "memory.positive:")
    llvm_line(context, "  %count.valid = icmp sgt i64 " + count + ", 0")
    llvm_line(context, "  br i1 %count.valid, label %memory.size, label %memory.invalid")
    llvm_line(context, "memory.size:")
    emit_llvm_memory_size(context, llvm_memory_element_type(context, function))
    llvm_line(context, "  %size.positive = icmp sgt i64 %element.size, 0")
    llvm_line(context, "  br i1 %size.positive, label %memory.bounds, label %memory.invalid")
    llvm_line(context, "memory.bounds:")
    llvm_line(context, "  %maximum.count = udiv i64 9223372036854775807, %element.size")
    llvm_line(context, "  %count.fits = icmp sle i64 " + count + ", %maximum.count")
    llvm_line(context, "  br i1 %count.fits, label %memory.resize, label %memory.invalid")
    llvm_line(context, "memory.resize:")
    llvm_line(context, "  %allocation.bytes = mul i64 " + count + ", %element.size")
    llvm_line(context, "  %allocation.result = call ptr @realloc(ptr " + pointer_value + ", i64 %allocation.bytes)")
    llvm_line(context, "  ret ptr %allocation.result")
    llvm_line(context, "memory.invalid:")
    llvm_line(context, "  ret ptr null")
    llvm_line(context, "}")
    return
end

fn emit_llvm_memory_free(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "memory.entry:")
    llvm_line(context, "  call void @free(ptr " + llvm_parameter_name(function, 0) + ")")
    llvm_line(context, "  ret void")
    llvm_line(context, "}")
    return
end

fn emit_llvm_memory_load(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>, indexed: boolean) -> void
    let element: string = llvm_type(context, llvm_memory_element_type(context, function))
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "memory.entry:")
    @mut let address: string = llvm_parameter_name(function, 0)
    if indexed then
        llvm_line(context, "  %memory.address = getelementptr " + element + ", ptr " + address + ", i64 " + llvm_parameter_name(function, 1))
        address = "%memory.address"
    end
    llvm_line(context, "  %memory.value = load " + element + ", ptr " + address)
    llvm_line(context, "  ret " + element + " %memory.value")
    llvm_line(context, "}")
    return
end

fn emit_llvm_memory_store(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>, indexed: boolean) -> void
    let element: string = llvm_type(context, llvm_memory_element_type(context, function))
    llvm_line(context, "define " + llvm_function_signature(context, function, true) + " {")
    llvm_line(context, "memory.entry:")
    @mut let address: string = llvm_parameter_name(function, 0)
    @mut let value_index: int = 1
    if indexed then
        llvm_line(context, "  %memory.address = getelementptr " + element + ", ptr " + address + ", i64 " + llvm_parameter_name(function, 1))
        address = "%memory.address"
        value_index = 2
    end
    llvm_line(context, "  store " + element + " " + llvm_parameter_name(function, value_index) + ", ptr " + address)
    llvm_line(context, "  ret void")
    llvm_line(context, "}")
    return
end

fn emit_llvm_block(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>, block: pointer<IrBasicBlock>, first: boolean) -> void
    llvm_line(context, "block" + format_ir_int(block->target->id) + ":")
    if first then
        emit_llvm_local_allocations(context, function)
    end
    @mut let instruction_index: int = 0
    while instruction_index < vector_length<pointer<IrInstruction>>(block->instructions) do
        let instruction: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, instruction_index)
        emit_llvm_literal_operands(context, instruction->operands, function->id, block->target->id, instruction_index)
        emit_llvm_instruction(context, instruction, block->target->id, instruction_index)
        instruction_index = instruction_index + 1
    end
    emit_llvm_terminator_literals(context, block->terminator, function->id, block->target->id)
    emit_llvm_terminator(context, block->terminator, block->target->id)
    return
end

fn emit_llvm_local_allocations(context: pointer<LlvmGenerationContext>, function: pointer<IrFunction>) -> void
    @mut let block_index: int = 0
    while block_index < vector_length<pointer<IrBasicBlock>>(function->blocks) do
        let block: pointer<IrBasicBlock> = vector_get<pointer<IrBasicBlock>>(function->blocks, block_index)
        @mut let instruction_index: int = 0
        while instruction_index < vector_length<pointer<IrInstruction>>(block->instructions) do
            let instruction: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, instruction_index)
            if instruction->kind == ir_instruction_local_initialize() then
                llvm_line(context, "  %local" + format_ir_int(instruction->local->id) + " = alloca " + llvm_type(context, instruction->local->type))
            end
            instruction_index = instruction_index + 1
        end
        block_index = block_index + 1
    end
    return
end

fn llvm_literal_name(block_id: int, instruction_id: int, operand_id: int) -> string
    return "%literal.b" + format_ir_int(block_id) + ".i" + format_ir_int(instruction_id) + ".o" + format_ir_int(operand_id)
end

fn emit_llvm_literal_operands(context: pointer<LlvmGenerationContext>, operands: pointer<Vector<pointer<IrValue>>>, function_id: int, block_id: int, instruction_id: int) -> void
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(operands) do
        emit_llvm_literal(context, vector_get<pointer<IrValue>>(operands, index), function_id, block_id, instruction_id, index)
        index = index + 1
    end
    return
end

fn emit_llvm_terminator_literals(context: pointer<LlvmGenerationContext>, terminator: pointer<IrTerminator>, function_id: int, block_id: int) -> void
    if terminator->kind == ir_terminator_return() && terminator->value != null then
        emit_llvm_literal(context, terminator->value, function_id, block_id, -1, 0)
    end
    if terminator->kind == ir_terminator_conditional_branch() then
        emit_llvm_literal(context, terminator->condition, function_id, block_id, -1, 0)
    end
    return
end

fn emit_llvm_literal(context: pointer<LlvmGenerationContext>, value: pointer<IrValue>, function_id: int, block_id: int, instruction_id: int, operand_id: int) -> void
    if value->kind == ir_value_char_constant() then
        llvm_line(context, "  ; char literal " + llvm_comment_text(value->string_value))
        llvm_line(context, "  " + llvm_literal_name(block_id, instruction_id, operand_id) + " = call i32 @sol_runtime_char_literal(i64 " + format_ir_int(function_id) + ", i64 " + format_ir_int(value->id) + ")")
    end
    if value->kind == ir_value_string_constant() then
        llvm_line(context, "  ; string literal " + llvm_comment_text(quote_ir_string(value->string_value)))
        llvm_line(context, "  " + llvm_literal_name(block_id, instruction_id, operand_id) + ".address = alloca %sol.string")
        llvm_line(context, "  call void @sol_runtime_string_literal(ptr " + llvm_literal_name(block_id, instruction_id, operand_id) + ".address, i64 " + format_ir_int(function_id) + ", i64 " + format_ir_int(value->id) + ")")
        llvm_line(context, "  " + llvm_literal_name(block_id, instruction_id, operand_id) + " = load %sol.string, ptr " + llvm_literal_name(block_id, instruction_id, operand_id) + ".address")
    end
    return
end

fn llvm_value(value: pointer<IrValue>, block_id: int, instruction_id: int, operand_id: int) -> string
    if value->kind == ir_value_parameter() || value->kind == ir_value_instruction() then
        return "%value" + format_ir_int(value->id)
    end
    if value->kind == ir_value_int_constant() then
        return format_ir_int(value->int_value)
    end
    if value->kind == ir_value_float_constant() then
        return value->string_value
    end
    if value->kind == ir_value_boolean_constant() then
        if value->boolean_value then
            return "true"
        end
        return "false"
    end
    if value->kind == ir_value_null_constant() then
        return "null"
    end
    return llvm_literal_name(block_id, instruction_id, operand_id)
end

fn llvm_operand(context: pointer<LlvmGenerationContext>, value: pointer<IrValue>, block_id: int, instruction_id: int, operand_id: int) -> string
    return llvm_type(context, value->type) + " " + llvm_value(value, block_id, instruction_id, operand_id)
end

fn llvm_string_temporary(block_id: int, instruction_id: int, operand_id: int) -> string
    return "%string.b" + format_ir_int(block_id) + ".i" + format_ir_int(instruction_id) + ".o" + format_ir_int(operand_id)
end

fn emit_llvm_string_fields(context: pointer<LlvmGenerationContext>, value: pointer<IrValue>, block_id: int, instruction_id: int, operand_id: int) -> string
    let temporary: string = llvm_string_temporary(block_id, instruction_id, operand_id)
    let source: string = llvm_value(value, block_id, instruction_id, operand_id)
    llvm_line(context, "  " + temporary + ".data = extractvalue %sol.string " + source + ", 0")
    llvm_line(context, "  " + temporary + ".bytes = extractvalue %sol.string " + source + ", 1")
    llvm_line(context, "  " + temporary + ".scalars = extractvalue %sol.string " + source + ", 2")
    return temporary
end

fn emit_llvm_instruction(context: pointer<LlvmGenerationContext>, instruction: pointer<IrInstruction>, block_id: int, instruction_id: int) -> void
    let result: string = llvm_instruction_result(instruction)
    if instruction->kind == ir_instruction_local_initialize() then
        llvm_line(context, "  store " + llvm_operand(context, vector_get<pointer<IrValue>>(instruction->operands, 0), block_id, instruction_id, 0) + ", ptr %local" + format_ir_int(instruction->local->id))
        return
    end
    if instruction->kind == ir_instruction_local_load() then
        llvm_line(context, "  " + result + " = load " + llvm_type(context, instruction->result->type) + ", ptr %local" + format_ir_int(instruction->local->id))
        return
    end
    if instruction->kind == ir_instruction_local_store() then
        llvm_line(context, "  store " + llvm_operand(context, vector_get<pointer<IrValue>>(instruction->operands, 0), block_id, instruction_id, 0) + ", ptr %local" + format_ir_int(instruction->local->id))
        return
    end
    if instruction->kind == ir_instruction_unary() then
        emit_llvm_unary(context, instruction, block_id, instruction_id)
        return
    end
    if instruction->kind == ir_instruction_binary() then
        emit_llvm_binary(context, instruction, block_id, instruction_id)
        return
    end
    if instruction->kind == ir_instruction_value_call() || instruction->kind == ir_instruction_void_call() then
        emit_llvm_call(context, instruction, block_id, instruction_id)
        return
    end
    if instruction->kind == ir_instruction_struct_construct() then
        emit_llvm_struct_construct(context, instruction, block_id, instruction_id)
        return
    end
    if instruction->kind == ir_instruction_struct_extract() then
        let aggregate: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
        llvm_line(context, "  " + result + " = extractvalue " + llvm_operand(context, aggregate, block_id, instruction_id, 0) + ", " + format_ir_int(instruction->field->index))
        return
    end
    if instruction->kind == ir_instruction_struct_field_store() then
        emit_llvm_struct_field_store(context, instruction, block_id, instruction_id)
        return
    end
    if instruction->kind == ir_instruction_pointer_load() then
        let pointer_value: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
        llvm_line(context, "  " + result + " = load " + llvm_type(context, instruction->result->type) + ", ptr " + llvm_value(pointer_value, block_id, instruction_id, 0))
        return
    end
    if instruction->kind == ir_instruction_pointer_store() then
        let pointer_value: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
        let stored: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 1)
        llvm_line(context, "  store " + llvm_operand(context, stored, block_id, instruction_id, 1) + ", ptr " + llvm_value(pointer_value, block_id, instruction_id, 0))
        return
    end
    if instruction->kind == ir_instruction_pointer_index_load() || instruction->kind == ir_instruction_pointer_index_store() then
        emit_llvm_pointer_index(context, instruction, block_id, instruction_id)
        return
    end
    if instruction->kind == ir_instruction_pointer_field_load() || instruction->kind == ir_instruction_pointer_field_store() then
        emit_llvm_pointer_field(context, instruction, block_id, instruction_id)
        return
    end
    if instruction->kind == ir_instruction_string_index() then
        let text: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
        let index: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 1)
        let fields: string = emit_llvm_string_fields(context, text, block_id, instruction_id, 0)
        llvm_line(context, "  " + result + " = call i32 @sol_runtime_string_index(ptr " + fields + ".data, i64 " + fields + ".bytes, i64 " + fields + ".scalars, " + llvm_operand(context, index, block_id, instruction_id, 1) + ")")
        return
    end
    llvm_fail(context, "LLVM generation encountered an unsupported instruction")
    return
end

fn llvm_instruction_result(instruction: pointer<IrInstruction>) -> string
    if instruction->result == null then
        return ""
    end
    return "%value" + format_ir_int(instruction->result->id)
end

fn emit_llvm_unary(context: pointer<LlvmGenerationContext>, instruction: pointer<IrInstruction>, block_id: int, instruction_id: int) -> void
    let operand: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
    let result: string = llvm_instruction_result(instruction)
    if instruction->operator == ir_unary_logical_not() then
        llvm_line(context, "  " + result + " = xor i1 " + llvm_value(operand, block_id, instruction_id, 0) + ", true")
        return
    end
    if operand->type == context->program->arena->float_type then
        if instruction->operator == ir_unary_negate() then
            llvm_line(context, "  " + result + " = fneg double " + llvm_value(operand, block_id, instruction_id, 0))
        else
            llvm_line(context, "  " + result + " = fadd double " + llvm_value(operand, block_id, instruction_id, 0) + ", 0.0")
        end
        return
    end
    if instruction->operator == ir_unary_negate() then
        llvm_line(context, "  " + result + " = sub i64 0, " + llvm_value(operand, block_id, instruction_id, 0))
    else
        llvm_line(context, "  " + result + " = add i64 " + llvm_value(operand, block_id, instruction_id, 0) + ", 0")
    end
    return
end

fn emit_llvm_binary(context: pointer<LlvmGenerationContext>, instruction: pointer<IrInstruction>, block_id: int, instruction_id: int) -> void
    let left: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
    let right: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 1)
    let result: string = llvm_instruction_result(instruction)
    if left->type == context->program->arena->string_type then
        let left_fields: string = emit_llvm_string_fields(context, left, block_id, instruction_id, 0)
        let right_fields: string = emit_llvm_string_fields(context, right, block_id, instruction_id, 1)
        if instruction->operator == ir_binary_add() then
            llvm_line(context, "  " + result + ".address = alloca %sol.string")
            llvm_line(context, "  call void @sol_runtime_string_concat(ptr " + result + ".address, ptr " + left_fields + ".data, i64 " + left_fields + ".bytes, i64 " + left_fields + ".scalars, ptr " + right_fields + ".data, i64 " + right_fields + ".bytes, i64 " + right_fields + ".scalars)")
            llvm_line(context, "  " + result + " = load %sol.string, ptr " + result + ".address")
            return
        end
        if instruction->operator == ir_binary_not_equal() then
            llvm_line(context, "  " + result + ".equal = call i1 @sol_runtime_string_equal(ptr " + left_fields + ".data, i64 " + left_fields + ".bytes, ptr " + right_fields + ".data, i64 " + right_fields + ".bytes)")
            llvm_line(context, "  " + result + " = xor i1 " + result + ".equal, true")
        else
            llvm_line(context, "  " + result + " = call i1 @sol_runtime_string_equal(ptr " + left_fields + ".data, i64 " + left_fields + ".bytes, ptr " + right_fields + ".data, i64 " + right_fields + ".bytes)")
        end
        return
    end
    let floating: boolean = left->type == context->program->arena->float_type
    let operation: string = llvm_binary_operation(instruction->operator, floating)
    llvm_line(context, "  " + result + " = " + operation + " " + llvm_type(context, left->type) + " " + llvm_value(left, block_id, instruction_id, 0) + ", " + llvm_value(right, block_id, instruction_id, 1))
    return
end

fn llvm_binary_operation(operator: int, floating: boolean) -> string
    if operator == ir_binary_multiply() then
        if floating then
            return "fmul"
        end
        return "mul"
    end
    if operator == ir_binary_divide() then
        if floating then
            return "fdiv"
        end
        return "sdiv"
    end
    if operator == ir_binary_remainder() then
        return "srem"
    end
    if operator == ir_binary_add() then
        if floating then
            return "fadd"
        end
        return "add"
    end
    if operator == ir_binary_subtract() then
        if floating then
            return "fsub"
        end
        return "sub"
    end
    if operator == ir_binary_logical_and() then
        return "and"
    end
    if operator == ir_binary_logical_or() then
        return "or"
    end
    if floating then
        if operator == ir_binary_less_than() then
            return "fcmp olt"
        end
        if operator == ir_binary_less_than_or_equal() then
            return "fcmp ole"
        end
        if operator == ir_binary_greater_than() then
            return "fcmp ogt"
        end
        if operator == ir_binary_greater_than_or_equal() then
            return "fcmp oge"
        end
        if operator == ir_binary_equal() then
            return "fcmp oeq"
        end
        return "fcmp une"
    end
    if operator == ir_binary_less_than() then
        return "icmp slt"
    end
    if operator == ir_binary_less_than_or_equal() then
        return "icmp sle"
    end
    if operator == ir_binary_greater_than() then
        return "icmp sgt"
    end
    if operator == ir_binary_greater_than_or_equal() then
        return "icmp sge"
    end
    if operator == ir_binary_equal() then
        return "icmp eq"
    end
    return "icmp ne"
end

fn emit_llvm_call(context: pointer<LlvmGenerationContext>, instruction: pointer<IrInstruction>, block_id: int, instruction_id: int) -> void
    @mut let arguments: string = ""
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(instruction->operands) do
        if index > 0 then
            arguments = arguments + ", "
        end
        arguments = arguments + llvm_operand(context, vector_get<pointer<IrValue>>(instruction->operands, index), block_id, instruction_id, index)
        index = index + 1
    end
    @mut let line: string = "  "
    if instruction->result != null then
        line = line + llvm_instruction_result(instruction) + " = "
    end
    line = line + "call " + llvm_type(context, instruction->target->return_type) + " " + llvm_function_symbol(context, instruction->target->id) + "(" + arguments + ")"
    llvm_line(context, line)
    return
end

fn emit_llvm_struct_construct(context: pointer<LlvmGenerationContext>, instruction: pointer<IrInstruction>, block_id: int, instruction_id: int) -> void
    let count: int = vector_length<pointer<IrValue>>(instruction->operands)
    let type: string = llvm_type(context, instruction->result->type)
    if count == 0 then
        llvm_line(context, "  " + llvm_instruction_result(instruction) + " = freeze " + type + " poison")
        return
    end
    @mut let index: int = 0
    while index < count do
        @mut let destination: string = llvm_instruction_result(instruction)
        if index < count - 1 then
            destination = destination + ".field" + format_ir_int(index)
        end
        @mut let aggregate: string = "poison"
        if index > 0 then
            aggregate = llvm_instruction_result(instruction) + ".field" + format_ir_int(index - 1)
        end
        llvm_line(context, "  " + destination + " = insertvalue " + type + " " + aggregate + ", " + llvm_operand(context, vector_get<pointer<IrValue>>(instruction->operands, index), block_id, instruction_id, index) + ", " + format_ir_int(index))
        index = index + 1
    end
    return
end

fn emit_llvm_struct_field_store(context: pointer<LlvmGenerationContext>, instruction: pointer<IrInstruction>, block_id: int, instruction_id: int) -> void
    let local_type: string = llvm_type(context, instruction->local->type)
    let temporary: string = "%temp.b" + format_ir_int(block_id) + ".i" + format_ir_int(instruction_id)
    llvm_line(context, "  " + temporary + ".aggregate = load " + local_type + ", ptr %local" + format_ir_int(instruction->local->id))
    @mut let indices: string = ""
    @mut let index: int = 0
    while index < vector_length<pointer<IrStructField>>(instruction->field_path) do
        indices = indices + ", " + format_ir_int(vector_get<pointer<IrStructField>>(instruction->field_path, index)->index)
        index = index + 1
    end
    let value: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
    llvm_line(context, "  " + temporary + ".updated = insertvalue " + local_type + " " + temporary + ".aggregate, " + llvm_operand(context, value, block_id, instruction_id, 0) + indices)
    llvm_line(context, "  store " + local_type + " " + temporary + ".updated, ptr %local" + format_ir_int(instruction->local->id))
    return
end

fn emit_llvm_pointer_index(context: pointer<LlvmGenerationContext>, instruction: pointer<IrInstruction>, block_id: int, instruction_id: int) -> void
    let pointer_value: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
    let index: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 1)
    let element_type: string = llvm_type(context, pointer_value->type->element_type)
    let temporary: string = "%temp.b" + format_ir_int(block_id) + ".i" + format_ir_int(instruction_id) + ".address"
    llvm_line(context, "  " + temporary + " = getelementptr " + element_type + ", ptr " + llvm_value(pointer_value, block_id, instruction_id, 0) + ", i64 " + llvm_value(index, block_id, instruction_id, 1))
    if instruction->kind == ir_instruction_pointer_index_load() then
        llvm_line(context, "  " + llvm_instruction_result(instruction) + " = load " + element_type + ", ptr " + temporary)
    else
        let value: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 2)
        llvm_line(context, "  store " + llvm_operand(context, value, block_id, instruction_id, 2) + ", ptr " + temporary)
    end
    return
end

fn emit_llvm_pointer_field(context: pointer<LlvmGenerationContext>, instruction: pointer<IrInstruction>, block_id: int, instruction_id: int) -> void
    let pointer_value: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 0)
    let struct_type: string = llvm_type(context, pointer_value->type->element_type)
    let temporary: string = "%temp.b" + format_ir_int(block_id) + ".i" + format_ir_int(instruction_id) + ".field"
    llvm_line(context, "  " + temporary + " = getelementptr " + struct_type + ", ptr " + llvm_value(pointer_value, block_id, instruction_id, 0) + ", i32 0, i32 " + format_ir_int(instruction->field->index))
    if instruction->kind == ir_instruction_pointer_field_load() then
        llvm_line(context, "  " + llvm_instruction_result(instruction) + " = load " + llvm_type(context, instruction->field->type) + ", ptr " + temporary)
    else
        let value: pointer<IrValue> = vector_get<pointer<IrValue>>(instruction->operands, 1)
        llvm_line(context, "  store " + llvm_operand(context, value, block_id, instruction_id, 1) + ", ptr " + temporary)
    end
    return
end

fn emit_llvm_terminator(context: pointer<LlvmGenerationContext>, terminator: pointer<IrTerminator>, block_id: int) -> void
    if terminator->kind == ir_terminator_return() then
        if terminator->value == null then
            llvm_line(context, "  ret void")
        else
            llvm_line(context, "  ret " + llvm_operand(context, terminator->value, block_id, -1, 0))
        end
        return
    end
    if terminator->kind == ir_terminator_branch() then
        llvm_line(context, "  br label %block" + format_ir_int(terminator->true_target->id))
        return
    end
    if terminator->kind == ir_terminator_conditional_branch() then
        llvm_line(context, "  br i1 " + llvm_value(terminator->condition, block_id, -1, 0) + ", label %block" + format_ir_int(terminator->true_target->id) + ", label %block" + format_ir_int(terminator->false_target->id))
        return
    end
    llvm_fail(context, "LLVM generation encountered an unsupported terminator")
    return
end

fn emit_llvm_entry(context: pointer<LlvmGenerationContext>) -> void
    let entry: pointer<IrFunction> = context->program->entry_function
    if vector_length<pointer<IrParameter>>(entry->parameters) != 0 then
        llvm_fail(context, "LLVM native entry function must not accept parameters")
        return
    end
    llvm_line(context, "define i32 @main() {")
    llvm_line(context, "entry:")
    llvm_line(context, "  %sol.status64 = call i64 " + llvm_function_symbol(context, entry->id) + "()")
    llvm_line(context, "  %sol.status = trunc i64 %sol.status64 to i32")
    llvm_line(context, "  ret i32 %sol.status")
    llvm_line(context, "}")
    return
end

fn llvm_comment_text(value: string) -> string
    @mut let result: string = ""
    @mut let index: int = 0
    while index < strings::length(value) do
        let scalar: char = value[index]
        if scalar == '\n' || scalar == '\r' then
            result = result + " "
        else
            result = result + strings::slice(value, index, index + 1)
        end
        index = index + 1
    end
    return result
end
