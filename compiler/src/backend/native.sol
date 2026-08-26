inject namespace std.memory as memory
inject std.collections.vector
inject namespace std.string as strings
inject ir.model
inject ir.formatter only format_ir_int
inject backend.llvm

struct NativeArtifactResult
    llvm_ir: string
    literals_c: string
    error: string
end

struct NativeLiteralEntry
    function_id: int
    value: pointer<IrValue>
end

fn generate_native_artifacts(program: pointer<IrProgram>, module_name: string) -> NativeArtifactResult
    let generated: LlvmGenerationResult = generate_llvm_ir(program, module_name)
    if !llvm_generation_succeeded(generated) then
        return NativeArtifactResult { llvm_ir: "", literals_c: "", error: generated.error }
    end
    let entries: pointer<Vector<pointer<NativeLiteralEntry>>> = collect_native_literals(program)
    if entries == null then
        return NativeArtifactResult { llvm_ir: "", literals_c: "", error: "native literal catalog allocation failed" }
    end
    let literals: string = generate_native_literal_c(entries)
    destroy_native_literals(entries)
    return NativeArtifactResult { llvm_ir: generated.text, literals_c: literals, error: "" }
end

fn native_artifact_succeeded(result: NativeArtifactResult) -> boolean
    return result.error == ""
end

fn collect_native_literals(program: pointer<IrProgram>) -> pointer<Vector<pointer<NativeLiteralEntry>>>
    let entries: pointer<Vector<pointer<NativeLiteralEntry>>> = create_vector<pointer<NativeLiteralEntry>>()
    @mut let module_index: int = 0
    while module_index < vector_length<pointer<IrModule>>(program->modules) do
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(program->modules, module_index)
        @mut let function_index: int = 0
        while function_index < vector_length<pointer<IrFunction>>(module->functions) do
            let function: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, function_index)
            @mut let block_index: int = 0
            while block_index < vector_length<pointer<IrBasicBlock>>(function->blocks) do
                let block: pointer<IrBasicBlock> = vector_get<pointer<IrBasicBlock>>(function->blocks, block_index)
                @mut let instruction_index: int = 0
                while instruction_index < vector_length<pointer<IrInstruction>>(block->instructions) do
                    let instruction: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, instruction_index)
                    @mut let operand_index: int = 0
                    while operand_index < vector_length<pointer<IrValue>>(instruction->operands) do
                        if !add_native_literal(entries, function->id, vector_get<pointer<IrValue>>(instruction->operands, operand_index)) then
                            destroy_native_literals(entries)
                            return null
                        end
                        operand_index = operand_index + 1
                    end
                    instruction_index = instruction_index + 1
                end
                if block->terminator->value != null then
                    if !add_native_literal(entries, function->id, block->terminator->value) then
                        destroy_native_literals(entries)
                        return null
                    end
                end
                if block->terminator->condition != null then
                    if !add_native_literal(entries, function->id, block->terminator->condition) then
                        destroy_native_literals(entries)
                        return null
                    end
                end
                block_index = block_index + 1
            end
            function_index = function_index + 1
        end
        module_index = module_index + 1
    end
    return entries
end

fn add_native_literal(entries: pointer<Vector<pointer<NativeLiteralEntry>>>, function_id: int, value: pointer<IrValue>) -> boolean
    if value->kind != ir_value_char_constant() && value->kind != ir_value_string_constant() then
        return true
    end
    @mut let index: int = 0
    while index < vector_length<pointer<NativeLiteralEntry>>(entries) do
        let existing: pointer<NativeLiteralEntry> = vector_get<pointer<NativeLiteralEntry>>(entries, index)
        if existing->function_id == function_id && existing->value == value then
            return true
        end
        index = index + 1
    end
    let entry: pointer<NativeLiteralEntry> = memory::allocate<NativeLiteralEntry>(1)
    if entry == null then
        return false
    end
    entry->function_id = function_id
    entry->value = value
    vector_push<pointer<NativeLiteralEntry>>(entries, entry)
    return true
end

fn destroy_native_literals(entries: pointer<Vector<pointer<NativeLiteralEntry>>>) -> void
    if entries == null then
        return
    end
    @mut let index: int = vector_length<pointer<NativeLiteralEntry>>(entries)
    while index > 0 do
        index = index - 1
        memory::free<NativeLiteralEntry>(vector_get<pointer<NativeLiteralEntry>>(entries, index))
    end
    destroy_vector<pointer<NativeLiteralEntry>>(entries)
    return
end

fn native_literal_name(entry: pointer<NativeLiteralEntry>) -> string
    return "sol_literal_" + format_ir_int(entry->function_id) + "_" + format_ir_int(entry->value->id)
end

fn generate_native_literal_c(entries: pointer<Vector<pointer<NativeLiteralEntry>>>) -> string
    let lines: pointer<Vector<string>> = create_vector<string>()
    vector_push<string>(lines, "#include \"selfhost.h\"\n\n")
    @mut let index: int = 0
    while index < vector_length<pointer<NativeLiteralEntry>>(entries) do
        let entry: pointer<NativeLiteralEntry> = vector_get<pointer<NativeLiteralEntry>>(entries, index)
        let content: string = native_literal_content(entry->value)
        vector_push<string>(lines, "static const unsigned char " + native_literal_name(entry) + "[] = \"" + native_c_string(content) + "\";\n")
        index = index + 1
    end
    vector_push<string>(lines, "\nint32_t sol_runtime_char_literal(int64_t function_id, int64_t value_id) {\n")
    vector_push<string>(lines, "    (void)function_id;\n    (void)value_id;\n")
    index = 0
    while index < vector_length<pointer<NativeLiteralEntry>>(entries) do
        let entry: pointer<NativeLiteralEntry> = vector_get<pointer<NativeLiteralEntry>>(entries, index)
        if entry->value->kind == ir_value_char_constant() then
            let name: string = native_literal_name(entry)
            vector_push<string>(lines, "    if (function_id == " + format_ir_int(entry->function_id) + " && value_id == " + format_ir_int(entry->value->id) + ") return sol_runtime_decode_literal_scalar(" + name + ", (int64_t)(sizeof(" + name + ") - 1));\n")
        end
        index = index + 1
    end
    vector_push<string>(lines, "    sol_runtime_unknown_literal();\n    return 0;\n}\n\n")
    vector_push<string>(lines, "void sol_runtime_string_literal(SolString *result, int64_t function_id, int64_t value_id) {\n")
    vector_push<string>(lines, "    (void)function_id;\n    (void)value_id;\n")
    index = 0
    while index < vector_length<pointer<NativeLiteralEntry>>(entries) do
        let entry: pointer<NativeLiteralEntry> = vector_get<pointer<NativeLiteralEntry>>(entries, index)
        if entry->value->kind == ir_value_string_constant() then
            let name: string = native_literal_name(entry)
            vector_push<string>(lines, "    if (function_id == " + format_ir_int(entry->function_id) + " && value_id == " + format_ir_int(entry->value->id) + ") { *result = (SolString){" + name + ", (int64_t)(sizeof(" + name + ") - 1), " + format_ir_int(strings::length(entry->value->string_value)) + "}; return; }\n")
        end
        index = index + 1
    end
    vector_push<string>(lines, "    sol_runtime_unknown_literal();\n    *result = (SolString){0, 0, 0};\n}\n")
    let result: string = join_llvm_lines(lines, 0, vector_length<string>(lines))
    destroy_vector<string>(lines)
    return result
end

fn native_literal_content(value: pointer<IrValue>) -> string
    if value->kind == ir_value_string_constant() then
        return value->string_value
    end
    let text: string = value->string_value
    if strings::length(text) < 3 then
        return ""
    end
    let content: string = strings::slice(text, 1, strings::length(text) - 1)
    if content == "\\n" then
        return "\n"
    end
    if content == "\\r" then
        return "\r"
    end
    if content == "\\t" then
        return "\t"
    end
    if content == "\\\\" then
        return "\\"
    end
    if content == "\\\"" || content == "\"" then
        return "\""
    end
    if content == "\\'" then
        return "'"
    end
    return content
end

fn native_c_string(value: string) -> string
    let fragments: pointer<Vector<string>> = create_vector<string>()
    @mut let index: int = 0
    while index < strings::length(value) do
        let scalar: char = value[index]
        if scalar == '\\' then
            vector_push<string>(fragments, "\\\\")
        else
            if scalar == '"' then
                vector_push<string>(fragments, "\\\"")
            else
                if scalar == '\n' then
                    vector_push<string>(fragments, "\\n")
                else
                    if scalar == '\r' then
                        vector_push<string>(fragments, "\\r")
                    else
                        if scalar == '\t' then
                            vector_push<string>(fragments, "\\t")
                        else
                            vector_push<string>(fragments, strings::slice(value, index, index + 1))
                        end
                    end
                end
            end
        end
        index = index + 1
    end
    let result: string = join_llvm_lines(fragments, 0, vector_length<string>(fragments))
    destroy_vector<string>(fragments)
    return result
end
