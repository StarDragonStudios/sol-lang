inject namespace std.console as console
inject namespace std.file as files
inject namespace std.memory as memory
inject namespace std.string as strings
inject std.collections.vector
inject frontend.diagnostic only Diagnostic
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens
inject frontend.syntax
inject semantics.model
inject semantics.analyzer only analyze_executable_program
inject lowering.model only IrLoweringResult
inject lowering.program only lower_semantic_program
inject ir.model only destroy_ir_program
inject ir.formatter only format_ir_int
inject backend.native

struct CompilerRequest
    source_path: string
    module_root: string
    module_name: string
    standard_library_root: string
    llvm_output: string
    literal_output: string
end

struct CompilerSource
    module_name: string
    path: string
    lexical: LexResult
    parsed: ParseResult
end

struct CompilerDiscovery
    sources: pointer<Vector<pointer<CompilerSource>>>
    failure: int
end

fn run_compiler_request() -> int
    let request_path: string = console::read_line()
    if request_path == "" || !files::exists(request_path) then
        console::print_line("input error: self-host request file does not exist")
        return 3
    end
    let request_fields: pointer<Vector<string>> = compiler_request_fields(files::read_text(request_path))
    if request_fields == null then
        console::print_line("command-line error: malformed self-host request")
        return 2
    end
    let request: CompilerRequest = compiler_request_from_fields(request_fields)
    destroy_vector<string>(request_fields)
    let status: int = compile_request(request)
    return status
end

fn compiler_request_fields(text: string) -> pointer<Vector<string>>
    let fields: pointer<Vector<string>> = create_vector<string>()
    @mut let current: string = ""
    @mut let index: int = 0
    while index < strings::length(text) do
        let scalar: char = text[index]
        if scalar == '\n' then
            if strings::length(current) > 0 && current[strings::length(current) - 1] == '\r' then
                current = strings::slice(current, 0, strings::length(current) - 1)
            end
            vector_push<string>(fields, current)
            current = ""
        else
            current = current + strings::slice(text, index, index + 1)
        end
        index = index + 1
    end
    if current != "" then
        vector_push<string>(fields, current)
    end
    if vector_length<string>(fields) != 7 || vector_get<string>(fields, 0) != "SOL-SELFHOST-REQUEST-1" then
        destroy_vector<string>(fields)
        return null
    end
    index = 1
    while index < 7 do
        if vector_get<string>(fields, index) == "" then
            destroy_vector<string>(fields)
            return null
        end
        index = index + 1
    end
    return fields
end

fn compiler_request_from_fields(fields: pointer<Vector<string>>) -> CompilerRequest
    return CompilerRequest {
        source_path: vector_get<string>(fields, 1),
        module_root: vector_get<string>(fields, 2),
        module_name: vector_get<string>(fields, 3),
        standard_library_root: vector_get<string>(fields, 4),
        llvm_output: vector_get<string>(fields, 5),
        literal_output: vector_get<string>(fields, 6)
    }
end

fn compile_request(request: CompilerRequest) -> int
    let discovery: pointer<CompilerDiscovery> = create_compiler_discovery()
    if discovery == null then
        console::print_line("input error: source discovery allocation failed")
        return 3
    end
    discover_compiler_module(discovery, request.module_name, request.source_path, request.module_root, request.standard_library_root, true)
    if discovery->failure != 0 then
        let failure: int = discovery->failure
        destroy_compiler_discovery(discovery)
        return failure
    end
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    @mut let index: int = 0
    while index < vector_length<pointer<CompilerSource>>(discovery->sources) do
        let source: pointer<CompilerSource> = vector_get<pointer<CompilerSource>>(discovery->sources, index)
        vector_push<SourceModule>(modules, source_module(source->module_name, source->parsed.root))
        index = index + 1
    end
    let semantic: pointer<SemanticProgram> = analyze_executable_program(modules)
    destroy_vector<SourceModule>(modules)
    if semantic == null then
        console::print_line("input error: semantic analysis allocation failed")
        destroy_compiler_discovery(discovery)
        return 3
    end
    if !semantic_program_successful(semantic) then
        print_semantic_diagnostics(discovery, semantic, request.source_path)
        destroy_semantic_program(semantic)
        destroy_compiler_discovery(discovery)
        return 4
    end
    let lowered: IrLoweringResult = lower_semantic_program(semantic)
    if lowered.program == null then
        console::print_line("lowering error: " + lowered.error)
        destroy_semantic_program(semantic)
        destroy_compiler_discovery(discovery)
        return 5
    end
    let artifacts: NativeArtifactResult = generate_native_artifacts(lowered.program, request.module_name)
    if !native_artifact_succeeded(artifacts) then
        console::print_line("backend error: " + artifacts.error)
        destroy_ir_program(lowered.program)
        destroy_semantic_program(semantic)
        destroy_compiler_discovery(discovery)
        return 6
    end
    @mut let status: int = 0
    if !files::write_text(request.literal_output, artifacts.literals_c) then
        console::print_line("toolchain error: cannot write generated literal artifact")
        status = 7
    end
    if status == 0 && !files::write_text(request.llvm_output, artifacts.llvm_ir) then
        console::print_line("toolchain error: cannot write generated LLVM artifact")
        status = 7
    end
    destroy_ir_program(lowered.program)
    destroy_semantic_program(semantic)
    destroy_compiler_discovery(discovery)
    return status
end

fn create_compiler_discovery() -> pointer<CompilerDiscovery>
    let discovery: pointer<CompilerDiscovery> = memory::allocate<CompilerDiscovery>(1)
    if discovery == null then
        return null
    end
    discovery->sources = create_vector<pointer<CompilerSource>>()
    discovery->failure = 0
    return discovery
end

fn destroy_compiler_discovery(discovery: pointer<CompilerDiscovery>) -> void
    if discovery == null then
        return
    end
    @mut let index: int = vector_length<pointer<CompilerSource>>(discovery->sources)
    while index > 0 do
        index = index - 1
        let source: pointer<CompilerSource> = vector_get<pointer<CompilerSource>>(discovery->sources, index)
        destroy_parse_result(source->parsed)
        destroy_lex_result(source->lexical)
        memory::free<CompilerSource>(source)
    end
    destroy_vector<pointer<CompilerSource>>(discovery->sources)
    memory::free<CompilerDiscovery>(discovery)
    return
end

fn discover_compiler_module(discovery: pointer<CompilerDiscovery>, module_name: string, path: string, module_root: string, standard_library_root: string, required: boolean) -> void
    if discovery->failure != 0 || compiler_source_for(discovery, module_name) != null then
        return
    end
    @mut let source_path: string = compiler_standard_path(standard_library_root, module_name)
    if source_path == "" then
        source_path = path
        if !required then
            source_path = module_root + "/" + compiler_module_relative_path(module_name)
        end
    end
    if !files::exists(source_path) then
        if required then
            console::print_line("input error: Sol source file '" + source_path + "' does not exist or is not a regular file")
            discovery->failure = 3
        end
        return
    end
    let lexical: LexResult = scan_source(files::read_text(source_path))
    @mut let parsed: ParseResult = parse_tokens(null)
    if !lexical.successful then
        print_source_diagnostic(source_path, lexical.diagnostic)
        destroy_parse_result(parsed)
        destroy_lex_result(lexical)
        discovery->failure = 4
        return
    end
    parsed = parse_tokens(lexical.tokens)
    if !parsed.successful then
        print_source_diagnostic(source_path, parsed.diagnostic)
        destroy_parse_result(parsed)
        destroy_lex_result(lexical)
        discovery->failure = 4
        return
    end
    let source: pointer<CompilerSource> = memory::allocate<CompilerSource>(1)
    if source == null then
        destroy_parse_result(parsed)
        destroy_lex_result(lexical)
        console::print_line("input error: source discovery allocation failed")
        discovery->failure = 3
        return
    end
    source->module_name = module_name
    source->path = source_path
    source->lexical = lexical
    source->parsed = parsed
    vector_push<pointer<CompilerSource>>(discovery->sources, source)
    @mut let index: int = 0
    while index < syntax_child_count(parsed.root) do
        let declaration: pointer<SyntaxNode> = syntax_child(parsed.root, index)
        if declaration->kind == syntax_kind_injection_declaration() then
            let injected: string = syntax_child(declaration, 0)->text
            discover_compiler_module(discovery, injected, "", module_root, standard_library_root, false)
        end
        index = index + 1
    end
    return
end

fn compiler_source_for(discovery: pointer<CompilerDiscovery>, module_name: string) -> pointer<CompilerSource>
    @mut let index: int = 0
    while index < vector_length<pointer<CompilerSource>>(discovery->sources) do
        let source: pointer<CompilerSource> = vector_get<pointer<CompilerSource>>(discovery->sources, index)
        if source->module_name == module_name then
            return source
        end
        index = index + 1
    end
    return null
end

fn compiler_standard_path(root: string, module_name: string) -> string
    if module_name == "std.console" then
        return root + "/std/console.sol"
    end
    if module_name == "std.file" then
        return root + "/std/file.sol"
    end
    if module_name == "std.memory" then
        return root + "/std/memory.sol"
    end
    if module_name == "std.string" then
        return root + "/std/string.sol"
    end
    if module_name == "std.collections.vector" then
        return root + "/std/collections/vector.sol"
    end
    return ""
end

fn compiler_module_relative_path(module_name: string) -> string
    @mut let result: string = ""
    @mut let index: int = 0
    while index < strings::length(module_name) do
        if module_name[index] == '.' then
            result = result + "/"
        else
            result = result + strings::slice(module_name, index, index + 1)
        end
        index = index + 1
    end
    return result + ".sol"
end

fn print_semantic_diagnostics(discovery: pointer<CompilerDiscovery>, program: pointer<SemanticProgram>, entry_path: string) -> void
    @mut let index: int = 0
    while index < semantic_program_diagnostic_count(program) do
        let item: SemanticDiagnostic = semantic_program_diagnostic(program, index)
        let source: pointer<CompilerSource> = compiler_source_for(discovery, item.module_name)
        @mut let path: string = entry_path
        if source != null then
            path = source->path
        end
        print_source_diagnostic(path, item.diagnostic)
        index = index + 1
    end
    return
end

fn print_source_diagnostic(path: string, item: Diagnostic) -> void
    console::print_line(path + ":" + format_ir_int(item.span.start.line) + ":" + format_ir_int(item.span.start.column) + ": error [" + item.code + "]: " + item.message)
    return
end
