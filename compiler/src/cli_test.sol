inject std.collections.vector
inject cli.core
inject frontend.lexer only LexResult, scan_source, destroy_lex_result
inject frontend.parser only ParseResult, parse_tokens, destroy_parse_result
inject semantics.model
inject semantics.analyzer only analyze_source_modules_in_mode
inject lowering.model only IrLoweringResult
inject lowering.program only lower_semantic_program
inject ir.model only destroy_ir_program

@init
fn launch() -> int
    let unix: pointer<Vector<string>> = compiler_request_fields("SOL-SELFHOST-REQUEST-1\nsource.sol\nroot\nsource\nstdlib\nmodule.ll\nliterals.c\n")
    if unix == null || vector_length<string>(unix) != 7 || vector_get<string>(unix, 1) != "source.sol" then
        destroy_vector<string>(unix)
        return 1
    end
    destroy_vector<string>(unix)

    let windows: pointer<Vector<string>> = compiler_request_fields("SOL-SELFHOST-REQUEST-1\r\nsource.sol\r\nroot\r\nsource\r\nstdlib\r\nmodule.ll\r\nliterals.c\r\n")
    if windows == null || vector_get<string>(windows, 6) != "literals.c" then
        destroy_vector<string>(windows)
        return 2
    end
    destroy_vector<string>(windows)

    if compiler_request_fields("SOL-SELFHOST-REQUEST-1\nmissing\n") != null then
        return 3
    end
    if compiler_request_fields("WRONG\na\nb\nc\nd\ne\nf\n") != null then
        return 4
    end
    if compiler_module_relative_path("utilities.math") != "utilities/math.sol" then
        return 5
    end
    if compiler_standard_path("stdlib", "std.collections.vector") != "stdlib/std/collections/vector.sol" then
        return 6
    end
    if compiler_standard_path("stdlib", "user.module") != "" then
        return 7
    end
    let safe: pointer<Vector<string>> = compiler_request_fields("SOL-SELFHOST-REQUEST-2\r\nsafe-experimental\r\nsource.sol\r\nroot\r\nsource\r\nsafe library\r\nmodule.ll\r\nliterals.c\r\n")
    if safe == null then
        return 8
    end
    let request: CompilerRequest = compiler_request_from_fields(safe)
    destroy_vector<string>(safe)
    if request.language_mode != "safe-experimental" || request.source_path != "source.sol" || request.standard_library_root != "safe library" || request.literal_output != "literals.c" then
        return 9
    end
    if compiler_request_fields("SOL-SELFHOST-REQUEST-2\nlegacy\na\nb\nc\nd\ne\nf\n") != null then
        return 10
    end
    if compiler_request_fields("SOL-SELFHOST-REQUEST-2\nsafe-experimental\na\nb\nc\nd\ne\n") != null then
        return 11
    end
    if compiler_request_fields("SOL-SELFHOST-REQUEST-1\nsafe-experimental\na\nb\nc\nd\ne\nf\n") != null then
        return 12
    end
    let legacy: pointer<Vector<string>> = compiler_request_fields("SOL-SELFHOST-REQUEST-1\na\nb\nc\nd\ne\nf\n")
    let old: CompilerRequest = compiler_request_from_fields(legacy)
    destroy_vector<string>(legacy)
    if old.language_mode != "legacy" || old.source_path != "a" then
        return 13
    end
    if !compiler_reserved_standard_module("std.custom") || compiler_reserved_standard_module("standard.custom") then
        return 14
    end
    if compiler_request_fields("SOL-SELFHOST-REQUEST-2\n\na\nb\nc\nd\ne\nf\n") != null then
        return 15
    end
    if compiler_request_fields("SOL-SELFHOST-REQUEST-1\na\n\nc\nd\ne\nf\n") != null then
        return 16
    end
    if !mode_context_regression() then
        return 17
    end
    return 0
end

fn mode_context_regression() -> boolean
    let lexical: LexResult = scan_source("struct Data\nvalue: int\nend\n@init\nfn launch() -> int\nreturn 0\nend\n")
    let parsed: ParseResult = parse_tokens(lexical.tokens)
    let sources: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(sources, source_module("main", parsed.root))
    @mut let successful: boolean = parsed.successful
    @mut let index: int = 0
    while index < 3 do
        @mut let mode: string = "legacy"
        if index == 1 then
            mode = "safe-experimental"
        end
        let program: pointer<SemanticProgram> = analyze_source_modules_in_mode(sources, true, mode)
        successful = successful && program->language_mode == mode
        if index == 1 then
            successful = successful && !semantic_program_successful(program)
        else
            successful = successful && semantic_program_successful(program)
        end
        destroy_semantic_program(program)
        index = index + 1
    end
    let unknown: pointer<SemanticProgram> = analyze_source_modules_in_mode(sources, true, "unknown")
    successful = successful && !semantic_program_successful(unknown)
    destroy_semantic_program(unknown)
    destroy_vector<SourceModule>(sources)
    destroy_parse_result(parsed)
    destroy_lex_result(lexical)

    let safe_lexical: LexResult = scan_source("@init\nfn launch() -> int\nreturn 0\nend\n")
    let safe_parsed: ParseResult = parse_tokens(safe_lexical.tokens)
    let safe_sources: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(safe_sources, source_module("main", safe_parsed.root))
    let safe_program: pointer<SemanticProgram> = analyze_source_modules_in_mode(safe_sources, true, "safe-experimental")
    let lowered: IrLoweringResult = lower_semantic_program(safe_program)
    if lowered.program == null then
        successful = false
    else
        successful = successful && lowered.program->language_mode == "safe-experimental"
        destroy_ir_program(lowered.program)
    end
    destroy_semantic_program(safe_program)
    destroy_vector<SourceModule>(safe_sources)
    destroy_parse_result(safe_parsed)
    destroy_lex_result(safe_lexical)
    return successful
end
