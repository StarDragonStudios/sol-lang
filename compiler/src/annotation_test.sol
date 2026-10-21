inject namespace std.console as console
inject frontend.lexer only LexResult, scan_source, destroy_lex_result
inject frontend.parser only ParseResult, parse_tokens_in_mode, destroy_parse_result
inject frontend.syntax

struct AnnotationParse
    lexical: LexResult
    parsed: ParseResult
end

fn annotation_parse(text: string, mode: string) -> AnnotationParse
    let lexical: LexResult = scan_source(text)
    let parsed: ParseResult = parse_tokens_in_mode(lexical.tokens, mode)
    return AnnotationParse { lexical: lexical, parsed: parsed }
end

fn destroy_annotation_parse(value: AnnotationParse) -> void
    destroy_parse_result(value.parsed)
    destroy_lex_result(value.lexical)
    return
end

fn annotation_shape_equal(left: pointer<SyntaxNode>, right: pointer<SyntaxNode>) -> boolean
    if left == null || right == null then
        return left == right
    end
    if left->kind != right->kind || left->variant != right->variant || left->text != right->text || syntax_child_count(left) != syntax_child_count(right) then
        return false
    end
    @mut let index: int = 0
    while index < syntax_child_count(left) do
        if !annotation_shape_equal(syntax_child(left, index), syntax_child(right, index)) then
            return false
        end
        index = index + 1
    end
    return true
end

fn annotation_accepts(text: string) -> boolean
    let value: AnnotationParse = annotation_parse(text, "safe-experimental")
    let success: boolean = value.lexical.successful && value.parsed.successful
    if !success then
        console::print_line(value.parsed.diagnostic.message)
    end
    destroy_annotation_parse(value)
    return success
end

fn annotation_rejects(text: string, code: string) -> boolean
    let value: AnnotationParse = annotation_parse(text, "safe-experimental")
    let success: boolean = value.lexical.successful && !value.parsed.successful && value.parsed.root == null && value.parsed.diagnostic.code == code
    if !success then
        console::print_line("unexpected annotation diagnostic: " + value.parsed.diagnostic.code + " " + value.parsed.diagnostic.message)
    end
    destroy_annotation_parse(value)
    return success
end

fn annotation_equivalence_and_spans() -> boolean
    let compact: AnnotationParse = annotation_parse("@returns(once, must_resolve) @public fn work(@once @must_resolve action: int) -> void\n@mut @pinned let value: int = 1\n@exit(work) return\nend\n", "safe-experimental")
    let multiline: AnnotationParse = annotation_parse("@returns(\nonce,\nmust_resolve,\n)\n@public\nfn work(\n@once\n@must_resolve\naction: int\n) -> void\n@mut\n@pinned\nlet value: int = 1\n@exit(work)\nreturn\nend\n", "safe-experimental")
    @mut let success: boolean = compact.parsed.successful && multiline.parsed.successful
    if success then
        success = annotation_shape_equal(compact.parsed.root, multiline.parsed.root)
    end
    destroy_annotation_parse(compact)
    destroy_annotation_parse(multiline)
    let span: AnnotationParse = annotation_parse("@returns(once) fn work() -> void\nend\n", "safe-experimental")
    success = success && span.parsed.successful
    if span.parsed.successful then
        let function: pointer<SyntaxNode> = syntax_child(span.parsed.root, 0)
        let annotation: pointer<SyntaxNode> = syntax_child(function, syntax_child_count(function) - 1)
        let argument: pointer<SyntaxNode> = syntax_child(annotation, 1)
        success = success && annotation->kind == syntax_kind_annotation() && annotation->variant == syntax_annotation_arguments()
        success = success && annotation->span.start.offset == 0 && annotation->span.end_position.offset == 14 && annotation->span.start.column == 1
        success = success && argument->kind == syntax_kind_annotation_argument() && argument->span.start.offset == 9 && argument->span.end_position.offset == 13
        success = success && function->span.start.offset == 0
    end
    destroy_annotation_parse(span)
    return success
end

fn annotation_blocks_and_captures() -> boolean
    let source: AnnotationParse = annotation_parse("fn work() -> void\n@captures(item = move(value), other = 2) @once let action: int = 1\n@unsafe begin\nbegin return end\nend\nend\n", "safe-experimental")
    @mut let success: boolean = source.parsed.successful
    if success then
        let function: pointer<SyntaxNode> = syntax_child(source.parsed.root, 0)
        let body: pointer<SyntaxNode> = syntax_child(function, 2)
        let variable: pointer<SyntaxNode> = syntax_child(body, 0)
        let captures: pointer<SyntaxNode> = syntax_child(variable, 3)
        let argument: pointer<SyntaxNode> = syntax_child(captures, 1)
        success = argument->text == "item" && syntax_child(argument, 0)->kind == syntax_kind_call_expression()
        success = success && argument->span.start.line == 2 && argument->span.start.column == 11
        let block: pointer<SyntaxNode> = syntax_child(body, 1)
        let inner: pointer<SyntaxNode> = syntax_child(syntax_child(block, 0), 0)
        success = success && block->kind == syntax_kind_code_block_statement() && block->variant == 1
        success = success && block->span.start.line == 3 && block->span.end_position.line == 5
        success = success && inner->kind == syntax_kind_code_block_statement() && inner->variant == 0
    end
    destroy_annotation_parse(source)
    return success
end

@init
fn launch() -> int
    if !annotation_equivalence_and_spans() then
        return 1
    end
    if !annotation_blocks_and_captures() then
        return 2
    end
    if !annotation_accepts("@copy @lifetime(a, b) struct View\n@from(a) first: int\n@depends(resource = b) second: int\nend\n") then
        return 3
    end
    if !annotation_accepts("@abstract class Base\n@public @constructor fn build() -> void\nend\n@protected field: int\nend\n") then
        return 4
    end
    if !annotation_accepts("@public @fn external(@pinned value: int) -> int\n") then
        return 5
    end
    if !annotation_rejects("@mystery fn work() -> void\nend", "SOL-P011") then
        return 6
    end
    if !annotation_rejects("@once @once fn work() -> void\nend", "SOL-P012") then
        return 7
    end
    if !annotation_rejects("@shared @exclusive fn work() -> void\nend", "SOL-P013") then
        return 8
    end
    if !annotation_rejects("@public @private fn work() -> void\nend", "SOL-P013") then
        return 9
    end
    if !annotation_rejects("@copy fn work() -> void\nend", "SOL-P014") then
        return 10
    end
    if !annotation_rejects("fn work() -> void\n@mut const value: int = 0\nend", "SOL-P014") then
        return 11
    end
    if !annotation_rejects("fn work() -> void\n@unsafe return\nend", "SOL-P014") then
        return 12
    end
    if !annotation_rejects("fn work() -> void\n@exit(1) return\nend", "SOL-P015") then
        return 13
    end
    if !annotation_rejects("@init() fn work() -> void\nend", "SOL-P015") then
        return 14
    end
    if !annotation_rejects("@returns fn work() -> void\nend", "SOL-P015") then
        return 15
    end
    if !annotation_rejects("fn work() -> void\n@captures(item=1, item=2) let action: int = 0\nend", "SOL-P012") then
        return 16
    end
    if !annotation_rejects("fn work() -> void\n@captures(value) let action: int = 0\nend", "SOL-P015") then
        return 17
    end
    if !annotation_rejects("@returns(item=once, shared) fn work() -> void\nend", "SOL-P015") then
        return 18
    end
    if !annotation_rejects("fn work() -> void\n@mut let value = 0\nend", "SOL-P002") then
        return 19
    end
    if !annotation_rejects("fn work() -> void\n@unsafe begin\nreturn\n", "SOL-P002") then
        return 20
    end
    if !annotation_rejects("fn work() -> void begin\nend", "SOL-P002") then
        return 21
    end
    if !annotation_rejects("@returns(once fn work() -> void\nend", "SOL-P002") then
        return 22
    end
    let legacy: AnnotationParse = annotation_parse("@init fn work() -> int\nreturn 0\nend", "legacy")
    let rejected: boolean = !legacy.parsed.successful && legacy.parsed.diagnostic.code == "SOL-P002"
    destroy_annotation_parse(legacy)
    if !rejected then
        return 23
    end
    let unknown: AnnotationParse = annotation_parse("", "unknown")
    let invalid: boolean = !unknown.parsed.successful && unknown.parsed.diagnostic.code == "SOL-P010"
    destroy_annotation_parse(unknown)
    if !invalid then
        return 24
    end
    if !annotation_rejects("@abstract @interface class Invalid\nend", "SOL-P013") then
        return 25
    end
    if !annotation_rejects("@constructor @destructor fn invalid() -> void\nend", "SOL-P013") then
        return 26
    end
    return 0
end
