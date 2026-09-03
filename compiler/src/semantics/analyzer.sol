inject namespace std.memory as memory
inject std.collections.vector
inject frontend.source only SourcePosition, SourceSpan, source_span
inject frontend.syntax
inject semantics.types
inject semantics.symbol
inject semantics.scope
inject semantics.model
inject semantics.operators

struct SemanticGenericFrame
    function: pointer<SemanticSymbol>
    arguments: pointer<Vector<pointer<SemanticType>>>
end

fn analyze_isolated_module(unit: pointer<SyntaxNode>) -> pointer<SemanticProgram>
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("<isolated>", unit))
    let program: pointer<SemanticProgram> = analyze_source_modules(modules, false)
    destroy_vector<SourceModule>(modules)
    return program
end

fn analyze_library_modules(
    sources: pointer<Vector<SourceModule>>
) -> pointer<SemanticProgram>
    return analyze_source_modules(sources, false)
end

fn analyze_executable_program(
    sources: pointer<Vector<SourceModule>>
) -> pointer<SemanticProgram>
    return analyze_source_modules(sources, true)
end

fn analyze_source_modules(
    sources: pointer<Vector<SourceModule>>,
    require_entry_point: boolean
) -> pointer<SemanticProgram>
    if sources == null then
        return null
    end

    let program: pointer<SemanticProgram> = create_semantic_program(
        require_entry_point
    )

    if program == null then
        return null
    end

    @mut let index: int = 0
    let count: int = vector_length<SourceModule>(sources)

    while index < count do
        if create_semantic_module(program, vector_get<SourceModule>(sources, index)) == null then
            destroy_semantic_program(program)
            return null
        end

        index = index + 1
    end

    semantic_predeclare_all(program, syntax_kind_class_declaration())
    semantic_predeclare_all(program, syntax_kind_struct_declaration())
    semantic_predeclare_all(program, syntax_kind_function_declaration())
    semantic_resolve_all_injections(program)
    semantic_bind_all_class_scopes(program)
    semantic_bind_all_structs(program)
    semantic_validate_all_struct_layouts(program)
    semantic_bind_all_function_signatures(program)
    semantic_resolve_entry_point(program)
    semantic_bind_all_function_bodies(program)
    semantic_validate_generic_instantiations(program)
    semantic_finish_program(program)
    return program
end

fn semantic_predeclare_all(program: pointer<SemanticProgram>, kind: int) -> void
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let declaration_index: int = 0
        let declaration_count: int = syntax_child_count(module->unit)

        while declaration_index < declaration_count do
            let declaration: pointer<SyntaxNode> = syntax_child(
                module->unit,
                declaration_index
            )

            if declaration->kind == kind then
                semantic_predeclare(program, module, declaration)
            end

            declaration_index = declaration_index + 1
        end

        module_index = module_index + 1
    end

    return
end

fn semantic_predeclare(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    declaration: pointer<SyntaxNode>
) -> void
    @mut let symbol: pointer<SemanticSymbol> = null

    if declaration->kind == syntax_kind_struct_declaration() then
        symbol = create_struct_symbol(declaration)
    end

    if declaration->kind == syntax_kind_class_declaration() then
        symbol = create_class_symbol(declaration)
    end

    if declaration->kind == syntax_kind_function_declaration() then
        symbol = create_function_symbol(declaration)
    end

    if symbol == null then
        return
    end

    semantic_program_record_symbol(
        program,
        semantic_binding_kind_declared_symbol(),
        declaration,
        symbol
    )
    semantic_record_child_symbols(program, symbol)

    if declaration->kind == syntax_kind_struct_declaration() || declaration->kind == syntax_kind_class_declaration() then
        if semantic_type_is_reserved_name(program->catalog, symbol->name) then
            semantic_program_own_symbol(program, symbol)
            @mut let category: string = "Struct"

            if declaration->kind == syntax_kind_class_declaration() then
                category = "Class"
            end

            semantic_report(
                program,
                module,
                "SOL-S038",
                category + " name '" + symbol->name + "' is reserved by a built-in type.",
                declaration
            )
            return
        end
    end

    if scope_declare(module->scope, symbol) != scope_declare_success() then
        semantic_program_own_symbol(program, symbol)
        semantic_report_duplicate(program, module, symbol->name, declaration)
    end

    return
end

fn semantic_record_child_symbols(
    program: pointer<SemanticProgram>,
    owner: pointer<SemanticSymbol>
) -> void
    @mut let index: int = 0
    let count: int = semantic_symbol_child_count(owner)

    while index < count do
        let child: pointer<SemanticSymbol> = semantic_symbol_child(owner, index)
        semantic_program_record_symbol(
            program,
            semantic_binding_kind_declared_symbol(),
            child->declaration,
            child
        )
        index = index + 1
    end

    return
end

fn semantic_resolve_all_injections(program: pointer<SemanticProgram>) -> void
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

            if declaration->kind == syntax_kind_injection_declaration() then
                semantic_resolve_injection(program, module, declaration)
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    return
end

fn semantic_resolve_injection(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    injection: pointer<SyntaxNode>
) -> void
    let path: pointer<SyntaxNode> = syntax_child(injection, 0)
    let target: pointer<SemanticModule> = semantic_program_module(
        program,
        path->text
    )

    if target == null then
        semantic_report(
            program,
            module,
            "SOL-S019",
            "Cannot resolve module '" + path->text + "'.",
            path
        )
        return
    end

    semantic_program_record_module(
        program,
        semantic_binding_kind_injected_module(),
        injection,
        target
    )

    if injection->variant == syntax_injection_namespace() then
        semantic_resolve_namespace_injection(program, module, target, injection)
        return
    end

    semantic_resolve_direct_injection(program, module, target, injection)
    return
end

fn semantic_resolve_direct_injection(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    target: pointer<SemanticModule>,
    injection: pointer<SyntaxNode>
) -> void
    let binding: pointer<SemanticBinding> = semantic_program_add_binding(
        program,
        semantic_binding_kind_direct_injection(),
        injection
    )
    let selected_count: int = syntax_child_count(injection) - 1

    if selected_count == 0 then
        @mut let index: int = 0
        let count: int = syntax_child_count(target->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(target->unit, index)

            if declaration->kind == syntax_kind_function_declaration() || declaration->kind == syntax_kind_struct_declaration() || declaration->kind == syntax_kind_class_declaration() then
                let exported: pointer<SemanticSymbol> = semantic_program_symbol_of(
                    program,
                    semantic_binding_kind_declared_symbol(),
                    declaration
                )

                if exported != null then
                    if scope_lookup_local(target->scope, exported->name) == exported then
                        semantic_declare_import(
                            program,
                            module,
                            injection,
                            exported,
                            binding
                        )
                    end
                end
            end

            index = index + 1
        end

        return
    end

    @mut let selected_index: int = 1
    let child_count: int = syntax_child_count(injection)

    while selected_index < child_count do
        let selected: pointer<SyntaxNode> = syntax_child(injection, selected_index)
        let exported: pointer<SemanticSymbol> = semantic_module_export(
            program,
            target,
            selected->text
        )

        if exported == null then
            semantic_report(
                program,
                module,
                "SOL-S020",
                "Module '" + target->name + "' does not declare exported symbol '" + selected->text + "'.",
                selected
            )
        else
            semantic_declare_import(
                program,
                module,
                injection,
                exported,
                binding
            )
        end

        selected_index = selected_index + 1
    end

    return
end

fn semantic_declare_import(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    injection: pointer<SyntaxNode>,
    target: pointer<SemanticSymbol>,
    binding: pointer<SemanticBinding>
) -> void
    let imported: pointer<SemanticSymbol> = create_imported_name_symbol(
        target->name,
        injection
    )

    if imported == null then
        return
    end

    if scope_declare(module->scope, imported) != scope_declare_success() then
        semantic_program_own_symbol(program, imported)
        semantic_report_duplicate(program, module, imported->name, injection)
        return
    end

    semantic_binding_add_symbol(binding, imported)
    semantic_binding_add_symbol(binding, target)
    return
end

fn semantic_resolve_namespace_injection(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    target: pointer<SemanticModule>,
    injection: pointer<SyntaxNode>
) -> void
    @mut let name: string = ""

    if syntax_child_count(injection) > 1 then
        name = syntax_child(injection, 1)->text
    else
        let path: pointer<SyntaxNode> = syntax_child(injection, 0)
        name = syntax_child(path, syntax_child_count(path) - 1)->text
    end

    let namespace_symbol: pointer<SemanticSymbol> = create_module_namespace_symbol(
        name,
        injection
    )

    if namespace_symbol == null then
        return
    end

    if scope_declare(module->scope, namespace_symbol) != scope_declare_success() then
        semantic_program_own_symbol(program, namespace_symbol)
        semantic_report_duplicate(program, module, name, injection)
        return
    end

    let binding: pointer<SemanticBinding> = semantic_program_add_binding(
        program,
        semantic_binding_kind_injected_namespace(),
        injection
    )
    binding->symbol = namespace_symbol
    binding->module = target
    return
end

fn semantic_module_export(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    name: string
) -> pointer<SemanticSymbol>
    if program == null || module == null || name == "" then
        return null
    end

    @mut let index: int = 0
    let count: int = syntax_child_count(module->unit)

    while index < count do
        let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

        if declaration->kind == syntax_kind_function_declaration() || declaration->kind == syntax_kind_struct_declaration() || declaration->kind == syntax_kind_class_declaration() then
            let symbol: pointer<SemanticSymbol> = semantic_program_symbol_of(
                program,
                semantic_binding_kind_declared_symbol(),
                declaration
            )

            if symbol != null then
                if symbol->name == name then
                    if scope_lookup_local(module->scope, name) == symbol then
                        return symbol
                    end
                end
            end
        end

        index = index + 1
    end

    return null
end

fn semantic_import_target(
    program: pointer<SemanticProgram>,
    imported: pointer<SemanticSymbol>
) -> pointer<SemanticSymbol>
    if program == null || imported == null then
        return null
    end

    @mut let binding_index: int = 0
    let binding_count: int = vector_length<pointer<SemanticBinding>>(
        program->bindings
    )

    while binding_index < binding_count do
        let binding: pointer<SemanticBinding> = vector_get<pointer<SemanticBinding>>(
            program->bindings,
            binding_index
        )

        if binding->kind == semantic_binding_kind_direct_injection() then
            @mut let index: int = 0
            let count: int = semantic_binding_symbol_count(binding)

            while index + 1 < count do
                if semantic_binding_symbol(binding, index) == imported then
                    return semantic_binding_symbol(binding, index + 1)
                end

                index = index + 2
            end
        end

        binding_index = binding_index + 1
    end

    return null
end

fn semantic_namespace_target(
    program: pointer<SemanticProgram>,
    namespace_symbol: pointer<SemanticSymbol>
) -> pointer<SemanticModule>
    if program == null || namespace_symbol == null then
        return null
    end

    let binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        semantic_binding_kind_injected_namespace(),
        namespace_symbol->declaration
    )

    if binding == null then
        return null
    end

    if binding->symbol != namespace_symbol then
        return null
    end

    return binding->module
end

fn semantic_bind_all_class_scopes(program: pointer<SemanticProgram>) -> void
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

            if declaration->kind == syntax_kind_class_declaration() then
                semantic_bind_class_scope(program, module, declaration)
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    return
end

fn semantic_bind_class_scope(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    declaration: pointer<SyntaxNode>
) -> void
    let symbol: pointer<SemanticSymbol> = semantic_program_symbol_of(
        program,
        semantic_binding_kind_declared_symbol(),
        declaration
    )

    if symbol == null then
        return
    end

    let member_scope: pointer<Scope> = semantic_program_create_scope(
        program,
        scope_kind_class(),
        module->scope
    )
    semantic_program_record_scope(
        program,
        semantic_binding_kind_class_scope(),
        declaration,
        member_scope
    )
    semantic_validate_class_annotations(program, module, declaration)
    return
end

fn semantic_validate_class_annotations(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    declaration: pointer<SyntaxNode>
) -> void
    @mut let index: int = 0
    @mut let visibility_count: int = 0
    let count: int = syntax_child_count(declaration)

    while index < count do
        let annotation: pointer<SyntaxNode> = syntax_child(declaration, index)

        if annotation->kind == syntax_kind_annotation() then
            let name: string = annotation->text
            let allowed: boolean = name == "public" || name == "protected" || name == "private" || name == "abstract" || name == "interface"

            if !allowed then
                semantic_report(
                    program,
                    module,
                    "SOL-S048",
                    "Annotation '@" + name + "' is not valid on a class declaration.",
                    annotation
                )
            end

            if semantic_declaration_annotation_count(declaration, name) > 1 then
                @mut let previous: int = 0
                @mut let already_seen: boolean = false

                while previous < index do
                    let previous_child: pointer<SyntaxNode> = syntax_child(
                        declaration,
                        previous
                    )

                    if previous_child->kind == syntax_kind_annotation() && previous_child->text == name then
                        already_seen = true
                    end

                    previous = previous + 1
                end

                if already_seen then
                    semantic_report(
                        program,
                        module,
                        "SOL-S049",
                        "Class annotation '@" + name + "' is declared more than once.",
                        annotation
                    )
                end
            end

            if name == "public" || name == "protected" || name == "private" then
                visibility_count = visibility_count + 1
            end
        end

        index = index + 1
    end

    if visibility_count > 1 then
        semantic_report(
            program,
            module,
            "SOL-S050",
            "Class declaration must not specify more than one visibility.",
            declaration
        )
    end

    if semantic_declaration_has_annotation(declaration, "abstract") && semantic_declaration_has_annotation(declaration, "interface") then
        semantic_report(
            program,
            module,
            "SOL-S051",
            "An interface cannot also be declared '@abstract'.",
            declaration
        )
    end

    return
end

fn semantic_bind_all_structs(program: pointer<SemanticProgram>) -> void
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

            if declaration->kind == syntax_kind_struct_declaration() then
                semantic_bind_struct(program, module, declaration)
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    return
end


fn semantic_bind_struct(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    declaration: pointer<SyntaxNode>
) -> void
    let symbol: pointer<SemanticSymbol> = semantic_program_symbol_of(
        program,
        semantic_binding_kind_declared_symbol(),
        declaration
    )

    if symbol == null then
        return
    end

    semantic_validate_type_parameters(program, module, symbol)
    @mut let field_index: int = 0
    let field_count: int = semantic_struct_field_count(symbol)

    while field_index < field_count do
        let field: pointer<SemanticSymbol> = semantic_struct_field(
            symbol,
            field_index
        )
        @mut let previous: int = 0

        while previous < field_index do
            if semantic_struct_field(symbol, previous)->name == field->name then
                semantic_report(
                    program,
                    module,
                    "SOL-S027",
                    "Struct '" + symbol->name + "' declares field '" + field->name + "' more than once.",
                    field->declaration
                )
            end

            previous = previous + 1
        end

        let reference: pointer<SyntaxNode> = semantic_symbol_declared_type_reference(field)
        let field_type: pointer<SemanticType> = semantic_resolve_type_reference(
            program,
            module,
            reference,
            symbol
        )

        if field_type->kind != semantic_type_kind_error() && !field_type->value_type then
            semantic_report(
                program,
                module,
                "SOL-S028",
                "Field '" + field->name + "' of struct '" + symbol->name + "' cannot have non-value type '" + field_type->name + "'.",
                reference
            )
        end

        field_index = field_index + 1
    end

    return
end

fn semantic_bind_all_function_signatures(program: pointer<SemanticProgram>) -> void
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

            if declaration->kind == syntax_kind_function_declaration() then
                semantic_bind_function_signature(program, module, declaration)
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    return
end

fn semantic_bind_function_signature(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    declaration: pointer<SyntaxNode>
) -> void
    let function: pointer<SemanticSymbol> = semantic_program_symbol_of(
        program,
        semantic_binding_kind_declared_symbol(),
        declaration
    )

    if function == null then
        return
    end

    semantic_validate_type_parameters(program, module, function)
    let function_scope: pointer<Scope> = semantic_program_create_scope(
        program,
        scope_kind_function(),
        module->scope
    )
    semantic_program_record_scope(
        program,
        semantic_binding_kind_function_scope(),
        declaration,
        function_scope
    )
    @mut let parameter_index: int = 0
    let parameter_count: int = semantic_direct_child_count(
        declaration,
        syntax_kind_parameter()
    )

    while parameter_index < parameter_count do
        let parameter_declaration: pointer<SyntaxNode> = semantic_direct_child(
            declaration,
            syntax_kind_parameter(),
            parameter_index
        )
        let parameter_type: pointer<SemanticType> = semantic_resolve_type_reference(
            program,
            module,
            syntax_child(parameter_declaration, 1),
            function
        )

        if parameter_type->kind != semantic_type_kind_error() && !parameter_type->value_type then
            semantic_report(
                program,
                module,
                "SOL-S012",
                "Parameter '" + parameter_declaration->text + "' of function '" + function->name + "' cannot have non-value type '" + parameter_type->name + "'.",
                syntax_child(parameter_declaration, 1)
            )
        end

        let parameter: pointer<SemanticSymbol> = create_parameter_symbol(
            parameter_declaration
        )
        semantic_program_record_symbol(
            program,
            semantic_binding_kind_declared_symbol(),
            parameter_declaration,
            parameter
        )

        if scope_declare(function_scope, parameter) != scope_declare_success() then
            semantic_program_own_symbol(program, parameter)
            semantic_report_duplicate(
                program,
                module,
                parameter->name,
                parameter_declaration
            )
        end

        parameter_index = parameter_index + 1
    end

    let return_reference: pointer<SyntaxNode> = semantic_function_return_type(
        declaration
    )
    semantic_resolve_type_reference(program, module, return_reference, function)
    let body: pointer<SyntaxNode> = semantic_function_body(declaration)

    if body != null then
        semantic_program_record_scope(
            program,
            semantic_binding_kind_block_scope(),
            body,
            function_scope
        )
    end

    return
end

fn semantic_validate_type_parameters(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    owner: pointer<SemanticSymbol>
) -> void
    @mut let index: int = 0
    let count: int = semantic_symbol_type_parameter_count(owner)

    while index < count do
        let parameter: pointer<SemanticSymbol> = semantic_symbol_type_parameter(
            owner,
            index
        )
        @mut let previous: int = 0

        while previous < index do
            if semantic_symbol_type_parameter(owner, previous)->name == parameter->name then
                semantic_report(
                    program,
                    module,
                    "SOL-S039",
                    "Type parameter '" + parameter->name + "' is declared more than once on '" + owner->name + "'.",
                    parameter->declaration
                )
            end

            previous = previous + 1
        end

        index = index + 1
    end

    return
end

fn semantic_resolve_type_reference(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    reference: pointer<SyntaxNode>,
    type_owner: pointer<SemanticSymbol>
) -> pointer<SemanticType>
    let recorded: pointer<SemanticType> = semantic_program_type_of(
        program,
        semantic_binding_kind_resolved_type(),
        reference
    )

    if recorded != null then
        return recorded
    end

    let arguments: pointer<Vector<pointer<SemanticType>>> = create_vector<pointer<SemanticType>>()
    @mut let index: int = 0
    let count: int = semantic_direct_child_count(reference, syntax_kind_type_reference())

    while index < count do
        vector_push<pointer<SemanticType>>(
            arguments,
            semantic_resolve_type_reference(
                program,
                module,
                semantic_direct_child(reference, syntax_kind_type_reference(), index),
                type_owner
            )
        )
        index = index + 1
    end

    let parameter: pointer<SemanticSymbol> = semantic_type_parameter_named(
        type_owner,
        reference->text
    )

    if parameter != null then
        if count != 0 then
            destroy_vector<pointer<SemanticType>>(arguments)
            return semantic_report_type_arity(program, module, reference, parameter->name)
        end

        destroy_vector<pointer<SemanticType>>(arguments)
        semantic_program_record_type(
            program,
            semantic_binding_kind_resolved_type(),
            reference,
            parameter->type
        )
        return parameter->type
    end

    if reference->text == semantic_type_pointer_name() then
        if count != 1 then
            destroy_vector<pointer<SemanticType>>(arguments)
            return semantic_report_type_arity(program, module, reference, "pointer")
        end

        let element: pointer<SemanticType> = vector_get<pointer<SemanticType>>(arguments, 0)

        if element->kind == semantic_type_kind_error() then
            destroy_vector<pointer<SemanticType>>(arguments)
            return semantic_record_error_type(program, reference)
        end

        if !element->value_type && element->kind != semantic_type_kind_class() && element->kind != semantic_type_kind_interface() then
            semantic_report(
                program,
                module,
                "SOL-S041",
                "Type argument of 'pointer' must be a storable type, but found '" + element->name + "'.",
                semantic_direct_child(reference, syntax_kind_type_reference(), 0)
            )
            destroy_vector<pointer<SemanticType>>(arguments)
            return semantic_record_error_type(program, reference)
        end

        let pointer_type: pointer<SemanticType> = semantic_program_own_type(
            program,
            create_pointer_type(element)
        )
        destroy_vector<pointer<SemanticType>>(arguments)
        semantic_program_record_type(
            program,
            semantic_binding_kind_resolved_type(),
            reference,
            pointer_type
        )
        return pointer_type
    end

    let primitive: pointer<SemanticType> = type_catalog_lookup(
        program->catalog,
        reference->text
    )

    if primitive != null then
        if count != 0 then
            destroy_vector<pointer<SemanticType>>(arguments)
            return semantic_report_type_arity(program, module, reference, primitive->name)
        end

        destroy_vector<pointer<SemanticType>>(arguments)
        semantic_program_record_type(
            program,
            semantic_binding_kind_resolved_type(),
            reference,
            primitive
        )
        return primitive
    end

    @mut let declared: pointer<SemanticSymbol> = scope_lookup_local(
        module->scope,
        reference->text
    )

    if declared != null then
        if declared->kind == semantic_symbol_kind_imported_name() then
            declared = semantic_import_target(program, declared)
        end
    end

    if declared != null then
        if declared->kind != semantic_symbol_kind_struct() && declared->kind != semantic_symbol_kind_class() && declared->kind != semantic_symbol_kind_interface() then
            declared = null
        end
    end

    if declared != null then
        if declared->kind == semantic_symbol_kind_class() || declared->kind == semantic_symbol_kind_interface() then
            if count != 0 then
                destroy_vector<pointer<SemanticType>>(arguments)
                return semantic_report_type_arity(program, module, reference, declared->name)
            end

            destroy_vector<pointer<SemanticType>>(arguments)
            semantic_program_record_type(
                program,
                semantic_binding_kind_resolved_type(),
                reference,
                declared->type
            )
            return declared->type
        end

        if count != semantic_symbol_type_parameter_count(declared) then
            destroy_vector<pointer<SemanticType>>(arguments)
            return semantic_report_type_arity(program, module, reference, declared->name)
        end

        @mut let valid: boolean = true
        index = 0

        while index < count do
            let argument: pointer<SemanticType> = vector_get<pointer<SemanticType>>(
                arguments,
                index
            )

            if argument->kind == semantic_type_kind_error() || !argument->value_type then
                valid = false
                semantic_report(
                    program,
                    module,
                    "SOL-S041",
                    "Struct type argument must be a value type.",
                    semantic_direct_child(reference, syntax_kind_type_reference(), index)
                )
            end

            index = index + 1
        end

        if !valid then
            destroy_vector<pointer<SemanticType>>(arguments)
            return semantic_record_error_type(program, reference)
        end

        let struct_type: pointer<SemanticType> = semantic_program_own_type(
            program,
            create_struct_type(declared->declaration, arguments)
        )
        destroy_vector<pointer<SemanticType>>(arguments)
        semantic_program_record_type(
            program,
            semantic_binding_kind_resolved_type(),
            reference,
            struct_type
        )
        return struct_type
    end

    destroy_vector<pointer<SemanticType>>(arguments)
    semantic_report(
        program,
        module,
        "SOL-S003",
        "Unknown type '" + reference->text + "'.",
        reference
    )
    return semantic_record_error_type(program, reference)
end

fn semantic_report_type_arity(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    reference: pointer<SyntaxNode>,
    target: string
) -> pointer<SemanticType>
    semantic_report(
        program,
        module,
        "SOL-S040",
        "Type '" + target + "' received an incorrect number of type arguments.",
        reference
    )
    return semantic_record_error_type(program, reference)
end

fn semantic_record_error_type(
    program: pointer<SemanticProgram>,
    node: pointer<SyntaxNode>
) -> pointer<SemanticType>
    let error_type: pointer<SemanticType> = type_catalog_error(program->catalog)
    semantic_program_record_type(
        program,
        semantic_binding_kind_resolved_type(),
        node,
        error_type
    )
    return error_type
end

fn semantic_type_parameter_named(
    owner: pointer<SemanticSymbol>,
    name: string
) -> pointer<SemanticSymbol>
    if owner == null || name == "" then
        return null
    end

    @mut let index: int = 0
    let count: int = semantic_symbol_type_parameter_count(owner)

    while index < count do
        let parameter: pointer<SemanticSymbol> = semantic_symbol_type_parameter(
            owner,
            index
        )

        if parameter->name == name then
            return parameter
        end

        index = index + 1
    end

    return null
end

fn semantic_function_return_type(
    declaration: pointer<SyntaxNode>
) -> pointer<SyntaxNode>
    @mut let index: int = syntax_child_count(declaration)

    while index > 0 do
        index = index - 1
        let child: pointer<SyntaxNode> = syntax_child(declaration, index)

        if child->kind == syntax_kind_type_reference() then
            return child
        end
    end

    return null
end

fn semantic_function_body(
    declaration: pointer<SyntaxNode>
) -> pointer<SyntaxNode>
    return semantic_direct_child(declaration, syntax_kind_block(), 0)
end

fn semantic_direct_child_count(node: pointer<SyntaxNode>, kind: int) -> int
    if node == null then
        return 0
    end

    @mut let count: int = 0
    @mut let index: int = 0
    let child_count: int = syntax_child_count(node)

    while index < child_count do
        if syntax_child(node, index)->kind == kind then
            count = count + 1
        end

        index = index + 1
    end

    return count
end

fn semantic_direct_child(
    node: pointer<SyntaxNode>,
    kind: int,
    requested_index: int
) -> pointer<SyntaxNode>
    if node == null || requested_index < 0 then
        return null
    end

    @mut let found: int = 0
    @mut let index: int = 0
    let count: int = syntax_child_count(node)

    while index < count do
        let child: pointer<SyntaxNode> = syntax_child(node, index)

        if child->kind == kind then
            if found == requested_index then
                return child
            end

            found = found + 1
        end

        index = index + 1
    end

    return null
end

fn semantic_report_duplicate(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    name: string,
    node: pointer<SyntaxNode>
) -> void
    semantic_report(
        program,
        module,
        "SOL-S001",
        "Duplicate declaration of '" + name + "'.",
        node
    )
    return
end

fn semantic_report(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    code: string,
    message: string,
    node: pointer<SyntaxNode>
) -> void
    semantic_program_add_diagnostic(
        program,
        module->name,
        code,
        message,
        node->span
    )
    return
end

fn semantic_finish_program(program: pointer<SemanticProgram>) -> void
    @mut let index: int = 0
    let count: int = vector_length<pointer<Scope>>(program->scopes)

    while index < count do
        scope_freeze(vector_get<pointer<Scope>>(program->scopes, index))
        index = index + 1
    end

    semantic_sort_diagnostics(program)
    program->complete = true
    return
end

fn semantic_sort_diagnostics(program: pointer<SemanticProgram>) -> void
    @mut let index: int = 1
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        let current: SemanticDiagnostic = semantic_program_diagnostic(program, index)
        @mut let position: int = index

        while position > 0 do
            let previous: SemanticDiagnostic = semantic_program_diagnostic(
                program,
                position - 1
            )

            if !semantic_diagnostic_after(program, previous, current) then
                position = 0 - position - 1
            else
                vector_set<SemanticDiagnostic>(program->diagnostics, position, previous)
                position = position - 1
            end
        end

        if position < 0 then
            position = 0 - position - 1
        end

        vector_set<SemanticDiagnostic>(program->diagnostics, position, current)
        index = index + 1
    end

    return
end

fn semantic_diagnostic_after(
    program: pointer<SemanticProgram>,
    left: SemanticDiagnostic,
    right: SemanticDiagnostic
) -> boolean
    let left_module: int = semantic_module_order(program, left.module_name)
    let right_module: int = semantic_module_order(program, right.module_name)

    if left_module != right_module then
        return left_module > right_module
    end

    if left.diagnostic.span.start.offset != right.diagnostic.span.start.offset then
        return left.diagnostic.span.start.offset > right.diagnostic.span.start.offset
    end

    return left.diagnostic.span.end_position.offset > right.diagnostic.span.end_position.offset
end

fn semantic_module_order(program: pointer<SemanticProgram>, name: string) -> int
    if name == "" then
        return semantic_program_module_count(program)
    end

    @mut let index: int = 0
    let count: int = semantic_program_module_count(program)

    while index < count do
        if semantic_program_module_at(program, index)->name == name then
            return index
        end

        index = index + 1
    end

    return count
end

fn semantic_validate_all_struct_layouts(program: pointer<SemanticProgram>) -> void
    let checked: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

            if declaration->kind == syntax_kind_struct_declaration() then
                let active: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()
                semantic_validate_struct_layout(
                    program,
                    module,
                    semantic_program_symbol_of(
                        program,
                        semantic_binding_kind_declared_symbol(),
                        declaration
                    ),
                    active,
                    checked
                )
                destroy_vector<pointer<SyntaxNode>>(active)
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    destroy_vector<pointer<SyntaxNode>>(checked)
    return
end

fn semantic_resolve_entry_point(program: pointer<SemanticProgram>) -> void
    @mut let candidate_count: int = 0
    @mut let first_module: pointer<SemanticModule> = null
    @mut let first_declaration: pointer<SyntaxNode> = null
    @mut let first_function: pointer<SemanticSymbol> = null
    @mut let first_valid: boolean = false
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

            if declaration->kind == syntax_kind_function_declaration() then
                let annotation: pointer<SyntaxNode> = semantic_function_annotation(
                    declaration,
                    "init"
                )

                if annotation != null then
                    let function: pointer<SemanticSymbol> = semantic_program_symbol_of(
                        program,
                        semantic_binding_kind_declared_symbol(),
                        declaration
                    )
                    let valid: boolean = semantic_validate_entry_point(
                        program,
                        module,
                        declaration,
                        function
                    )

                    if candidate_count == 0 then
                        first_module = module
                        first_declaration = declaration
                        first_function = function
                        first_valid = valid
                    else
                        semantic_report(
                            program,
                            module,
                            "SOL-S024",
                            "Function '" + function->name + "' is an additional '@init' entry point; only one is allowed.",
                            annotation
                        )
                    end

                    candidate_count = candidate_count + 1
                end
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    if candidate_count == 0 then
        if program->require_entry_point then
            let position: SourcePosition = SourcePosition {
                offset: 0,
                line: 1,
                column: 1
            }
            semantic_program_add_diagnostic(
                program,
                "",
                "SOL-S023",
                "Executable program must declare exactly one function annotated with '@init'.",
                source_span(position, position)
            )
        end

        return
    end

    if candidate_count == 1 && first_valid then
        program->entry_module = first_module
        program->entry_function = first_function
    end

    return
end

fn semantic_validate_struct_layout(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    symbol: pointer<SemanticSymbol>,
    active: pointer<Vector<pointer<SyntaxNode>>>,
    checked: pointer<Vector<pointer<SyntaxNode>>>
) -> boolean
    if symbol == null then
        return false
    end

    if semantic_node_vector_contains(checked, symbol->declaration) then
        return true
    end

    vector_push<pointer<SyntaxNode>>(active, symbol->declaration)
    @mut let valid: boolean = true
    @mut let index: int = 0
    let count: int = semantic_struct_field_count(symbol)

    while index < count do
        let field: pointer<SemanticSymbol> = semantic_struct_field(symbol, index)
        let field_type: pointer<SemanticType> = semantic_program_type_of(
            program,
            semantic_binding_kind_resolved_type(),
            semantic_symbol_declared_type_reference(field)
        )

        if field_type != null then
            if field_type->kind == semantic_type_kind_struct() then
            let nested: pointer<SemanticSymbol> = semantic_struct_symbol_for_type(
                program,
                field_type
            )

            if nested != null then
                if semantic_node_vector_contains(active, nested->declaration) then
                    semantic_report(
                        program,
                        module,
                        "SOL-S029",
                        "Struct type '" + symbol->name + "' has a recursive value layout through field '" + field->name + "'.",
                        semantic_symbol_declared_type_reference(field)
                    )
                    valid = false
                else
                    let nested_module: pointer<SemanticModule> = semantic_module_for_declaration(
                        program,
                        nested->declaration
                    )

                    if !semantic_validate_struct_layout(
                        program,
                        nested_module,
                        nested,
                        active,
                        checked
                    ) then
                        valid = false
                    end
                end
            end
            end
        end

        index = index + 1
    end

    vector_pop<pointer<SyntaxNode>>(active)
    vector_push<pointer<SyntaxNode>>(checked, symbol->declaration)
    return valid
end

fn semantic_validate_entry_point(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    declaration: pointer<SyntaxNode>,
    function: pointer<SemanticSymbol>
) -> boolean
    @mut let valid: boolean = scope_lookup_local(
        module->scope,
        function->name
    ) == function

    if semantic_symbol_type_parameter_count(function) != 0 then
        semantic_report(
            program,
            module,
            "SOL-S040",
            "Entry point '" + function->name + "' cannot declare type parameters.",
            declaration
        )
        valid = false
    end

    if semantic_function_body(declaration) == null then
        semantic_report(
            program,
            module,
            "SOL-S025",
            "Entry point '" + function->name + "' must have a function body.",
            declaration
        )
        valid = false
    end

    let return_reference: pointer<SyntaxNode> = semantic_function_return_type(
        declaration
    )
    let return_type: pointer<SemanticType> = semantic_program_type_of(
        program,
        semantic_binding_kind_resolved_type(),
        return_reference
    )

    if return_type == null then
        valid = false
    else
        if return_type->kind == semantic_type_kind_error() then
            valid = false
        else
        if return_type->name != "int" then
            semantic_report(
                program,
                module,
                "SOL-S026",
                "Entry point '" + function->name + "' must return 'int', but returns '" + return_type->name + "'.",
                return_reference
            )
            valid = false
        end
        end
    end

    return valid
end

fn semantic_function_annotation(
    declaration: pointer<SyntaxNode>,
    name: string
) -> pointer<SyntaxNode>
    @mut let index: int = 0
    let count: int = semantic_direct_child_count(
        declaration,
        syntax_kind_annotation()
    )

    while index < count do
        let annotation: pointer<SyntaxNode> = semantic_direct_child(
            declaration,
            syntax_kind_annotation(),
            index
        )

        if annotation->text == name then
            return annotation
        end

        index = index + 1
    end

    return null
end

fn semantic_node_vector_contains(
    values: pointer<Vector<pointer<SyntaxNode>>>,
    node: pointer<SyntaxNode>
) -> boolean
    @mut let index: int = 0
    let count: int = vector_length<pointer<SyntaxNode>>(values)

    while index < count do
        if vector_get<pointer<SyntaxNode>>(values, index) == node then
            return true
        end

        index = index + 1
    end

    return false
end

fn semantic_struct_symbol_for_type(
    program: pointer<SemanticProgram>,
    type: pointer<SemanticType>
) -> pointer<SemanticSymbol>
    if program == null || type == null then
        return null
    end

    if type->kind != semantic_type_kind_struct() then
        return null
    end

    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_declared_symbol(),
        type->identity
    )
end

fn semantic_module_for_declaration(
    program: pointer<SemanticProgram>,
    declaration: pointer<SyntaxNode>
) -> pointer<SemanticModule>
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            if syntax_child(module->unit, index) == declaration then
                return module
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    return null
end

fn semantic_bind_all_function_bodies(program: pointer<SemanticProgram>) -> void
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

            if declaration->kind == syntax_kind_function_declaration() then
                let body: pointer<SyntaxNode> = semantic_function_body(declaration)

                if body != null then
                    semantic_bind_block(
                        program,
                        module,
                        body,
                        semantic_program_scope_of(
                            program,
                            semantic_binding_kind_function_scope(),
                            declaration
                        ),
                        semantic_program_symbol_of(
                            program,
                            semantic_binding_kind_declared_symbol(),
                            declaration
                        )
                    )
                end
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    return
end

fn semantic_bind_block(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    block: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    @mut let index: int = 0
    let count: int = syntax_child_count(block)

    while index < count do
        semantic_bind_statement(
            program,
            module,
            syntax_child(block, index),
            scope,
            function
        )
        index = index + 1
    end

    return
end

fn semantic_bind_statement(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    statement: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    if statement->kind == syntax_kind_variable_declaration_statement() then
        semantic_bind_variable(program, module, statement, scope, function)
        return
    end

    if statement->kind == syntax_kind_assignment_statement() then
        semantic_bind_assignment(program, module, statement, scope, function)
        return
    end

    if statement->kind == syntax_kind_field_assignment_statement() then
        semantic_bind_field_assignment(program, module, statement, scope, function)
        return
    end

    if statement->kind == syntax_kind_pointer_field_assignment_statement() then
        semantic_bind_pointer_field_assignment(program, module, statement, scope, function)
        return
    end

    if statement->kind == syntax_kind_index_assignment_statement() then
        semantic_bind_index_assignment(program, module, statement, scope, function)
        return
    end

    if statement->kind == syntax_kind_call_statement() then
        semantic_bind_expression(program, module, syntax_child(statement, 0), scope, null, function)
        return
    end

    if statement->kind == syntax_kind_return_statement() then
        semantic_bind_return(program, module, statement, scope, function)
        return
    end

    if statement->kind == syntax_kind_conditional_statement() then
        let condition: pointer<SyntaxNode> = syntax_child(statement, 0)
        let condition_type: pointer<SemanticType> = semantic_bind_expression(
            program,
            module,
            condition,
            scope,
            null,
            function
        )
        semantic_validate_condition(program, module, condition, condition_type)
        semantic_bind_nested_block(program, module, syntax_child(statement, 1), scope, function)

        if syntax_child_count(statement) > 2 then
            semantic_bind_nested_block(program, module, syntax_child(statement, 2), scope, function)
        end

        return
    end

    if statement->kind == syntax_kind_while_statement() then
        let condition: pointer<SyntaxNode> = syntax_child(statement, 0)
        let condition_type: pointer<SemanticType> = semantic_bind_expression(
            program,
            module,
            condition,
            scope,
            null,
            function
        )
        semantic_validate_condition(program, module, condition, condition_type)
        semantic_bind_nested_block(program, module, syntax_child(statement, 1), scope, function)
    end

    return
end

fn semantic_bind_nested_block(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    block: pointer<SyntaxNode>,
    parent: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    let scope: pointer<Scope> = semantic_program_create_scope(
        program,
        scope_kind_block(),
        parent
    )
    semantic_program_record_scope(
        program,
        semantic_binding_kind_block_scope(),
        block,
        scope
    )
    semantic_bind_block(program, module, block, scope, function)
    return
end

fn semantic_bind_variable(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    declaration: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    let reference: pointer<SyntaxNode> = syntax_child(declaration, 1)
    let declared_type: pointer<SemanticType> = semantic_resolve_type_reference(
        program,
        module,
        reference,
        function
    )
    let initializer: pointer<SyntaxNode> = syntax_child(declaration, 2)
    let initializer_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        initializer,
        scope,
        declared_type,
        function
    )

    if declared_type->kind != semantic_type_kind_error() && !declared_type->value_type then
        semantic_report(
            program,
            module,
            "SOL-S007",
            "Variable '" + declaration->text + "' cannot have non-value type '" + declared_type->name + "'.",
            reference
        )
    end

    if semantic_types_are_incompatible(declared_type, initializer_type) then
        semantic_report(
            program,
            module,
            "SOL-S008",
            "Cannot initialize variable '" + declaration->text + "' of type '" + declared_type->name + "' with value of type '" + initializer_type->name + "'.",
            initializer
        )
    end

    let local: pointer<SemanticSymbol> = create_local_variable_symbol(declaration)
    semantic_program_record_symbol(
        program,
        semantic_binding_kind_declared_symbol(),
        declaration,
        local
    )

    if scope_declare(scope, local) != scope_declare_success() then
        semantic_program_own_symbol(program, local)
        semantic_report_duplicate(program, module, local->name, declaration)
    end

    return
end

fn semantic_bind_assignment(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    assignment: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    let target: pointer<SyntaxNode> = syntax_child(assignment, 0)
    semantic_bind_expression(program, module, target, scope, null, function)
    let symbol: pointer<SemanticSymbol> = semantic_program_symbol_of(
        program,
        semantic_binding_kind_resolved_name(),
        target
    )
    @mut let target_type: pointer<SemanticType> = type_catalog_error(program->catalog)

    if symbol != null then
        semantic_program_record_symbol(
            program,
            semantic_binding_kind_assignment_target(),
            assignment,
            symbol
        )
        target_type = semantic_type_of_value_symbol(program, symbol)
    end

    let value: pointer<SyntaxNode> = syntax_child(assignment, 1)
    let value_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        value,
        scope,
        target_type,
        function
    )

    if symbol == null then
        return
    end

    if symbol->kind == semantic_symbol_kind_local_variable() then
        if !symbol->mutable then
            semantic_report(
                program,
                module,
                "SOL-S010",
                "Cannot assign to immutable variable '" + symbol->name + "'.",
                target
            )
        end
    else
        if symbol->kind == semantic_symbol_kind_parameter() then
            semantic_report(
                program,
                module,
                "SOL-S010",
                "Cannot assign to immutable parameter '" + symbol->name + "'.",
                target
            )
        else
            semantic_report(
                program,
                module,
                "SOL-S009",
                "Cannot assign to '" + symbol->name + "' because it is not a variable.",
                target
            )
        end
    end

    if semantic_types_are_incompatible(target_type, value_type) then
        semantic_report(
            program,
            module,
            "SOL-S011",
            "Cannot assign value of type '" + value_type->name + "' to '" + symbol->name + "' of type '" + target_type->name + "'.",
            value
        )
    end

    return
end

fn semantic_bind_field_assignment(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    assignment: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    let target: pointer<SyntaxNode> = syntax_child(assignment, 0)
    let target_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        target,
        scope,
        null,
        function
    )
    let root: pointer<SemanticSymbol> = semantic_field_root_symbol(program, target)

    if root != null then
        semantic_program_record_symbol(
            program,
            semantic_binding_kind_field_assignment_target(),
            assignment,
            root
        )
    end

    let value: pointer<SyntaxNode> = syntax_child(assignment, 1)
    let value_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        value,
        scope,
        target_type,
        function
    )

    if root == null then
        semantic_report(
            program,
            module,
            "SOL-S037",
            "Field assignment target must be rooted in a local variable or parameter.",
            target
        )
    else
        if root->kind == semantic_symbol_kind_local_variable() then
            if !root->mutable then
                semantic_report(
                    program,
                    module,
                    "SOL-S010",
                    "Cannot mutate a field of immutable variable '" + root->name + "'.",
                    target
                )
            end
        else
            if root->kind == semantic_symbol_kind_parameter() then
                semantic_report(
                    program,
                    module,
                    "SOL-S010",
                    "Cannot mutate a field of immutable parameter '" + root->name + "'.",
                    target
                )
            else
                semantic_report(
                    program,
                    module,
                    "SOL-S037",
                    "Field assignment target must be rooted in a local variable or parameter.",
                    target
                )
            end
        end
    end

    if semantic_types_are_incompatible(target_type, value_type) then
        semantic_report(
            program,
            module,
            "SOL-S011",
            "Cannot assign value of type '" + value_type->name + "' to field of type '" + target_type->name + "'.",
            value
        )
    end

    return
end

fn semantic_bind_pointer_field_assignment(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    assignment: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    let target_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        syntax_child(assignment, 0),
        scope,
        null,
        function
    )
    let value: pointer<SyntaxNode> = syntax_child(assignment, 1)
    let value_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        value,
        scope,
        target_type,
        function
    )

    if semantic_types_are_incompatible(target_type, value_type) then
        semantic_report(
            program,
            module,
            "SOL-S011",
            "Cannot assign value of type '" + value_type->name + "' to pointer field of type '" + target_type->name + "'.",
            value
        )
    end

    return
end

fn semantic_bind_index_assignment(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    assignment: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    let index_expression: pointer<SyntaxNode> = syntax_child(assignment, 0)
    let target_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        index_expression,
        scope,
        null,
        function
    )
    semantic_bind_expression(
        program,
        module,
        syntax_child(assignment, 1),
        scope,
        target_type,
        function
    )
    let indexed_type: pointer<SemanticType> = semantic_program_type_of(
        program,
        semantic_binding_kind_expression_type(),
        syntax_child(index_expression, 0)
    )

    if indexed_type != null then
        if indexed_type->name == "string" then
            semantic_report(
                program,
                module,
                "SOL-S047",
                "String values are immutable and cannot be assigned through indexing.",
                index_expression
            )
        end
    end

    return
end

fn semantic_bind_return(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    statement: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    let return_type: pointer<SemanticType> = semantic_program_type_of(
        program,
        semantic_binding_kind_resolved_type(),
        semantic_function_return_type(function->declaration)
    )

    if syntax_child_count(statement) == 0 then
        if return_type != null then
            if return_type->value_type then
                semantic_report(
                    program,
                    module,
                    "SOL-S016",
                    "Function '" + function->name + "' must return a value of type '" + return_type->name + "'.",
                    statement
                )
            end
        end

        return
    end

    let expression: pointer<SyntaxNode> = syntax_child(statement, 0)
    let expression_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        expression,
        scope,
        return_type,
        function
    )

    if return_type != null then
        if return_type->name == "void" then
            semantic_report(
                program,
                module,
                "SOL-S017",
                "Function '" + function->name + "' returns 'void' and cannot return a value.",
                expression
            )
            return
        end
    end

    if semantic_types_are_incompatible(return_type, expression_type) then
        semantic_report(
            program,
            module,
            "SOL-S018",
            "Cannot return value of type '" + expression_type->name + "' from function '" + function->name + "' returning '" + return_type->name + "'.",
            expression
        )
    end

    return
end

fn semantic_validate_condition(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    condition: pointer<SyntaxNode>,
    type: pointer<SemanticType>
) -> void
    if type == null then
        return
    end

    if type->kind == semantic_type_kind_error() || type->name == "boolean" then
        return
    end

    semantic_report(
        program,
        module,
        "SOL-S006",
        "Condition must have type 'boolean', but found '" + type->name + "'.",
        condition
    )
    return
end

fn semantic_bind_expression(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    expression: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    expected_type: pointer<SemanticType>,
    function: pointer<SemanticSymbol>
) -> pointer<SemanticType>
    @mut let type: pointer<SemanticType> = type_catalog_error(program->catalog)

    if expression->kind == syntax_kind_literal_expression() then
        type = type_catalog_type_of_literal(program->catalog, expression->variant)
    end

    if expression->kind == syntax_kind_null_expression() then
        @mut let contextual_pointer: boolean = false

        if expected_type != null then
            contextual_pointer = expected_type->kind == semantic_type_kind_pointer()
        end

        if contextual_pointer then
            type = expected_type
        else
            semantic_report(
                program,
                module,
                "SOL-S043",
                "Literal 'null' requires a contextual pointer<T> type.",
                expression
            )
        end
    end

    if expression->kind == syntax_kind_name_expression() then
        type = semantic_bind_name_expression(program, module, expression, scope)
    end

    if expression->kind == syntax_kind_parenthesized_expression() then
        type = semantic_bind_expression(
            program,
            module,
            syntax_child(expression, 0),
            scope,
            expected_type,
            function
        )
    end

    if expression->kind == syntax_kind_unary_expression() then
        type = semantic_check_unary(
            program,
            module->name,
            expression,
            semantic_bind_expression(
                program,
                module,
                syntax_child(expression, 0),
                scope,
                null,
                function
            )
        )
    end

    if expression->kind == syntax_kind_binary_expression() then
        type = semantic_bind_binary_expression(
            program,
            module,
            expression,
            scope,
            function
        )
    end

    if expression->kind == syntax_kind_call_expression() then
        type = semantic_bind_call_expression(
            program,
            module,
            expression,
            scope,
            function
        )
    end

    if expression->kind == syntax_kind_qualified_name_expression() then
        type = semantic_bind_qualified_expression(
            program,
            module,
            expression,
            scope
        )
    end

    if expression->kind == syntax_kind_struct_construction_expression() then
        type = semantic_bind_struct_construction(
            program,
            module,
            expression,
            scope,
            function
        )
    end

    if expression->kind == syntax_kind_field_access_expression() then
        type = semantic_bind_field_access(
            program,
            module,
            expression,
            scope,
            function,
            false
        )
    end

    if expression->kind == syntax_kind_pointer_field_access_expression() then
        type = semantic_bind_field_access(
            program,
            module,
            expression,
            scope,
            function,
            true
        )
    end

    if expression->kind == syntax_kind_index_expression() then
        type = semantic_bind_index_expression(
            program,
            module,
            expression,
            scope,
            function
        )
    end

    semantic_program_record_type(
        program,
        semantic_binding_kind_expression_type(),
        expression,
        type
    )
    return type
end

fn semantic_bind_name_expression(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    expression: pointer<SyntaxNode>,
    scope: pointer<Scope>
) -> pointer<SemanticType>
    @mut let symbol: pointer<SemanticSymbol> = scope_lookup(scope, expression->text)

    if symbol == null then
        semantic_report(
            program,
            module,
            "SOL-S002",
            "Unresolved name '" + expression->text + "'.",
            expression
        )
        return type_catalog_error(program->catalog)
    end

    if symbol->kind == semantic_symbol_kind_imported_name() then
        symbol = semantic_import_target(program, symbol)
    end

    semantic_program_record_symbol(
        program,
        semantic_binding_kind_resolved_name(),
        expression,
        symbol
    )
    return semantic_type_of_value_symbol(program, symbol)
end

fn semantic_bind_binary_expression(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    expression: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> pointer<SemanticType>
    let left_expression: pointer<SyntaxNode> = syntax_child(expression, 0)
    let right_expression: pointer<SyntaxNode> = syntax_child(expression, 1)
    let equality: boolean = expression->variant == syntax_binary_equal() || expression->variant == syntax_binary_not_equal()
    @mut let left_type: pointer<SemanticType> = null
    @mut let right_type: pointer<SemanticType> = null

    if equality && semantic_is_null_expression(left_expression) then
        right_type = semantic_bind_expression(
            program,
            module,
            right_expression,
            scope,
            null,
            function
        )
        left_type = semantic_bind_expression(
            program,
            module,
            left_expression,
            scope,
            right_type,
            function
        )
    else
        left_type = semantic_bind_expression(
            program,
            module,
            left_expression,
            scope,
            null,
            function
        )
        @mut let expected: pointer<SemanticType> = null

        if equality && left_type->kind == semantic_type_kind_pointer() then
            expected = left_type
        end

        right_type = semantic_bind_expression(
            program,
            module,
            right_expression,
            scope,
            expected,
            function
        )
    end

    return semantic_check_binary(
        program,
        module->name,
        expression,
        left_type,
        right_type
    )
end

fn semantic_is_null_expression(expression: pointer<SyntaxNode>) -> boolean
    if expression->kind == syntax_kind_null_expression() then
        return true
    end

    if expression->kind == syntax_kind_parenthesized_expression() then
        return semantic_is_null_expression(syntax_child(expression, 0))
    end

    return false
end

fn semantic_bind_qualified_expression(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    expression: pointer<SyntaxNode>,
    scope: pointer<Scope>
) -> pointer<SemanticType>
    let qualifier: pointer<SyntaxNode> = syntax_child(expression, 0)
    let member: pointer<SyntaxNode> = syntax_child(expression, 1)
    let qualifier_type: pointer<SemanticType> = semantic_bind_name_expression(
        program,
        module,
        qualifier,
        scope
    )
    semantic_program_record_type(
        program,
        semantic_binding_kind_expression_type(),
        qualifier,
        qualifier_type
    )
    semantic_program_record_type(
        program,
        semantic_binding_kind_expression_type(),
        member,
        type_catalog_error(program->catalog)
    )
    let symbol: pointer<SemanticSymbol> = semantic_program_symbol_of(
        program,
        semantic_binding_kind_resolved_name(),
        qualifier
    )

    if symbol == null then
        return type_catalog_error(program->catalog)
    end

    if symbol->kind != semantic_symbol_kind_module_namespace() then
        semantic_report(
            program,
            module,
            "SOL-S021",
            "Name '" + qualifier->text + "' does not refer to an injected namespace.",
            qualifier
        )
        return type_catalog_error(program->catalog)
    end

    let target_module: pointer<SemanticModule> = semantic_namespace_target(
        program,
        symbol
    )
    let target: pointer<SemanticSymbol> = semantic_module_export(
        program,
        target_module,
        member->text
    )

    @mut let valid_target: boolean = target != null

    if valid_target then
        valid_target = target->kind == semantic_symbol_kind_function()
    end

    if !valid_target then
        semantic_report(
            program,
            module,
            "SOL-S022",
            "Module '" + target_module->name + "' does not declare function '" + member->text + "'.",
            member
        )
        return type_catalog_error(program->catalog)
    end

    semantic_program_record_symbol(
        program,
        semantic_binding_kind_qualified_function(),
        expression,
        target
    )
    return type_catalog_error(program->catalog)
end

fn semantic_bind_call_expression(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    call: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    active_function: pointer<SemanticSymbol>
) -> pointer<SemanticType>
    let callee: pointer<SyntaxNode> = syntax_child(call, 0)
    let callee_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        callee,
        scope,
        null,
        active_function
    )
    let function: pointer<SemanticSymbol> = semantic_resolved_function_of(
        program,
        callee
    )

    if function == null then
        @mut let type_index: int = 0
        let unresolved_type_count: int = semantic_direct_child_count(
            call,
            syntax_kind_type_reference()
        )

        while type_index < unresolved_type_count do
            semantic_resolve_type_reference(
                program,
                module,
                semantic_direct_child(call, syntax_kind_type_reference(), type_index),
                active_function
            )
            type_index = type_index + 1
        end

        if callee_type->kind != semantic_type_kind_error() then
            semantic_report(
                program,
                module,
                "SOL-S013",
                "Expression of type '" + callee_type->name + "' is not callable.",
                callee
            )
        end

        semantic_bind_call_values_without_target(
            program,
            module,
            call,
            scope,
            active_function
        )
        return type_catalog_error(program->catalog)
    end

    let binding: pointer<SemanticBinding> = semantic_program_add_binding(
        program,
        semantic_binding_kind_called_function(),
        call
    )
    binding->symbol = function
    @mut let index: int = 0
    let type_argument_count: int = semantic_direct_child_count(
        call,
        syntax_kind_type_reference()
    )

    while index < type_argument_count do
        semantic_binding_add_type(
            binding,
            semantic_resolve_type_reference(
                program,
                module,
                semantic_direct_child(call, syntax_kind_type_reference(), index),
                active_function
            )
        )
        index = index + 1
    end

    let expected_type_arguments: int = semantic_symbol_type_parameter_count(function)
    @mut let valid_type_arguments: boolean = type_argument_count == expected_type_arguments

    if !valid_type_arguments then
        semantic_report(
            program,
            module,
            "SOL-S040",
            "Function '" + function->name + "' received an incorrect number of type arguments.",
            call
        )
    end

    index = 0

    while index < type_argument_count do
        let argument: pointer<SemanticType> = semantic_binding_type(binding, index)

        if argument->kind == semantic_type_kind_error() || !argument->value_type then
            valid_type_arguments = false

            if argument->kind != semantic_type_kind_error() then
                semantic_report(
                    program,
                    module,
                    "SOL-S041",
                    "Function type argument must be a value type.",
                    semantic_direct_child(call, syntax_kind_type_reference(), index)
                )
            end
        end

        index = index + 1
    end

    let value_count: int = semantic_call_value_count(call)
    let parameter_count: int = semantic_direct_child_count(
        function->declaration,
        syntax_kind_parameter()
    )

    if value_count != parameter_count then
        semantic_report(
            program,
            module,
            "SOL-S014",
            "Function '" + function->name + "' received an incorrect number of arguments.",
            call
        )
    end

    index = 0

    while index < value_count do
        let value: pointer<SyntaxNode> = semantic_call_value(call, index)
        @mut let expected: pointer<SemanticType> = null

        if index < parameter_count && valid_type_arguments then
            let parameter: pointer<SyntaxNode> = semantic_direct_child(
                function->declaration,
                syntax_kind_parameter(),
                index
            )
            expected = semantic_substitute_type(
                program,
                semantic_program_type_of(
                    program,
                    semantic_binding_kind_resolved_type(),
                    syntax_child(parameter, 1)
                ),
                function,
                binding->types
            )
        end

        let actual: pointer<SemanticType> = semantic_bind_expression(
            program,
            module,
            value,
            scope,
            expected,
            active_function
        )

        if expected != null && semantic_types_are_incompatible(expected, actual) then
            semantic_report(
                program,
                module,
                "SOL-S015",
                "Argument of function '" + function->name + "' expects type '" + expected->name + "', but found '" + actual->name + "'.",
                value
            )
        end

        index = index + 1
    end

    if !valid_type_arguments then
        return type_catalog_error(program->catalog)
    end

    return semantic_substitute_type(
        program,
        semantic_program_type_of(
            program,
            semantic_binding_kind_resolved_type(),
            semantic_function_return_type(function->declaration)
        ),
        function,
        binding->types
    )
end

fn semantic_bind_call_values_without_target(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    call: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    @mut let index: int = 0
    let count: int = semantic_call_value_count(call)

    while index < count do
        semantic_bind_expression(
            program,
            module,
            semantic_call_value(call, index),
            scope,
            null,
            function
        )
        index = index + 1
    end

    return
end

fn semantic_resolved_function_of(
    program: pointer<SemanticProgram>,
    expression: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    if expression->kind == syntax_kind_name_expression() then
        let symbol: pointer<SemanticSymbol> = semantic_program_symbol_of(
            program,
            semantic_binding_kind_resolved_name(),
            expression
        )

        if symbol != null then
            if symbol->kind == semantic_symbol_kind_function() then
                return symbol
            end
        end

        return null
    end

    if expression->kind == syntax_kind_parenthesized_expression() then
        return semantic_resolved_function_of(program, syntax_child(expression, 0))
    end

    if expression->kind == syntax_kind_qualified_name_expression() then
        return semantic_program_symbol_of(
            program,
            semantic_binding_kind_qualified_function(),
            expression
        )
    end

    return null
end

fn semantic_call_value_count(call: pointer<SyntaxNode>) -> int
    @mut let count: int = 0
    @mut let index: int = 1
    let child_count: int = syntax_child_count(call)

    while index < child_count do
        if syntax_child(call, index)->kind != syntax_kind_type_reference() then
            count = count + 1
        end

        index = index + 1
    end

    return count
end

fn semantic_call_value(
    call: pointer<SyntaxNode>,
    requested_index: int
) -> pointer<SyntaxNode>
    @mut let found: int = 0
    @mut let index: int = 1
    let count: int = syntax_child_count(call)

    while index < count do
        let child: pointer<SyntaxNode> = syntax_child(call, index)

        if child->kind != syntax_kind_type_reference() then
            if found == requested_index then
                return child
            end

            found = found + 1
        end

        index = index + 1
    end

    return null
end

fn semantic_bind_struct_construction(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    expression: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> pointer<SemanticType>
    let reference: pointer<SyntaxNode> = syntax_child(expression, 0)
    let type: pointer<SemanticType> = semantic_resolve_type_reference(
        program,
        module,
        reference,
        function
    )

    if type->kind == semantic_type_kind_error() then
        semantic_bind_struct_initializers_without_target(
            program,
            module,
            expression,
            scope,
            function
        )
        return type
    end

    if type->kind != semantic_type_kind_struct() then
        semantic_bind_struct_initializers_without_target(
            program,
            module,
            expression,
            scope,
            function
        )
        semantic_report(
            program,
            module,
            "SOL-S030",
            "Type '" + type->name + "' is not a struct and cannot be constructed with field initializers.",
            reference
        )
        return type_catalog_error(program->catalog)
    end

    let struct_symbol: pointer<SemanticSymbol> = semantic_struct_symbol_for_type(
        program,
        type
    )
    semantic_program_record_symbol(
        program,
        semantic_binding_kind_constructed_struct(),
        expression,
        struct_symbol
    )
    @mut let initializer_index: int = 0
    let initializer_count: int = semantic_direct_child_count(
        expression,
        syntax_kind_struct_field_initializer()
    )

    while initializer_index < initializer_count do
        let initializer: pointer<SyntaxNode> = semantic_direct_child(
            expression,
            syntax_kind_struct_field_initializer(),
            initializer_index
        )
        let field: pointer<SemanticSymbol> = semantic_struct_field_by_name(
            struct_symbol,
            initializer->text
        )

        if field == null then
            semantic_bind_expression(
                program,
                module,
                syntax_child(initializer, 1),
                scope,
                null,
                function
            )
            semantic_report(
                program,
                module,
                "SOL-S031",
                "Struct '" + struct_symbol->name + "' has no field named '" + initializer->text + "'.",
                initializer
            )
        else
            semantic_program_record_symbol(
                program,
                semantic_binding_kind_initialized_field(),
                initializer,
                field
            )
            @mut let duplicate: boolean = false
            @mut let previous: int = 0

            while previous < initializer_index do
                if semantic_direct_child(expression, syntax_kind_struct_field_initializer(), previous)->text == initializer->text then
                    duplicate = true
                end

                previous = previous + 1
            end

            if duplicate then
                semantic_report(
                    program,
                    module,
                    "SOL-S032",
                    "Field '" + field->name + "' of struct '" + struct_symbol->name + "' is initialized more than once.",
                    initializer
                )
            else
                let expected: pointer<SemanticType> = semantic_field_type(
                    program,
                    type,
                    field
                )
                let value: pointer<SyntaxNode> = syntax_child(initializer, 1)
                let actual: pointer<SemanticType> = semantic_bind_expression(
                    program,
                    module,
                    value,
                    scope,
                    expected,
                    function
                )

                if semantic_types_are_incompatible(expected, actual) then
                    semantic_report(
                        program,
                        module,
                        "SOL-S034",
                        "Field '" + field->name + "' of struct '" + struct_symbol->name + "' expects type '" + expected->name + "', but found '" + actual->name + "'.",
                        value
                    )
                end
            end
        end

        initializer_index = initializer_index + 1
    end

    @mut let field_index: int = 0
    let field_count: int = semantic_struct_field_count(struct_symbol)

    while field_index < field_count do
        let field: pointer<SemanticSymbol> = semantic_struct_field(
            struct_symbol,
            field_index
        )

        if !semantic_initializer_named(expression, field->name) then
            semantic_report(
                program,
                module,
                "SOL-S033",
                "Construction of struct '" + struct_symbol->name + "' is missing field '" + field->name + "'.",
                expression
            )
        end

        field_index = field_index + 1
    end

    return type
end

fn semantic_bind_struct_initializers_without_target(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    expression: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> void
    @mut let index: int = 0
    let count: int = semantic_direct_child_count(
        expression,
        syntax_kind_struct_field_initializer()
    )

    while index < count do
        semantic_bind_expression(
            program,
            module,
            syntax_child(
                semantic_direct_child(expression, syntax_kind_struct_field_initializer(), index),
                1
            ),
            scope,
            null,
            function
        )
        index = index + 1
    end

    return
end

fn semantic_initializer_named(
    expression: pointer<SyntaxNode>,
    name: string
) -> boolean
    @mut let index: int = 0
    let count: int = semantic_direct_child_count(
        expression,
        syntax_kind_struct_field_initializer()
    )

    while index < count do
        if semantic_direct_child(expression, syntax_kind_struct_field_initializer(), index)->text == name then
            return true
        end

        index = index + 1
    end

    return false
end

fn semantic_bind_field_access(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    expression: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>,
    through_pointer: boolean
) -> pointer<SemanticType>
    let target_expression: pointer<SyntaxNode> = syntax_child(expression, 0)
    @mut let target_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        target_expression,
        scope,
        null,
        function
    )

    if target_type->kind == semantic_type_kind_error() then
        return target_type
    end

    if through_pointer then
        if target_type->kind != semantic_type_kind_pointer() then
            semantic_report(
                program,
                module,
                "SOL-S044",
                "Cannot use '->' on value of non-pointer type '" + target_type->name + "'.",
                target_expression
            )
            return type_catalog_error(program->catalog)
        end

        target_type = target_type->element_type
    end

    let field_name: string = expression->text

    if target_type->kind != semantic_type_kind_struct() then
        semantic_report(
            program,
            module,
            "SOL-S035",
            "Cannot access field '" + field_name + "' on non-struct type '" + target_type->name + "'.",
            syntax_child(expression, 1)
        )
        return type_catalog_error(program->catalog)
    end

    let struct_symbol: pointer<SemanticSymbol> = semantic_struct_symbol_for_type(
        program,
        target_type
    )
    let field: pointer<SemanticSymbol> = semantic_struct_field_by_name(
        struct_symbol,
        field_name
    )

    if field == null then
        semantic_report(
            program,
            module,
            "SOL-S036",
            "Struct '" + struct_symbol->name + "' has no field named '" + field_name + "'.",
            syntax_child(expression, 1)
        )
        return type_catalog_error(program->catalog)
    end

    @mut let binding_kind: int = semantic_binding_kind_accessed_field()

    if through_pointer then
        binding_kind = semantic_binding_kind_accessed_pointer_field()
    end

    semantic_program_record_symbol(program, binding_kind, expression, field)
    return semantic_field_type(program, target_type, field)
end

fn semantic_bind_index_expression(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    expression: pointer<SyntaxNode>,
    scope: pointer<Scope>,
    function: pointer<SemanticSymbol>
) -> pointer<SemanticType>
    let target: pointer<SyntaxNode> = syntax_child(expression, 0)
    let index: pointer<SyntaxNode> = syntax_child(expression, 1)
    let target_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        target,
        scope,
        null,
        function
    )
    let index_type: pointer<SemanticType> = semantic_bind_expression(
        program,
        module,
        index,
        scope,
        null,
        function
    )

    if index_type->kind != semantic_type_kind_error() && index_type->name != "int" then
        semantic_report(
            program,
            module,
            "SOL-S046",
            "Index must have type 'int', but found '" + index_type->name + "'.",
            index
        )
    end

    if target_type->kind == semantic_type_kind_error() || index_type->kind == semantic_type_kind_error() then
        return type_catalog_error(program->catalog)
    end

    if target_type->name == "string" then
        if index_type->name == "int" then
            return type_catalog_lookup(program->catalog, "char")
        end

        return type_catalog_error(program->catalog)
    end

    if target_type->kind == semantic_type_kind_pointer() then
        if index_type->name == "int" then
            return target_type->element_type
        end

        return type_catalog_error(program->catalog)
    end

    semantic_report(
        program,
        module,
        "SOL-S045",
        "Cannot index value of type '" + target_type->name + "'; only strings and pointers are indexable.",
        target
    )
    return type_catalog_error(program->catalog)
end

fn semantic_type_of_value_symbol(
    program: pointer<SemanticProgram>,
    symbol: pointer<SemanticSymbol>
) -> pointer<SemanticType>
    if symbol == null then
        return type_catalog_error(program->catalog)
    end

    let reference: pointer<SyntaxNode> = semantic_symbol_declared_type_reference(symbol)

    if reference == null then
        return type_catalog_error(program->catalog)
    end

    let type: pointer<SemanticType> = semantic_program_type_of(
        program,
        semantic_binding_kind_resolved_type(),
        reference
    )

    if type == null then
        return type_catalog_error(program->catalog)
    end

    return type
end

fn semantic_field_root_symbol(
    program: pointer<SemanticProgram>,
    expression: pointer<SyntaxNode>
) -> pointer<SemanticSymbol>
    @mut let target: pointer<SyntaxNode> = expression

    while target->kind == syntax_kind_field_access_expression() do
        target = syntax_child(target, 0)
    end

    if target->kind != syntax_kind_name_expression() then
        return null
    end

    return semantic_program_symbol_of(
        program,
        semantic_binding_kind_resolved_name(),
        target
    )
end

fn semantic_field_type(
    program: pointer<SemanticProgram>,
    instance: pointer<SemanticType>,
    field: pointer<SemanticSymbol>
) -> pointer<SemanticType>
    let open_type: pointer<SemanticType> = semantic_program_type_of(
        program,
        semantic_binding_kind_resolved_type(),
        semantic_symbol_declared_type_reference(field)
    )

    return semantic_substitute_type(
        program,
        open_type,
        field->owner,
        instance->arguments
    )
end

fn semantic_substitute_type(
    program: pointer<SemanticProgram>,
    type: pointer<SemanticType>,
    owner: pointer<SemanticSymbol>,
    arguments: pointer<Vector<pointer<SemanticType>>>
) -> pointer<SemanticType>
    if type == null then
        return type_catalog_error(program->catalog)
    end

    if type->kind == semantic_type_kind_type_parameter() && owner != null then
        @mut let index: int = 0
        let count: int = semantic_symbol_type_parameter_count(owner)

        while index < count && index < vector_length<pointer<SemanticType>>(arguments) do
            let parameter: pointer<SemanticSymbol> = semantic_symbol_type_parameter(
                owner,
                index
            )

            if parameter->declaration == type->identity then
                return vector_get<pointer<SemanticType>>(arguments, index)
            end

            index = index + 1
        end

        return type
    end

    if type->kind == semantic_type_kind_pointer() then
        return semantic_program_own_type(
            program,
            create_pointer_type(
                semantic_substitute_type(program, type->element_type, owner, arguments)
            )
        )
    end

    if type->kind == semantic_type_kind_struct() then
        let substituted: pointer<Vector<pointer<SemanticType>>> = create_vector<pointer<SemanticType>>()
        @mut let index: int = 0
        let count: int = semantic_type_argument_count(type)

        while index < count do
            vector_push<pointer<SemanticType>>(
                substituted,
                semantic_substitute_type(
                    program,
                    semantic_type_argument(type, index),
                    owner,
                    arguments
                )
            )
            index = index + 1
        end

        let result: pointer<SemanticType> = semantic_program_own_type(
            program,
            create_struct_type(type->identity, substituted)
        )
        destroy_vector<pointer<SemanticType>>(substituted)
        return result
    end

    return type
end

fn semantic_types_are_incompatible(
    expected: pointer<SemanticType>,
    actual: pointer<SemanticType>
) -> boolean
    if expected == null || actual == null then
        return false
    end

    if expected->kind == semantic_type_kind_error() || actual->kind == semantic_type_kind_error() then
        return false
    end

    if !expected->value_type then
        return false
    end

    return !semantic_type_equals(expected, actual)
end

fn semantic_validate_generic_instantiations(program: pointer<SemanticProgram>) -> void
    @mut let module_index: int = 0
    let module_count: int = semantic_program_module_count(program)

    while module_index < module_count do
        let module: pointer<SemanticModule> = semantic_program_module_at(
            program,
            module_index
        )
        @mut let index: int = 0
        let count: int = syntax_child_count(module->unit)

        while index < count do
            let declaration: pointer<SyntaxNode> = syntax_child(module->unit, index)

            if declaration->kind == syntax_kind_function_declaration() then
                let function: pointer<SemanticSymbol> = semantic_program_symbol_of(
                    program,
                    semantic_binding_kind_declared_symbol(),
                    declaration
                )

                if semantic_symbol_type_parameter_count(function) == 0 then
                    let active: pointer<Vector<pointer<SemanticGenericFrame>>> = create_vector<pointer<SemanticGenericFrame>>()
                    let arguments: pointer<Vector<pointer<SemanticType>>> = create_vector<pointer<SemanticType>>()
                    semantic_validate_generic_frame(
                        program,
                        module,
                        function,
                        arguments,
                        active
                    )
                    destroy_vector<pointer<SemanticType>>(arguments)
                    destroy_vector<pointer<SemanticGenericFrame>>(active)
                end
            end

            index = index + 1
        end

        module_index = module_index + 1
    end

    return
end

fn semantic_validate_generic_frame(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    function: pointer<SemanticSymbol>,
    arguments: pointer<Vector<pointer<SemanticType>>>,
    active: pointer<Vector<pointer<SemanticGenericFrame>>>
) -> void
    let body: pointer<SyntaxNode> = semantic_function_body(function->declaration)

    if body == null then
        return
    end

    let frame: pointer<SemanticGenericFrame> = create_semantic_generic_frame(
        function,
        arguments
    )
    vector_push<pointer<SemanticGenericFrame>>(active, frame)
    semantic_validate_generic_calls_in_node(program, module, body, frame, active)
    vector_pop<pointer<SemanticGenericFrame>>(active)
    destroy_semantic_generic_frame(frame)
    return
end

fn semantic_validate_generic_calls_in_node(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    node: pointer<SyntaxNode>,
    current: pointer<SemanticGenericFrame>,
    active: pointer<Vector<pointer<SemanticGenericFrame>>>
) -> void
    if node->kind == syntax_kind_call_expression() then
        semantic_validate_generic_call(program, module, node, current, active)
    end

    @mut let index: int = 0
    let count: int = syntax_child_count(node)

    while index < count do
        semantic_validate_generic_calls_in_node(
            program,
            module,
            syntax_child(node, index),
            current,
            active
        )
        index = index + 1
    end

    return
end

fn semantic_validate_generic_call(
    program: pointer<SemanticProgram>,
    module: pointer<SemanticModule>,
    call: pointer<SyntaxNode>,
    current: pointer<SemanticGenericFrame>,
    active: pointer<Vector<pointer<SemanticGenericFrame>>>
) -> void
    let binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        semantic_binding_kind_called_function(),
        call
    )

    if binding == null then
        return
    end

    if binding->symbol == null then
        return
    end

    let target: pointer<SemanticSymbol> = binding->symbol

    if semantic_binding_type_count(binding) != semantic_symbol_type_parameter_count(target) then
        return
    end

    let concrete: pointer<Vector<pointer<SemanticType>>> = create_vector<pointer<SemanticType>>()
    @mut let index: int = 0
    let count: int = semantic_binding_type_count(binding)
    @mut let unresolved: boolean = false

    while index < count do
        let argument: pointer<SemanticType> = semantic_substitute_type(
            program,
            semantic_binding_type(binding, index),
            current->function,
            current->arguments
        )
        vector_push<pointer<SemanticType>>(concrete, argument)

        if semantic_type_contains_unresolved(argument) then
            unresolved = true
        end

        index = index + 1
    end

    if unresolved then
        destroy_vector<pointer<SemanticType>>(concrete)
        return
    end

    index = 0
    let active_count: int = vector_length<pointer<SemanticGenericFrame>>(active)

    while index < active_count do
        let ancestor: pointer<SemanticGenericFrame> = vector_get<pointer<SemanticGenericFrame>>(
            active,
            index
        )

        if ancestor->function == target then
            if !semantic_type_vector_equals(ancestor->arguments, concrete) then
                if !semantic_has_diagnostic(program, module->name, "SOL-S042", call) then
                    semantic_report(
                        program,
                        module,
                        "SOL-S042",
                        "Generic function '" + target->name + "' recursively requests a different specialization.",
                        call
                    )
                end
            end

            destroy_vector<pointer<SemanticType>>(concrete)
            return
        end

        index = index + 1
    end

    let target_module: pointer<SemanticModule> = semantic_module_for_declaration(
        program,
        target->declaration
    )
    semantic_validate_generic_frame(
        program,
        target_module,
        target,
        concrete,
        active
    )
    destroy_vector<pointer<SemanticType>>(concrete)
    return
end

fn create_semantic_generic_frame(
    function: pointer<SemanticSymbol>,
    arguments: pointer<Vector<pointer<SemanticType>>>
) -> pointer<SemanticGenericFrame>
    let frame: pointer<SemanticGenericFrame> = memory::allocate<SemanticGenericFrame>(1)

    if frame == null then
        return null
    end

    frame->function = function
    frame->arguments = create_vector<pointer<SemanticType>>()
    @mut let index: int = 0
    let count: int = vector_length<pointer<SemanticType>>(arguments)

    while index < count do
        vector_push<pointer<SemanticType>>(
            frame->arguments,
            vector_get<pointer<SemanticType>>(arguments, index)
        )
        index = index + 1
    end

    return frame
end

fn destroy_semantic_generic_frame(frame: pointer<SemanticGenericFrame>) -> void
    if frame == null then
        return
    end

    destroy_vector<pointer<SemanticType>>(frame->arguments)
    frame->function = null
    frame->arguments = null
    memory::free<SemanticGenericFrame>(frame)
    return
end

fn semantic_type_contains_unresolved(type: pointer<SemanticType>) -> boolean
    if type->kind == semantic_type_kind_error() || type->kind == semantic_type_kind_type_parameter() then
        return true
    end

    if type->kind == semantic_type_kind_pointer() then
        return semantic_type_contains_unresolved(type->element_type)
    end

    if type->kind == semantic_type_kind_struct() then
        @mut let index: int = 0
        let count: int = semantic_type_argument_count(type)

        while index < count do
            if semantic_type_contains_unresolved(semantic_type_argument(type, index)) then
                return true
            end

            index = index + 1
        end
    end

    return false
end

fn semantic_type_vector_equals(
    left: pointer<Vector<pointer<SemanticType>>>,
    right: pointer<Vector<pointer<SemanticType>>>
) -> boolean
    let count: int = vector_length<pointer<SemanticType>>(left)

    if count != vector_length<pointer<SemanticType>>(right) then
        return false
    end

    @mut let index: int = 0

    while index < count do
        if !semantic_type_equals(
            vector_get<pointer<SemanticType>>(left, index),
            vector_get<pointer<SemanticType>>(right, index)
        ) then
            return false
        end

        index = index + 1
    end

    return true
end

fn semantic_has_diagnostic(
    program: pointer<SemanticProgram>,
    module_name: string,
    code: string,
    node: pointer<SyntaxNode>
) -> boolean
    @mut let index: int = 0
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        let item: SemanticDiagnostic = semantic_program_diagnostic(program, index)

        if item.module_name == module_name && item.diagnostic.code == code && item.diagnostic.span.start.offset == node->span.start.offset && item.diagnostic.span.end_position.offset == node->span.end_position.offset then
            return true
        end

        index = index + 1
    end

    return false
end
