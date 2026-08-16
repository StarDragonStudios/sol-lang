inject frontend.syntax
inject semantics.types
inject semantics.model only SemanticProgram, semantic_program_add_diagnostic

fn semantic_check_unary(
    program: pointer<SemanticProgram>,
    module_name: string,
    expression: pointer<SyntaxNode>,
    operand: pointer<SemanticType>
) -> pointer<SemanticType>
    if operand == null then
        return null
    end

    if operand->kind == semantic_type_kind_error() then
        return operand
    end

    if expression->variant == syntax_unary_not() && operand->name == "boolean" then
        return operand
    end

    if expression->variant == syntax_unary_positive() || expression->variant == syntax_unary_negative() then
        if operand->numeric then
            return operand
        end
    end

    semantic_program_add_diagnostic(
        program,
        module_name,
        semantic_diagnostic_invalid_unary_operand(),
        "Unary operator '" + semantic_unary_spelling(expression->variant) + "' is not defined for type '" + operand->name + "'.",
        expression->span
    )
    return type_catalog_error(program->catalog)
end

fn semantic_check_binary(
    program: pointer<SemanticProgram>,
    module_name: string,
    expression: pointer<SyntaxNode>,
    left: pointer<SemanticType>,
    right: pointer<SemanticType>
) -> pointer<SemanticType>
    if left == null || right == null then
        return null
    end

    if left->kind == semantic_type_kind_error() || right->kind == semantic_type_kind_error() then
        return type_catalog_error(program->catalog)
    end

    let variant: int = expression->variant
    @mut let valid: boolean = false
    @mut let result: pointer<SemanticType> = left

    if variant == syntax_binary_multiply() || variant == syntax_binary_divide() || variant == syntax_binary_subtract() then
        valid = left->numeric && semantic_type_equals(left, right)
    end

    if variant == syntax_binary_add() then
        valid = left->numeric && semantic_type_equals(left, right)

        if left->name == "string" && right->name == "string" then
            valid = true
        end
    end

    if variant == syntax_binary_remainder() then
        valid = left->name == "int" && right->name == "int"
        result = type_catalog_lookup(program->catalog, "int")
    end

    if variant == syntax_binary_less() || variant == syntax_binary_less_equal() || variant == syntax_binary_greater() || variant == syntax_binary_greater_equal() then
        valid = left->numeric && semantic_type_equals(left, right)
        result = type_catalog_lookup(program->catalog, "boolean")
    end

    if variant == syntax_binary_equal() || variant == syntax_binary_not_equal() then
        valid = semantic_equality_types(left, right)
        result = type_catalog_lookup(program->catalog, "boolean")
    end

    if variant == syntax_binary_and() || variant == syntax_binary_or() then
        valid = left->name == "boolean" && right->name == "boolean"
        result = type_catalog_lookup(program->catalog, "boolean")
    end

    if valid then
        return result
    end

    semantic_program_add_diagnostic(
        program,
        module_name,
        semantic_diagnostic_invalid_binary_operands(),
        "Binary operator '" + semantic_binary_spelling(variant) + "' is not defined for types '" + left->name + "' and '" + right->name + "'.",
        expression->span
    )
    return type_catalog_error(program->catalog)
end

fn semantic_equality_types(
    left: pointer<SemanticType>,
    right: pointer<SemanticType>
) -> boolean
    if !semantic_type_equals(left, right) then
        return false
    end

    if left->kind == semantic_type_kind_pointer() then
        return true
    end

    return left->name == "int" || left->name == "float" || left->name == "boolean" || left->name == "char" || left->name == "string"
end

fn semantic_unary_spelling(variant: int) -> string
    if variant == syntax_unary_not() then
        return "!"
    end

    if variant == syntax_unary_negative() then
        return "-"
    end

    return "+"
end

fn semantic_binary_spelling(variant: int) -> string
    if variant == syntax_binary_multiply() then
        return "*"
    end

    if variant == syntax_binary_divide() then
        return "/"
    end

    if variant == syntax_binary_remainder() then
        return "%"
    end

    if variant == syntax_binary_add() then
        return "+"
    end

    if variant == syntax_binary_subtract() then
        return "-"
    end

    if variant == syntax_binary_less() then
        return "<"
    end

    if variant == syntax_binary_less_equal() then
        return "<="
    end

    if variant == syntax_binary_greater() then
        return ">"
    end

    if variant == syntax_binary_greater_equal() then
        return ">="
    end

    if variant == syntax_binary_equal() then
        return "=="
    end

    if variant == syntax_binary_not_equal() then
        return "!="
    end

    if variant == syntax_binary_and() then
        return "&&"
    end

    return "||"
end

fn semantic_diagnostic_invalid_unary_operand() -> string
    return "SOL-S004"
end

fn semantic_diagnostic_invalid_binary_operands() -> string
    return "SOL-S005"
end
