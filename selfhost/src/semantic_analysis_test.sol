inject namespace std.console as console
inject std.collections.vector
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens
inject frontend.syntax
inject semantics.types
inject semantics.symbol
inject semantics.scope
inject semantics.model
inject semantics.analyzer

struct ParsedSemanticSource
    lexical: LexResult
    parsed: ParseResult
end

@init
fn launch() -> int
    let successful: int = test_successful_program()

    if successful != 0 then
        console::print_line("semantic analysis test failed: successful program")
        return 10 + successful
    end

    let modules: int = test_module_resolution()

    if modules != 0 then
        console::print_line("semantic analysis test failed: module resolution")
        return 30 + modules
    end

    let core: int = test_core_diagnostics()

    if core != 0 then
        console::print_line("semantic analysis test failed: core diagnostics")
        return 50 + core
    end

    let structs: int = test_struct_pointer_and_generic_diagnostics()

    if structs != 0 then
        console::print_line("semantic analysis test failed: structs and generics")
        return 90 + structs
    end

    let program_rules: int = test_program_diagnostics()

    if program_rules != 0 then
        console::print_line("semantic analysis test failed: program diagnostics")
        return 130 + program_rules
    end

    return 0
end

fn parse_semantic_test_source(source: string) -> ParsedSemanticSource
    let lexical: LexResult = scan_source(source)
    @mut let parsed: ParseResult = parse_tokens(null)

    if lexical.successful then
        parsed = parse_tokens(lexical.tokens)
    end

    return ParsedSemanticSource {
        lexical: lexical,
        parsed: parsed
    }
end

fn semantic_test_parse_valid(source: ParsedSemanticSource) -> boolean
    return source.lexical.successful && source.parsed.successful && source.parsed.root != null
end

fn destroy_semantic_test_source(source: ParsedSemanticSource) -> void
    destroy_parse_result(source.parsed)
    destroy_lex_result(source.lexical)
    return
end

fn semantic_test_analyze(
    name: string,
    source: ParsedSemanticSource,
    executable: boolean
) -> pointer<SemanticProgram>
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module(name, source.parsed.root))
    @mut let program: pointer<SemanticProgram> = analyze_library_modules(modules)

    if executable then
        destroy_semantic_program(program)
        program = analyze_executable_program(modules)
    end

    destroy_vector<SourceModule>(modules)
    return program
end

fn semantic_test_has_code(program: pointer<SemanticProgram>, code: string) -> boolean
    @mut let index: int = 0
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        if semantic_program_diagnostic(program, index).diagnostic.code == code then
            return true
        end

        index = index + 1
    end

    return false
end

fn semantic_test_code_count(program: pointer<SemanticProgram>, code: string) -> int
    @mut let found: int = 0
    @mut let index: int = 0
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        if semantic_program_diagnostic(program, index).diagnostic.code == code then
            found = found + 1
        end

        index = index + 1
    end

    return found
end

fn test_successful_program() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "struct Box<T>\n    value: T\nend\nstruct Node\n    value: int\n    next: pointer<Node>\nend\nfn id<T>(value: T) -> T\n    return value\nend\n@init\nfn launch() -> int\n    @mut let box: Box<int> = Box<int> { value: 1 }\n    box.value = id<int>(2)\n    let node: Node = Node { value: box.value, next: null }\n    let pointer: pointer<Node> = null\n    if pointer == null then\n        pointer->value = node.value\n    end\n    let letter: char = \"Sol\"[0]\n    return box.value\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "application",
        source,
        true
    )
    @mut let failure: int = 0

    if program == null || !semantic_program_successful(program) then
        failure = 2
    end

    let root: pointer<SyntaxNode> = source.parsed.root
    let box_declaration: pointer<SyntaxNode> = syntax_child(root, 0)
    let launch_declaration: pointer<SyntaxNode> = syntax_child(root, 3)
    let launch_symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        launch_declaration
    )
    let body: pointer<SyntaxNode> = semantic_function_body(launch_declaration)
    let function_scope: pointer<Scope> = semantic_model_function_scope(
        program,
        launch_declaration
    )
    let box_local: pointer<SyntaxNode> = syntax_child(body, 0)
    let construction: pointer<SyntaxNode> = syntax_child(box_local, 2)
    let assignment: pointer<SyntaxNode> = syntax_child(body, 1)
    let call: pointer<SyntaxNode> = syntax_child(assignment, 1)

    if failure == 0 && (program->entry_function != launch_symbol || program->entry_module->name != "application" || function_scope == null || !function_scope->frozen) then
        failure = 3
    end

    if failure == 0 && (semantic_model_constructed_struct(program, construction)->declaration != box_declaration || semantic_model_type_of_expression(program, construction)->name != "Box<int>") then
        failure = 4
    end

    if failure == 0 && (semantic_model_called_function(program, call)->name != "id" || semantic_model_type_of_expression(program, call)->name != "int" || semantic_model_call_type_argument_count(program, call) != 1 || semantic_model_call_type_argument(program, call, 0)->name != "int") then
        failure = 5
    end

    if failure == 0 && (scope_lookup(function_scope, "box") == null || semantic_program_owned_type_count(program) == 0) then
        failure = 6
    end

    destroy_semantic_program(program)
    destroy_semantic_test_source(source)
    return failure
end

fn test_module_resolution() -> int
    let base: ParsedSemanticSource = parse_semantic_test_source(
        "struct Value\n    number: int\nend\nfn id<T>(value: T) -> T\n    return value\nend\nfn ping() -> int\n    return 7\nend"
    )
    let middle: ParsedSemanticSource = parse_semantic_test_source(
        "inject namespace base as b\nfn relay() -> int\n    return b::ping()\nend"
    )
    let application: ParsedSemanticSource = parse_semantic_test_source(
        "inject base only Value\ninject namespace middle as m\n@init\nfn start() -> int\n    let value: Value = Value { number: 1 }\n    return m::relay()\nend"
    )

    if !semantic_test_parse_valid(base) || !semantic_test_parse_valid(middle) || !semantic_test_parse_valid(application) then
        destroy_semantic_test_source(application)
        destroy_semantic_test_source(middle)
        destroy_semantic_test_source(base)
        return 1
    end

    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("application", application.parsed.root))
    vector_push<SourceModule>(modules, source_module("base", base.parsed.root))
    vector_push<SourceModule>(modules, source_module("middle", middle.parsed.root))
    let program: pointer<SemanticProgram> = analyze_executable_program(modules)
    destroy_vector<SourceModule>(modules)
    @mut let failure: int = 0

    if program == null || !semantic_program_successful(program) then
        failure = 2
    end

    let app_module: pointer<SemanticModule> = semantic_program_module(program, "application")
    let direct: pointer<SyntaxNode> = syntax_child(application.parsed.root, 0)
    let namespace_injection: pointer<SyntaxNode> = syntax_child(application.parsed.root, 1)
    let direct_binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        semantic_binding_kind_direct_injection(),
        direct
    )

    if failure == 0 && (semantic_program_module_count(program) != 3 || program->entry_function->name != "start" || semantic_program_module_of(program, semantic_binding_kind_injected_module(), direct)->name != "base") then
        failure = 3
    end

    if failure == 0 && (semantic_binding_symbol_count(direct_binding) != 2 || semantic_model_direct_injected_symbol_count(program, direct) != 1 || semantic_model_direct_injected_symbol(program, direct, 0)->name != "Value" || semantic_program_module_of(program, semantic_binding_kind_injected_namespace(), namespace_injection)->name != "middle") then
        failure = 4
    end

    if failure == 0 && (scope_lookup_local(app_module->scope, "Value") == null || scope_lookup_local(app_module->scope, "m") == null || scope_lookup_local(app_module->scope, "ping") != null) then
        failure = 5
    end

    destroy_semantic_program(program)
    destroy_semantic_test_source(application)
    destroy_semantic_test_source(middle)
    destroy_semantic_test_source(base)
    return failure
end

fn test_core_diagnostics() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "fn duplicate() -> int\n    return 0\nend\nfn duplicate() -> int\n    return 1\nend\nfn target(value: int) -> int\n    return value\nend\nfn invalid_parameter(value: void) -> void\n    return\nend\nfn invalid(parameter: int) -> int\n    let self: int = self\n    let unknown: Missing = 1\n    let invalid_value: void = 1\n    let mismatch: int = true\n    target = 1\n    let immutable: int = 0\n    immutable = 1\n    @mut let mutable: int = 0\n    mutable = true\n    let unary: int = !1\n    let binary: int = 1 + true\n    if 1 then\n        return\n    end\n    parameter()\n    target()\n    target(true)\n    return true\nend\nfn voider() -> void\n    return 1\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.core",
        source,
        false
    )
    let expected: pointer<Vector<string>> = create_vector<string>()
    vector_push<string>(expected, "SOL-S001")
    vector_push<string>(expected, "SOL-S002")
    vector_push<string>(expected, "SOL-S003")
    vector_push<string>(expected, "SOL-S004")
    vector_push<string>(expected, "SOL-S005")
    vector_push<string>(expected, "SOL-S006")
    vector_push<string>(expected, "SOL-S007")
    vector_push<string>(expected, "SOL-S008")
    vector_push<string>(expected, "SOL-S009")
    vector_push<string>(expected, "SOL-S010")
    vector_push<string>(expected, "SOL-S011")
    vector_push<string>(expected, "SOL-S012")
    vector_push<string>(expected, "SOL-S013")
    vector_push<string>(expected, "SOL-S014")
    vector_push<string>(expected, "SOL-S015")
    vector_push<string>(expected, "SOL-S016")
    vector_push<string>(expected, "SOL-S017")
    vector_push<string>(expected, "SOL-S018")
    @mut let failure: int = 0

    if program == null || semantic_program_successful(program) then
        failure = 2
    end

    if failure == 0 && !semantic_test_has_all_codes(program, expected) then
        failure = 3
    end

    if failure == 0 && !semantic_test_diagnostics_ordered(program) then
        failure = 4
    end

    destroy_vector<string>(expected)
    destroy_semantic_program(program)
    destroy_semantic_test_source(source)
    return failure
end

fn test_struct_pointer_and_generic_diagnostics() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "struct pointer\n    value: int\nend\nstruct Duplicate<T, T>\n    same: int\n    same: void\nend\nstruct Cycle\n    next: Cycle\nend\nstruct Pair\n    first: int\n    second: string\nend\nfn make() -> Pair\n    return Pair { first: 1, second: \"\" }\nend\nfn bad(number: int, integers: pointer<int>, flags: pointer<boolean>) -> void\n    let primitive: int = int { value: 1 }\n    let unknown: Pair = Pair { first: 1, ghost: 2 }\n    let repeated: Pair = Pair { first: 1, first: 2, second: \"\" }\n    let wrong: Pair = Pair { first: true, second: \"\" }\n    let field: int = number.value\n    let absent: int = wrong.missing\n    make().first = 2\n    number->value = 1\n    let indexed: int = number[0]\n    let bad_index: char = \"x\"[true]\n    @mut let text: string = \"x\"\n    text[0] = 'y'\n    let missing: pointer = null\n    let impossible: pointer<void> = null\n    let untyped: int = null\n    if integers == flags then\n        return\n    end\n    return\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.structs",
        source,
        false
    )
    let expected: pointer<Vector<string>> = create_vector<string>()
    vector_push<string>(expected, "SOL-S027")
    vector_push<string>(expected, "SOL-S028")
    vector_push<string>(expected, "SOL-S029")
    vector_push<string>(expected, "SOL-S030")
    vector_push<string>(expected, "SOL-S031")
    vector_push<string>(expected, "SOL-S032")
    vector_push<string>(expected, "SOL-S033")
    vector_push<string>(expected, "SOL-S034")
    vector_push<string>(expected, "SOL-S035")
    vector_push<string>(expected, "SOL-S036")
    vector_push<string>(expected, "SOL-S037")
    vector_push<string>(expected, "SOL-S038")
    vector_push<string>(expected, "SOL-S039")
    vector_push<string>(expected, "SOL-S040")
    vector_push<string>(expected, "SOL-S041")
    vector_push<string>(expected, "SOL-S043")
    vector_push<string>(expected, "SOL-S044")
    vector_push<string>(expected, "SOL-S045")
    vector_push<string>(expected, "SOL-S046")
    vector_push<string>(expected, "SOL-S047")
    @mut let failure: int = 0

    if program == null || !semantic_test_has_all_codes(program, expected) then
        failure = 2
    end

    destroy_vector<string>(expected)
    destroy_semantic_program(program)
    destroy_semantic_test_source(source)

    if failure != 0 then
        return failure
    end

    let generic_source: ParsedSemanticSource = parse_semantic_test_source(
        "fn recurse<T>(value: int) -> int\n    return recurse<pointer<T>>(value)\nend\nfn root() -> int\n    return recurse<int>(1)\nend"
    )

    if !semantic_test_parse_valid(generic_source) then
        destroy_semantic_test_source(generic_source)
        return 3
    end

    let generic_program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.generics",
        generic_source,
        false
    )

    if generic_program == null || !semantic_test_has_code(generic_program, "SOL-S042") then
        failure = 4
    end

    destroy_semantic_program(generic_program)
    destroy_semantic_test_source(generic_source)
    return failure
end

fn test_program_diagnostics() -> int
    let base: ParsedSemanticSource = parse_semantic_test_source(
        "fn ping() -> int\n    return 1\nend"
    )
    let application: ParsedSemanticSource = parse_semantic_test_source(
        "inject missing\ninject base only absent\ninject namespace base as b\nfn base() -> int\n    return 0\nend\nfn run() -> int\n    base::ping()\n    b::absent()\n    return 0\nend"
    )

    if !semantic_test_parse_valid(base) || !semantic_test_parse_valid(application) then
        destroy_semantic_test_source(application)
        destroy_semantic_test_source(base)
        return 1
    end

    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("application", application.parsed.root))
    vector_push<SourceModule>(modules, source_module("base", base.parsed.root))
    let module_program: pointer<SemanticProgram> = analyze_library_modules(modules)
    destroy_vector<SourceModule>(modules)
    @mut let failure: int = 0

    if module_program == null || !semantic_test_has_code(module_program, "SOL-S019") || !semantic_test_has_code(module_program, "SOL-S020") || !semantic_test_has_code(module_program, "SOL-S021") || !semantic_test_has_code(module_program, "SOL-S022") || !semantic_test_code_has_span(module_program, "SOL-S019", 7, 14) then
        failure = 2
    end

    destroy_semantic_program(module_program)
    destroy_semantic_test_source(application)
    destroy_semantic_test_source(base)

    if failure != 0 then
        return failure
    end

    let missing_entry: ParsedSemanticSource = parse_semantic_test_source(
        "fn library() -> int\n    return 0\nend"
    )

    if !semantic_test_parse_valid(missing_entry) then
        destroy_semantic_test_source(missing_entry)
        return 3
    end

    let missing_program: pointer<SemanticProgram> = semantic_test_analyze(
        "library",
        missing_entry,
        true
    )

    if missing_program == null || !semantic_test_has_code(missing_program, "SOL-S023") || missing_program->entry_function != null then
        failure = 4
    end

    destroy_semantic_program(missing_program)
    destroy_semantic_test_source(missing_entry)

    if failure != 0 then
        return failure
    end

    let invalid_entries: ParsedSemanticSource = parse_semantic_test_source(
        "@init\n@fn native<T>() -> void\n@init\nfn second() -> int\n    return 0\nend"
    )

    if !semantic_test_parse_valid(invalid_entries) then
        destroy_semantic_test_source(invalid_entries)
        return 5
    end

    let entry_program: pointer<SemanticProgram> = semantic_test_analyze(
        "entries",
        invalid_entries,
        true
    )

    if entry_program == null || !semantic_test_has_code(entry_program, "SOL-S024") || !semantic_test_has_code(entry_program, "SOL-S025") || !semantic_test_has_code(entry_program, "SOL-S026") || entry_program->entry_function != null then
        failure = 6
    end

    destroy_semantic_program(entry_program)
    destroy_semantic_test_source(invalid_entries)

    let empty: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    let empty_program: pointer<SemanticProgram> = analyze_library_modules(empty)
    destroy_vector<SourceModule>(empty)

    if empty_program == null || !semantic_program_successful(empty_program) || semantic_program_module_count(empty_program) != 0 then
        failure = 7
    end

    destroy_semantic_program(empty_program)
    return failure
end

fn semantic_test_has_all_codes(
    program: pointer<SemanticProgram>,
    expected: pointer<Vector<string>>
) -> boolean
    @mut let index: int = 0
    let count: int = vector_length<string>(expected)

    while index < count do
        if !semantic_test_has_code(program, vector_get<string>(expected, index)) then
            return false
        end

        index = index + 1
    end

    return true
end

fn semantic_test_diagnostics_ordered(program: pointer<SemanticProgram>) -> boolean
    @mut let index: int = 1
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        let previous: SemanticDiagnostic = semantic_program_diagnostic(program, index - 1)
        let current: SemanticDiagnostic = semantic_program_diagnostic(program, index)

        if previous.module_name == current.module_name then
            if previous.diagnostic.span.start.offset > current.diagnostic.span.start.offset then
                return false
            end
        end

        index = index + 1
    end

    return true
end

fn semantic_test_code_has_span(
    program: pointer<SemanticProgram>,
    code: string,
    start_offset: int,
    end_offset: int
) -> boolean
    @mut let index: int = 0
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        let item: SemanticDiagnostic = semantic_program_diagnostic(program, index)

        if item.diagnostic.code == code then
            return item.diagnostic.span.start.offset == start_offset && item.diagnostic.span.end_position.offset == end_offset
        end

        index = index + 1
    end

    return false
end
