inject namespace std.console as console
inject std.collections.vector
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens
inject semantics.model
inject semantics.analyzer
inject ir.model
inject lowering.model only IrLoweringResult
inject lowering.program
inject backend.llvm

@init
fn launch() -> int
    let source: string = "struct Pair<T>\n    first: T\n    second: char\nend\n@native\n@fn observe(value: int) -> void\nfn id<T>(value: T) -> T\n    return value\nend\nfn add(left: int, right: int) -> int\n    return left + right\nend\nfn countdown(value: int) -> int\n    if value <= 0 then\n        return 0\n    else\n        return countdown(value - 1)\n    end\nend\nfn touch(data: pointer<int>, pair: pointer<Pair<int>>) -> int\n    data[0] = +data[0]\n    pair->first = -data[0]\n    observe(pair->first)\n    return pair->first\nend\n@init\nfn launch() -> int\n    @mut let pair: Pair<int> = Pair<int> { first: 1, second: 'λ' }\n    pair.first = id<int>(2)\n    let text: string = \"Sol 🐉\"\n    let other: string = text + \"!\"\n    let letter: char = other[0]\n    @mut let index: int = 0\n    while index < 2 do\n        index = index + 1\n    end\n    if other != \"\" && letter == 'S' then\n        return add(countdown(pair.first), index)\n    else\n        return 1\n    end\nend"
    let lexical: LexResult = scan_source(source)
    if !lexical.successful then
        destroy_lex_result(lexical)
        return 1
    end
    let parsed: ParseResult = parse_tokens(lexical.tokens)
    if !parsed.successful || parsed.root == null then
        destroy_parse_result(parsed)
        destroy_lex_result(lexical)
        return 2
    end
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("fixture", parsed.root))
    let semantic: pointer<SemanticProgram> = analyze_executable_program(modules)
    destroy_vector<SourceModule>(modules)
    if semantic == null || !semantic_program_successful(semantic) then
        destroy_semantic_program(semantic)
        destroy_parse_result(parsed)
        destroy_lex_result(lexical)
        return 3
    end
    let lowered: IrLoweringResult = lower_semantic_program(semantic)
    if lowered.program == null then
        destroy_semantic_program(semantic)
        destroy_parse_result(parsed)
        destroy_lex_result(lexical)
        return 4
    end
    let generated: LlvmGenerationResult = generate_llvm_ir(lowered.program, "bootstrap-verification")
    if generated.error != "" then
        destroy_ir_program(lowered.program)
        destroy_semantic_program(semantic)
        destroy_parse_result(parsed)
        destroy_lex_result(lexical)
        return 5
    end
    console::print(generated.text)
    destroy_ir_program(lowered.program)
    destroy_semantic_program(semantic)
    destroy_parse_result(parsed)
    destroy_lex_result(lexical)
    return 0
end
