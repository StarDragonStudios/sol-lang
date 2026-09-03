inject namespace std.console as console
inject std.collections.vector
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens
inject frontend.syntax
inject semantics.types
inject semantics.symbol
inject semantics.scope

struct SemanticParse
    lexical: LexResult
    parsed: ParseResult
end

@init
fn launch() -> int
    let builtins: int = test_type_catalog_and_pointers()

    if builtins != 0 then
        console::print_line("semantic foundation test failed: built-in types")
        return 10 + builtins
    end

    let identities: int = test_struct_and_type_parameter_identity()

    if identities != 0 then
        console::print_line("semantic foundation test failed: type identity")
        return 30 + identities
    end

    let objects: int = test_class_interface_types_and_scopes()

    if objects != 0 then
        console::print_line("semantic foundation test failed: object types and scopes")
        return 40 + objects
    end

    let symbols: int = test_symbol_catalog()

    if symbols != 0 then
        console::print_line("semantic foundation test failed: symbols")
        return 50 + symbols
    end

    let scopes: int = test_scope_lookup_shadowing_and_freezing()

    if scopes != 0 then
        console::print_line("semantic foundation test failed: scopes")
        return 70 + scopes
    end

    let invalid: int = test_invalid_model_inputs()

    if invalid != 0 then
        console::print_line("semantic foundation test failed: invalid inputs")
        return 90 + invalid
    end

    return 0
end

fn test_class_interface_types_and_scopes() -> int
    let lexical: LexResult = scan_source("@protected\n@abstract\nclass Base\n    @private\n    name: string\n    @public\n    fn name_length() -> int\n        return 0\n    end\n    @protected\n    @constructor\n    fn create(name: string) -> void\n        this.name = name\n        return\n    end\nend\n@public\n@interface\nclass Printable\nend")
    @mut let parsed: ParseResult = parse_tokens(null)

    if lexical.successful then
        parsed = parse_tokens(lexical.tokens)
    end

    if !lexical.successful || !parsed.successful || parsed.root == null then
        destroy_parse_result(parsed)
        destroy_lex_result(lexical)
        return 1
    end

    let base_declaration: pointer<SyntaxNode> = syntax_child(parsed.root, 0)
    let interface_declaration: pointer<SyntaxNode> = syntax_child(parsed.root, 1)
    let base: pointer<SemanticSymbol> = create_class_symbol(base_declaration)
    let interface_symbol: pointer<SemanticSymbol> = create_class_symbol(
        interface_declaration
    )
    let same_base: pointer<SemanticType> = create_object_type(
        base_declaration,
        false
    )
    let pointer_type: pointer<SemanticType> = create_pointer_type(base->type)
    let module_scope: pointer<Scope> = create_root_scope(scope_kind_module())
    let class_scope: pointer<Scope> = create_child_scope(
        scope_kind_class(),
        module_scope
    )
    let field_declaration: pointer<SyntaxNode> = syntax_child(base_declaration, 3)
    let field: pointer<SemanticSymbol> = create_class_field_symbol(
        base,
        field_declaration,
        0
    )
    let method_declaration: pointer<SyntaxNode> = syntax_child(base_declaration, 4)
    let method: pointer<SemanticSymbol> = create_method_symbol(
        base,
        method_declaration,
        0
    )
    let receiver: pointer<SemanticSymbol> = create_receiver_symbol(
        base,
        method_declaration
    )
    let constructor_declaration: pointer<SyntaxNode> = syntax_child(
        base_declaration,
        5
    )
    let constructor: pointer<SemanticSymbol> = create_constructor_symbol(
        base,
        constructor_declaration,
        0
    )
    @mut let failure: int = 0

    if base == null || interface_symbol == null || same_base == null || pointer_type == null || class_scope == null || field == null || method == null || receiver == null || constructor == null then
        failure = 2
    end

    if failure == 0 && (base->kind != semantic_symbol_kind_class() || base->type->kind != semantic_type_kind_class() || base->type->value_type || !semantic_symbol_is_abstract(base) || semantic_symbol_visibility(base) != semantic_visibility_protected()) then
        failure = 3
    end

    if failure == 0 && (interface_symbol->kind != semantic_symbol_kind_interface() || interface_symbol->type->kind != semantic_type_kind_interface() || semantic_symbol_is_abstract(interface_symbol) || semantic_symbol_visibility(interface_symbol) != semantic_visibility_public()) then
        failure = 4
    end

    if failure == 0 && (!semantic_type_equals(base->type, same_base) || semantic_type_equals(base->type, interface_symbol->type) || pointer_type->element_type != base->type || pointer_type->name != "pointer<Base>") then
        failure = 5
    end

    if failure == 0 && (class_scope->kind != scope_kind_class() || class_scope->parent != module_scope) then
        failure = 6
    end

    if failure == 0 && (field->kind != semantic_symbol_kind_class_field() || field->owner != base || !field->mutable || semantic_symbol_visibility(field) != semantic_visibility_private() || semantic_symbol_declared_type_reference(field)->text != "string") then
        failure = 7
    end

    if failure == 0 && (scope_declare_member(class_scope, field) != scope_declare_success() || scope_lookup_class_field(class_scope, "name") != field || scope_lookup_class_field(class_scope, "missing") != null) then
        failure = 8
    end

    if failure == 0 && (method->kind != semantic_symbol_kind_method() || method->owner != base || !semantic_method_is_virtual(method) || receiver->kind != semantic_symbol_kind_receiver() || receiver->owner != base || receiver->name != "this") then
        failure = 9
    end

    if failure == 0 && (scope_declare_member(class_scope, method) != scope_declare_success() || scope_class_method_count(class_scope, "name_length") != 1 || scope_class_method(class_scope, "name_length", 0) != method) then
        failure = 10
    end

    if failure == 0 && (constructor->kind != semantic_symbol_kind_constructor() || constructor->owner != base || semantic_symbol_visibility(constructor) != semantic_visibility_protected() || scope_declare_member(class_scope, constructor) != scope_declare_success() || scope_constructor_count(class_scope) != 1 || scope_constructor(class_scope, 0) != constructor) then
        failure = 11
    end

    destroy_scope(class_scope)
    destroy_scope(module_scope)
    destroy_semantic_type(pointer_type)
    destroy_semantic_type(same_base)
    destroy_semantic_symbol(receiver)
    destroy_semantic_symbol(interface_symbol)
    destroy_semantic_symbol(base)
    destroy_parse_result(parsed)
    destroy_lex_result(lexical)
    return failure
end

fn semantic_test_source() -> string
    return "inject std.console only print_line\ninject namespace std.file as files\nstruct Pair<T, U>\n    first: T\n    second: U\nend\nstruct Other<T>\n    value: T\nend\nfn transform<T>(value: int) -> int\n    @mut let value: int = 1\n    const fixed: int = 2\n    return value\nend\nfn helper() -> void\n    return\nend"
end

fn parse_semantic_source() -> SemanticParse
    let lexical: LexResult = scan_source(semantic_test_source())
    @mut let parsed: ParseResult = parse_tokens(null)

    if lexical.successful then
        parsed = parse_tokens(lexical.tokens)
    end

    return SemanticParse {
        lexical: lexical,
        parsed: parsed
    }
end

fn destroy_semantic_parse(result: SemanticParse) -> void
    destroy_parse_result(result.parsed)
    destroy_lex_result(result.lexical)
    return
end

fn semantic_parse_is_valid(result: SemanticParse) -> boolean
    return result.lexical.successful && result.parsed.successful && result.parsed.root != null
end

fn test_type_catalog_and_pointers() -> int
    let catalog: pointer<TypeCatalog> = create_type_catalog()

    if catalog == null then
        return 1
    end

    @mut let failure: int = 0

    if type_catalog_primitive_count(catalog) != 6 then
        failure = 2
    end

    if failure == 0 && (type_catalog_primitive(catalog, 0)->name != "int" || type_catalog_primitive(catalog, 1)->name != "float" || type_catalog_primitive(catalog, 2)->name != "boolean" || type_catalog_primitive(catalog, 3)->name != "char" || type_catalog_primitive(catalog, 4)->name != "string" || type_catalog_primitive(catalog, 5)->name != "void") then
        failure = 3
    end

    let integer: pointer<SemanticType> = type_catalog_lookup(catalog, "int")
    let floating: pointer<SemanticType> = type_catalog_lookup(catalog, "float")
    let boolean_type: pointer<SemanticType> = type_catalog_lookup(catalog, "boolean")
    let void_type: pointer<SemanticType> = type_catalog_lookup(catalog, "void")
    let error_type: pointer<SemanticType> = type_catalog_error(catalog)

    if failure == 0 && (integer != type_catalog_primitive(catalog, 0) || integer->kind != semantic_type_kind_primitive() || !integer->value_type || !integer->numeric || !integer->integral) then
        failure = 4
    end

    if failure == 0 && (!floating->value_type || !floating->numeric || floating->integral || !boolean_type->value_type || boolean_type->numeric || void_type->value_type) then
        failure = 5
    end

    if failure == 0 && (error_type->kind != semantic_type_kind_error() || error_type->name != "<error>" || error_type->value_type || error_type->numeric || error_type->integral) then
        failure = 6
    end

    if failure == 0 && (type_catalog_lookup(catalog, "Int") != null || type_catalog_lookup(catalog, "missing") != null || !semantic_type_is_reserved_name(catalog, "pointer") || !semantic_type_is_reserved_name(catalog, "string") || semantic_type_is_reserved_name(catalog, "Pair")) then
        failure = 7
    end

    if failure == 0 && (type_catalog_type_of_literal(catalog, syntax_literal_integer()) != integer || type_catalog_type_of_literal(catalog, syntax_literal_float()) != floating || type_catalog_type_of_literal(catalog, syntax_literal_boolean()) != boolean_type || type_catalog_type_of_literal(catalog, syntax_literal_char())->name != "char" || type_catalog_type_of_literal(catalog, syntax_literal_string())->name != "string") then
        failure = 8
    end

    let pointer_one: pointer<SemanticType> = create_pointer_type(integer)
    let pointer_two: pointer<SemanticType> = create_pointer_type(integer)
    let float_pointer: pointer<SemanticType> = create_pointer_type(floating)

    if failure == 0 && (pointer_one == null || pointer_two == null || float_pointer == null) then
        failure = 9
    end

    if failure == 0 && (pointer_one->name != "pointer<int>" || pointer_one->kind != semantic_type_kind_pointer() || !pointer_one->value_type || pointer_one->element_type != integer) then
        failure = 10
    end

    if failure == 0 && (!semantic_type_equals(pointer_one, pointer_two) || semantic_type_equals(pointer_one, float_pointer) || create_pointer_type(void_type) != null || create_pointer_type(error_type) != null) then
        failure = 11
    end

    destroy_semantic_type(pointer_one)
    destroy_semantic_type(pointer_two)
    destroy_semantic_type(float_pointer)
    destroy_type_catalog(catalog)
    return failure
end

fn test_struct_and_type_parameter_identity() -> int
    let source: SemanticParse = parse_semantic_source()

    if !semantic_parse_is_valid(source) then
        destroy_semantic_parse(source)
        return 1
    end

    let catalog: pointer<TypeCatalog> = create_type_catalog()
    let pair_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 2)
    let other_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 3)
    let pair: pointer<SemanticSymbol> = create_struct_symbol(pair_declaration)
    let other: pointer<SemanticSymbol> = create_struct_symbol(other_declaration)
    @mut let failure: int = 0

    if catalog == null || pair == null || other == null then
        failure = 2
    end

    if failure == 0 && (pair->kind != semantic_symbol_kind_struct() || semantic_symbol_type_parameter_count(pair) != 2 || semantic_struct_field_count(pair) != 2 || pair->type->name != "Pair<T, U>") then
        failure = 3
    end

    let first_parameter: pointer<SemanticSymbol> = semantic_symbol_type_parameter(pair, 0)
    let second_parameter: pointer<SemanticSymbol> = semantic_symbol_type_parameter(pair, 1)
    let first_field: pointer<SemanticSymbol> = semantic_struct_field(pair, 0)
    let second_field: pointer<SemanticSymbol> = semantic_struct_field_by_name(pair, "second")

    if failure == 0 && (first_parameter->name != "T" || first_parameter->owner != pair || first_parameter->index != 0 || first_parameter->type->kind != semantic_type_kind_type_parameter() || second_parameter->index != 1) then
        failure = 4
    end

    if failure == 0 && (first_field->name != "first" || first_field->owner != pair || first_field->index != 0 || second_field->index != 1 || semantic_symbol_declared_type_reference(second_field)->text != "U") then
        failure = 5
    end

    let pair_arguments: pointer<Vector<pointer<SemanticType>>> = create_vector<pointer<SemanticType>>()
    vector_push<pointer<SemanticType>>(pair_arguments, type_catalog_lookup(catalog, "int"))
    vector_push<pointer<SemanticType>>(pair_arguments, type_catalog_lookup(catalog, "string"))
    let pair_int_string: pointer<SemanticType> = create_struct_type(
        pair_declaration,
        pair_arguments
    )
    let same_pair: pointer<SemanticType> = create_struct_type(
        pair_declaration,
        pair_arguments
    )
    destroy_vector<pointer<SemanticType>>(pair_arguments)

    let other_arguments: pointer<Vector<pointer<SemanticType>>> = create_vector<pointer<SemanticType>>()
    vector_push<pointer<SemanticType>>(other_arguments, type_catalog_lookup(catalog, "int"))
    let other_int: pointer<SemanticType> = create_struct_type(
        other_declaration,
        other_arguments
    )
    destroy_vector<pointer<SemanticType>>(other_arguments)

    if failure == 0 && (pair_int_string == null || same_pair == null || other_int == null || pair_int_string->name != "Pair<int, string>" || semantic_type_argument_count(pair_int_string) != 2) then
        failure = 6
    end

    if failure == 0 && (!semantic_type_equals(pair_int_string, same_pair) || semantic_type_equals(pair_int_string, other_int) || semantic_type_equals(pair->type, pair_int_string)) then
        failure = 7
    end

    let same_parameter_type: pointer<SemanticType> = create_type_parameter_type(
        first_parameter->declaration
    )
    let other_parameter_type: pointer<SemanticType> = create_type_parameter_type(
        semantic_symbol_type_parameter(other, 0)->declaration
    )

    if failure == 0 && (!semantic_type_equals(first_parameter->type, same_parameter_type) || semantic_type_equals(first_parameter->type, other_parameter_type)) then
        failure = 8
    end

    let wrong_arity: pointer<Vector<pointer<SemanticType>>> = create_vector<pointer<SemanticType>>()
    vector_push<pointer<SemanticType>>(wrong_arity, type_catalog_lookup(catalog, "int"))

    if failure == 0 && create_struct_type(pair_declaration, wrong_arity) != null then
        failure = 9
    end

    destroy_vector<pointer<SemanticType>>(wrong_arity)
    destroy_semantic_type(pair_int_string)
    destroy_semantic_type(same_pair)
    destroy_semantic_type(other_int)
    destroy_semantic_type(same_parameter_type)
    destroy_semantic_type(other_parameter_type)
    destroy_semantic_symbol(pair)
    destroy_semantic_symbol(other)
    destroy_type_catalog(catalog)
    destroy_semantic_parse(source)
    return failure
end

fn test_symbol_catalog() -> int
    let source: SemanticParse = parse_semantic_source()

    if !semantic_parse_is_valid(source) then
        destroy_semantic_parse(source)
        return 1
    end

    let direct_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 0)
    let namespace_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 1)
    let struct_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 2)
    let function_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 4)
    let parameter_declaration: pointer<SyntaxNode> = syntax_child(function_declaration, 2)
    let block: pointer<SyntaxNode> = syntax_child(function_declaration, 4)
    let function: pointer<SemanticSymbol> = create_function_symbol(function_declaration)
    let parameter: pointer<SemanticSymbol> = create_parameter_symbol(parameter_declaration)
    let local: pointer<SemanticSymbol> = create_local_variable_symbol(syntax_child(block, 0))
    let constant: pointer<SemanticSymbol> = create_local_variable_symbol(syntax_child(block, 1))
    let imported: pointer<SemanticSymbol> = create_imported_name_symbol("print_line", direct_declaration)
    let namespace_symbol: pointer<SemanticSymbol> = create_module_namespace_symbol("files", namespace_declaration)
    let struct_symbol: pointer<SemanticSymbol> = create_struct_symbol(struct_declaration)
    @mut let failure: int = 0

    if function == null || parameter == null || local == null || constant == null || imported == null || namespace_symbol == null || struct_symbol == null then
        failure = 2
    end

    if failure == 0 && (function->kind != semantic_symbol_kind_function() || function->name != "transform" || function->declaration != function_declaration || semantic_symbol_type_parameter_count(function) != 1) then
        failure = 3
    end

    if failure == 0 && (parameter->kind != semantic_symbol_kind_parameter() || parameter->name != "value" || semantic_symbol_declared_type_reference(parameter)->text != "int") then
        failure = 4
    end

    if failure == 0 && (!local->mutable || local->constant || local->name != "value" || constant->mutable || !constant->constant || constant->name != "fixed") then
        failure = 5
    end

    if failure == 0 && (imported->kind != semantic_symbol_kind_imported_name() || semantic_symbol_module_path(imported)->text != "std.console" || namespace_symbol->kind != semantic_symbol_kind_module_namespace() || namespace_symbol->name != "files" || semantic_symbol_module_path(namespace_symbol)->text != "std.file") then
        failure = 6
    end

    if failure == 0 && (struct_symbol->kind != semantic_symbol_kind_struct() || semantic_struct_field(struct_symbol, 0)->kind != semantic_symbol_kind_struct_field() || semantic_symbol_type_parameter(struct_symbol, 0)->kind != semantic_symbol_kind_type_parameter()) then
        failure = 7
    end

    destroy_semantic_symbol(function)
    destroy_semantic_symbol(parameter)
    destroy_semantic_symbol(local)
    destroy_semantic_symbol(constant)
    destroy_semantic_symbol(imported)
    destroy_semantic_symbol(namespace_symbol)
    destroy_semantic_symbol(struct_symbol)
    destroy_semantic_parse(source)
    return failure
end

fn test_scope_lookup_shadowing_and_freezing() -> int
    let source: SemanticParse = parse_semantic_source()

    if !semantic_parse_is_valid(source) then
        destroy_semantic_parse(source)
        return 1
    end

    let transform_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 4)
    let helper_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 5)
    let parameter_declaration: pointer<SyntaxNode> = syntax_child(transform_declaration, 2)
    let block_declaration: pointer<SyntaxNode> = syntax_child(transform_declaration, 4)
    let module_scope: pointer<Scope> = create_root_scope(scope_kind_module())
    let function_scope: pointer<Scope> = create_child_scope(
        scope_kind_function(),
        module_scope
    )
    let block_scope: pointer<Scope> = create_child_scope(
        scope_kind_block(),
        function_scope
    )
    let transform: pointer<SemanticSymbol> = create_function_symbol(
        transform_declaration
    )
    let helper: pointer<SemanticSymbol> = create_function_symbol(helper_declaration)
    let duplicate: pointer<SemanticSymbol> = create_function_symbol(
        transform_declaration
    )
    let parameter: pointer<SemanticSymbol> = create_parameter_symbol(
        parameter_declaration
    )
    let shadow: pointer<SemanticSymbol> = create_local_variable_symbol(
        syntax_child(block_declaration, 0)
    )
    @mut let failure: int = 0

    if module_scope == null || function_scope == null || block_scope == null then
        failure = 2
    end

    if failure == 0 && (module_scope->parent != null || function_scope->parent != module_scope || block_scope->parent != function_scope) then
        failure = 3
    end

    if failure == 0 && (scope_declare(module_scope, transform) != scope_declare_success() || scope_declare(module_scope, helper) != scope_declare_success() || scope_declare(module_scope, duplicate) != scope_declare_duplicate()) then
        failure = 4
    end

    if failure == 0 && (scope_declared_symbol_count(module_scope) != 2 || scope_declared_symbol(module_scope, 0) != transform || scope_declared_symbol(module_scope, 1) != helper || scope_lookup_local(module_scope, "transform") != transform) then
        failure = 5
    end

    if failure == 0 && (scope_declare(function_scope, parameter) != scope_declare_success() || scope_declare(block_scope, shadow) != scope_declare_success()) then
        failure = 6
    end

    if failure == 0 && (scope_lookup(block_scope, "value") != shadow || scope_lookup(function_scope, "value") != parameter || scope_lookup(block_scope, "helper") != helper || scope_lookup_local(block_scope, "helper") != null || scope_lookup(block_scope, "missing") != null) then
        failure = 7
    end

    let frozen_rejection: pointer<SemanticSymbol> = create_function_symbol(
        helper_declaration
    )
    scope_freeze(module_scope)

    if failure == 0 && (!module_scope->frozen || scope_declare(module_scope, frozen_rejection) != scope_declare_frozen() || scope_declared_symbol_count(module_scope) != 2) then
        failure = 8
    end

    destroy_semantic_symbol(duplicate)
    destroy_semantic_symbol(frozen_rejection)
    destroy_scope(block_scope)
    destroy_scope(function_scope)
    destroy_scope(module_scope)
    destroy_semantic_parse(source)
    return failure
end

fn test_invalid_model_inputs() -> int
    let source: SemanticParse = parse_semantic_source()

    if !semantic_parse_is_valid(source) then
        destroy_semantic_parse(source)
        return 1
    end

    let direct: pointer<SyntaxNode> = syntax_child(source.parsed.root, 0)
    let namespace_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 1)
    let function: pointer<SyntaxNode> = syntax_child(source.parsed.root, 4)
    @mut let failure: int = 0

    if create_function_symbol(null) != null || create_parameter_symbol(function) != null || create_local_variable_symbol(function) != null then
        failure = 2
    end

    if failure == 0 && (create_imported_name_symbol("", direct) != null || create_imported_name_symbol("files", namespace_declaration) != null || create_module_namespace_symbol("console", direct) != null) then
        failure = 3
    end

    if failure == 0 && (create_primitive_type("", true, false, false) != null || create_primitive_type("invalid", true, false, true) != null || create_pointer_type(null) != null) then
        failure = 4
    end

    if failure == 0 && (create_root_scope(99) != null || create_child_scope(scope_kind_block(), null) != null || scope_declare(null, null) != scope_declare_invalid() || scope_lookup(null, "name") != null || scope_lookup_local(null, "name") != null) then
        failure = 5
    end

    destroy_semantic_parse(source)
    return failure
end
