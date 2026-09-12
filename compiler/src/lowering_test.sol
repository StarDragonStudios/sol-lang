inject namespace std.console as console
inject namespace std.string as strings
inject std.collections.vector
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens
inject semantics.model
inject semantics.analyzer
inject ir.model
inject ir.formatter
inject lowering.model only IrLoweringResult
inject lowering.program
inject backend.llvm

struct ParsedLoweringSource
    lexical: LexResult
    parsed: ParseResult
end

@init
fn launch() -> int
    let storage: int = test_object_storage_lowering()
    if storage != 0 then
        console::print_line("self-host lowering test failed: object storage")
        return 200 + storage
    end
    let complete: int = test_complete_lowering()
    if complete != 0 then
        console::print_line("self-host lowering test failed: complete lowering")
        return 10 + complete
    end
    let modules: int = test_module_lowering()
    if modules != 0 then
        console::print_line("self-host lowering test failed: module lowering")
        return 30 + modules
    end
    let operations: int = test_pointer_and_primitive_lowering()
    if operations != 0 then
        console::print_line("self-host lowering test failed: pointer and primitive lowering")
        return 50 + operations
    end
    let objects: int = test_object_lowering()
    if objects != 0 then
        console::print_line("self-host lowering test failed: object lowering")
        return 60 + objects
    end
    let rejection: int = test_lowering_rejection()
    if rejection != 0 then
        console::print_line("self-host lowering test failed: rejection")
        return 70 + rejection
    end
    return 0
end

fn test_object_storage_lowering() -> int
    let source: ParsedLoweringSource = parse_lowering_source(
        "struct Box<T>\n    value: T\nend\nstruct Link\n    item: pointer<Derived>\nend\nclass Derived << Base\n    box: Box<int>\n    child: Child\n    @constructor\n    fn build() -> void\n        base()\n        this.box = Box<int> { value: 1 }\n        this.child = Child()\n        this.box.value = 2\n    end\n    @override\n    fn count() -> int\n        return base.count() + this.box.value\n    end\nend\nclass Base\n    @constructor\n    fn build() -> void\n        let initial: int = this.count()\n    end\n    fn count() -> int\n        return 1\n    end\nend\nclass Child\n    @constructor\n    fn build() -> void\n        return\n    end\nend\nfn use() -> void\n    @mut let item: Derived = Derived()\n    item.box.value = 3\n    item.child = Child()\n    item = Derived()\n    let heap: pointer<Derived> = new Derived()\n    heap->box.value = 4\n    delete heap\n    return\nend"
    )
    if !lowering_source_valid(source) then
        destroy_lowering_source(source)
        return 1
    end
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("storage", source.parsed.root))
    let semantic: pointer<SemanticProgram> = analyze_library_modules(modules)
    destroy_vector<SourceModule>(modules)
    if !semantic_program_successful(semantic) then
        @mut let index: int = 0
        while index < semantic_program_diagnostic_count(semantic) do
            console::print_line(semantic_program_diagnostic(semantic, index).diagnostic.message)
            index = index + 1
        end
        destroy_semantic_program(semantic)
        destroy_lowering_source(source)
        return 2
    end
    let lowered: IrLoweringResult = lower_semantic_program(semantic)
    @mut let failure: int = 0
    if lowered.program == null then
        console::print_line(lowered.error)
        failure = 3
    else
        let formatted: string = format_ir_program(lowered.program)
        if !lowering_text_contains(formatted, "object_reconstruct") || !lowering_text_contains(formatted, "object_field_construct") || !lowering_text_contains(formatted, "direct_call") then
            failure = 4
        end
        destroy_ir_program(lowered.program)
    end
    destroy_semantic_program(semantic)
    destroy_lowering_source(source)
    return failure
end


fn test_object_lowering() -> int
    let source: ParsedLoweringSource = parse_lowering_source(
        "@interface\nclass Named\n    @fn name() -> string\n    @fn echo<T>(value: T) -> T\nend\nclass Person < Named\n    label: string\n    @constructor\n    fn build(label: string) -> void\n        this.label = label\n    end\n    @override\n    fn name() -> string\n        return this.label\n    end\n    @override\n    fn echo<T>(value: T) -> T\n        return value\n    end\nend\nfn use() -> void\n    let direct: Person = Person(\"direct\")\n    let first: string = direct.name()\n    let heap: pointer<Person> = new Person(\"heap\")\n    let named: pointer<Named> = heap\n    let second: string = named->name()\n    let echoed: int = named->echo<int>(7)\n    delete heap\n    return\nend\nfn receiver() -> pointer<Person>\n    return new Person(\"ordered\")\nend\nfn argument() -> int\n    return 7\nend\nfn ordered() -> int\n    return receiver()->echo<int>(argument())\nend"
    )
    if !lowering_source_valid(source) then
        destroy_lowering_source(source)
        return 1
    end
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("objects", source.parsed.root))
    let semantic: pointer<SemanticProgram> = analyze_library_modules(modules)
    destroy_vector<SourceModule>(modules)
    @mut let invalid: boolean = semantic == null
    if !invalid then
        invalid = !semantic_program_successful(semantic)
    end
    if invalid then
        destroy_semantic_program(semantic)
        destroy_lowering_source(source)
        return 2
    end
    let lowered: IrLoweringResult = lower_semantic_program(semantic)
    @mut let failure: int = 0
    if lowered.program == null then
        console::print_line(lowered.error)
        failure = 3
    else
        let module: pointer<IrModule> = vector_get<pointer<IrModule>>(lowered.program->modules, 0)
        let formatted: string = format_ir_program(lowered.program)
        let backend: LlvmGenerationResult = generate_llvm_ir(lowered.program, "objects")
        let repeated: IrLoweringResult = lower_semantic_program(semantic)
        if repeated.program == null then
            failure = 10
        else
            if format_ir_program(repeated.program) != formatted then
                failure = 10
            end
            destroy_ir_program(repeated.program)
        end
        @mut let function_index: int = 0
        @mut let ordered_found: boolean = false
        while function_index < vector_length<pointer<IrFunction>>(module->functions) do
            let function: pointer<IrFunction> = vector_get<pointer<IrFunction>>(module->functions, function_index)
            if function->name == "ordered" then
                ordered_found = true
                let block: pointer<IrBasicBlock> = vector_get<pointer<IrBasicBlock>>(function->blocks, 0)
                if vector_length<pointer<IrInstruction>>(block->instructions) != 3 then
                    failure = 7
                else
                    let first: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, 0)
                    let second: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, 1)
                    let call: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, 2)
                    if first->target->name != "receiver" || second->target->name != "argument" || vector_get<pointer<IrValue>>(call->operands, 0) != first->result || vector_get<pointer<IrValue>>(call->operands, 1) != second->result then
                        failure = 8
                    end
                end
            end
            function_index = function_index + 1
        end
        if !ordered_found then
            failure = 9
        end
        if llvm_generation_succeeded(backend) || backend.text != "" then
            failure = 6
        end
        if vector_length<pointer<IrType>>(module->objects) != 2 || !lowering_text_contains(formatted, "interface objects::Named") || !lowering_text_contains(formatted, "class objects::Person implements objects::Named") then
            failure = 4
        end
        if failure == 0 && (!lowering_text_contains(formatted, "object_initialize") || !lowering_text_contains(formatted, "object_address") || !lowering_text_contains(formatted, "virtual_call") || !lowering_text_contains(formatted, "interface_call") || !lowering_text_contains(formatted, "object_new") || !lowering_text_contains(formatted, "object_view") || !lowering_text_contains(formatted, "object_delete")) then
            failure = 5
        end
        destroy_ir_program(lowered.program)
    end
    destroy_semantic_program(semantic)
    destroy_lowering_source(source)
    return failure
end

fn parse_lowering_source(source: string) -> ParsedLoweringSource
    let lexical: LexResult = scan_source(source)
    @mut let parsed: ParseResult = parse_tokens(null)
    if lexical.successful then
        parsed = parse_tokens(lexical.tokens)
    end
    return ParsedLoweringSource { lexical: lexical, parsed: parsed }
end

fn lowering_source_valid(source: ParsedLoweringSource) -> boolean
    return source.lexical.successful && source.parsed.successful && source.parsed.root != null
end

fn destroy_lowering_source(source: ParsedLoweringSource) -> void
    destroy_parse_result(source.parsed)
    destroy_lex_result(source.lexical)
    return
end

fn test_complete_lowering() -> int
    let source: ParsedLoweringSource = parse_lowering_source(
        "struct Pair<T>\n    first: T\n    second: char\nend\nstruct Wrapper<T>\n    pair: Pair<T>\nend\nfn id<T>(value: T) -> T\n    return value\nend\nfn add(left: int, right: int) -> int\n    return left + right\nend\n@init\nfn launch() -> int\n    @mut let pair: Pair<int> = Pair<int> { first: 1, second: 'a' }\n    pair.first = id<int>(2)\n    let pointer: pointer<Pair<int>> = null\n    if pointer == null then\n        pair.second = id<char>(\"Sol\"[0])\n    else\n        pair.first = pointer->first\n    end\n    @mut let wrapper: Wrapper<int> = Wrapper<int> { pair: pair }\n    wrapper.pair.first = 3\n    @mut let index: int = 0\n    while index < 2 do\n        index = index + 1\n    end\n    return add(wrapper.pair.first, index)\nend"
    )
    if !lowering_source_valid(source) then
        destroy_lowering_source(source)
        return 1
    end
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("application", source.parsed.root))
    let semantic: pointer<SemanticProgram> = analyze_executable_program(modules)
    destroy_vector<SourceModule>(modules)
    @mut let invalid: boolean = semantic == null
    if !invalid then
        invalid = !semantic_program_successful(semantic)
    end
    if invalid then
        @mut let diagnostic_index: int = 0
        while semantic != null && diagnostic_index < semantic_program_diagnostic_count(semantic) do
            console::print_line(semantic_program_diagnostic(semantic, diagnostic_index).diagnostic.code + ": " + semantic_program_diagnostic(semantic, diagnostic_index).diagnostic.message)
            diagnostic_index = diagnostic_index + 1
        end
        destroy_semantic_program(semantic)
        destroy_lowering_source(source)
        return 2
    end
    let lowered: IrLoweringResult = lower_semantic_program(semantic)
    @mut let failure: int = 0
    if lowered.program == null then
        console::print_line(lowered.error)
        failure = 3
    else
        if !lowered.program->sealed || lowered.program->entry_function == null || lowered.program->entry_function->name != "launch" || vector_length<pointer<IrModule>>(lowered.program->modules) != 1 then
            failure = 4
        end
        if failure == 0 && (vector_length<pointer<IrType>>(vector_get<pointer<IrModule>>(lowered.program->modules, 0)->structs) != 2 || vector_length<pointer<IrFunction>>(vector_get<pointer<IrModule>>(lowered.program->modules, 0)->functions) != 4) then
            failure = 5
        end
        let formatted: string = format_ir_program(lowered.program)
        if failure == 0 && (!lowering_text_contains(formatted, "struct application::Pair<int>") || !lowering_text_contains(formatted, "struct application::Wrapper<int>") || !lowering_text_contains(formatted, "store_field local2.pair.first") || !lowering_text_contains(formatted, "call @function2 id$int") || !lowering_text_contains(formatted, "call @function3 id$char") || !lowering_text_contains(formatted, "pointer_field_load") || !lowering_text_contains(formatted, "branch_if")) then
            failure = 6
        end
        let generated: LlvmGenerationResult = generate_llvm_ir(lowered.program, "lowering-complete")
        if failure == 0 && !llvm_generation_succeeded(generated) then
            console::print_line(generated.error)
            failure = 8
        end
        let repeated: IrLoweringResult = lower_semantic_program(semantic)
        if failure == 0 then
            if repeated.program == null then
                failure = 7
            else
                if format_ir_program(repeated.program) != formatted then
                    failure = 7
                end
            end
        end
        if repeated.program != null then
            destroy_ir_program(repeated.program)
        end
        destroy_ir_program(lowered.program)
    end
    destroy_semantic_program(semantic)
    destroy_lowering_source(source)
    return failure
end

fn test_pointer_and_primitive_lowering() -> int
    let source: ParsedLoweringSource = parse_lowering_source(
        "struct Cell\n    value: int\nend\n@native\n@fn external(value: int) -> void\nfn touch(data: pointer<int>, cell: pointer<Cell>) -> void\n    data[0] = +data[0]\n    cell->value = -data[0]\n    external(data[0])\n    return\nend\nfn calculate(data: pointer<int>, cell: pointer<Cell>) -> float\n    touch(data, cell)\n    let truth: boolean = !(false || true) && (1 < 2)\n    let decimal: float = 1.5 + 2.5\n    if truth then\n        return decimal\n    else\n        return 0.0\n    end\nend"
    )
    if !lowering_source_valid(source) then
        destroy_lowering_source(source)
        return 1
    end
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("operations", source.parsed.root))
    let semantic: pointer<SemanticProgram> = analyze_library_modules(modules)
    destroy_vector<SourceModule>(modules)
    @mut let invalid: boolean = semantic == null
    if !invalid then
        invalid = !semantic_program_successful(semantic)
    end
    if invalid then
        @mut let diagnostic_index: int = 0
        while semantic != null && diagnostic_index < semantic_program_diagnostic_count(semantic) do
            console::print_line(semantic_program_diagnostic(semantic, diagnostic_index).diagnostic.code + ": " + semantic_program_diagnostic(semantic, diagnostic_index).diagnostic.message)
            diagnostic_index = diagnostic_index + 1
        end
        destroy_semantic_program(semantic)
        destroy_lowering_source(source)
        return 2
    end
    let lowered: IrLoweringResult = lower_semantic_program(semantic)
    @mut let failure: int = 0
    if lowered.program == null then
        console::print_line(lowered.error)
        failure = 3
    else
        let formatted: string = format_ir_program(lowered.program)
        if lowered.program->entry_function != null || !lowering_text_contains(formatted, "pointer_index_load") || !lowering_text_contains(formatted, "pointer_index_store") || !lowering_text_contains(formatted, "pointer_field_store") then
            failure = 4
        end
        if failure == 0 && (!lowering_text_contains(formatted, "declare @function0 external") || !lowering_text_contains(formatted, "logical_not") || !lowering_text_contains(formatted, "logical_or") || !lowering_text_contains(formatted, "logical_and") || !lowering_text_contains(formatted, "const 1.5") || !lowering_text_contains(formatted, "call @function1 touch")) then
            failure = 5
        end
        let generated: LlvmGenerationResult = generate_llvm_ir(lowered.program, "lowering-operations")
        if failure == 0 && !llvm_generation_succeeded(generated) then
            console::print_line(generated.error)
            failure = 6
        end
        if failure == 0 && lowering_text_contains(generated.text, "define i32 @main()") then
            failure = 7
        end
        destroy_ir_program(lowered.program)
    end
    destroy_semantic_program(semantic)
    destroy_lowering_source(source)
    return failure
end

fn test_module_lowering() -> int
    let base: ParsedLoweringSource = parse_lowering_source("fn value() -> int\n    return 7\nend")
    let app: ParsedLoweringSource = parse_lowering_source("inject namespace base as b\n@init\nfn start() -> int\n    return b::value()\nend")
    if !lowering_source_valid(base) || !lowering_source_valid(app) then
        destroy_lowering_source(app)
        destroy_lowering_source(base)
        return 1
    end
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("application", app.parsed.root))
    vector_push<SourceModule>(modules, source_module("base", base.parsed.root))
    let semantic: pointer<SemanticProgram> = analyze_executable_program(modules)
    destroy_vector<SourceModule>(modules)
    let lowered: IrLoweringResult = lower_semantic_program(semantic)
    @mut let failure: int = 0
    if lowered.program == null then
        console::print_line(lowered.error)
        failure = 2
    else
        if vector_length<pointer<IrModule>>(lowered.program->modules) != 2 || lowered.program->entry_module->name != "application" || vector_get<pointer<IrModule>>(lowered.program->modules, 1)->name != "base" then
            failure = 3
        end
        let generated: LlvmGenerationResult = generate_llvm_ir(lowered.program, "lowering-modules")
        if failure == 0 && (!llvm_generation_succeeded(generated) || !lowering_text_contains(generated.text, "call i64 @sol.function0()")) then
            failure = 4
        end
        destroy_ir_program(lowered.program)
    end
    destroy_semantic_program(semantic)
    destroy_lowering_source(app)
    destroy_lowering_source(base)
    return failure
end

fn test_lowering_rejection() -> int
    let incomplete: pointer<SemanticProgram> = create_semantic_program(false)
    let incomplete_result: IrLoweringResult = lower_semantic_program(incomplete)
    @mut let failure: int = 0
    if incomplete_result.program != null || incomplete_result.error != "semantic program must be complete before IR lowering" then
        failure = 1
    end
    destroy_semantic_program(incomplete)

    let invalid: ParsedLoweringSource = parse_lowering_source("fn bad() -> int\n    return true\nend")
    if failure == 0 && !lowering_source_valid(invalid) then
        failure = 2
    end
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("invalid", invalid.parsed.root))
    let semantic: pointer<SemanticProgram> = analyze_library_modules(modules)
    destroy_vector<SourceModule>(modules)
    let invalid_result: IrLoweringResult = lower_semantic_program(semantic)
    if failure == 0 && (invalid_result.program != null || invalid_result.error != "semantic program contains diagnostics and cannot be lowered") then
        failure = 3
    end
    destroy_semantic_program(semantic)
    destroy_lowering_source(invalid)
    return failure
end

fn lowering_text_contains(text: string, fragment: string) -> boolean
    if strings::length(fragment) == 0 then
        return true
    end
    if strings::length(fragment) > strings::length(text) then
        return false
    end
    @mut let index: int = 0
    while index + strings::length(fragment) <= strings::length(text) do
        if strings::slice(text, index, index + strings::length(fragment)) == fragment then
            return true
        end
        index = index + 1
    end
    return false
end
