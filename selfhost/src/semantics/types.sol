inject namespace std.memory as memory
inject std.collections.vector
inject frontend.syntax only SyntaxNode, syntax_child, syntax_child_count, syntax_kind_struct_declaration, syntax_kind_type_parameter, syntax_literal_boolean, syntax_literal_char, syntax_literal_float, syntax_literal_integer, syntax_literal_string

struct SemanticType
    kind: int
    name: string
    value_type: boolean
    numeric: boolean
    integral: boolean
    identity: pointer<SyntaxNode>
    element_type: pointer<SemanticType>
    arguments: pointer<Vector<pointer<SemanticType>>>
end

struct TypeCatalog
    primitives: pointer<Vector<pointer<SemanticType>>>
    error_type: pointer<SemanticType>
end

fn create_type_catalog() -> pointer<TypeCatalog>
    let catalog: pointer<TypeCatalog> = memory::allocate<TypeCatalog>(1)

    if catalog == null then
        return null
    end

    catalog->primitives = create_vector<pointer<SemanticType>>()
    catalog->error_type = null

    type_catalog_add_primitive(catalog, "int", true, true, true)
    type_catalog_add_primitive(catalog, "float", true, true, false)
    type_catalog_add_primitive(catalog, "boolean", true, false, false)
    type_catalog_add_primitive(catalog, "char", true, false, false)
    type_catalog_add_primitive(catalog, "string", true, false, false)
    type_catalog_add_primitive(catalog, "void", false, false, false)
    catalog->error_type = create_semantic_type(
        semantic_type_kind_error(),
        "<error>",
        false,
        false,
        false,
        null,
        null
    )

    if vector_length<pointer<SemanticType>>(catalog->primitives) != 6 || catalog->error_type == null then
        destroy_type_catalog(catalog)
        return null
    end

    return catalog
end

fn destroy_type_catalog(catalog: pointer<TypeCatalog>) -> void
    if catalog == null then
        return
    end

    @mut let index: int = 0
    let count: int = vector_length<pointer<SemanticType>>(catalog->primitives)

    while index < count do
        destroy_semantic_type(
            vector_get<pointer<SemanticType>>(catalog->primitives, index)
        )
        index = index + 1
    end

    destroy_vector<pointer<SemanticType>>(catalog->primitives)
    destroy_semantic_type(catalog->error_type)
    catalog->primitives = null
    catalog->error_type = null
    memory::free<TypeCatalog>(catalog)
    return
end

fn type_catalog_add_primitive(
    catalog: pointer<TypeCatalog>,
    name: string,
    value_type: boolean,
    numeric: boolean,
    integral: boolean
) -> void
    let primitive: pointer<SemanticType> = create_primitive_type(
        name,
        value_type,
        numeric,
        integral
    )

    if primitive != null then
        vector_push<pointer<SemanticType>>(catalog->primitives, primitive)
    end

    return
end

fn create_primitive_type(
    name: string,
    value_type: boolean,
    numeric: boolean,
    integral: boolean
) -> pointer<SemanticType>
    if name == "" || (integral && !numeric) then
        return null
    end

    return create_semantic_type(
        semantic_type_kind_primitive(),
        name,
        value_type,
        numeric,
        integral,
        null,
        null
    )
end

fn create_pointer_type(element_type: pointer<SemanticType>) -> pointer<SemanticType>
    if element_type == null then
        return null
    end

    if !element_type->value_type then
        return null
    end

    return create_semantic_type(
        semantic_type_kind_pointer(),
        semantic_type_pointer_name() + "<" + element_type->name + ">",
        true,
        false,
        false,
        null,
        element_type
    )
end

fn create_type_parameter_type(
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticType>
    if declaration == null then
        return null
    end

    if declaration->kind != syntax_kind_type_parameter() then
        return null
    end

    return create_semantic_type(
        semantic_type_kind_type_parameter(),
        declaration->text,
        true,
        false,
        false,
        declaration,
        null
    )
end

fn create_struct_type(
    declaration: pointer<SyntaxNode>,
    arguments: pointer<Vector<pointer<SemanticType>>>
) -> pointer<SemanticType>
    if declaration == null || arguments == null then
        return null
    end

    if declaration->kind != syntax_kind_struct_declaration() then
        return null
    end

    let expected: int = struct_type_parameter_count(declaration)
    let actual: int = vector_length<pointer<SemanticType>>(arguments)

    if actual != expected then
        return null
    end

    @mut let index: int = 0
    @mut let name: string = declaration->text

    if actual > 0 then
        name = name + "<"
    end

    while index < actual do
        let argument: pointer<SemanticType> = vector_get<pointer<SemanticType>>(
            arguments,
            index
        )

        if argument == null then
            return null
        end

        if index > 0 then
            name = name + ", "
        end

        name = name + argument->name
        index = index + 1
    end

    if actual > 0 then
        name = name + ">"
    end

    let type: pointer<SemanticType> = create_semantic_type(
        semantic_type_kind_struct(),
        name,
        true,
        false,
        false,
        declaration,
        null
    )

    if type == null then
        return null
    end

    index = 0

    while index < actual do
        vector_push<pointer<SemanticType>>(
            type->arguments,
            vector_get<pointer<SemanticType>>(arguments, index)
        )
        index = index + 1
    end

    return type
end

fn create_semantic_type(
    kind: int,
    name: string,
    value_type: boolean,
    numeric: boolean,
    integral: boolean,
    identity: pointer<SyntaxNode>,
    element_type: pointer<SemanticType>
) -> pointer<SemanticType>
    let type: pointer<SemanticType> = memory::allocate<SemanticType>(1)

    if type == null then
        return null
    end

    type->kind = kind
    type->name = name
    type->value_type = value_type
    type->numeric = numeric
    type->integral = integral
    type->identity = identity
    type->element_type = element_type
    type->arguments = create_vector<pointer<SemanticType>>()
    return type
end

fn destroy_semantic_type(type: pointer<SemanticType>) -> void
    if type == null then
        return
    end

    destroy_vector<pointer<SemanticType>>(type->arguments)
    type->identity = null
    type->element_type = null
    type->arguments = null
    memory::free<SemanticType>(type)
    return
end

fn type_catalog_primitive_count(catalog: pointer<TypeCatalog>) -> int
    if catalog == null then
        return 0
    end

    return vector_length<pointer<SemanticType>>(catalog->primitives)
end

fn type_catalog_primitive(
    catalog: pointer<TypeCatalog>,
    index: int
) -> pointer<SemanticType>
    if catalog == null || index < 0 || index >= type_catalog_primitive_count(catalog) then
        return null
    end

    return vector_get<pointer<SemanticType>>(catalog->primitives, index)
end

fn type_catalog_lookup(
    catalog: pointer<TypeCatalog>,
    name: string
) -> pointer<SemanticType>
    if catalog == null || name == "" then
        return null
    end

    @mut let index: int = 0
    let count: int = type_catalog_primitive_count(catalog)

    while index < count do
        let type: pointer<SemanticType> = type_catalog_primitive(catalog, index)

        if type->name == name then
            return type
        end

        index = index + 1
    end

    return null
end

fn type_catalog_error(catalog: pointer<TypeCatalog>) -> pointer<SemanticType>
    if catalog == null then
        return null
    end

    return catalog->error_type
end

fn type_catalog_type_of_literal(
    catalog: pointer<TypeCatalog>,
    literal_variant: int
) -> pointer<SemanticType>
    if literal_variant == syntax_literal_integer() then
        return type_catalog_lookup(catalog, "int")
    end

    if literal_variant == syntax_literal_float() then
        return type_catalog_lookup(catalog, "float")
    end

    if literal_variant == syntax_literal_boolean() then
        return type_catalog_lookup(catalog, "boolean")
    end

    if literal_variant == syntax_literal_char() then
        return type_catalog_lookup(catalog, "char")
    end

    if literal_variant == syntax_literal_string() then
        return type_catalog_lookup(catalog, "string")
    end

    return null
end

fn semantic_type_is_reserved_name(
    catalog: pointer<TypeCatalog>,
    name: string
) -> boolean
    if name == semantic_type_pointer_name() then
        return true
    end

    return type_catalog_lookup(catalog, name) != null
end

fn semantic_type_equals(
    left: pointer<SemanticType>,
    right: pointer<SemanticType>
) -> boolean
    if left == right then
        return true
    end

    if left == null || right == null then
        return false
    end

    if left->kind != right->kind then
        return false
    end

    if left->kind == semantic_type_kind_pointer() then
        return semantic_type_equals(left->element_type, right->element_type)
    end

    if left->kind == semantic_type_kind_type_parameter() then
        return left->identity == right->identity
    end

    if left->kind == semantic_type_kind_struct() then
        if left->identity != right->identity then
            return false
        end

        let count: int = semantic_type_argument_count(left)

        if count != semantic_type_argument_count(right) then
            return false
        end

        @mut let index: int = 0

        while index < count do
            if !semantic_type_equals(
                semantic_type_argument(left, index),
                semantic_type_argument(right, index)
            ) then
                return false
            end

            index = index + 1
        end

        return true
    end

    return false
end

fn semantic_type_argument_count(type: pointer<SemanticType>) -> int
    if type == null then
        return 0
    end

    return vector_length<pointer<SemanticType>>(type->arguments)
end

fn semantic_type_argument(
    type: pointer<SemanticType>,
    index: int
) -> pointer<SemanticType>
    if type == null || index < 0 || index >= semantic_type_argument_count(type) then
        return null
    end

    return vector_get<pointer<SemanticType>>(type->arguments, index)
end

fn struct_type_parameter_count(declaration: pointer<SyntaxNode>) -> int
    if declaration == null then
        return 0
    end

    if declaration->kind != syntax_kind_struct_declaration() then
        return 0
    end

    @mut let count: int = 0
    @mut let index: int = 0
    let child_count: int = syntax_child_count(declaration)

    while index < child_count do
        if syntax_child(declaration, index)->kind == syntax_kind_type_parameter() then
            count = count + 1
        end

        index = index + 1
    end

    return count
end

fn semantic_type_pointer_name() -> string
    return "pointer"
end

fn semantic_type_kind_primitive() -> int
    return 1
end

fn semantic_type_kind_struct() -> int
    return 2
end

fn semantic_type_kind_pointer() -> int
    return 3
end

fn semantic_type_kind_type_parameter() -> int
    return 4
end

fn semantic_type_kind_error() -> int
    return 5
end
