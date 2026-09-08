inject namespace std.memory as memory
inject std.collections.vector
inject frontend.source only SourcePosition, SourceSpan, source_position, source_span
inject frontend.diagnostic only Diagnostic, diagnostic, empty_diagnostic
inject frontend.token
inject frontend.syntax

struct ParseResult
    successful: boolean
    root: pointer<SyntaxNode>
    diagnostic: Diagnostic
end

struct Parser
    tokens: pointer<Vector<Token>>
    current: int
    successful: boolean
    diagnostic: Diagnostic
end

fn parse_tokens(tokens: pointer<Vector<Token>>) -> ParseResult
    let parser: pointer<Parser> = memory::allocate<Parser>(1)

    if parser == null then
        let position: SourcePosition = source_position(0, 1, 1)

        return ParseResult {
            successful: false,
            root: null,
            diagnostic: diagnostic(
                "SOL-P000",
                "Unable to allocate parser state.",
                position,
                position
            )
        }
    end

    parser->tokens = tokens
    parser->current = 0
    parser->successful = true
    parser->diagnostic = empty_diagnostic()

    validate_token_stream(parser)

    @mut let root: pointer<SyntaxNode> = null

    if parser->successful then
        root = parse_compilation_unit(parser)
    end

    let result: ParseResult = ParseResult {
        successful: parser->successful,
        root: root,
        diagnostic: parser->diagnostic
    }

    memory::free<Parser>(parser)
    return result
end

fn destroy_parse_result(result: ParseResult) -> void
    destroy_syntax_tree(result.root)
    return
end

fn validate_token_stream(parser: pointer<Parser>) -> void
    if parser->tokens == null then
        parser_fail_at_zero(
            parser,
            "SOL-P000",
            "Parser token stream must not be null."
        )
        return
    end

    let count: int = vector_length<Token>(parser->tokens)

    if count == 0 then
        parser_fail_at_zero(
            parser,
            "SOL-P000",
            "Parser token stream must not be empty."
        )
        return
    end

    let last: Token = vector_get<Token>(parser->tokens, count - 1)

    if last.kind != token_kind_eof() then
        parser_fail(
            parser,
            "SOL-P000",
            "Parser token stream must terminate with EOF.",
            last.span
        )
        return
    end

    @mut let index: int = 0

    while index < count - 1 do
        let current: Token = vector_get<Token>(parser->tokens, index)

        if current.kind == token_kind_eof() then
            parser_fail(
                parser,
                "SOL-P000",
                "Parser token stream must not contain EOF before its end.",
                current.span
            )
            return
        end

        index = index + 1
    end

    return
end

fn parse_compilation_unit(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let root: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_compilation_unit(),
        syntax_variant_none(),
        "",
        parser_complete_source_span(parser)
    )

    if root == null then
        return null
    end

    parser_skip_newlines(parser)

    while !parser_is_at_end(parser) && parser->successful do
        let declaration: pointer<SyntaxNode> = parse_top_level_declaration(parser)

        if parser->successful then
            syntax_add_child(root, declaration)
            parser_skip_newlines(parser)
        end
    end

    if !parser->successful then
        destroy_syntax_tree(root)
        return null
    end

    root->span = parser_complete_source_span(parser)
    return root
end

fn parse_top_level_declaration(parser: pointer<Parser>) -> pointer<SyntaxNode>
    if parser_check(parser, token_kind_fn()) then
        return parse_function_declaration(parser)
    end

    if parser_check(parser, token_kind_at()) then
        return parse_annotated_top_level_declaration(parser)
    end

    if parser_check(parser, token_kind_inject()) then
        return parse_injection_declaration(parser)
    end

    if parser_check(parser, token_kind_struct()) then
        return parse_struct_declaration(parser)
    end

    if parser_check(parser, token_kind_class()) then
        let annotations: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()
        return parse_class_declaration_after_marker(
            parser,
            annotations,
            parser_peek(parser)
        )
    end

    let unexpected: Token = parser_peek(parser)
    parser_fail(
        parser,
        "SOL-P001",
        "Unexpected token '" + unexpected.lexeme + "' at top level.",
        unexpected.span
    )
    return null
end

fn parse_annotated_top_level_declaration(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let declaration_start: Token = parser_peek(parser)
    let annotations: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()

    while parser_check(parser, token_kind_at()) && parser->successful do
        let at_token: Token = parser_advance(parser)

        if parser_match(parser, token_kind_fn()) then
            return parse_function_declaration_after_marker(
                parser,
                annotations,
                declaration_start,
                true
            )
        end

        let name_token: Token = parser_consume(
            parser,
            token_kind_identifier(),
            "an annotation name after '@'"
        )

        if parser->successful then
            let annotation: pointer<SyntaxNode> = parser_create_node(
                parser,
                syntax_kind_annotation(),
                syntax_variant_none(),
                name_token.lexeme,
                source_span(at_token.span.start, name_token.span.end_position)
            )

            if annotation != null then
                parser_add_name_child(parser, annotation, name_token)
                vector_push<pointer<SyntaxNode>>(annotations, annotation)
            end
        end

        parser_consume(
            parser,
            token_kind_newline(),
            "a newline after the declaration annotation"
        )
    end

    if parser_match(parser, token_kind_fn()) then
        return parse_function_declaration_after_marker(
            parser,
            annotations,
            declaration_start,
            false
        )
    end

    if parser_check(parser, token_kind_class()) then
        return parse_class_declaration_after_marker(
            parser,
            annotations,
            declaration_start
        )
    end

    if parser->successful then
        parser_expected(parser, "'fn', '@fn', or 'class' after the declaration annotations")
    end

    destroy_node_vector(annotations)
    return null
end

fn parse_class_declaration_after_marker(
    parser: pointer<Parser>,
    annotations: pointer<Vector<pointer<SyntaxNode>>>,
    declaration_start: Token
) -> pointer<SyntaxNode>
    let class_token: Token = parser_consume(parser, token_kind_class(), "'class'")
    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "a class name after 'class'"
    )

    if !parser->successful then
        destroy_node_vector(annotations)
        return null
    end

    let declaration: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_class_declaration(),
        syntax_variant_none(),
        name_token.lexeme,
        source_span(declaration_start.span.start, name_token.span.end_position)
    )

    if declaration == null then
        destroy_node_vector(annotations)
        return null
    end

    transfer_node_vector(declaration, annotations)
    parser_add_name_child(parser, declaration, name_token)

    if parser_match(parser, token_kind_double_less()) then
        let base_type: pointer<SyntaxNode> = parse_class_type_name(
            parser,
            "a base class name after '<<'"
        )
        let base_clause: pointer<SyntaxNode> = create_class_relation_clause(
            parser,
            syntax_kind_class_base_clause(),
            base_type
        )

        if parser->successful then
            syntax_add_child(declaration, base_clause)
        else
            destroy_syntax_tree(base_clause)
        end
    end

    if parser_match(parser, token_kind_less()) then
        parse_class_interface_list(parser, declaration)
    end

    parser_consume(
        parser,
        token_kind_newline(),
        "a newline after the class declaration header"
    )
    parser_skip_newlines(parser)

    while !parser_check(parser, token_kind_end()) && !parser_is_at_end(parser) && parser->successful do
        let member: pointer<SyntaxNode> = parse_class_member(parser)

        if parser->successful then
            syntax_add_child(declaration, member)

            if parser_match(parser, token_kind_newline()) then
                parser_skip_newlines(parser)
            else
                if !parser_check(parser, token_kind_end()) then
                    parser_expected(parser, "a newline or 'end' after the class member")
                end
            end
        end
    end

    let end_token: Token = parser_consume(
        parser,
        token_kind_end(),
        "'end' to close the class declaration"
    )

    if !parser->successful then
        destroy_syntax_tree(declaration)
        return null
    end

    declaration->span = source_span(
        declaration_start.span.start,
        end_token.span.end_position
    )
    return declaration
end

fn parse_class_interface_list(
    parser: pointer<Parser>,
    declaration: pointer<SyntaxNode>
) -> void
    @mut let reading_interfaces: boolean = true

    while parser->successful && reading_interfaces do
        let interface_type: pointer<SyntaxNode> = parse_class_type_name(
            parser,
            "an interface name after '<'"
        )
        let interface_clause: pointer<SyntaxNode> = create_class_relation_clause(
            parser,
            syntax_kind_class_interface_clause(),
            interface_type
        )

        if parser->successful then
            syntax_add_child(declaration, interface_clause)
        else
            destroy_syntax_tree(interface_clause)
            return
        end

        if !parser_match(parser, token_kind_comma()) then
            reading_interfaces = false
        end
    end

    return
end

fn parse_class_type_name(
    parser: pointer<Parser>,
    expectation: string
) -> pointer<SyntaxNode>
    let first: Token = parser_consume(
        parser,
        token_kind_identifier(),
        expectation
    )

    if !parser->successful then
        return null
    end

    let type_name: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_type_reference(),
        syntax_variant_none(),
        first.lexeme,
        first.span
    )

    if type_name == null then
        return null
    end

    parser_add_name_child(parser, type_name, first)

    while parser->successful && parser_match(parser, token_kind_double_colon()) do
        let segment: Token = parser_consume(
            parser,
            token_kind_identifier(),
            "a type name segment after '::'"
        )

        if parser->successful then
            parser_add_name_child(parser, type_name, segment)
            type_name->text = type_name->text + "::" + segment.lexeme
            type_name->span = source_span(
                type_name->span.start,
                segment.span.end_position
            )
        end
    end

    if !parser->successful then
        destroy_syntax_tree(type_name)
        return null
    end

    return type_name
end

fn create_class_relation_clause(
    parser: pointer<Parser>,
    kind: int,
    relation_type: pointer<SyntaxNode>
) -> pointer<SyntaxNode>
    if !parser->successful || relation_type == null then
        destroy_syntax_tree(relation_type)
        return null
    end

    let clause: pointer<SyntaxNode> = parser_create_node(
        parser,
        kind,
        syntax_variant_none(),
        relation_type->text,
        relation_type->span
    )

    if clause == null then
        destroy_syntax_tree(relation_type)
        return null
    end

    syntax_add_child(clause, relation_type)
    return clause
end

fn parse_class_member(parser: pointer<Parser>) -> pointer<SyntaxNode>
    if parser_check(parser, token_kind_fn()) then
        return parse_function_declaration(parser)
    end

    if parser_check(parser, token_kind_identifier()) then
        let annotations: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()
        return parse_class_field_declaration(parser, annotations, parser_peek(parser))
    end

    if parser_check(parser, token_kind_at()) then
        return parse_annotated_class_member(parser)
    end

    parser_expected(parser, "a class field or method")
    return null
end

fn parse_annotated_class_member(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let member_start: Token = parser_peek(parser)
    let annotations: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()

    while parser_check(parser, token_kind_at()) && parser->successful do
        let at_token: Token = parser_advance(parser)

        if parser_match(parser, token_kind_fn()) then
            return parse_function_declaration_after_marker(
                parser,
                annotations,
                member_start,
                true
            )
        end

        let name_token: Token = parser_consume(
            parser,
            token_kind_identifier(),
            "an annotation name after '@'"
        )

        if parser->successful then
            let annotation: pointer<SyntaxNode> = parser_create_node(
                parser,
                syntax_kind_annotation(),
                syntax_variant_none(),
                name_token.lexeme,
                source_span(at_token.span.start, name_token.span.end_position)
            )

            if annotation != null then
                parser_add_name_child(parser, annotation, name_token)
                vector_push<pointer<SyntaxNode>>(annotations, annotation)
            end
        end

        parser_consume(
            parser,
            token_kind_newline(),
            "a newline after the class member annotation"
        )
    end

    if parser_match(parser, token_kind_fn()) then
        return parse_function_declaration_after_marker(
            parser,
            annotations,
            member_start,
            false
        )
    end

    if parser_check(parser, token_kind_identifier()) then
        return parse_class_field_declaration(
            parser,
            annotations,
            member_start
        )
    end

    if parser->successful then
        parser_expected(parser, "a field, 'fn', or '@fn' after the member annotations")
    end

    destroy_node_vector(annotations)
    return null
end

fn parse_class_field_declaration(
    parser: pointer<Parser>,
    annotations: pointer<Vector<pointer<SyntaxNode>>>,
    member_start: Token
) -> pointer<SyntaxNode>
    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "a class field name"
    )
    parser_consume(parser, token_kind_colon(), "':' after the class field name")
    let field_type: pointer<SyntaxNode> = parse_type_reference(
        parser,
        "a class field type after ':'"
    )

    if !parser->successful then
        destroy_node_vector(annotations)
        destroy_syntax_tree(field_type)
        return null
    end

    let field: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_class_field_declaration(),
        syntax_variant_none(),
        name_token.lexeme,
        source_span(member_start.span.start, field_type->span.end_position)
    )

    if field == null then
        destroy_node_vector(annotations)
        destroy_syntax_tree(field_type)
        return null
    end

    transfer_node_vector(field, annotations)
    parser_add_name_child(parser, field, name_token)
    syntax_add_child(field, field_type)
    return field
end

fn parse_struct_declaration(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let struct_token: Token = parser_consume(parser, token_kind_struct(), "'struct'")
    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "a struct name after 'struct'"
    )

    if !parser->successful then
        return null
    end

    let declaration: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_struct_declaration(),
        syntax_variant_none(),
        name_token.lexeme,
        source_span(struct_token.span.start, name_token.span.end_position)
    )

    if declaration == null then
        return null
    end

    parser_add_name_child(parser, declaration, name_token)
    parse_type_parameter_list(parser, declaration)
    parser_consume(
        parser,
        token_kind_newline(),
        "a newline after the struct declaration header"
    )
    parser_skip_newlines(parser)

    while !parser_check(parser, token_kind_end()) && !parser_is_at_end(parser) && parser->successful do
        let field: pointer<SyntaxNode> = parse_struct_field_declaration(parser)

        if parser->successful then
            syntax_add_child(declaration, field)

            if parser_match(parser, token_kind_newline()) then
                parser_skip_newlines(parser)
            else
                if !parser_check(parser, token_kind_end()) then
                    parser_expected(
                        parser,
                        "a newline or 'end' after the struct field"
                    )
                end
            end
        end
    end

    let end_token: Token = parser_consume(
        parser,
        token_kind_end(),
        "'end' to close the struct declaration"
    )

    if !parser->successful then
        destroy_syntax_tree(declaration)
        return null
    end

    declaration->span = source_span(
        struct_token.span.start,
        end_token.span.end_position
    )
    return declaration
end

fn parse_struct_field_declaration(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "a struct field name"
    )
    parser_consume(
        parser,
        token_kind_colon(),
        "':' after the struct field name"
    )
    let field_type: pointer<SyntaxNode> = parse_type_reference(
        parser,
        "a struct field type after ':'"
    )

    if !parser->successful then
        destroy_syntax_tree(field_type)
        return null
    end

    let field: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_struct_field_declaration(),
        syntax_variant_none(),
        name_token.lexeme,
        source_span(name_token.span.start, field_type->span.end_position)
    )

    if field == null then
        destroy_syntax_tree(field_type)
        return null
    end

    parser_add_name_child(parser, field, name_token)
    syntax_add_child(field, field_type)
    return field
end

fn parse_function_declaration(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let function_token: Token = parser_consume(parser, token_kind_fn(), "'fn'")
    let annotations: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()

    return parse_function_declaration_after_marker(
        parser,
        annotations,
        function_token,
        false
    )
end

fn parse_function_declaration_after_marker(
    parser: pointer<Parser>,
    annotations: pointer<Vector<pointer<SyntaxNode>>>,
    declaration_start: Token,
    bodyless: boolean
) -> pointer<SyntaxNode>
    @mut let name_expectation: string = "a function name after 'fn'"

    if bodyless then
        name_expectation = "a function name after '@fn'"
    end

    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        name_expectation
    )

    if !parser->successful then
        destroy_node_vector(annotations)
        return null
    end

    @mut let variant: int = syntax_function_with_body()

    if bodyless then
        variant = syntax_function_bodyless()
    end

    let declaration: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_function_declaration(),
        variant,
        name_token.lexeme,
        source_span(declaration_start.span.start, name_token.span.end_position)
    )

    if declaration == null then
        destroy_node_vector(annotations)
        return null
    end

    transfer_node_vector(declaration, annotations)
    parser_add_name_child(parser, declaration, name_token)
    parse_type_parameter_list(parser, declaration)
    parser_consume(
        parser,
        token_kind_left_paren(),
        "'(' after the function name"
    )
    parse_parameter_list(parser, declaration)
    parser_consume(
        parser,
        token_kind_right_paren(),
        "')' after the function parameter list"
    )
    parser_consume(
        parser,
        token_kind_arrow(),
        "'->' before the function return type"
    )
    let return_type: pointer<SyntaxNode> = parse_type_reference(
        parser,
        "a return type after '->'"
    )

    if parser->successful then
        syntax_add_child(declaration, return_type)
    else
        destroy_syntax_tree(return_type)
    end

    if bodyless && parser->successful then
        if !parser_check(parser, token_kind_newline()) && !parser_is_at_end(parser) then
            parser_expected(
                parser,
                "a newline or end of file after the bodyless function declaration"
            )
        end

        if parser->successful then
            declaration->span = source_span(
                declaration_start.span.start,
                return_type->span.end_position
            )
            return declaration
        end
    end

    if !bodyless && parser->successful then
        let header_newline: Token = parser_consume(
            parser,
            token_kind_newline(),
            "a newline after the function declaration header"
        )
        let body: pointer<SyntaxNode> = parse_statement_block(
            parser,
            header_newline.span.end_position,
            token_kind_end(),
            -1,
            "a newline or 'end' after the statement"
        )
        let end_token: Token = parser_consume(
            parser,
            token_kind_end(),
            "'end' to close the function declaration"
        )

        if parser->successful then
            body->span = source_span(
                body->span.start,
                end_token.span.end_position
            )
            syntax_add_child(declaration, body)
            declaration->span = source_span(
                declaration_start.span.start,
                end_token.span.end_position
            )
            return declaration
        end

        destroy_syntax_tree(body)
    end

    destroy_syntax_tree(declaration)
    return null
end

fn parse_parameter_list(parser: pointer<Parser>, declaration: pointer<SyntaxNode>) -> void
    parser_skip_delimited_newlines(parser)

    if parser_check(parser, token_kind_right_paren()) then
        return
    end

    while parser->successful do
        let parameter: pointer<SyntaxNode> = parse_parameter(parser)

        if parser->successful then
            syntax_add_child(declaration, parameter)
        else
            destroy_syntax_tree(parameter)
            return
        end

        parser_skip_delimited_newlines(parser)

        if !parser_match(parser, token_kind_comma()) then
            return
        end

        parser_skip_delimited_newlines(parser)

        if parser_check(parser, token_kind_right_paren()) then
            return
        end
    end

    return
end

fn parse_parameter(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "a parameter name"
    )
    parser_consume(parser, token_kind_colon(), "':' after the parameter name")
    let parameter_type: pointer<SyntaxNode> = parse_type_reference(
        parser,
        "a parameter type after ':'"
    )

    if !parser->successful then
        destroy_syntax_tree(parameter_type)
        return null
    end

    let parameter: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_parameter(),
        syntax_variant_none(),
        name_token.lexeme,
        source_span(name_token.span.start, parameter_type->span.end_position)
    )

    if parameter == null then
        destroy_syntax_tree(parameter_type)
        return null
    end

    parser_add_name_child(parser, parameter, name_token)
    syntax_add_child(parameter, parameter_type)
    return parameter
end

fn parse_type_parameter_list(parser: pointer<Parser>, declaration: pointer<SyntaxNode>) -> void
    if !parser_match(parser, token_kind_less()) then
        return
    end

    parser_skip_delimited_newlines(parser)
    @mut let reading_parameters: boolean = true

    while parser->successful && reading_parameters do
        let name_token: Token = parser_consume(
            parser,
            token_kind_identifier(),
            "a type parameter name after '<'"
        )

        if parser->successful then
            let parameter: pointer<SyntaxNode> = parser_create_node(
                parser,
                syntax_kind_type_parameter(),
                syntax_variant_none(),
                name_token.lexeme,
                name_token.span
            )

            if parameter != null then
                syntax_add_child(declaration, parameter)
            end
        end

        parser_skip_delimited_newlines(parser)

        if parser_match(parser, token_kind_comma()) then
            parser_skip_delimited_newlines(parser)

            if parser_check(parser, token_kind_greater()) then
                reading_parameters = false
            end
        else
            reading_parameters = false
        end
    end

    parser_consume(
        parser,
        token_kind_greater(),
        "'>' after the type parameter list"
    )
    return
end

fn parse_type_reference(
    parser: pointer<Parser>,
    expectation: string
) -> pointer<SyntaxNode>
    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        expectation
    )

    if !parser->successful then
        return null
    end

    let type_reference: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_type_reference(),
        syntax_variant_none(),
        name_token.lexeme,
        name_token.span
    )

    if type_reference == null then
        return null
    end

    parser_add_name_child(parser, type_reference, name_token)

    if parser_check(parser, token_kind_less()) then
        let closing: Token = parse_explicit_type_arguments(parser, type_reference)

        if parser->successful then
            type_reference->span = source_span(
                type_reference->span.start,
                closing.span.end_position
            )
        end
    end

    if !parser->successful then
        destroy_syntax_tree(type_reference)
        return null
    end

    return type_reference
end

fn parse_explicit_type_arguments(
    parser: pointer<Parser>,
    parent: pointer<SyntaxNode>
) -> Token
    let opening: Token = parser_consume(
        parser,
        token_kind_less(),
        "'<' before the explicit type arguments"
    )
    parser_skip_delimited_newlines(parser)

    @mut let reading_arguments: boolean = true

    while parser->successful && reading_arguments do
        let argument: pointer<SyntaxNode> = parse_type_reference(
            parser,
            "a type argument after '<'"
        )

        if parser->successful then
            syntax_add_child(parent, argument)
        else
            destroy_syntax_tree(argument)
            return opening
        end

        parser_skip_delimited_newlines(parser)

        if parser_match(parser, token_kind_comma()) then
            parser_skip_delimited_newlines(parser)

            if parser_check(parser, token_kind_greater()) then
                reading_arguments = false
            end
        else
            reading_arguments = false
        end
    end

    return parser_consume(
        parser,
        token_kind_greater(),
        "'>' after the explicit type arguments"
    )
end

fn parse_injection_declaration(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let inject_token: Token = parser_consume(parser, token_kind_inject(), "'inject'")
    @mut let variant: int = syntax_injection_direct()

    if parser_match(parser, token_kind_namespace()) then
        variant = syntax_injection_namespace()
    end

    @mut let path_expectation: string = "a module path after 'inject'"

    if variant == syntax_injection_namespace() then
        path_expectation = "a module path after 'namespace'"
    end

    let module_path: pointer<SyntaxNode> = parse_module_path(
        parser,
        path_expectation
    )

    if !parser->successful then
        destroy_syntax_tree(module_path)
        return null
    end

    let injection: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_injection_declaration(),
        variant,
        "",
        source_span(inject_token.span.start, module_path->span.end_position)
    )

    if injection == null then
        destroy_syntax_tree(module_path)
        return null
    end

    syntax_add_child(injection, module_path)

    if variant == syntax_injection_direct() then
        if parser_match(parser, token_kind_only()) then
            let selected: Token = parser_consume(
                parser,
                token_kind_identifier(),
                "an injected name after 'only'"
            )

            if parser->successful then
                parser_add_name_child(parser, injection, selected)
                injection->span = source_span(
                    injection->span.start,
                    selected.span.end_position
                )
            end

            while parser->successful && parser_check(parser, token_kind_comma()) do
                parser_advance(parser)
                let next_selected: Token = parser_consume(
                    parser,
                    token_kind_identifier(),
                    "an injected name after ','"
                )

                if parser->successful then
                    parser_add_name_child(parser, injection, next_selected)
                    injection->span = source_span(
                        injection->span.start,
                        next_selected.span.end_position
                    )
                end
            end
        end
    end

    if variant == syntax_injection_namespace() then
        if parser_match(parser, token_kind_as()) then
            let alias: Token = parser_consume(
                parser,
                token_kind_identifier(),
                "a namespace alias after 'as'"
            )

            if parser->successful then
                parser_add_name_child(parser, injection, alias)
                injection->text = alias.lexeme
                injection->span = source_span(
                    injection->span.start,
                    alias.span.end_position
                )
            end
        end
    end

    if parser->successful && !parser_check(parser, token_kind_newline()) && !parser_is_at_end(parser) then
        @mut let terminator: string = "a newline or end of file after the direct injection"

        if variant == syntax_injection_namespace() then
            terminator = "a newline or end of file after the namespace injection"
        end

        parser_expected(parser, terminator)
    end

    if !parser->successful then
        destroy_syntax_tree(injection)
        return null
    end

    return injection
end

fn parse_module_path(
    parser: pointer<Parser>,
    expectation: string
) -> pointer<SyntaxNode>
    let first: Token = parser_consume(
        parser,
        token_kind_identifier(),
        expectation
    )

    if !parser->successful then
        return null
    end

    let path: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_module_path(),
        syntax_variant_none(),
        first.lexeme,
        first.span
    )

    if path == null then
        return null
    end

    parser_add_name_child(parser, path, first)

    while parser->successful && parser_check(parser, token_kind_dot()) do
        parser_advance(parser)
        let segment: Token = parser_consume(
            parser,
            token_kind_identifier(),
            "a module path segment after '.'"
        )

        if parser->successful then
            parser_add_name_child(parser, path, segment)
            path->text = path->text + "." + segment.lexeme
            path->span = source_span(
                path->span.start,
                segment.span.end_position
            )
        end
    end

    if !parser->successful then
        destroy_syntax_tree(path)
        return null
    end

    return path
end

fn parse_statement_block(
    parser: pointer<Parser>,
    start: SourcePosition,
    first_terminator: int,
    second_terminator: int,
    separator_expectation: string
) -> pointer<SyntaxNode>
    let block: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_block(),
        syntax_variant_none(),
        "",
        source_span(start, start)
    )

    if block == null then
        return null
    end

    parser_skip_newlines(parser)

    while !parser_check_either(parser, first_terminator, second_terminator) && !parser_is_at_end(parser) && parser->successful do
        let statement: pointer<SyntaxNode> = parse_statement(parser)

        if parser->successful then
            syntax_add_child(block, statement)

            if parser_match(parser, token_kind_newline()) then
                parser_skip_newlines(parser)
            else
                if !parser_check_either(parser, first_terminator, second_terminator) then
                    parser_expected(parser, separator_expectation)
                end
            end
        end
    end

    if !parser->successful then
        destroy_syntax_tree(block)
        return null
    end

    block->span = source_span(
        block->span.start,
        parser_peek(parser).span.start
    )
    return block
end

fn parse_statement(parser: pointer<Parser>) -> pointer<SyntaxNode>
    if parser_check(parser, token_kind_return()) then
        return parse_return_statement(parser)
    end

    if parser_check(parser, token_kind_delete()) then
        return parse_delete_statement(parser)
    end

    if parser_check(parser, token_kind_const()) || parser_check(parser, token_kind_let()) || parser_check(parser, token_kind_at()) then
        return parse_variable_declaration_statement(parser)
    end

    if parser_check(parser, token_kind_identifier()) then
        return parse_identifier_started_statement(parser)
    end

    if parser_check(parser, token_kind_if()) then
        return parse_conditional_statement(parser)
    end

    if parser_check(parser, token_kind_while()) then
        return parse_while_statement(parser)
    end

    parser_expected(parser, "a statement")
    return null
end

fn parse_delete_statement(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let delete_token: Token = parser_consume(parser, token_kind_delete(), "'delete'")
    let operand: pointer<SyntaxNode> = parse_expression(parser)

    if !parser->successful then
        destroy_syntax_tree(operand)
        return null
    end

    let statement: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_delete_statement(),
        syntax_variant_none(),
        "",
        source_span(delete_token.span.start, operand->span.end_position)
    )

    if statement == null then
        destroy_syntax_tree(operand)
        return null
    end

    syntax_add_child(statement, operand)
    return statement
end

fn parse_while_statement(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let while_token: Token = parser_consume(parser, token_kind_while(), "'while'")
    let condition: pointer<SyntaxNode> = parse_expression(parser)
    parser_consume(parser, token_kind_do(), "'do' after the while condition")
    let header_newline: Token = parser_consume(
        parser,
        token_kind_newline(),
        "a newline after 'do'"
    )
    let body: pointer<SyntaxNode> = parse_statement_block(
        parser,
        header_newline.span.end_position,
        token_kind_end(),
        -1,
        "a newline or 'end' after the statement"
    )
    let end_token: Token = parser_consume(
        parser,
        token_kind_end(),
        "'end' to close the while statement"
    )

    if !parser->successful then
        destroy_syntax_tree(condition)
        destroy_syntax_tree(body)
        return null
    end

    let statement: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_while_statement(),
        syntax_variant_none(),
        "",
        source_span(while_token.span.start, end_token.span.end_position)
    )

    if statement == null then
        destroy_syntax_tree(condition)
        destroy_syntax_tree(body)
        return null
    end

    syntax_add_child(statement, condition)
    syntax_add_child(statement, body)
    return statement
end

fn parse_conditional_statement(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let if_token: Token = parser_consume(parser, token_kind_if(), "'if'")
    let condition: pointer<SyntaxNode> = parse_expression(parser)
    parser_consume(
        parser,
        token_kind_then(),
        "'then' after the conditional condition"
    )
    let then_newline: Token = parser_consume(
        parser,
        token_kind_newline(),
        "a newline after 'then'"
    )
    let then_block: pointer<SyntaxNode> = parse_statement_block(
        parser,
        then_newline.span.end_position,
        token_kind_else(),
        token_kind_end(),
        "a newline, 'else', or 'end' after the statement"
    )
    @mut let else_block: pointer<SyntaxNode> = null

    if parser_match(parser, token_kind_else()) then
        let else_newline: Token = parser_consume(
            parser,
            token_kind_newline(),
            "a newline after 'else'"
        )
        else_block = parse_statement_block(
            parser,
            else_newline.span.end_position,
            token_kind_end(),
            -1,
            "a newline or 'end' after the statement"
        )
    end

    let end_token: Token = parser_consume(
        parser,
        token_kind_end(),
        "'end' to close the conditional statement"
    )

    if !parser->successful then
        destroy_syntax_tree(condition)
        destroy_syntax_tree(then_block)
        destroy_syntax_tree(else_block)
        return null
    end

    let statement: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_conditional_statement(),
        syntax_variant_none(),
        "",
        source_span(if_token.span.start, end_token.span.end_position)
    )

    if statement == null then
        destroy_syntax_tree(condition)
        destroy_syntax_tree(then_block)
        destroy_syntax_tree(else_block)
        return null
    end

    syntax_add_child(statement, condition)
    syntax_add_child(statement, then_block)

    if else_block != null then
        syntax_add_child(statement, else_block)
    end

    return statement
end

fn parse_identifier_started_statement(parser: pointer<Parser>) -> pointer<SyntaxNode>
    if parser_check_next(parser, token_kind_assign()) then
        return parse_assignment_statement(parser)
    end

    if !parser_check_next(parser, token_kind_left_paren()) && !parser_check_next(parser, token_kind_double_colon()) && !parser_check_next(parser, token_kind_less()) && !parser_check_next(parser, token_kind_dot()) && !parser_check_next(parser, token_kind_arrow()) && !parser_check_next(parser, token_kind_left_bracket()) then
        return parse_assignment_statement(parser)
    end

    let expression: pointer<SyntaxNode> = parse_expression(parser)

    if !parser->successful then
        destroy_syntax_tree(expression)
        return null
    end

    if parser_match(parser, token_kind_assign()) then
        let value: pointer<SyntaxNode> = parse_expression(parser)

        if !parser->successful then
            destroy_syntax_tree(expression)
            destroy_syntax_tree(value)
            return null
        end

        @mut let statement_kind: int = 0

        if expression->kind == syntax_kind_field_access_expression() then
            statement_kind = syntax_kind_field_assignment_statement()
        end

        if expression->kind == syntax_kind_pointer_field_access_expression() then
            statement_kind = syntax_kind_pointer_field_assignment_statement()
        end

        if expression->kind == syntax_kind_index_expression() then
            statement_kind = syntax_kind_index_assignment_statement()
        end

        if statement_kind == 0 then
            parser_expected(parser, "a field access or string index before '='")
            destroy_syntax_tree(expression)
            destroy_syntax_tree(value)
            return null
        end

        let assignment: pointer<SyntaxNode> = parser_create_node(
            parser,
            statement_kind,
            syntax_variant_none(),
            "",
            source_span(expression->span.start, value->span.end_position)
        )

        if assignment == null then
            destroy_syntax_tree(expression)
            destroy_syntax_tree(value)
            return null
        end

        syntax_add_child(assignment, expression)
        syntax_add_child(assignment, value)
        return assignment
    end

    if expression->kind != syntax_kind_call_expression() then
        parser_expected(parser, "a function call statement")
        destroy_syntax_tree(expression)
        return null
    end

    let call_statement: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_call_statement(),
        syntax_variant_none(),
        "",
        expression->span
    )

    if call_statement == null then
        destroy_syntax_tree(expression)
        return null
    end

    syntax_add_child(call_statement, expression)
    return call_statement
end

fn parse_assignment_statement(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let target_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "an assignment target"
    )
    let target: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_name_expression(),
        syntax_variant_none(),
        target_token.lexeme,
        target_token.span
    )
    parser_consume(
        parser,
        token_kind_assign(),
        "'=' after the assignment target"
    )
    let value: pointer<SyntaxNode> = parse_expression(parser)

    if !parser->successful then
        destroy_syntax_tree(target)
        destroy_syntax_tree(value)
        return null
    end

    let statement: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_assignment_statement(),
        syntax_variant_none(),
        target_token.lexeme,
        source_span(target_token.span.start, value->span.end_position)
    )

    if statement == null then
        destroy_syntax_tree(target)
        destroy_syntax_tree(value)
        return null
    end

    syntax_add_child(statement, target)
    syntax_add_child(statement, value)
    return statement
end

fn parse_variable_declaration_statement(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let start_token: Token = parser_peek(parser)
    @mut let variant: int = syntax_variable_let()

    if parser_match(parser, token_kind_const()) then
        variant = syntax_variable_const()
    else
        if parser_match(parser, token_kind_let()) then
            variant = syntax_variable_let()
        else
            if parser_match(parser, token_kind_at()) then
                parser_consume_identifier_lexeme(
                    parser,
                    "mut",
                    "'mut' after '@'"
                )
                parser_consume(
                    parser,
                    token_kind_let(),
                    "'let' after '@mut'"
                )
                variant = syntax_variable_mutable_let()
            else
                parser_expected(parser, "a variable declaration")
            end
        end
    end

    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "a variable name"
    )
    parser_consume(parser, token_kind_colon(), "':' after the variable name")
    let variable_type: pointer<SyntaxNode> = parse_type_reference(
        parser,
        "a variable type after ':'"
    )
    parser_consume(
        parser,
        token_kind_assign(),
        "'=' after the variable type"
    )
    let initializer: pointer<SyntaxNode> = parse_expression(parser)

    if !parser->successful then
        destroy_syntax_tree(variable_type)
        destroy_syntax_tree(initializer)
        return null
    end

    let statement: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_variable_declaration_statement(),
        variant,
        name_token.lexeme,
        source_span(start_token.span.start, initializer->span.end_position)
    )

    if statement == null then
        destroy_syntax_tree(variable_type)
        destroy_syntax_tree(initializer)
        return null
    end

    parser_add_name_child(parser, statement, name_token)
    syntax_add_child(statement, variable_type)
    syntax_add_child(statement, initializer)
    return statement
end

fn parse_return_statement(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let return_token: Token = parser_consume(parser, token_kind_return(), "'return'")
    @mut let expression: pointer<SyntaxNode> = null
    @mut let end_position: SourcePosition = return_token.span.end_position

    if !parser_check(parser, token_kind_newline()) && !parser_check(parser, token_kind_end()) then
        expression = parse_expression(parser)

        if parser->successful then
            end_position = expression->span.end_position
        end
    end

    if !parser->successful then
        destroy_syntax_tree(expression)
        return null
    end

    let statement: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_return_statement(),
        syntax_variant_none(),
        "",
        source_span(return_token.span.start, end_position)
    )

    if statement == null then
        destroy_syntax_tree(expression)
        return null
    end

    if expression != null then
        syntax_add_child(statement, expression)
    end

    return statement
end

fn parse_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    return parse_logical_or_expression(parser)
end

fn parse_logical_or_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    @mut let expression: pointer<SyntaxNode> = parse_logical_and_expression(parser)

    while parser_check(parser, token_kind_or_or()) && parser->successful do
        parser_advance(parser)
        let right: pointer<SyntaxNode> = parse_logical_and_expression(parser)
        expression = parser_create_binary_expression(
            parser,
            expression,
            syntax_binary_or(),
            right
        )
    end

    return expression
end

fn parse_logical_and_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    @mut let expression: pointer<SyntaxNode> = parse_equality_expression(parser)

    while parser_check(parser, token_kind_and_and()) && parser->successful do
        parser_advance(parser)
        let right: pointer<SyntaxNode> = parse_equality_expression(parser)
        expression = parser_create_binary_expression(
            parser,
            expression,
            syntax_binary_and(),
            right
        )
    end

    return expression
end

fn parse_equality_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    @mut let expression: pointer<SyntaxNode> = parse_relational_expression(parser)

    while (parser_check(parser, token_kind_equal_equal()) || parser_check(parser, token_kind_not_equal())) && parser->successful do
        let operator_token: Token = parser_advance(parser)
        @mut let variant: int = syntax_binary_equal()

        if operator_token.kind == token_kind_not_equal() then
            variant = syntax_binary_not_equal()
        end

        let right: pointer<SyntaxNode> = parse_relational_expression(parser)
        expression = parser_create_binary_expression(
            parser,
            expression,
            variant,
            right
        )
    end

    return expression
end

fn parse_relational_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    @mut let expression: pointer<SyntaxNode> = parse_additive_expression(parser)

    while (parser_check(parser, token_kind_less()) || parser_check(parser, token_kind_less_equal()) || parser_check(parser, token_kind_greater()) || parser_check(parser, token_kind_greater_equal())) && parser->successful do
        let operator_token: Token = parser_advance(parser)
        @mut let variant: int = syntax_binary_less()

        if operator_token.kind == token_kind_less_equal() then
            variant = syntax_binary_less_equal()
        end

        if operator_token.kind == token_kind_greater() then
            variant = syntax_binary_greater()
        end

        if operator_token.kind == token_kind_greater_equal() then
            variant = syntax_binary_greater_equal()
        end

        let right: pointer<SyntaxNode> = parse_additive_expression(parser)
        expression = parser_create_binary_expression(
            parser,
            expression,
            variant,
            right
        )
    end

    return expression
end

fn parse_additive_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    @mut let expression: pointer<SyntaxNode> = parse_multiplicative_expression(parser)

    while (parser_check(parser, token_kind_plus()) || parser_check(parser, token_kind_minus())) && parser->successful do
        let operator_token: Token = parser_advance(parser)
        @mut let variant: int = syntax_binary_add()

        if operator_token.kind == token_kind_minus() then
            variant = syntax_binary_subtract()
        end

        let right: pointer<SyntaxNode> = parse_multiplicative_expression(parser)
        expression = parser_create_binary_expression(
            parser,
            expression,
            variant,
            right
        )
    end

    return expression
end

fn parse_multiplicative_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    @mut let expression: pointer<SyntaxNode> = parse_unary_expression(parser)

    while (parser_check(parser, token_kind_star()) || parser_check(parser, token_kind_slash()) || parser_check(parser, token_kind_percent())) && parser->successful do
        let operator_token: Token = parser_advance(parser)
        @mut let variant: int = syntax_binary_multiply()

        if operator_token.kind == token_kind_slash() then
            variant = syntax_binary_divide()
        end

        if operator_token.kind == token_kind_percent() then
            variant = syntax_binary_remainder()
        end

        let right: pointer<SyntaxNode> = parse_unary_expression(parser)
        expression = parser_create_binary_expression(
            parser,
            expression,
            variant,
            right
        )
    end

    return expression
end

fn parse_unary_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    if parser_check(parser, token_kind_bang()) || parser_check(parser, token_kind_minus()) || parser_check(parser, token_kind_plus()) then
        let operator_token: Token = parser_advance(parser)
        @mut let variant: int = syntax_unary_not()

        if operator_token.kind == token_kind_minus() then
            variant = syntax_unary_negative()
        end

        if operator_token.kind == token_kind_plus() then
            variant = syntax_unary_positive()
        end

        let operand: pointer<SyntaxNode> = parse_unary_expression(parser)

        if !parser->successful then
            destroy_syntax_tree(operand)
            return null
        end

        let expression: pointer<SyntaxNode> = parser_create_node(
            parser,
            syntax_kind_unary_expression(),
            variant,
            operator_token.lexeme,
            source_span(operator_token.span.start, operand->span.end_position)
        )

        if expression == null then
            destroy_syntax_tree(operand)
            return null
        end

        syntax_add_child(expression, operand)
        return expression
    end

    return parse_postfix_expression(parser)
end

fn parse_postfix_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    @mut let expression: pointer<SyntaxNode> = parse_primary_expression(parser)
    @mut let reading_postfix: boolean = true

    while parser->successful && reading_postfix do
        @mut let matched_postfix: boolean = false

        if parser_check(parser, token_kind_less()) && parser_looks_like_explicit_type_arguments(parser) then
            matched_postfix = true

            if parser_explicit_type_arguments_followed_by(parser, token_kind_left_brace()) then
                if expression->kind != syntax_kind_name_expression() then
                    parser_expected(
                        parser,
                        "a named struct type before explicit type arguments"
                    )
                    destroy_syntax_tree(expression)
                    return null
                end

                let type_reference: pointer<SyntaxNode> = parser_type_from_name_expression(
                    parser,
                    expression
                )
                expression = null

                if type_reference == null then
                    return null
                end

                let closing: Token = parse_explicit_type_arguments(
                    parser,
                    type_reference
                )

                if parser->successful then
                    type_reference->span = source_span(
                        type_reference->span.start,
                        closing.span.end_position
                    )
                    expression = parse_struct_construction_expression(
                        parser,
                        type_reference
                    )
                else
                    destroy_syntax_tree(type_reference)
                    return null
                end
            else
                expression = parse_call_expression(parser, expression, true)
            end
        end

        if !matched_postfix then
            if parser_check(parser, token_kind_left_brace()) && expression->kind == syntax_kind_name_expression() then
                matched_postfix = true
                let type_reference: pointer<SyntaxNode> = parser_type_from_name_expression(
                    parser,
                    expression
                )
                expression = null

                if type_reference == null then
                    return null
                end

                expression = parse_struct_construction_expression(
                    parser,
                    type_reference
                )
            end
        end

        if !matched_postfix then
            if parser_check(parser, token_kind_left_paren()) then
                matched_postfix = true
                expression = parse_call_expression(parser, expression, false)
            end
        end

        if !matched_postfix then
            if parser_match(parser, token_kind_dot()) then
                matched_postfix = true
                expression = parse_field_access_expression(
                    parser,
                    expression,
                    false
                )
            end
        end

        if !matched_postfix then
            if parser_match(parser, token_kind_arrow()) then
                matched_postfix = true
                expression = parse_field_access_expression(
                    parser,
                    expression,
                    true
                )
            end
        end

        if !matched_postfix then
            if parser_match(parser, token_kind_left_bracket()) then
                matched_postfix = true
                let index: pointer<SyntaxNode> = parse_expression(parser)
                let right_bracket: Token = parser_consume(
                    parser,
                    token_kind_right_bracket(),
                    "']' after the string index"
                )

                if !parser->successful then
                    destroy_syntax_tree(expression)
                    destroy_syntax_tree(index)
                    return null
                end

                let indexed: pointer<SyntaxNode> = parser_create_node(
                    parser,
                    syntax_kind_index_expression(),
                    syntax_variant_none(),
                    "",
                    source_span(
                        expression->span.start,
                        right_bracket.span.end_position
                    )
                )

                if indexed == null then
                    destroy_syntax_tree(expression)
                    destroy_syntax_tree(index)
                    return null
                end

                syntax_add_child(indexed, expression)
                syntax_add_child(indexed, index)
                expression = indexed
            end
        end

        if !matched_postfix then
            reading_postfix = false
        end
    end

    return expression
end

fn parse_field_access_expression(
    parser: pointer<Parser>,
    target: pointer<SyntaxNode>,
    pointer_access: boolean
) -> pointer<SyntaxNode>
    @mut let expectation: string = "a field name after '.'"
    @mut let kind: int = syntax_kind_field_access_expression()

    if pointer_access then
        expectation = "a field name after '->'"
        kind = syntax_kind_pointer_field_access_expression()
    end

    let field_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        expectation
    )

    if !parser->successful then
        destroy_syntax_tree(target)
        return null
    end

    let access: pointer<SyntaxNode> = parser_create_node(
        parser,
        kind,
        syntax_variant_none(),
        field_token.lexeme,
        source_span(target->span.start, field_token.span.end_position)
    )

    if access == null then
        destroy_syntax_tree(target)
        return null
    end

    syntax_add_child(access, target)
    parser_add_name_child(parser, access, field_token)
    return access
end

fn parse_call_expression(
    parser: pointer<Parser>,
    callee: pointer<SyntaxNode>,
    has_explicit_type_arguments: boolean
) -> pointer<SyntaxNode>
    let call: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_call_expression(),
        syntax_variant_none(),
        "",
        callee->span
    )

    if call == null then
        destroy_syntax_tree(callee)
        return null
    end

    syntax_add_child(call, callee)

    if has_explicit_type_arguments then
        parse_explicit_type_arguments(parser, call)
    end

    parser_consume(
        parser,
        token_kind_left_paren(),
        "'(' after the called expression"
    )
    parse_call_argument_list(parser, call)
    let right_parenthesis: Token = parser_consume(
        parser,
        token_kind_right_paren(),
        "')' after the function call arguments"
    )

    if !parser->successful then
        destroy_syntax_tree(call)
        return null
    end

    call->span = source_span(
        call->span.start,
        right_parenthesis.span.end_position
    )
    return call
end

fn parse_call_argument_list(
    parser: pointer<Parser>,
    call: pointer<SyntaxNode>
) -> void
    parser_skip_delimited_newlines(parser)

    if parser_check(parser, token_kind_right_paren()) then
        return
    end

    while parser->successful do
        let argument: pointer<SyntaxNode> = parse_expression(parser)

        if parser->successful then
            syntax_add_child(call, argument)
        else
            destroy_syntax_tree(argument)
            return
        end

        parser_skip_delimited_newlines(parser)

        if !parser_match(parser, token_kind_comma()) then
            return
        end

        parser_skip_delimited_newlines(parser)

        if parser_check(parser, token_kind_right_paren()) then
            return
        end
    end

    return
end

fn parse_struct_construction_expression(
    parser: pointer<Parser>,
    type_reference: pointer<SyntaxNode>
) -> pointer<SyntaxNode>
    parser_consume(
        parser,
        token_kind_left_brace(),
        "'{' after the struct type name"
    )

    let construction: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_struct_construction_expression(),
        syntax_variant_none(),
        type_reference->text,
        type_reference->span
    )

    if construction == null then
        destroy_syntax_tree(type_reference)
        return null
    end

    syntax_add_child(construction, type_reference)
    parser_skip_delimited_newlines(parser)

    if !parser_check(parser, token_kind_right_brace()) then
        @mut let reading_fields: boolean = true

        while parser->successful && reading_fields do
            let initializer: pointer<SyntaxNode> = parse_struct_field_initializer(
                parser
            )

            if parser->successful then
                syntax_add_child(construction, initializer)
            else
                destroy_syntax_tree(initializer)
            end

            parser_skip_delimited_newlines(parser)

            if parser_match(parser, token_kind_comma()) then
                parser_skip_delimited_newlines(parser)

                if parser_check(parser, token_kind_right_brace()) then
                    reading_fields = false
                end
            else
                reading_fields = false
            end
        end
    end

    let right_brace: Token = parser_consume(
        parser,
        token_kind_right_brace(),
        "'}' after the struct field initializers"
    )

    if !parser->successful then
        destroy_syntax_tree(construction)
        return null
    end

    construction->span = source_span(
        construction->span.start,
        right_brace.span.end_position
    )
    return construction
end

fn parse_struct_field_initializer(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "a struct field initializer name"
    )
    parser_consume(
        parser,
        token_kind_colon(),
        "':' after the struct field initializer name"
    )
    let value: pointer<SyntaxNode> = parse_expression(parser)

    if !parser->successful then
        destroy_syntax_tree(value)
        return null
    end

    let initializer: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_struct_field_initializer(),
        syntax_variant_none(),
        name_token.lexeme,
        source_span(name_token.span.start, value->span.end_position)
    )

    if initializer == null then
        destroy_syntax_tree(value)
        return null
    end

    parser_add_name_child(parser, initializer, name_token)
    syntax_add_child(initializer, value)
    return initializer
end

fn parse_primary_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    if parser_check(parser, token_kind_integer_literal()) || parser_check(parser, token_kind_float_literal()) || parser_check(parser, token_kind_true()) || parser_check(parser, token_kind_false()) || parser_check(parser, token_kind_char_literal()) || parser_check(parser, token_kind_string_literal()) then
        return parse_literal_expression(parser)
    end

    if parser_check(parser, token_kind_null()) then
        let null_token: Token = parser_advance(parser)
        return parser_create_node(
            parser,
            syntax_kind_null_expression(),
            syntax_variant_none(),
            null_token.lexeme,
            null_token.span
        )
    end

    if parser_check(parser, token_kind_new()) then
        return parse_new_expression(parser)
    end

    if parser_check(parser, token_kind_identifier()) then
        return parse_name_or_qualified_name_expression(parser)
    end

    if parser_check(parser, token_kind_left_paren()) then
        return parse_parenthesized_expression(parser)
    end

    parser_expected(parser, "an expression")
    return null
end

fn parse_new_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let new_token: Token = parser_consume(parser, token_kind_new(), "'new'")
    let class_type: pointer<SyntaxNode> = parse_class_type_name(
        parser,
        "a class name after 'new'"
    )

    if !parser->successful then
        destroy_syntax_tree(class_type)
        return null
    end

    let expression: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_new_expression(),
        syntax_variant_none(),
        class_type->text,
        source_span(new_token.span.start, class_type->span.end_position)
    )

    if expression == null then
        destroy_syntax_tree(class_type)
        return null
    end

    syntax_add_child(expression, class_type)
    parser_consume(
        parser,
        token_kind_left_paren(),
        "'(' after the allocated class name"
    )
    parse_call_argument_list(parser, expression)
    let right_parenthesis: Token = parser_consume(
        parser,
        token_kind_right_paren(),
        "')' after the constructor arguments"
    )

    if !parser->successful then
        destroy_syntax_tree(expression)
        return null
    end

    expression->span = source_span(
        new_token.span.start,
        right_parenthesis.span.end_position
    )
    return expression
end

fn parse_literal_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let literal_token: Token = parser_peek(parser)
    @mut let variant: int = syntax_literal_integer()

    if literal_token.kind == token_kind_float_literal() then
        variant = syntax_literal_float()
    end

    if literal_token.kind == token_kind_string_literal() then
        variant = syntax_literal_string()
    end

    if literal_token.kind == token_kind_char_literal() then
        variant = syntax_literal_char()
    end

    if literal_token.kind == token_kind_true() || literal_token.kind == token_kind_false() then
        variant = syntax_literal_boolean()
    end

    parser_advance(parser)
    return parser_create_node(
        parser,
        syntax_kind_literal_expression(),
        variant,
        literal_token.lexeme,
        literal_token.span
    )
end

fn parse_name_or_qualified_name_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let qualifier: pointer<SyntaxNode> = parse_name_expression(parser)

    if !parser_match(parser, token_kind_double_colon()) then
        return qualifier
    end

    let member: pointer<SyntaxNode> = parse_name_expression(parser)

    if parser_check(parser, token_kind_double_colon()) then
        parser_expected(
            parser,
            "a call or expression operator after the namespace-qualified name"
        )
    end

    if !parser->successful then
        destroy_syntax_tree(qualifier)
        destroy_syntax_tree(member)
        return null
    end

    let qualified: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_qualified_name_expression(),
        syntax_variant_none(),
        qualifier->text + "::" + member->text,
        source_span(qualifier->span.start, member->span.end_position)
    )

    if qualified == null then
        destroy_syntax_tree(qualifier)
        destroy_syntax_tree(member)
        return null
    end

    syntax_add_child(qualified, qualifier)
    syntax_add_child(qualified, member)
    return qualified
end

fn parse_name_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let name_token: Token = parser_consume(
        parser,
        token_kind_identifier(),
        "an identifier"
    )

    if !parser->successful then
        return null
    end

    return parser_create_node(
        parser,
        syntax_kind_name_expression(),
        syntax_variant_none(),
        name_token.lexeme,
        name_token.span
    )
end

fn parse_parenthesized_expression(parser: pointer<Parser>) -> pointer<SyntaxNode>
    let left_parenthesis: Token = parser_consume(
        parser,
        token_kind_left_paren(),
        "'('"
    )
    let inner: pointer<SyntaxNode> = parse_expression(parser)
    let right_parenthesis: Token = parser_consume(
        parser,
        token_kind_right_paren(),
        "')' after the parenthesized expression"
    )

    if !parser->successful then
        destroy_syntax_tree(inner)
        return null
    end

    let expression: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_parenthesized_expression(),
        syntax_variant_none(),
        "",
        source_span(
            left_parenthesis.span.start,
            right_parenthesis.span.end_position
        )
    )

    if expression == null then
        destroy_syntax_tree(inner)
        return null
    end

    syntax_add_child(expression, inner)
    return expression
end

fn parser_type_from_name_expression(
    parser: pointer<Parser>,
    name: pointer<SyntaxNode>
) -> pointer<SyntaxNode>
    let type_reference: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_type_reference(),
        syntax_variant_none(),
        name->text,
        name->span
    )

    if type_reference == null then
        destroy_syntax_tree(name)
        return null
    end

    let name_child: pointer<SyntaxNode> = create_syntax_name(
        name->text,
        name->span
    )

    if name_child == null then
        parser_fail(
            parser,
            "SOL-P000",
            "Unable to allocate syntax name node.",
            name->span
        )
        destroy_syntax_tree(name)
        destroy_syntax_tree(type_reference)
        return null
    end

    syntax_add_child(type_reference, name_child)
    destroy_syntax_tree(name)
    return type_reference
end

fn parser_create_binary_expression(
    parser: pointer<Parser>,
    left: pointer<SyntaxNode>,
    variant: int,
    right: pointer<SyntaxNode>
) -> pointer<SyntaxNode>
    if !parser->successful || left == null || right == null then
        destroy_syntax_tree(left)
        destroy_syntax_tree(right)
        return null
    end

    let expression: pointer<SyntaxNode> = parser_create_node(
        parser,
        syntax_kind_binary_expression(),
        variant,
        "",
        source_span(left->span.start, right->span.end_position)
    )

    if expression == null then
        destroy_syntax_tree(left)
        destroy_syntax_tree(right)
        return null
    end

    syntax_add_child(expression, left)
    syntax_add_child(expression, right)
    return expression
end

fn parser_looks_like_explicit_type_arguments(parser: pointer<Parser>) -> boolean
    return parser_explicit_type_arguments_followed_by(
        parser,
        token_kind_left_paren()
    ) || parser_explicit_type_arguments_followed_by(
        parser,
        token_kind_left_brace()
    )
end

fn parser_explicit_type_arguments_followed_by(
    parser: pointer<Parser>,
    following_kind: int
) -> boolean
    if !parser_check(parser, token_kind_less()) then
        return false
    end

    @mut let depth: int = 0
    @mut let index: int = parser->current
    let count: int = vector_length<Token>(parser->tokens)

    while index < count do
        let kind: int = vector_get<Token>(parser->tokens, index).kind

        if kind == token_kind_less() then
            depth = depth + 1
        else
            if kind == token_kind_greater() then
                depth = depth - 1

                if depth < 0 then
                    return false
                end

                if depth == 0 then
                    let next: int = index + 1

                    if next >= count then
                        return false
                    end

                    return vector_get<Token>(parser->tokens, next).kind == following_kind
                end
            else
                if kind != token_kind_identifier() && kind != token_kind_comma() && kind != token_kind_newline() then
                    return false
                end
            end
        end

        index = index + 1
    end

    return false
end

fn parser_skip_newlines(parser: pointer<Parser>) -> void
    while parser_match(parser, token_kind_newline()) do
    end

    return
end

fn parser_skip_delimited_newlines(parser: pointer<Parser>) -> void
    while parser_match(parser, token_kind_newline()) do
    end

    return
end

fn parser_check_either(
    parser: pointer<Parser>,
    first_kind: int,
    second_kind: int
) -> boolean
    if parser_check(parser, first_kind) then
        return true
    end

    if second_kind >= 0 && parser_check(parser, second_kind) then
        return true
    end

    return false
end

fn parser_match(parser: pointer<Parser>, kind: int) -> boolean
    if !parser_check(parser, kind) then
        return false
    end

    parser_advance(parser)
    return true
end

fn parser_check(parser: pointer<Parser>, kind: int) -> boolean
    return parser_peek(parser).kind == kind
end

fn parser_check_next(parser: pointer<Parser>, kind: int) -> boolean
    let next: int = parser->current + 1

    if next >= vector_length<Token>(parser->tokens) then
        return false
    end

    return vector_get<Token>(parser->tokens, next).kind == kind
end

fn parser_advance(parser: pointer<Parser>) -> Token
    let current: Token = parser_peek(parser)

    if !parser_is_at_end(parser) then
        parser->current = parser->current + 1
    end

    return current
end

fn parser_consume(parser: pointer<Parser>, kind: int, expectation: string) -> Token
    if parser_check(parser, kind) then
        return parser_advance(parser)
    end

    let actual: Token = parser_peek(parser)

    parser_fail(
        parser,
        "SOL-P002",
        "Expected " + expectation + ", but found " + describe_token(actual) + ".",
        actual.span
    )
    return actual
end

fn parser_consume_identifier_lexeme(
    parser: pointer<Parser>,
    expected_lexeme: string,
    expectation: string
) -> Token
    let actual: Token = parser_consume(
        parser,
        token_kind_identifier(),
        expectation
    )

    if parser->successful && actual.lexeme != expected_lexeme then
        parser_fail(
            parser,
            "SOL-P002",
            "Expected " + expectation + ", but found " + describe_token(actual) + ".",
            actual.span
        )
    end

    return actual
end

fn parser_expected(parser: pointer<Parser>, expectation: string) -> void
    let actual: Token = parser_peek(parser)
    parser_fail(
        parser,
        "SOL-P002",
        "Expected " + expectation + ", but found " + describe_token(actual) + ".",
        actual.span
    )
    return
end

fn parser_create_node(
    parser: pointer<Parser>,
    kind: int,
    variant: int,
    text: string,
    span: SourceSpan
) -> pointer<SyntaxNode>
    let node: pointer<SyntaxNode> = create_syntax_node(
        kind,
        variant,
        text,
        span
    )

    if node == null then
        parser_fail(
            parser,
            "SOL-P000",
            "Unable to allocate syntax node.",
            span
        )
    end

    return node
end

fn parser_add_name_child(
    parser: pointer<Parser>,
    parent: pointer<SyntaxNode>,
    name: Token
) -> void
    let child: pointer<SyntaxNode> = create_syntax_name(name.lexeme, name.span)

    if child == null then
        parser_fail(
            parser,
            "SOL-P000",
            "Unable to allocate syntax name node.",
            name.span
        )
        return
    end

    syntax_add_child(parent, child)
    return
end

fn transfer_node_vector(
    parent: pointer<SyntaxNode>,
    nodes: pointer<Vector<pointer<SyntaxNode>>>
) -> void
    @mut let index: int = 0
    let count: int = vector_length<pointer<SyntaxNode>>(nodes)

    while index < count do
        syntax_add_child(
            parent,
            vector_get<pointer<SyntaxNode>>(nodes, index)
        )
        index = index + 1
    end

    destroy_vector<pointer<SyntaxNode>>(nodes)
    return
end

fn destroy_node_vector(nodes: pointer<Vector<pointer<SyntaxNode>>>) -> void
    @mut let index: int = 0
    let count: int = vector_length<pointer<SyntaxNode>>(nodes)

    while index < count do
        destroy_syntax_tree(
            vector_get<pointer<SyntaxNode>>(nodes, index)
        )
        index = index + 1
    end

    destroy_vector<pointer<SyntaxNode>>(nodes)
    return
end

fn parser_is_at_end(parser: pointer<Parser>) -> boolean
    return parser_check(parser, token_kind_eof())
end

fn parser_peek(parser: pointer<Parser>) -> Token
    return vector_get<Token>(parser->tokens, parser->current)
end

fn parser_complete_source_span(parser: pointer<Parser>) -> SourceSpan
    let first: Token = vector_get<Token>(parser->tokens, 0)
    let last: Token = vector_get<Token>(
        parser->tokens,
        vector_length<Token>(parser->tokens) - 1
    )

    return source_span(first.span.start, last.span.end_position)
end

fn describe_token(token: Token) -> string
    if token.kind == token_kind_eof() then
        return "end of file"
    end

    if token.kind == token_kind_newline() then
        return "newline"
    end

    return "'" + token.lexeme + "'"
end

fn parser_fail_at_zero(parser: pointer<Parser>, code: string, message: string) -> void
    let position: SourcePosition = source_position(0, 1, 1)
    parser->successful = false
    parser->diagnostic = diagnostic(code, message, position, position)
    return
end

fn parser_fail(parser: pointer<Parser>, code: string, message: string, span: SourceSpan) -> void
    if !parser->successful then
        return
    end

    parser->successful = false
    parser->diagnostic = diagnostic(
        code,
        message,
        span.start,
        span.end_position
    )
    return
end
