inject namespace std.console as console
inject namespace std.file as files
inject namespace std.string as strings
inject std.collections.vector
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens
inject semantics.model
inject semantics.analyzer
inject ir.model
inject lowering.model only IrLoweringResult
inject lowering.program
inject backend.native

struct ParsedNativeSource
    lexical: LexResult
    parsed: ParseResult
end

@init
fn launch() -> int
    let invalid: NativeArtifactResult = generate_native_artifacts(null, "invalid")
    let repeated_invalid: NativeArtifactResult = generate_native_artifacts(null, "invalid")
    if native_artifact_succeeded(invalid) || invalid.error == "" || invalid.error != repeated_invalid.error || invalid.llvm_ir != "" || invalid.literals_c != "" then
        return 1
    end
    let application: ParsedNativeSource = parse_native_source(
        "inject namespace std.console as console\ninject namespace std.file as files\ninject namespace std.memory as memory\ninject namespace std.string as strings\ninject std.collections.vector only _vector_fail_bounds\nstruct Pair\n    first: int\n    second: int\nend\n@init\nfn launch() -> int\n    @mut let values: pointer<int> = memory::allocate<int>(1)\n    if values == null then\n        return 1\n    end\n    memory::store<int>(values, 40)\n    values = memory::reallocate<int>(values, 2)\n    if values == null then\n        return 2\n    end\n    memory::store_at<int>(values, 1, 2)\n    let pair: pointer<Pair> = memory::allocate<Pair>(1)\n    if pair == null then\n        memory::free<int>(values)\n        return 3\n    end\n    pair->first = memory::load<int>(values)\n    pair->second = memory::load_at<int>(values, 1)\n    let answer: int = pair->first + pair->second\n    memory::free<Pair>(pair)\n    memory::free<int>(values)\n    let text: string = \"Sol 🐉\" + \"!\"\n    let path: string = \"compiler/build/tests/native-runtime.txt\"\n    if !files::write_text(path, \"Sol\") || !files::append_text(path, \" 🐉!\") || !files::exists(path) then\n        return 4\n    end\n    let loaded: string = files::read_text(path)\n    let input: string = console::read_line()\n    if input == \"vector-failure\" then\n        _vector_fail_bounds()\n        return 6\n    end\n    console::print(\"\")\n    console::print_line(text)\n    if answer == 42 && text == loaded && input == \"input\" && text[4] == '🐉' && strings::length(loaded) == 6 && strings::slice(loaded, 4, 5) == \"🐉\" && strings::substring(loaded, 0, 3) == \"Sol\" then\n        return 0\n    end\n    return 5\nend"
    )
    let console_module: ParsedNativeSource = parse_native_source(
        "@fn print(value: string) -> void\n@fn print_line(value: string) -> void\n@fn read_line() -> string"
    )
    let memory_module: ParsedNativeSource = parse_native_source(
        "@fn allocate<T>(count: int) -> pointer<T>\n@fn reallocate<T>(value: pointer<T>, count: int) -> pointer<T>\n@fn free<T>(value: pointer<T>) -> void\n@fn load<T>(value: pointer<T>) -> T\n@fn store<T>(target: pointer<T>, value: T) -> void\n@fn load_at<T>(value: pointer<T>, index: int) -> T\n@fn store_at<T>(target: pointer<T>, index: int, value: T) -> void"
    )
    let file_module: ParsedNativeSource = parse_native_source(
        "@fn exists(path: string) -> boolean\n@fn read_text(path: string) -> string\n@fn write_text(path: string, content: string) -> boolean\n@fn append_text(path: string, content: string) -> boolean"
    )
    let string_module: ParsedNativeSource = parse_native_source(
        "@fn length(value: string) -> int\n@fn slice(value: string, start: int, end_index: int) -> string\n@fn substring(value: string, start: int, count: int) -> string"
    )
    let vector_module: ParsedNativeSource = parse_native_source(
        "@fn _vector_fail_allocation() -> void\n@fn _vector_fail_bounds() -> void\n@fn _vector_fail_capacity() -> void\n@fn _vector_fail_empty_pop() -> void"
    )
    if !native_source_valid(application) || !native_source_valid(console_module) || !native_source_valid(memory_module) || !native_source_valid(file_module) || !native_source_valid(string_module) || !native_source_valid(vector_module) then
        destroy_native_source(vector_module)
        destroy_native_source(string_module)
        destroy_native_source(file_module)
        destroy_native_source(memory_module)
        destroy_native_source(console_module)
        destroy_native_source(application)
        return 1
    end

    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("application", application.parsed.root))
    vector_push<SourceModule>(modules, source_module("std.console", console_module.parsed.root))
    vector_push<SourceModule>(modules, source_module("std.file", file_module.parsed.root))
    vector_push<SourceModule>(modules, source_module("std.memory", memory_module.parsed.root))
    vector_push<SourceModule>(modules, source_module("std.string", string_module.parsed.root))
    vector_push<SourceModule>(modules, source_module("std.collections.vector", vector_module.parsed.root))
    let semantic: pointer<SemanticProgram> = analyze_executable_program(modules)
    destroy_vector<SourceModule>(modules)
    if semantic == null || !semantic_program_successful(semantic) then
        destroy_semantic_program(semantic)
        destroy_native_source(vector_module)
        destroy_native_source(string_module)
        destroy_native_source(file_module)
        destroy_native_source(memory_module)
        destroy_native_source(console_module)
        destroy_native_source(application)
        return 2
    end

    let lowered: IrLoweringResult = lower_semantic_program(semantic)
    if lowered.program == null then
        console::print_line(lowered.error)
        destroy_semantic_program(semantic)
        destroy_native_source(vector_module)
        destroy_native_source(string_module)
        destroy_native_source(file_module)
        destroy_native_source(memory_module)
        destroy_native_source(console_module)
        destroy_native_source(application)
        return 3
    end
    let artifacts: NativeArtifactResult = generate_native_artifacts(lowered.program, "native-artifact-fixture")
    let repeated: NativeArtifactResult = generate_native_artifacts(lowered.program, "native-artifact-fixture")
    @mut let failure: int = 0
    if !native_artifact_succeeded(artifacts) then
        console::print_line(artifacts.error)
        failure = 4
    else
        if artifacts.llvm_ir != repeated.llvm_ir || artifacts.literals_c != repeated.literals_c then
            failure = 5
        end
        if failure == 0 && (!native_text_contains(artifacts.literals_c, "sizeof(") || !native_text_contains(artifacts.literals_c, "Sol 🐉")) then
            failure = 6
        end
        if !files::write_text("compiler/build/tests/native_fixture.ll", artifacts.llvm_ir) then
            failure = 7
        end
        if failure == 0 && !files::write_text("compiler/build/tests/native_literals.c", artifacts.literals_c) then
            failure = 8
        end
    end

    destroy_ir_program(lowered.program)
    destroy_semantic_program(semantic)
    destroy_native_source(vector_module)
    destroy_native_source(string_module)
    destroy_native_source(file_module)
    destroy_native_source(memory_module)
    destroy_native_source(console_module)
    destroy_native_source(application)
    return failure
end

fn native_text_contains(text: string, fragment: string) -> boolean
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

fn parse_native_source(source: string) -> ParsedNativeSource
    let lexical: LexResult = scan_source(source)
    @mut let parsed: ParseResult = parse_tokens(null)
    if lexical.successful then
        parsed = parse_tokens(lexical.tokens)
    end
    return ParsedNativeSource { lexical: lexical, parsed: parsed }
end

fn native_source_valid(source: ParsedNativeSource) -> boolean
    return source.lexical.successful && source.parsed.successful && source.parsed.root != null
end

fn destroy_native_source(source: ParsedNativeSource) -> void
    destroy_parse_result(source.parsed)
    destroy_lex_result(source.lexical)
    return
end
