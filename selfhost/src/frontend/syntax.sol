inject namespace std.memory as memory
inject std.collections.vector
inject frontend.source only SourceSpan

struct SyntaxNode
    kind: int
    variant: int
    text: string
    span: SourceSpan
    children: pointer<Vector<pointer<SyntaxNode>>>
end

fn create_syntax_node(kind: int, variant: int, text: string, span: SourceSpan) -> pointer<SyntaxNode>
    let node: pointer<SyntaxNode> = memory::allocate<SyntaxNode>(1)

    if node == null then
        return null
    end

    node->kind = kind
    node->variant = variant
    node->text = text
    node->span = span
    node->children = create_vector<pointer<SyntaxNode>>()
    return node
end

fn create_compilation_unit(span: SourceSpan) -> pointer<SyntaxNode>
    return create_syntax_node(
        syntax_kind_compilation_unit(),
        syntax_variant_none(),
        "",
        span
    )
end

fn syntax_add_child(parent: pointer<SyntaxNode>, child: pointer<SyntaxNode>) -> boolean
    if parent == null || child == null then
        return false
    end

    vector_push<pointer<SyntaxNode>>(parent->children, child)
    return true
end

fn syntax_child_count(node: pointer<SyntaxNode>) -> int
    if node == null then
        return 0
    end

    return vector_length<pointer<SyntaxNode>>(node->children)
end

fn syntax_child(node: pointer<SyntaxNode>, index: int) -> pointer<SyntaxNode>
    return vector_get<pointer<SyntaxNode>>(node->children, index)
end

fn destroy_syntax_tree(node: pointer<SyntaxNode>) -> void
    if node == null then
        return
    end

    @mut let index: int = 0
    let count: int = vector_length<pointer<SyntaxNode>>(node->children)

    while index < count do
        destroy_syntax_tree(vector_get<pointer<SyntaxNode>>(node->children, index))
        index = index + 1
    end

    destroy_vector<pointer<SyntaxNode>>(node->children)
    node->children = null
    memory::free<SyntaxNode>(node)
    return
end

fn syntax_variant_none() -> int
    return 0
end

fn syntax_kind_compilation_unit() -> int
    return 1
end

fn syntax_kind_annotation() -> int
    return 2
end

fn syntax_kind_type_parameter() -> int
    return 3
end

fn syntax_kind_parameter() -> int
    return 4
end

fn syntax_kind_type_reference() -> int
    return 5
end

fn syntax_kind_module_path() -> int
    return 6
end

fn syntax_kind_function_declaration() -> int
    return 7
end

fn syntax_kind_struct_declaration() -> int
    return 8
end

fn syntax_kind_struct_field_declaration() -> int
    return 9
end

fn syntax_kind_injection_declaration() -> int
    return 10
end

fn syntax_kind_block() -> int
    return 11
end

fn syntax_kind_variable_declaration_statement() -> int
    return 12
end

fn syntax_kind_assignment_statement() -> int
    return 13
end

fn syntax_kind_field_assignment_statement() -> int
    return 14
end

fn syntax_kind_pointer_field_assignment_statement() -> int
    return 15
end

fn syntax_kind_index_assignment_statement() -> int
    return 16
end

fn syntax_kind_call_statement() -> int
    return 17
end

fn syntax_kind_return_statement() -> int
    return 18
end

fn syntax_kind_conditional_statement() -> int
    return 19
end

fn syntax_kind_while_statement() -> int
    return 20
end

fn syntax_kind_name_expression() -> int
    return 21
end

fn syntax_kind_qualified_name_expression() -> int
    return 22
end

fn syntax_kind_literal_expression() -> int
    return 23
end

fn syntax_kind_null_expression() -> int
    return 24
end

fn syntax_kind_parenthesized_expression() -> int
    return 25
end

fn syntax_kind_unary_expression() -> int
    return 26
end

fn syntax_kind_binary_expression() -> int
    return 27
end

fn syntax_kind_call_expression() -> int
    return 28
end

fn syntax_kind_field_access_expression() -> int
    return 29
end

fn syntax_kind_pointer_field_access_expression() -> int
    return 30
end

fn syntax_kind_index_expression() -> int
    return 31
end

fn syntax_kind_struct_construction_expression() -> int
    return 32
end

fn syntax_kind_struct_field_initializer() -> int
    return 33
end

fn syntax_kind_name() -> int
    return 34
end

fn syntax_literal_integer() -> int
    return 1
end

fn syntax_literal_float() -> int
    return 2
end

fn syntax_literal_string() -> int
    return 3
end

fn syntax_literal_char() -> int
    return 4
end

fn syntax_literal_boolean() -> int
    return 5
end

fn syntax_unary_positive() -> int
    return 1
end

fn syntax_unary_negative() -> int
    return 2
end

fn syntax_unary_not() -> int
    return 3
end

fn syntax_binary_add() -> int
    return 1
end

fn syntax_binary_subtract() -> int
    return 2
end

fn syntax_binary_multiply() -> int
    return 3
end

fn syntax_binary_divide() -> int
    return 4
end

fn syntax_binary_remainder() -> int
    return 5
end

fn syntax_binary_equal() -> int
    return 6
end

fn syntax_binary_not_equal() -> int
    return 7
end

fn syntax_binary_less() -> int
    return 8
end

fn syntax_binary_less_equal() -> int
    return 9
end

fn syntax_binary_greater() -> int
    return 10
end

fn syntax_binary_greater_equal() -> int
    return 11
end

fn syntax_binary_and() -> int
    return 12
end

fn syntax_binary_or() -> int
    return 13
end

fn syntax_variable_let() -> int
    return 1
end

fn syntax_variable_const() -> int
    return 2
end

fn syntax_variable_mutable_let() -> int
    return 3
end

fn syntax_injection_direct() -> int
    return 1
end

fn syntax_injection_namespace() -> int
    return 2
end

fn syntax_function_with_body() -> int
    return 1
end

fn syntax_function_bodyless() -> int
    return 2
end
