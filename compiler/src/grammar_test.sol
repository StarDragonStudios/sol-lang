inject namespace std.console as console
inject namespace std.string as strings
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens
inject frontend.syntax

struct GrammarParse
    lexical: LexResult
    parsed: ParseResult
end

@init
fn launch() -> int
    let declarations: int = test_declarations_and_multiline_lists()

    if declarations != 0 then
        console::print_line("self-host grammar test failed: declarations")
        return 10 + declarations
    end

    let empty_forms: int = test_empty_forms()

    if empty_forms != 0 then
        console::print_line("self-host grammar test failed: empty forms")
        return 30 + empty_forms
    end

    let statements: int = test_statements_and_nested_blocks()

    if statements != 0 then
        console::print_line("self-host grammar test failed: statements")
        return 50 + statements
    end

    let precedence: int = test_expression_precedence()

    if precedence != 0 then
        console::print_line("self-host grammar test failed: precedence")
        return 70 + precedence
    end

    let postfix: int = test_generics_structs_and_postfix_chains()

    if postfix != 0 then
        console::print_line("self-host grammar test failed: postfix and generics")
        return 90 + postfix
    end

    let classes: int = test_class_declarations_and_lifetime_syntax()

    if classes != 0 then
        console::print_line("self-host grammar test failed: classes and object lifetime")
        return 100 + classes
    end

    let diagnostics: int = test_grammar_diagnostics()

    if diagnostics != 0 then
        console::print_line("self-host grammar test failed: diagnostics")
        return 110 + diagnostics
    end

    return 0
end

fn test_class_declarations_and_lifetime_syntax() -> int
    let source: string = "@public\nclass Document << core::Entity < Printable, Serializable\n    @private\n    name: string\n    @public\n    @constructor\n    fn create(name: string) -> void\n        this.name = name\n    end\n    @public\n    @fn describe() -> string\nend\n@public\n@interface\nclass Printable\n    @public\n    @fn print() -> void\nend\nfn allocate() -> void\n    let document: pointer<Document> = new Document(\"Sol\")\n    delete document\n    return\nend"
    let result: GrammarParse = parse_source(source)
    @mut let failure: int = 0

    if !result.lexical.successful || !result.parsed.successful || result.parsed.root == null then
        failure = 1
    end

    if failure != 0 then
        destroy_grammar_parse(result)
        return failure
    end

    let root: pointer<SyntaxNode> = result.parsed.root

    if syntax_child_count(root) != 3 then
        failure = 2
    end

    let document: pointer<SyntaxNode> = syntax_child(root, 0)
    let printable: pointer<SyntaxNode> = syntax_child(root, 1)
    let allocate: pointer<SyntaxNode> = syntax_child(root, 2)

    if failure == 0 && (document->kind != syntax_kind_class_declaration() || document->text != "Document" || syntax_child_count(document) != 8) then
        failure = 3
    end

    if failure == 0 && (syntax_child(document, 0)->kind != syntax_kind_annotation() || syntax_child(document, 0)->text != "public" || syntax_child(document, 1)->kind != syntax_kind_name()) then
        failure = 4
    end

    let base_clause: pointer<SyntaxNode> = syntax_child(document, 2)
    let first_interface: pointer<SyntaxNode> = syntax_child(document, 3)

    if failure == 0 && (base_clause->kind != syntax_kind_class_base_clause() || base_clause->text != "core::Entity" || syntax_child(base_clause, 0)->text != "core::Entity") then
        failure = 5
    end

    if failure == 0 && (first_interface->kind != syntax_kind_class_interface_clause() || first_interface->text != "Printable" || syntax_child(document, 4)->text != "Serializable") then
        failure = 6
    end

    let field: pointer<SyntaxNode> = syntax_child(document, 5)
    let constructor: pointer<SyntaxNode> = syntax_child(document, 6)
    let abstract_method: pointer<SyntaxNode> = syntax_child(document, 7)

    if failure == 0 && (field->kind != syntax_kind_class_field_declaration() || field->text != "name" || syntax_child_count(field) != 3 || syntax_child(field, 0)->text != "private") then
        failure = 7
    end

    if failure == 0 && (constructor->kind != syntax_kind_function_declaration() || constructor->variant != syntax_function_with_body() || syntax_child(constructor, 0)->text != "public" || syntax_child(constructor, 1)->text != "constructor") then
        failure = 8
    end

    if failure == 0 && (abstract_method->variant != syntax_function_bodyless() || syntax_child(abstract_method, 0)->text != "public") then
        failure = 9
    end

    if failure == 0 && (printable->kind != syntax_kind_class_declaration() || syntax_child(printable, 0)->text != "public" || syntax_child(printable, 1)->text != "interface") then
        failure = 10
    end

    let body: pointer<SyntaxNode> = syntax_child(allocate, 2)
    let variable: pointer<SyntaxNode> = syntax_child(body, 0)
    let allocated: pointer<SyntaxNode> = syntax_child(variable, 2)

    if failure == 0 && (syntax_child_count(body) != 3 || allocated->kind != syntax_kind_new_expression() || allocated->text != "Document" || syntax_child_count(allocated) != 2) then
        failure = 11
    end

    if failure == 0 && (syntax_child(body, 1)->kind != syntax_kind_delete_statement() || syntax_child_count(syntax_child(body, 1)) != 1) then
        failure = 12
    end

    if failure == 0 && (document->span.start.offset != 0 || document->span.end_position.offset >= printable->span.start.offset || root->span.end_position.offset != strings::length(source)) then
        failure = 13
    end

    destroy_grammar_parse(result)
    return failure
end

fn parse_source(source: string) -> GrammarParse
    let lexical: LexResult = scan_source(source)
    @mut let parsed: ParseResult = parse_tokens(null)

    if lexical.successful then
        parsed = parse_tokens(lexical.tokens)
    end

    return GrammarParse {
        lexical: lexical,
        parsed: parsed
    }
end

fn destroy_grammar_parse(result: GrammarParse) -> void
    destroy_parse_result(result.parsed)
    destroy_lex_result(result.lexical)
    return
end

fn test_declarations_and_multiline_lists() -> int
    let source: string = "inject utilities.math only add, subtract\ninject namespace std.console as csl\nstruct Pair<A, B>\n    first: A\n    second: B\nend\n@first\n@second\nfn launch<T>(\n    value: Pair<T, int>,\n) -> int\n    return 0\nend\n@native\n@fn external(\n    value: int,\n) -> void"
    let result: GrammarParse = parse_source(source)
    @mut let failure: int = 0

    if !result.lexical.successful || !result.parsed.successful || result.parsed.root == null then
        failure = 1
    end

    if failure != 0 then
        destroy_grammar_parse(result)
        return failure
    end

    let root: pointer<SyntaxNode> = result.parsed.root

    if failure == 0 && syntax_child_count(root) != 5 then
        failure = 2
    end

    let direct: pointer<SyntaxNode> = syntax_child(root, 0)
    let namespace_injection: pointer<SyntaxNode> = syntax_child(root, 1)
    let pair: pointer<SyntaxNode> = syntax_child(root, 2)
    let launch: pointer<SyntaxNode> = syntax_child(root, 3)
    let external: pointer<SyntaxNode> = syntax_child(root, 4)

    if failure == 0 && (direct->kind != syntax_kind_injection_declaration() || direct->variant != syntax_injection_direct() || syntax_child_count(direct) != 3) then
        failure = 3
    end

    if failure == 0 && (syntax_child(direct, 0)->text != "utilities.math" || syntax_child(direct, 1)->text != "add" || syntax_child(direct, 2)->text != "subtract") then
        failure = 4
    end

    if failure == 0 && (namespace_injection->variant != syntax_injection_namespace() || namespace_injection->text != "csl" || syntax_child_count(namespace_injection) != 2) then
        failure = 5
    end

    if failure == 0 && (pair->kind != syntax_kind_struct_declaration() || pair->text != "Pair" || syntax_child_count(pair) != 5) then
        failure = 6
    end

    if failure == 0 && (syntax_child(pair, 0)->kind != syntax_kind_name() || syntax_child(pair, 1)->kind != syntax_kind_type_parameter() || syntax_child(pair, 2)->text != "B" || syntax_child(pair, 3)->kind != syntax_kind_struct_field_declaration()) then
        failure = 7
    end

    if failure == 0 && (launch->variant != syntax_function_with_body() || launch->text != "launch" || syntax_child_count(launch) != 7) then
        failure = 8
    end

    if failure == 0 && (syntax_child(launch, 0)->kind != syntax_kind_annotation() || syntax_child(launch, 0)->text != "first" || syntax_child(launch, 1)->text != "second" || syntax_child(launch, 2)->kind != syntax_kind_name() || syntax_child(launch, 3)->kind != syntax_kind_type_parameter()) then
        failure = 9
    end

    let parameter: pointer<SyntaxNode> = syntax_child(launch, 4)

    if failure == 0 && (parameter->kind != syntax_kind_parameter() || parameter->text != "value" || syntax_child_count(parameter) != 2) then
        failure = 10
    end

    if failure == 0 && (syntax_child(parameter, 1)->kind != syntax_kind_type_reference() || syntax_child_count(syntax_child(parameter, 1)) != 3) then
        failure = 11
    end

    if failure == 0 && (external->variant != syntax_function_bodyless() || external->text != "external" || syntax_child_count(external) != 4 || syntax_child(external, 0)->text != "native") then
        failure = 12
    end

    if failure == 0 && (root->span.start.offset != 0 || root->span.end_position.offset != strings::length(source)) then
        failure = 13
    end

    destroy_grammar_parse(result)
    return failure
end

fn test_empty_forms() -> int
    let source: string = "inject plain\ninject namespace std.file\nstruct Marker\nend\nfn empty() -> void\n    if true then\n    else\n    end\n    while false do\n    end\n    ping(\n    )\n    let marker: Marker = Marker {}\n    return\nend"
    let result: GrammarParse = parse_source(source)
    @mut let failure: int = 0

    if !result.lexical.successful || !result.parsed.successful || result.parsed.root == null then
        failure = 1
    end

    if failure != 0 then
        destroy_grammar_parse(result)
        return failure
    end

    let root: pointer<SyntaxNode> = result.parsed.root

    if syntax_child_count(root) != 4 then
        failure = 2
    end

    let direct: pointer<SyntaxNode> = syntax_child(root, 0)
    let namespace_injection: pointer<SyntaxNode> = syntax_child(root, 1)
    let marker: pointer<SyntaxNode> = syntax_child(root, 2)
    let function: pointer<SyntaxNode> = syntax_child(root, 3)

    if failure == 0 && (direct->variant != syntax_injection_direct() || syntax_child_count(direct) != 1 || namespace_injection->variant != syntax_injection_namespace() || namespace_injection->text != "" || syntax_child_count(namespace_injection) != 1) then
        failure = 3
    end

    if failure == 0 && (marker->kind != syntax_kind_struct_declaration() || syntax_child_count(marker) != 1) then
        failure = 4
    end

    let body: pointer<SyntaxNode> = syntax_child(function, 2)

    if failure == 0 && syntax_child_count(body) != 5 then
        failure = 5
    end

    let conditional: pointer<SyntaxNode> = syntax_child(body, 0)
    let while_statement: pointer<SyntaxNode> = syntax_child(body, 1)
    let call_statement: pointer<SyntaxNode> = syntax_child(body, 2)
    let variable: pointer<SyntaxNode> = syntax_child(body, 3)

    if failure == 0 && (syntax_child_count(syntax_child(conditional, 1)) != 0 || syntax_child_count(syntax_child(conditional, 2)) != 0 || syntax_child_count(syntax_child(while_statement, 1)) != 0) then
        failure = 6
    end

    if failure == 0 && (call_statement->kind != syntax_kind_call_statement() || syntax_child_count(syntax_child(call_statement, 0)) != 1) then
        failure = 7
    end

    if failure == 0 && (syntax_child(variable, 2)->kind != syntax_kind_struct_construction_expression() || syntax_child_count(syntax_child(variable, 2)) != 1) then
        failure = 8
    end

    destroy_grammar_parse(result)
    return failure
end

fn test_statements_and_nested_blocks() -> int
    let source: string = "fn work(data: pointer<Node>, text: string) -> void\n    const a: int = 1\n    let b: int = 2\n    @mut let c: int = 3\n    c = a + b\n    node.value = c\n    data->value = c\n    text[0] = 'x'\n    log(c)\n    if c > 0 then\n        while c > 1 do\n            c = c - 1\n        end\n    else\n        return\n    end\n    return\nend"
    let result: GrammarParse = parse_source(source)
    @mut let failure: int = 0

    if !result.lexical.successful || !result.parsed.successful || result.parsed.root == null then
        failure = 1
    end

    if failure != 0 then
        destroy_grammar_parse(result)
        return failure
    end

    let function: pointer<SyntaxNode> = syntax_child(result.parsed.root, 0)
    let body: pointer<SyntaxNode> = syntax_child(function, 4)

    if failure == 0 && (function->kind != syntax_kind_function_declaration() || syntax_child_count(function) != 5 || body->kind != syntax_kind_block()) then
        failure = 2
    end

    if failure == 0 && syntax_child_count(body) != 10 then
        failure = 3
    end

    if failure == 0 && (syntax_child(body, 0)->variant != syntax_variable_const() || syntax_child(body, 1)->variant != syntax_variable_let() || syntax_child(body, 2)->variant != syntax_variable_mutable_let()) then
        failure = 4
    end

    if failure == 0 && (syntax_child(body, 3)->kind != syntax_kind_assignment_statement() || syntax_child(body, 4)->kind != syntax_kind_field_assignment_statement() || syntax_child(body, 5)->kind != syntax_kind_pointer_field_assignment_statement() || syntax_child(body, 6)->kind != syntax_kind_index_assignment_statement() || syntax_child(body, 7)->kind != syntax_kind_call_statement()) then
        failure = 5
    end

    let conditional: pointer<SyntaxNode> = syntax_child(body, 8)

    if failure == 0 && (conditional->kind != syntax_kind_conditional_statement() || syntax_child_count(conditional) != 3) then
        failure = 6
    end

    let then_block: pointer<SyntaxNode> = syntax_child(conditional, 1)
    let else_block: pointer<SyntaxNode> = syntax_child(conditional, 2)

    if failure == 0 && (syntax_child_count(then_block) != 1 || syntax_child(then_block, 0)->kind != syntax_kind_while_statement() || syntax_child_count(else_block) != 1 || syntax_child(else_block, 0)->kind != syntax_kind_return_statement()) then
        failure = 7
    end

    let while_statement: pointer<SyntaxNode> = syntax_child(then_block, 0)

    if failure == 0 && (syntax_child_count(while_statement) != 2 || syntax_child_count(syntax_child(while_statement, 1)) != 1) then
        failure = 8
    end

    if failure == 0 && syntax_child_count(syntax_child(body, 9)) != 0 then
        failure = 9
    end

    destroy_grammar_parse(result)
    return failure
end

fn test_expression_precedence() -> int
    let source: string = "fn calculate() -> boolean\n    let floating: float = 1.5\n    let text: string = \"sol\"\n    let character: char = 's'\n    let empty: pointer<int> = null\n    let grouped: boolean = (true || false) && false\n    let signed: int = -+1\n    return 1 + 2 * 3 < 8 == true && !false || false\nend"
    let result: GrammarParse = parse_source(source)
    @mut let failure: int = 0

    if !result.lexical.successful || !result.parsed.successful || result.parsed.root == null then
        failure = 1
    end

    if failure != 0 then
        destroy_grammar_parse(result)
        return failure
    end

    let function: pointer<SyntaxNode> = syntax_child(result.parsed.root, 0)
    let body: pointer<SyntaxNode> = syntax_child(function, 2)
    let returned: pointer<SyntaxNode> = syntax_child(body, 6)
    let logical_or: pointer<SyntaxNode> = syntax_child(returned, 0)

    if failure == 0 && (logical_or->kind != syntax_kind_binary_expression() || logical_or->variant != syntax_binary_or()) then
        failure = 2
    end

    let logical_and: pointer<SyntaxNode> = syntax_child(logical_or, 0)

    if failure == 0 && (logical_and->variant != syntax_binary_and() || syntax_child(logical_and, 1)->kind != syntax_kind_unary_expression() || syntax_child(logical_and, 1)->variant != syntax_unary_not()) then
        failure = 3
    end

    let equality: pointer<SyntaxNode> = syntax_child(logical_and, 0)
    let relational: pointer<SyntaxNode> = syntax_child(equality, 0)
    let additive: pointer<SyntaxNode> = syntax_child(relational, 0)
    let multiplicative: pointer<SyntaxNode> = syntax_child(additive, 1)

    if failure == 0 && (equality->variant != syntax_binary_equal() || relational->variant != syntax_binary_less() || additive->variant != syntax_binary_add() || multiplicative->variant != syntax_binary_multiply()) then
        failure = 4
    end

    if failure == 0 && (syntax_child(syntax_child(body, 0), 2)->variant != syntax_literal_float() || syntax_child(syntax_child(body, 1), 2)->variant != syntax_literal_string() || syntax_child(syntax_child(body, 2), 2)->variant != syntax_literal_char() || syntax_child(syntax_child(body, 3), 2)->kind != syntax_kind_null_expression()) then
        failure = 5
    end

    let grouped: pointer<SyntaxNode> = syntax_child(syntax_child(body, 4), 2)
    let signed: pointer<SyntaxNode> = syntax_child(syntax_child(body, 5), 2)

    if failure == 0 && (grouped->variant != syntax_binary_and() || syntax_child(grouped, 0)->kind != syntax_kind_parenthesized_expression() || signed->variant != syntax_unary_negative() || syntax_child(signed, 0)->variant != syntax_unary_positive()) then
        failure = 6
    end

    if failure == 0 && (logical_or->span.start.line != 8 || logical_or->span.start.column != 12 || logical_or->span.end_position.offset != strings::length(source) - strings::length("\nend")) then
        failure = 7
    end

    destroy_grammar_parse(result)
    return failure
end

fn test_generics_structs_and_postfix_chains() -> int
    let source: string = "fn build() -> int\n    let value: Box<Pair<int, string>> = Box<Pair<int, string>> {\n        value: make<Pair<int, string>>(\n            ns::load<int>(\n                \"x\",\n            ),\n        ),\n    }\n    value.item->field[0] = other()\n    return factory()().item->field[0]\nend"
    let result: GrammarParse = parse_source(source)
    @mut let failure: int = 0

    if !result.lexical.successful || !result.parsed.successful || result.parsed.root == null then
        failure = 1
    end

    if failure != 0 then
        destroy_grammar_parse(result)
        return failure
    end

    let function: pointer<SyntaxNode> = syntax_child(result.parsed.root, 0)
    let body: pointer<SyntaxNode> = syntax_child(function, 2)
    let variable: pointer<SyntaxNode> = syntax_child(body, 0)
    let variable_type: pointer<SyntaxNode> = syntax_child(variable, 1)
    let construction: pointer<SyntaxNode> = syntax_child(variable, 2)

    if failure == 0 && (syntax_child_count(body) != 3 || variable_type->text != "Box" || syntax_child_count(variable_type) != 2 || syntax_child(variable_type, 1)->text != "Pair") then
        failure = 2
    end

    if failure == 0 && (construction->kind != syntax_kind_struct_construction_expression() || syntax_child_count(construction) != 2 || syntax_child(construction, 1)->kind != syntax_kind_struct_field_initializer()) then
        failure = 3
    end

    let make_call: pointer<SyntaxNode> = syntax_child(syntax_child(construction, 1), 1)

    if failure == 0 && (make_call->kind != syntax_kind_call_expression() || syntax_child(make_call, 1)->kind != syntax_kind_type_reference() || syntax_child(make_call, 2)->kind != syntax_kind_call_expression()) then
        failure = 4
    end

    let namespace_call: pointer<SyntaxNode> = syntax_child(make_call, 2)

    if failure == 0 && (syntax_child(namespace_call, 0)->kind != syntax_kind_qualified_name_expression() || syntax_child(namespace_call, 1)->text != "int") then
        failure = 5
    end

    if failure == 0 && syntax_child(body, 1)->kind != syntax_kind_index_assignment_statement() then
        failure = 6
    end

    let returned: pointer<SyntaxNode> = syntax_child(syntax_child(body, 2), 0)

    if failure == 0 && (returned->kind != syntax_kind_index_expression() || syntax_child(returned, 0)->kind != syntax_kind_pointer_field_access_expression()) then
        failure = 7
    end

    let pointer_access: pointer<SyntaxNode> = syntax_child(returned, 0)
    let item_access: pointer<SyntaxNode> = syntax_child(pointer_access, 0)

    if failure == 0 && (item_access->kind != syntax_kind_field_access_expression() || syntax_child(item_access, 0)->kind != syntax_kind_call_expression() || syntax_child(syntax_child(item_access, 0), 0)->kind != syntax_kind_call_expression()) then
        failure = 8
    end

    destroy_grammar_parse(result)
    return failure
end

fn test_grammar_diagnostics() -> int
    if !parse_error_matches(
        "fn () -> void\nend",
        "Expected a function name after 'fn', but found '('.",
        3,
        4
    ) then
        return 1
    end

    if !parse_error_matches(
        "fn f(, value: int) -> void\nend",
        "Expected a parameter name, but found ','.",
        5,
        6
    ) then
        return 2
    end

    if !parse_error_matches(
        "fn f() -> int\n    return 1 +\nend",
        "Expected an expression, but found newline.",
        28,
        29
    ) then
        return 3
    end

    if !parse_error_matches(
        "inject module as alias",
        "Expected a newline or end of file after the direct injection, but found 'as'.",
        14,
        16
    ) then
        return 4
    end

    if !parse_error_matches(
        "fn f() -> int\n    return a::b::c()\nend",
        "Expected a call or expression operator after the namespace-qualified name, but found '::'.",
        29,
        31
    ) then
        return 5
    end

    return 0
end

fn parse_error_matches(
    source: string,
    message: string,
    start_offset: int,
    end_offset: int
) -> boolean
    let result: GrammarParse = parse_source(source)
    @mut let matches: boolean = true

    if !result.lexical.successful || result.parsed.successful || result.parsed.root != null then
        matches = false
    end

    if matches && result.parsed.diagnostic.code != "SOL-P002" then
        matches = false
    end

    if matches && result.parsed.diagnostic.message != message then
        matches = false
    end

    if matches && (result.parsed.diagnostic.span.start.offset != start_offset || result.parsed.diagnostic.span.end_position.offset != end_offset) then
        matches = false
    end

    destroy_grammar_parse(result)
    return matches
end
