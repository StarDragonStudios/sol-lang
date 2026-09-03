inject namespace std.console as console
inject std.collections.vector
inject frontend.lexer only LexResult, destroy_lex_result, scan_source
inject frontend.parser only ParseResult, destroy_parse_result, parse_tokens
inject frontend.syntax
inject frontend.source only SourcePosition, source_position, source_span
inject semantics.types
inject semantics.symbol
inject semantics.scope
inject semantics.model
inject semantics.analyzer

struct ParsedSemanticSource
    lexical: LexResult
    parsed: ParseResult
end

@init
fn launch() -> int
    let bindings: int = test_binding_index()

    if bindings != 0 then
        console::print_line("semantic analysis test failed: binding index")
        return 150 + bindings
    end

    let successful: int = test_successful_program()

    if successful != 0 then
        console::print_line("semantic analysis test failed: successful program")
        return 10 + successful
    end

    let modules: int = test_module_resolution()

    if modules != 0 then
        console::print_line("semantic analysis test failed: module resolution")
        return 30 + modules
    end

    let objects: int = test_class_types_scopes_and_diagnostics()

    if objects != 0 then
        console::print_line("semantic analysis test failed: class types and scopes")
        return 40 + objects
    end

    let fields: int = test_class_fields_and_member_access()

    if fields != 0 then
        console::print_line("semantic analysis test failed: class fields and access")
        return 45 + fields
    end

    let methods: int = test_instance_methods_and_receivers()

    if methods != 0 then
        console::print_line("semantic analysis test failed: methods and receivers")
        return 48 + methods
    end

    let constructors: int = test_constructors_and_initialization()

    if constructors != 0 then
        console::print_line("semantic analysis test failed: constructors and initialization")
        return 49 + constructors
    end

    let overloads: int = test_overload_resolution()
    if overloads != 0 then
        console::print_line("semantic analysis test failed: overload resolution")
        return 170 + overloads
    end
    let overload_edges: int = test_overload_edge_cases()
    if overload_edges != 0 then
        console::print_line("semantic analysis test failed: overload edge cases")
        return 180 + overload_edges
    end

    let inheritance: int = test_class_inheritance()
    if inheritance != 0 then
        console::print_line("semantic analysis test failed: class inheritance")
        return 190 + inheritance
    end
    let inheritance_modules: int = test_inheritance_modules()
    if inheritance_modules != 0 then
        console::print_line("semantic analysis test failed: inherited module names")
        return 200 + inheritance_modules
    end

    let object_returns: int = test_object_return_and_reconstruction_rules()
    if object_returns != 0 then
        console::print_line("semantic analysis test failed: object return and reconstruction rules")
        return 210 + object_returns
    end

    let core: int = test_core_diagnostics()

    if core != 0 then
        console::print_line("semantic analysis test failed: core diagnostics")
        return 50 + core
    end

    let structs: int = test_struct_pointer_and_generic_diagnostics()

    if structs != 0 then
        console::print_line("semantic analysis test failed: structs and generics")
        return 90 + structs
    end

    let program_rules: int = test_program_diagnostics()

    if program_rules != 0 then
        console::print_line("semantic analysis test failed: program diagnostics")
        return 130 + program_rules
    end

    return 0
end

fn test_object_return_and_reconstruction_rules() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "class Person\n    @constructor\n    fn build() -> void\n        return\n    end\n    fn create() -> pointer<Person>\n        return new Person()\n    end\nend\nclass Holder\n    person: Person\n    @constructor\n    fn build() -> void\n        this.person = Person()\n    end\nend\nfn create() -> pointer<Person>\n    return new Person()\nend\nfn use(holder: pointer<Holder>) -> void\n    let direct: Holder = Holder()\n    direct.person = Person()\n    holder->person = Person()\n    return\nend"
    )
    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end
    let program: pointer<SemanticProgram> = semantic_test_analyze("object.returns", source, false)
    @mut let failure: int = 0
    if !semantic_program_successful(program) then
        failure = 2
    end
    destroy_semantic_program(program)
    destroy_semantic_test_source(source)
    if failure != 0 then
        return failure
    end

    let invalid: ParsedSemanticSource = parse_semantic_test_source(
        "class Person\n    @constructor\n    fn build() -> void\n        return\n    end\n    fn copy() -> Person\n        return this\n    end\nend\nclass Holder\n    person: Person\n    @constructor\n    fn build() -> void\n        this.person = Person()\n    end\nend\nfn create() -> Person\n    return Person()\nend\nfn copy() -> Person\n    let value: Person = Person()\n    return value\nend\nfn use(holder: pointer<Holder>) -> void\n    let direct: Holder = Holder()\n    let person: Person = Person()\n    direct.person = person\n    holder->person = person\n    return\nend"
    )
    if !semantic_test_parse_valid(invalid) then
        destroy_semantic_test_source(invalid)
        return 3
    end
    let invalid_program: pointer<SemanticProgram> = semantic_test_analyze("invalid.object.returns", invalid, false)
    if semantic_test_code_count(invalid_program, "SOL-S086") != 3 || semantic_test_code_count(invalid_program, "SOL-S072") != 2 then
        failure = 4
    end
    let return_reference: pointer<SyntaxNode> = semantic_function_return_type(semantic_direct_child(syntax_child(invalid.parsed.root, 0), syntax_kind_function_declaration(), 1))
    if !semantic_test_code_has_span(invalid_program, "SOL-S086", return_reference->span.start.offset, return_reference->span.end_position.offset) then
        failure = 5
    end
    if !semantic_test_diagnostics_ordered(invalid_program) then
        failure = 6
    end
    destroy_semantic_program(invalid_program)
    destroy_semantic_test_source(invalid)
    return failure
end

fn test_class_inheritance() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "class Child << Parent\n    label: string\n    @constructor\n    fn from_int(id: int) -> void\n        @mut let normalized: int = id\n        if normalized < 0 then\n            normalized = 0\n        end\n        base(normalized)\n        base.read()\n        this.label = \"child\"\n    end\n    @constructor\n    fn from_text(text: string) -> void\n        this(1)\n        this.label = text\n    end\n    @override\n    fn describe() -> string\n        return this.label\n    end\n    fn inherited() -> int\n        return this.read()\n    end\n    fn select(value: string) -> string\n        return value\n    end\n    fn accept(value: pointer<Parent>) -> void\n        return\n    end\nend\nclass Parent\n    @protected\n    value: int\n    @private\n    secret: int\n    @protected\n    @constructor\n    fn build(value: int) -> void\n        this.value = value\n        this.secret = 0\n    end\n    fn describe() -> string\n        return \"parent\"\n    end\n    @protected\n    fn read() -> int\n        return this.value\n    end\n    fn select(value: int) -> int\n        return value\n    end\nend\nfn use() -> void\n    let direct: Child = Child(1)\n    let dynamic: pointer<Child> = new Child(\"text\")\n    let parent: pointer<Parent> = dynamic\n    @mut let other: pointer<Parent> = null\n    other = dynamic\n    let base_value: int = direct.select(2)\n    let child_value: string = dynamic->select(\"value\")\n    dynamic->accept(parent)\n    return\nend\nclass Branches\n    value: int\n    @constructor\n    fn build(flag: boolean) -> void\n        if flag then\n            this.value = 1\n            return\n        else\n            this.value = 2\n        end\n        this.read()\n    end\n    fn read() -> int\n        return this.value\n    end\nend"
    )
    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end
    let program: pointer<SemanticProgram> = semantic_test_analyze("inheritance", source, false)
    @mut let failure: int = 0
    if !semantic_program_successful(program) then
        failure = 2
    end
    let child: pointer<SemanticSymbol> = semantic_model_declared_symbol(program, syntax_child(source.parsed.root, 0))
    let parent: pointer<SemanticSymbol> = semantic_model_declared_symbol(program, syntax_child(source.parsed.root, 1))
    let describe: pointer<SemanticSymbol> = semantic_model_declared_symbol(program, semantic_direct_child(child->declaration, syntax_kind_function_declaration(), 2))
    if failure == 0 && (child->base_class != parent || describe->overridden_method == null) then
        failure = 3
    end
    if failure == 0 && describe->overridden_method->owner != parent then
        failure = 4
    end
    let constructor_body: pointer<SyntaxNode> = semantic_function_body(semantic_direct_child(child->declaration, syntax_kind_function_declaration(), 0))
    let base_call: pointer<SyntaxNode> = syntax_child(syntax_child(constructor_body, 2), 0)
    if failure == 0 && (semantic_model_called_constructor(program, base_call)->owner != parent || semantic_model_type_of_expression(program, base_call)->name != "void") then
        failure = 5
    end
    let use_body: pointer<SyntaxNode> = semantic_function_body(syntax_child(source.parsed.root, 2))
    let inherited_call: pointer<SyntaxNode> = syntax_child(syntax_child(use_body, 5), 2)
    let local_call: pointer<SyntaxNode> = syntax_child(syntax_child(use_body, 6), 2)
    if failure == 0 && (semantic_model_called_function(program, inherited_call)->owner != parent || semantic_model_called_function(program, local_call)->owner != child) then
        failure = 6
    end
    destroy_semantic_program(program)
    destroy_semantic_test_source(source)
    if failure != 0 then
        return failure
    end

    let invalid: ParsedSemanticSource = parse_semantic_test_source(
        "class Parent\n    @private\n    secret: int\n    @protected\n    value: int\n    @constructor\n    fn build() -> void\n        this.secret = 0\n        this.value = 1\n    end\n    fn method() -> int\n        return 1\n    end\nend\nclass Child << Parent\n    secret: int\n    @constructor\n    fn build() -> void\n        let early: int = this.secret\n        if true then\n            base()\n        end\n        return\n    end\n    @override\n    @private\n    fn method() -> string\n        return \"wrong\"\n    end\n    @override\n    fn missing() -> void\n        return\n    end\n    fn access(other: pointer<Child>) -> int\n        return other->value\n    end\n    fn accept(value: pointer<Parent>) -> void\n        return\n    end\nend\nclass Reads\n    first: int\n    second: int\n    @constructor\n    fn build() -> void\n        this.first = this.second\n        this.read()\n        while true do\n            this.second = 1\n        end\n    end\n    fn read() -> int\n        return this.first\n    end\nend\nclass CycleA << CycleB\nend\nclass CycleB << CycleA\nend\nstruct Data\n    value: int\nend\nclass Wrong << Data\nend\nfn invalid(parent: pointer<Parent>, child: pointer<Child>) -> void\n    let down: pointer<Child> = parent\n    child->accept(child)\n    return\nend"
    )
    if !semantic_test_parse_valid(invalid) then
        destroy_semantic_test_source(invalid)
        return 7
    end
    let invalid_program: pointer<SemanticProgram> = semantic_test_analyze("invalid.inheritance", invalid, false)
    if !semantic_test_has_code(invalid_program, "SOL-S077") || !semantic_test_has_code(invalid_program, "SOL-S078") || !semantic_test_has_code(invalid_program, "SOL-S079") || !semantic_test_has_code(invalid_program, "SOL-S080") || !semantic_test_has_code(invalid_program, "SOL-S081") || !semantic_test_has_code(invalid_program, "SOL-S082") || !semantic_test_has_code(invalid_program, "SOL-S083") || !semantic_test_has_code(invalid_program, "SOL-S084") || !semantic_test_has_code(invalid_program, "SOL-S085") || !semantic_test_has_code(invalid_program, "SOL-S057") || !semantic_test_has_code(invalid_program, "SOL-S008") || !semantic_test_has_code(invalid_program, "SOL-S015") then
        failure = 8
    end
    let invalid_child: pointer<SyntaxNode> = syntax_child(invalid.parsed.root, 1)
    let hidden_field: pointer<SyntaxNode> = semantic_direct_child(invalid_child, syntax_kind_class_field_declaration(), 0)
    let cycle: pointer<SyntaxNode> = semantic_direct_child(syntax_child(invalid.parsed.root, 3), syntax_kind_class_base_clause(), 0)
    let wrong_override: pointer<SyntaxNode> = semantic_direct_child(invalid_child, syntax_kind_function_declaration(), 1)
    let missing_override: pointer<SyntaxNode> = semantic_direct_child(invalid_child, syntax_kind_function_declaration(), 2)
    if !semantic_test_code_has_span(invalid_program, "SOL-S078", cycle->span.start.offset, cycle->span.end_position.offset) || !semantic_test_code_has_span(invalid_program, "SOL-S079", hidden_field->span.start.offset, hidden_field->span.end_position.offset) || !semantic_test_code_has_span(invalid_program, "SOL-S080", missing_override->span.start.offset, missing_override->span.end_position.offset) || !semantic_test_code_has_span(invalid_program, "SOL-S081", wrong_override->span.start.offset, wrong_override->span.end_position.offset) then
        failure = 9
    end
    if !semantic_test_diagnostics_ordered(invalid_program) then
        failure = 10
    end
    destroy_semantic_program(invalid_program)
    destroy_semantic_test_source(invalid)
    return failure
end

fn test_inheritance_modules() -> int
    let library: ParsedSemanticSource = parse_semantic_test_source("@protected\nclass Base\n    @protected\n    value: int\n    @protected\n    @constructor\n    fn build() -> void\n        this.value = 1\n    end\nend\n@private\nclass Hidden\nend")
    let application: ParsedSemanticSource = parse_semantic_test_source("inject namespace library as model\nclass Child << model::Base\n    @constructor\n    fn build() -> void\n        base()\n    end\n    fn read() -> int\n        return this.value\n    end\nend")
    if !semantic_test_parse_valid(library) || !semantic_test_parse_valid(application) then
        destroy_semantic_test_source(application)
        destroy_semantic_test_source(library)
        return 1
    end
    let sources: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(sources, source_module("application", application.parsed.root))
    vector_push<SourceModule>(sources, source_module("library", library.parsed.root))
    let program: pointer<SemanticProgram> = analyze_library_modules(sources)
    @mut let failure: int = 0
    if !semantic_program_successful(program) then
        failure = 2
    end
    let child: pointer<SyntaxNode> = syntax_child(application.parsed.root, 1)
    let base: pointer<SemanticSymbol> = semantic_model_declared_symbol(program, syntax_child(library.parsed.root, 0))
    if semantic_model_base_class(program, child) != base then
        failure = 3
    end
    let constructor: pointer<SyntaxNode> = semantic_direct_child(child, syntax_kind_function_declaration(), 0)
    let receiver: pointer<SemanticSymbol> = semantic_model_base_receiver(program, constructor)
    if receiver == null then
        failure = 4
    else
        if receiver->owner != base || receiver->name != "base" then
            failure = 4
        end
    end
    destroy_semantic_program(program)
    destroy_vector<SourceModule>(sources)
    destroy_semantic_test_source(application)

    let invalid: ParsedSemanticSource = parse_semantic_test_source("inject namespace library as model\nclass Bad << model::Hidden\nend\nclass Unknown << absent::Base\nend")
    let invalid_sources: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(invalid_sources, source_module("invalid", invalid.parsed.root))
    vector_push<SourceModule>(invalid_sources, source_module("library", library.parsed.root))
    let invalid_program: pointer<SemanticProgram> = analyze_library_modules(invalid_sources)
    if !semantic_test_has_code(invalid_program, "SOL-S077") || !semantic_test_has_code(invalid_program, "SOL-S003") then
        failure = 5
    end
    destroy_semantic_program(invalid_program)
    destroy_vector<SourceModule>(invalid_sources)
    destroy_semantic_test_source(invalid)
    destroy_semantic_test_source(library)
    return failure
end

fn test_overload_resolution() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "struct Wrap\n    value: int\nend\nclass Choice\n    value: int\n    @constructor\n    fn from_int(value: int) -> void\n        this.value = value\n    end\n    @constructor\n    fn from_string(value: string) -> void\n        this(1)\n    end\n    fn choose(value: int) -> int\n        return value\n    end\n    fn choose(value: string) -> string\n        return value\n    end\n    fn choose(value: Wrap) -> int\n        return value.value\n    end\n    fn take<T>(value: T) -> T\n        return value\n    end\n    fn take<T>(value: pointer<T>) -> T\n        return value[0]\n    end\nend\nfn use() -> void\n    let direct: Choice = Choice(\"text\")\n    let dynamic: pointer<Choice> = new Choice(1)\n    let integer: int = direct.choose(2)\n    let text: string = dynamic->choose(\"value\")\n    let generic: int = direct.take<int>(3)\n    let contextual: int = dynamic->take<int>(null)\n    let wrapped: int = direct.choose(Wrap { value: 4 })\n    return\nend"
    )
    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end
    let program: pointer<SemanticProgram> = semantic_test_analyze("overloads", source, false)
    @mut let failure: int = 0
    if !semantic_program_successful(program) then
        failure = 2
    end
    let body: pointer<SyntaxNode> = semantic_function_body(syntax_child(source.parsed.root, 2))
    let direct: pointer<SyntaxNode> = syntax_child(syntax_child(body, 0), 2)
    let dynamic: pointer<SyntaxNode> = syntax_child(syntax_child(body, 1), 2)
    if failure == 0 && (semantic_model_called_constructor(program, direct)->name != "from_string" || semantic_model_called_constructor(program, dynamic)->name != "from_int") then
        failure = 3
    end
    @mut let index: int = 2
    while index < 7 && failure == 0 do
        let call: pointer<SyntaxNode> = syntax_child(syntax_child(body, index), 2)
        let selected: pointer<SemanticSymbol> = semantic_model_called_function(program, call)
        @mut let expected_index: int = index - 2
        if index == 4 then
            expected_index = 3
        end
        if index == 5 then
            expected_index = 4
        end
        if index == 6 then
            expected_index = 2
        end
        if selected == null || selected->index != expected_index then
            failure = 4
        end
        if index == 4 || index == 5 then
            if semantic_model_call_type_argument_count(program, call) != 1 || semantic_model_call_type_argument(program, call, 0)->name != "int" then
                failure = 5
            end
        end
        index = index + 1
    end
    destroy_semantic_program(program)
    destroy_semantic_test_source(source)
    if failure != 0 then
        return failure
    end

    let invalid: ParsedSemanticSource = parse_semantic_test_source(
        "class Bad\n    @constructor\n    fn first(value: int) -> void\n        this(\"cycle\")\n    end\n    @constructor\n    fn second(value: string) -> void\n        this(1)\n        this(2)\n    end\n    fn same<T>(value: pointer<T>) -> void\n        return\n    end\n    fn same<U>(value: pointer<U>) -> int\n        return 0\n    end\n    fn pick(value: pointer<int>) -> void\n        return\n    end\n    fn pick(value: pointer<string>) -> void\n        return\n    end\n    @private\n    fn hidden(value: int) -> void\n        return\n    end\n    fn hidden(value: string) -> void\n        return\n    end\nend\nclass Duplicate\n    @constructor\n    fn one() -> void\n        return\n    end\n    @constructor\n    fn two() -> void\n        return\n    end\nend\nfn invalid(object: pointer<Bad>) -> void\n    object->pick(null)\n    object->pick(true)\n    object->hidden(1)\n    let bad: pointer<Bad> = new Bad(false)\n    return\nend"
    )
    if !semantic_test_parse_valid(invalid) then
        destroy_semantic_test_source(invalid)
        return 6
    end
    let invalid_program: pointer<SemanticProgram> = semantic_test_analyze("invalid.overloads", invalid, false)
    if !semantic_test_has_code(invalid_program, "SOL-S073") || !semantic_test_has_code(invalid_program, "SOL-S074") || !semantic_test_has_code(invalid_program, "SOL-S065") || !semantic_test_has_code(invalid_program, "SOL-S066") || !semantic_test_has_code(invalid_program, "SOL-S075") || !semantic_test_has_code(invalid_program, "SOL-S076") then
        failure = 7
    end
    if semantic_test_code_count(invalid_program, "SOL-S073") != 2 || semantic_test_code_count(invalid_program, "SOL-S076") != 2 || semantic_test_code_count(invalid_program, "SOL-S075") != 1 || semantic_test_code_count(invalid_program, "SOL-S074") != 2 || semantic_test_code_count(invalid_program, "SOL-S065") != 1 || semantic_test_has_code(invalid_program, "SOL-S043") then
        failure = 8
    end
    let duplicate: pointer<SyntaxNode> = semantic_direct_child(syntax_child(invalid.parsed.root, 0), syntax_kind_function_declaration(), 3)
    if !semantic_test_code_has_span(invalid_program, "SOL-S073", duplicate->span.start.offset, duplicate->span.end_position.offset) then
        failure = 9
    end
    destroy_semantic_program(invalid_program)
    destroy_semantic_test_source(invalid)
    return failure
end

fn test_overload_edge_cases() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "class Access\n    @private\n    @constructor\n    fn hidden(value: int) -> void\n        return\n    end\n    @constructor\n    fn visible(value: string) -> void\n        return\n    end\n    fn mix<T>(first: T, second: int) -> void\n        return\n    end\n    fn mix<U>(first: int, second: U) -> void\n        return\n    end\n    fn pointer(value: pointer<int>) -> void\n        return\n    end\n    fn pointer(value: pointer<string>) -> void\n        return\n    end\nend\nfn invalid(object: pointer<Access>) -> void\n    object->mix<int>(1, 2)\n    object->mix<int, int>(1, 2)\n    object->pointer(null)\n    let hidden: pointer<Access> = new Access(1)\n    let direct: Access = new Access(\"heap\")\n    @mut let fresh: Access = Access(\"direct\")\n    fresh = new Access(\"heap\")\n    return\nend"
    )
    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end
    let program: pointer<SemanticProgram> = semantic_test_analyze("overload.edges", source, false)
    @mut let failure: int = 0
    if !semantic_test_has_code(program, "SOL-S065") || !semantic_test_has_code(program, "SOL-S074") || !semantic_test_has_code(program, "SOL-S070") || !semantic_test_has_code(program, "SOL-S007") || !semantic_test_has_code(program, "SOL-S072") || semantic_test_has_code(program, "SOL-S043") then
        failure = 2
    end
    let class_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 0)
    let first: pointer<SyntaxNode> = semantic_direct_child(class_declaration, syntax_kind_function_declaration(), 2)
    let second: pointer<SyntaxNode> = semantic_direct_child(class_declaration, syntax_kind_function_declaration(), 3)
    if semantic_callable_signatures_equal(program, semantic_model_declared_symbol(program, first), semantic_model_declared_symbol(program, second)) then
        failure = 3
    end
    destroy_semantic_program(program)
    destroy_semantic_test_source(source)
    return failure
end

fn test_constructors_and_initialization() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "class Document\n    name: string\n    @public\n    @constructor\n    fn create(name: string) -> void\n        this.name = name\n        return\n    end\nend\nfn use() -> void\n    let direct: Document = Document(\"Sol\")\n    let dynamic: pointer<Document> = new Document(\"Heap\")\n    return\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "constructors",
        source,
        false
    )
    @mut let failure: int = 0

    if program == null || !semantic_program_successful(program) then
        failure = 2
    end

    let class_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 0)
    let constructor_declaration: pointer<SyntaxNode> = semantic_direct_child(
        class_declaration,
        syntax_kind_function_declaration(),
        0
    )
    let constructor: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        constructor_declaration
    )
    let use_declaration: pointer<SyntaxNode> = syntax_child(source.parsed.root, 1)
    let body: pointer<SyntaxNode> = semantic_function_body(use_declaration)
    let direct: pointer<SyntaxNode> = syntax_child(syntax_child(body, 0), 2)
    let dynamic: pointer<SyntaxNode> = syntax_child(syntax_child(body, 1), 2)

    if failure == 0 && (constructor == null || constructor->kind != semantic_symbol_kind_constructor() || semantic_model_method_receiver(program, constructor_declaration) == null) then
        failure = 3
    end

    if failure == 0 && (semantic_model_called_constructor(program, direct) != constructor || semantic_model_constructed_class(program, direct)->name != "Document" || semantic_model_type_of_expression(program, direct)->name != "Document") then
        failure = 4
    end

    if failure == 0 && (semantic_model_called_constructor(program, dynamic) != constructor || semantic_model_constructed_class(program, dynamic)->name != "Document" || semantic_model_type_of_expression(program, dynamic)->name != "pointer<Document>") then
        failure = 5
    end

    destroy_semantic_program(program)
    destroy_semantic_test_source(source)

    if failure != 0 then
        return failure
    end

    let delegated: ParsedSemanticSource = parse_semantic_test_source(
        "class Delegated\n    value: int\n    @constructor\n    fn build() -> void\n        this()\n        return\n    end\nend"
    )
    let delegated_program: pointer<SemanticProgram> = semantic_test_analyze(
        "delegated",
        delegated,
        false
    )

    if delegated_program == null || !semantic_test_has_code(delegated_program, "SOL-S076") then
        failure = 6
    end
    let delegated_constructor: pointer<SyntaxNode> = semantic_direct_child(syntax_child(delegated.parsed.root, 0), syntax_kind_function_declaration(), 0)
    let delegation: pointer<SyntaxNode> = syntax_child(syntax_child(semantic_function_body(delegated_constructor), 0), 0)
    if !semantic_test_code_has_span(delegated_program, "SOL-S076", delegation->span.start.offset, delegation->span.end_position.offset) then
        failure = 6
    end

    destroy_semantic_program(delegated_program)
    destroy_semantic_test_source(delegated)

    if failure != 0 then
        return failure
    end

    let invalid: ParsedSemanticSource = parse_semantic_test_source(
        "class Missing\n    value: int\nend\nclass Hidden\n    value: int\n    @private\n    @constructor\n    fn hidden() -> void\n        this.value = 1\n        return\n    end\nend\nclass Incomplete\n    left: int\n    right: int\n    @constructor\n    fn bad(flag: boolean) -> void\n        if flag then\n            this.left = 1\n        else\n            this.right = 2\n        end\n        return\n    end\nend\nclass InvalidSignature\n    @mut\n    @constructor\n    fn generic<T>() -> int\n        return 0\n    end\nend\nfn invalid() -> void\n    let missing: pointer<Missing> = new Missing()\n    let hidden: pointer<Hidden> = new Hidden()\n    return\nend"
    )
    let invalid_program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.constructors",
        invalid,
        false
    )

    if invalid_program == null || !semantic_test_has_code(invalid_program, "SOL-S067") || !semantic_test_has_code(invalid_program, "SOL-S068") || !semantic_test_has_code(invalid_program, "SOL-S069") || !semantic_test_has_code(invalid_program, "SOL-S070") || !semantic_test_has_code(invalid_program, "SOL-S071") then
        failure = 7
    end

    destroy_semantic_program(invalid_program)
    destroy_semantic_test_source(invalid)
    return failure
end

fn test_instance_methods_and_receivers() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "@public\nclass Counter\n    @private\n    count: int\n    @public\n    fn get() -> int\n        return this.count\n    end\n    @private\n    fn reset() -> void\n        this.count = 0\n        return\n    end\n    @public\n    fn increment(delta: int) -> int\n        this.count = this.count + delta\n        this.reset()\n        return this.count\n    end\nend\nfn operate(counter: pointer<Counter>) -> int\n    counter->increment(2)\n    return counter->get()\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "methods",
        source,
        false
    )
    @mut let failure: int = 0

    if program == null || !semantic_program_successful(program) then
        failure = 2
    end

    let root: pointer<SyntaxNode> = source.parsed.root
    let class_declaration: pointer<SyntaxNode> = syntax_child(root, 0)
    let get_declaration: pointer<SyntaxNode> = syntax_child(class_declaration, 3)
    let reset_declaration: pointer<SyntaxNode> = syntax_child(class_declaration, 4)
    let increment_declaration: pointer<SyntaxNode> = syntax_child(class_declaration, 5)
    let operate_declaration: pointer<SyntaxNode> = syntax_child(root, 1)
    let get_method: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        get_declaration
    )
    let reset_method: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        reset_declaration
    )
    let receiver: pointer<SemanticSymbol> = semantic_model_method_receiver(
        program,
        increment_declaration
    )
    let increment_body: pointer<SyntaxNode> = semantic_function_body(
        increment_declaration
    )
    let reset_call: pointer<SyntaxNode> = syntax_child(
        syntax_child(increment_body, 1),
        0
    )
    let operate_body: pointer<SyntaxNode> = semantic_function_body(
        operate_declaration
    )
    let pointer_call: pointer<SyntaxNode> = syntax_child(
        syntax_child(operate_body, 0),
        0
    )

    if failure == 0 && (get_method->kind != semantic_symbol_kind_method() || get_method->owner != semantic_model_declared_symbol(program, class_declaration) || !semantic_method_is_virtual(get_method) || semantic_method_is_virtual(reset_method)) then
        failure = 3
    end

    if failure == 0 && (receiver == null || receiver->kind != semantic_symbol_kind_receiver() || scope_lookup(semantic_model_function_scope(program, increment_declaration), "this") != receiver) then
        failure = 4
    end

    if failure == 0 && (semantic_model_accessed_method(program, syntax_child(reset_call, 0)) != reset_method || semantic_model_called_function(program, reset_call) != reset_method) then
        failure = 5
    end

    if failure == 0 && (semantic_model_accessed_method(program, syntax_child(pointer_call, 0))->name != "increment" || semantic_model_called_function(program, pointer_call)->name != "increment" || semantic_model_type_of_expression(program, pointer_call)->name != "int") then
        failure = 6
    end

    destroy_semantic_program(program)
    destroy_semantic_test_source(source)

    if failure != 0 then
        return failure
    end

    let invalid: ParsedSemanticSource = parse_semantic_test_source(
        "class Bad\n    value: int\n    @mut\n    fn invalid() -> void\n        return\n    end\n    @private\n    @public\n    fn visibility() -> void\n        return\n    end\n    fn base() -> void\n        return\n    end\n    fn parameter(this: int) -> void\n        return\n    end\n    fn local() -> void\n        let base: int = 0\n        return\n    end\n    @private\n    fn hidden() -> void\n        return\n    end\n    fn repeat(value: int) -> void\n        return\n    end\n    fn repeat(value: string) -> void\n        return\n    end\nend\nfn outside(value: pointer<Bad>) -> void\n    value->hidden()\n    value->missing()\n    value->repeat(1)\n    return\nend\nfn receiver() -> int\n    return this.value\nend"
    )

    if !semantic_test_parse_valid(invalid) then
        destroy_semantic_test_source(invalid)
        return 7
    end

    let invalid_program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.methods",
        invalid,
        false
    )

    if invalid_program == null || !semantic_test_has_code(invalid_program, "SOL-S059") || !semantic_test_has_code(invalid_program, "SOL-S060") || !semantic_test_has_code(invalid_program, "SOL-S061") || !semantic_test_has_code(invalid_program, "SOL-S062") || !semantic_test_has_code(invalid_program, "SOL-S063") || !semantic_test_has_code(invalid_program, "SOL-S064") || !semantic_test_has_code(invalid_program, "SOL-S066") then
        failure = 8
    end

    destroy_semantic_program(invalid_program)
    destroy_semantic_test_source(invalid)
    return failure
end

fn test_class_fields_and_member_access() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "@public\nclass Document\n    @public\n    title: string\n    @public\n    count: int\n    @private\n    secret: int\nend\nfn read(document: pointer<Document>) -> string\n    return document->title\nend\nfn mutate(document: pointer<Document>) -> void\n    document->count = 1\n    return\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "fields",
        source,
        false
    )
    @mut let failure: int = 0

    if program == null || !semantic_program_successful(program) then
        failure = 2
    end

    let root: pointer<SyntaxNode> = source.parsed.root
    let class_declaration: pointer<SyntaxNode> = syntax_child(root, 0)
    let read_declaration: pointer<SyntaxNode> = syntax_child(root, 1)
    let mutate_declaration: pointer<SyntaxNode> = syntax_child(root, 2)
    let class_scope: pointer<Scope> = semantic_model_class_scope(
        program,
        class_declaration
    )
    let title_declaration: pointer<SyntaxNode> = syntax_child(class_declaration, 2)
    let title: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        title_declaration
    )
    let read_access: pointer<SyntaxNode> = syntax_child(
        syntax_child(semantic_function_body(read_declaration), 0),
        0
    )
    let mutate_assignment: pointer<SyntaxNode> = syntax_child(
        semantic_function_body(mutate_declaration),
        0
    )
    let mutate_access: pointer<SyntaxNode> = syntax_child(mutate_assignment, 0)

    if failure == 0 && (scope_declared_symbol_count(class_scope) != 3 || scope_lookup_class_field(class_scope, "title") != title || !class_scope->frozen) then
        failure = 3
    end

    if failure == 0 && (title->kind != semantic_symbol_kind_class_field() || title->owner != semantic_model_declared_symbol(program, class_declaration) || title->index != 0 || !title->mutable || semantic_symbol_visibility(title) != semantic_visibility_public()) then
        failure = 4
    end

    if failure == 0 && (semantic_model_accessed_pointer_field(program, read_access) != title || semantic_model_type_of_expression(program, read_access)->name != "string") then
        failure = 5
    end

    if failure == 0 && (semantic_model_accessed_pointer_field(program, mutate_access)->name != "count" || semantic_model_type_of_expression(program, mutate_access)->name != "int") then
        failure = 6
    end

    destroy_semantic_program(program)
    destroy_semantic_test_source(source)

    if failure != 0 then
        return failure
    end

    let invalid: ParsedSemanticSource = parse_semantic_test_source(
        "@public\nclass Invalid\n    @mut\n    bad: void\n    @public\n    duplicate: int\n    @private\n    duplicate: int\n    @public\n    @private\n    visibility: int\n    @private\n    hidden: int\nend\n@interface\nclass Contract\n    value: int\nend\nfn inspect(value: pointer<Invalid>) -> int\n    let missing: int = value->missing\n    return value->hidden\nend"
    )

    if !semantic_test_parse_valid(invalid) then
        destroy_semantic_test_source(invalid)
        return 7
    end

    let invalid_program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.fields",
        invalid,
        false
    )

    if invalid_program == null || !semantic_test_has_code(invalid_program, "SOL-S052") || !semantic_test_has_code(invalid_program, "SOL-S053") || !semantic_test_has_code(invalid_program, "SOL-S054") || !semantic_test_has_code(invalid_program, "SOL-S055") || !semantic_test_has_code(invalid_program, "SOL-S056") || !semantic_test_has_code(invalid_program, "SOL-S057") || !semantic_test_has_code(invalid_program, "SOL-S058") then
        failure = 8
    end

    destroy_semantic_program(invalid_program)
    destroy_semantic_test_source(invalid)
    return failure
end

fn test_class_types_scopes_and_diagnostics() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "@protected\n@abstract\nclass Base\nend\n@public\n@interface\nclass Printable\nend\n@public\nclass Document\nend\nfn inspect(value: pointer<Document>) -> pointer<Printable>\n    return null\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "objects",
        source,
        false
    )
    @mut let failure: int = 0

    if program == null || !semantic_program_successful(program) then
        failure = 2
    end

    let root: pointer<SyntaxNode> = source.parsed.root
    let base_declaration: pointer<SyntaxNode> = syntax_child(root, 0)
    let interface_declaration: pointer<SyntaxNode> = syntax_child(root, 1)
    let document_declaration: pointer<SyntaxNode> = syntax_child(root, 2)
    let function_declaration: pointer<SyntaxNode> = syntax_child(root, 3)
    let base: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        base_declaration
    )
    let interface_symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        interface_declaration
    )
    let document: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        document_declaration
    )
    let class_scope: pointer<Scope> = semantic_model_class_scope(
        program,
        document_declaration
    )
    let parameter: pointer<SyntaxNode> = semantic_direct_child(
        function_declaration,
        syntax_kind_parameter(),
        0
    )
    let parameter_pointer: pointer<SemanticType> = semantic_model_type_of_reference(
        program,
        syntax_child(parameter, 1)
    )

    if failure == 0 && (base->kind != semantic_symbol_kind_class() || !semantic_symbol_is_abstract(base) || base->type->kind != semantic_type_kind_class() || semantic_symbol_visibility(base) != semantic_visibility_protected()) then
        failure = 3
    end

    if failure == 0 && (interface_symbol->kind != semantic_symbol_kind_interface() || interface_symbol->type->kind != semantic_type_kind_interface() || document->kind != semantic_symbol_kind_class()) then
        failure = 4
    end

    if failure == 0 && (class_scope == null || class_scope->kind != scope_kind_class() || class_scope->parent != semantic_program_module(program, "objects")->scope || !class_scope->frozen) then
        failure = 5
    end

    if failure == 0 && (parameter_pointer->kind != semantic_type_kind_pointer() || parameter_pointer->element_type != document->type) then
        failure = 6
    end

    destroy_semantic_program(program)
    destroy_semantic_test_source(source)

    if failure != 0 then
        return failure
    end

    let exported: ParsedSemanticSource = parse_semantic_test_source(
        "@public\nclass Exported\nend"
    )
    let importing: ParsedSemanticSource = parse_semantic_test_source(
        "inject objects.base only Exported\nfn consume(value: pointer<Exported>) -> void\n    return\nend"
    )

    if !semantic_test_parse_valid(exported) || !semantic_test_parse_valid(importing) then
        destroy_semantic_test_source(importing)
        destroy_semantic_test_source(exported)
        return 7
    end

    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("objects.app", importing.parsed.root))
    vector_push<SourceModule>(modules, source_module("objects.base", exported.parsed.root))
    let imported_program: pointer<SemanticProgram> = analyze_library_modules(modules)
    destroy_vector<SourceModule>(modules)
    let injection: pointer<SyntaxNode> = syntax_child(importing.parsed.root, 0)

    if imported_program == null || !semantic_program_successful(imported_program) || semantic_model_direct_injected_symbol(imported_program, injection, 0)->kind != semantic_symbol_kind_class() then
        failure = 8
    end

    destroy_semantic_program(imported_program)
    destroy_semantic_test_source(importing)
    destroy_semantic_test_source(exported)

    if failure != 0 then
        return failure
    end

    let invalid: ParsedSemanticSource = parse_semantic_test_source(
        "@mystery\nclass UnknownAnnotation\nend\n@public\n@public\nclass Repeated\nend\n@public\n@private\nclass Visibility\nend\n@abstract\n@interface\nclass Conflict\nend\nclass int\nend"
    )

    if !semantic_test_parse_valid(invalid) then
        destroy_semantic_test_source(invalid)
        return 9
    end

    let invalid_program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.objects",
        invalid,
        false
    )

    if invalid_program == null || !semantic_test_has_code(invalid_program, "SOL-S038") || !semantic_test_has_code(invalid_program, "SOL-S048") || !semantic_test_has_code(invalid_program, "SOL-S049") || !semantic_test_has_code(invalid_program, "SOL-S050") || !semantic_test_has_code(invalid_program, "SOL-S051") then
        failure = 10
    end

    destroy_semantic_program(invalid_program)
    destroy_semantic_test_source(invalid)
    return failure
end

fn test_binding_index() -> int
    let program: pointer<SemanticProgram> = create_semantic_program(false)
    let nodes: pointer<Vector<pointer<SyntaxNode>>> = create_vector<pointer<SyntaxNode>>()
    @mut let failure: int = 0
    @mut let index: int = 0

    // More nodes than buckets exercises collisions and bucket-vector growth.
    while index < 5000 do
        @mut let offset: int = index
        if index >= 4900 then
            offset = (index - 4900) * semantic_binding_bucket_count()
        end
        if index == 4999 then
            offset = -1
        end
        let position: SourcePosition = source_position(offset, 1, index + 1)
        let node: pointer<SyntaxNode> = create_syntax_name("value", source_span(position, position))
        vector_push<pointer<SyntaxNode>>(nodes, node)
        let binding: pointer<SemanticBinding> = semantic_program_add_binding(program, 13, node)
        binding->type = type_catalog_lookup(program->catalog, "int")
        index = index + 1
    end

    index = 4999
    while index >= 0 do
        let node: pointer<SyntaxNode> = vector_get<pointer<SyntaxNode>>(nodes, index)
        let binding: pointer<SemanticBinding> = semantic_program_binding(program, 13, node)
        if binding == null then
            failure = 1
        else
            if binding->node != node || binding->type != type_catalog_lookup(program->catalog, "int") || semantic_program_add_binding(program, 13, node) != binding then
                failure = 2
            end
        end
        index = index - 1
    end

    if vector_length<pointer<SemanticBinding>>(program->bindings) != 5000 then
        failure = 3
    end

    // Separate syntax trees can have identical spans, text, and node kinds.
    let position: SourcePosition = source_position(0, 1, 1)
    let same_span: pointer<SyntaxNode> = create_syntax_name("value", source_span(position, position))
    let absent: pointer<SyntaxNode> = create_syntax_name("value", source_span(position, position))
    let first: pointer<SyntaxNode> = vector_get<pointer<SyntaxNode>>(nodes, 0)
    let original: pointer<SemanticBinding> = semantic_program_binding(program, 13, first)
    let collision: pointer<SemanticBinding> = semantic_program_add_binding(program, 13, same_span)

    if collision == original || semantic_program_binding(program, 13, first) != original || semantic_program_binding(program, 13, same_span) != collision || semantic_program_binding(program, 13, absent) != null then
        failure = 4
    end

    // Every supported binding kind remains independent on the same node.
    index = 1
    while index < 24 do
        let binding: pointer<SemanticBinding> = semantic_program_add_binding(program, index, first)
        if binding == null then
            failure = 5
        else
            if binding->kind != index || binding->node != first || semantic_program_binding(program, index, first) != binding then
                failure = 6
            end
        end
        index = index + 1
    end

    let count: int = vector_length<pointer<SemanticBinding>>(program->bindings)
    if semantic_program_add_binding(program, 0, first) != null || semantic_program_add_binding(program, 24, first) != null || semantic_program_add_binding(program, -1, first) != null || semantic_program_add_binding(program, 1, null) != null || semantic_program_add_binding(null, 1, first) != null then
        failure = 7
    end
    if semantic_program_binding(program, 0, first) != null || semantic_program_binding(program, 24, first) != null || semantic_program_binding(program, -1, first) != null || semantic_program_binding(program, 1, null) != null || semantic_program_binding(null, 1, first) != null || vector_length<pointer<SemanticBinding>>(program->bindings) != count then
        failure = 8
    end

    // Updating a binding does not insert duplicates or reorder the model.
    semantic_program_record_type(program, 13, first, type_catalog_lookup(program->catalog, "string"))
    if semantic_program_binding(program, 13, first) != original || original->type != type_catalog_lookup(program->catalog, "string") || vector_get<pointer<SemanticBinding>>(program->bindings, 0) != original || vector_length<pointer<SemanticBinding>>(program->bindings) != count then
        failure = 9
    end

    let other: pointer<SemanticProgram> = create_semantic_program(false)
    if semantic_program_binding(other, 13, first) != null || semantic_program_add_binding(other, 13, first) == original then
        failure = 10
    end
    destroy_semantic_program(other)
    destroy_semantic_program(program)

    index = 0
    while index < vector_length<pointer<SyntaxNode>>(nodes) do
        destroy_syntax_tree(vector_get<pointer<SyntaxNode>>(nodes, index))
        index = index + 1
    end
    destroy_vector<pointer<SyntaxNode>>(nodes)
    destroy_syntax_tree(same_span)
    destroy_syntax_tree(absent)
    return failure
end

fn parse_semantic_test_source(source: string) -> ParsedSemanticSource
    let lexical: LexResult = scan_source(source)
    @mut let parsed: ParseResult = parse_tokens(null)

    if lexical.successful then
        parsed = parse_tokens(lexical.tokens)
    end

    return ParsedSemanticSource {
        lexical: lexical,
        parsed: parsed
    }
end

fn semantic_test_parse_valid(source: ParsedSemanticSource) -> boolean
    return source.lexical.successful && source.parsed.successful && source.parsed.root != null
end

fn destroy_semantic_test_source(source: ParsedSemanticSource) -> void
    destroy_parse_result(source.parsed)
    destroy_lex_result(source.lexical)
    return
end

fn semantic_test_analyze(
    name: string,
    source: ParsedSemanticSource,
    executable: boolean
) -> pointer<SemanticProgram>
    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module(name, source.parsed.root))
    @mut let program: pointer<SemanticProgram> = analyze_library_modules(modules)

    if executable then
        destroy_semantic_program(program)
        program = analyze_executable_program(modules)
    end

    destroy_vector<SourceModule>(modules)
    return program
end

fn semantic_test_has_code(program: pointer<SemanticProgram>, code: string) -> boolean
    @mut let index: int = 0
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        if semantic_program_diagnostic(program, index).diagnostic.code == code then
            return true
        end

        index = index + 1
    end

    return false
end

fn semantic_test_code_count(program: pointer<SemanticProgram>, code: string) -> int
    @mut let found: int = 0
    @mut let index: int = 0
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        if semantic_program_diagnostic(program, index).diagnostic.code == code then
            found = found + 1
        end

        index = index + 1
    end

    return found
end

fn test_successful_program() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "struct Box<T>\n    value: T\nend\nstruct Node\n    value: int\n    next: pointer<Node>\nend\nfn id<T>(value: T) -> T\n    return value\nend\n@init\nfn launch() -> int\n    @mut let box: Box<int> = Box<int> { value: 1 }\n    box.value = id<int>(2)\n    let node: Node = Node { value: box.value, next: null }\n    let pointer: pointer<Node> = null\n    if pointer == null then\n        pointer->value = node.value\n    end\n    let letter: char = \"Sol\"[0]\n    let integers: pointer<int> = null\n    let first: int = integers[0]\n    return box.value\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "application",
        source,
        true
    )
    @mut let failure: int = 0

    if program == null || !semantic_program_successful(program) then
        failure = 2
    end

    let root: pointer<SyntaxNode> = source.parsed.root
    let box_declaration: pointer<SyntaxNode> = syntax_child(root, 0)
    let launch_declaration: pointer<SyntaxNode> = syntax_child(root, 3)
    let launch_symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(
        program,
        launch_declaration
    )
    let body: pointer<SyntaxNode> = semantic_function_body(launch_declaration)
    let function_scope: pointer<Scope> = semantic_model_function_scope(
        program,
        launch_declaration
    )
    let box_local: pointer<SyntaxNode> = syntax_child(body, 0)
    let construction: pointer<SyntaxNode> = syntax_child(box_local, 2)
    let assignment: pointer<SyntaxNode> = syntax_child(body, 1)
    let call: pointer<SyntaxNode> = syntax_child(assignment, 1)

    if failure == 0 && (program->entry_function != launch_symbol || program->entry_module->name != "application" || function_scope == null || !function_scope->frozen) then
        failure = 3
    end

    if failure == 0 && (semantic_model_constructed_struct(program, construction)->declaration != box_declaration || semantic_model_type_of_expression(program, construction)->name != "Box<int>") then
        failure = 4
    end

    if failure == 0 && (semantic_model_called_function(program, call)->name != "id" || semantic_model_type_of_expression(program, call)->name != "int" || semantic_model_call_type_argument_count(program, call) != 1 || semantic_model_call_type_argument(program, call, 0)->name != "int") then
        failure = 5
    end

    if failure == 0 && (scope_lookup(function_scope, "box") == null || semantic_program_owned_type_count(program) == 0) then
        failure = 6
    end

    destroy_semantic_program(program)
    destroy_semantic_test_source(source)
    return failure
end

fn test_module_resolution() -> int
    let base: ParsedSemanticSource = parse_semantic_test_source(
        "struct Value\n    number: int\nend\nfn id<T>(value: T) -> T\n    return value\nend\nfn ping() -> int\n    return 7\nend"
    )
    let middle: ParsedSemanticSource = parse_semantic_test_source(
        "inject namespace base as b\nfn relay() -> int\n    return b::ping()\nend"
    )
    let application: ParsedSemanticSource = parse_semantic_test_source(
        "inject base only Value\ninject namespace middle as m\n@init\nfn start() -> int\n    let value: Value = Value { number: 1 }\n    return m::relay()\nend"
    )

    if !semantic_test_parse_valid(base) || !semantic_test_parse_valid(middle) || !semantic_test_parse_valid(application) then
        destroy_semantic_test_source(application)
        destroy_semantic_test_source(middle)
        destroy_semantic_test_source(base)
        return 1
    end

    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("application", application.parsed.root))
    vector_push<SourceModule>(modules, source_module("base", base.parsed.root))
    vector_push<SourceModule>(modules, source_module("middle", middle.parsed.root))
    let program: pointer<SemanticProgram> = analyze_executable_program(modules)
    destroy_vector<SourceModule>(modules)
    @mut let failure: int = 0

    if program == null || !semantic_program_successful(program) then
        failure = 2
    end

    let app_module: pointer<SemanticModule> = semantic_program_module(program, "application")
    let direct: pointer<SyntaxNode> = syntax_child(application.parsed.root, 0)
    let namespace_injection: pointer<SyntaxNode> = syntax_child(application.parsed.root, 1)
    let direct_binding: pointer<SemanticBinding> = semantic_program_binding(
        program,
        semantic_binding_kind_direct_injection(),
        direct
    )

    if failure == 0 && (semantic_program_module_count(program) != 3 || program->entry_function->name != "start" || semantic_program_module_of(program, semantic_binding_kind_injected_module(), direct)->name != "base") then
        failure = 3
    end

    if failure == 0 && (semantic_binding_symbol_count(direct_binding) != 2 || semantic_model_direct_injected_symbol_count(program, direct) != 1 || semantic_model_direct_injected_symbol(program, direct, 0)->name != "Value" || semantic_program_module_of(program, semantic_binding_kind_injected_namespace(), namespace_injection)->name != "middle") then
        failure = 4
    end

    if failure == 0 && (scope_lookup_local(app_module->scope, "Value") == null || scope_lookup_local(app_module->scope, "m") == null || scope_lookup_local(app_module->scope, "ping") != null) then
        failure = 5
    end

    destroy_semantic_program(program)
    destroy_semantic_test_source(application)
    destroy_semantic_test_source(middle)
    destroy_semantic_test_source(base)
    return failure
end

fn test_core_diagnostics() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "fn duplicate() -> int\n    return 0\nend\nfn duplicate() -> int\n    return 1\nend\nfn target(value: int) -> int\n    return value\nend\nfn invalid_parameter(value: void) -> void\n    return\nend\nfn invalid(parameter: int) -> int\n    let self: int = self\n    let unknown: Missing = 1\n    let invalid_value: void = 1\n    let mismatch: int = true\n    target = 1\n    let immutable: int = 0\n    immutable = 1\n    @mut let mutable: int = 0\n    mutable = true\n    let unary: int = !1\n    let binary: int = 1 + true\n    if 1 then\n        return\n    end\n    parameter()\n    target()\n    target(true)\n    return true\nend\nfn voider() -> void\n    return 1\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.core",
        source,
        false
    )
    let expected: pointer<Vector<string>> = create_vector<string>()
    vector_push<string>(expected, "SOL-S001")
    vector_push<string>(expected, "SOL-S002")
    vector_push<string>(expected, "SOL-S003")
    vector_push<string>(expected, "SOL-S004")
    vector_push<string>(expected, "SOL-S005")
    vector_push<string>(expected, "SOL-S006")
    vector_push<string>(expected, "SOL-S007")
    vector_push<string>(expected, "SOL-S008")
    vector_push<string>(expected, "SOL-S009")
    vector_push<string>(expected, "SOL-S010")
    vector_push<string>(expected, "SOL-S011")
    vector_push<string>(expected, "SOL-S012")
    vector_push<string>(expected, "SOL-S013")
    vector_push<string>(expected, "SOL-S014")
    vector_push<string>(expected, "SOL-S015")
    vector_push<string>(expected, "SOL-S016")
    vector_push<string>(expected, "SOL-S017")
    vector_push<string>(expected, "SOL-S018")
    @mut let failure: int = 0

    if program == null || semantic_program_successful(program) then
        failure = 2
    end

    if failure == 0 && !semantic_test_has_all_codes(program, expected) then
        failure = 3
    end

    if failure == 0 && !semantic_test_diagnostics_ordered(program) then
        failure = 4
    end

    destroy_vector<string>(expected)
    destroy_semantic_program(program)
    destroy_semantic_test_source(source)
    return failure
end

fn test_struct_pointer_and_generic_diagnostics() -> int
    let source: ParsedSemanticSource = parse_semantic_test_source(
        "struct pointer\n    value: int\nend\nstruct Duplicate<T, T>\n    same: int\n    same: void\nend\nstruct Cycle\n    next: Cycle\nend\nstruct Pair\n    first: int\n    second: string\nend\nfn make() -> Pair\n    return Pair { first: 1, second: \"\" }\nend\nfn bad(number: int, integers: pointer<int>, flags: pointer<boolean>) -> void\n    let primitive: int = int { value: 1 }\n    let unknown: Pair = Pair { first: 1, ghost: 2 }\n    let repeated: Pair = Pair { first: 1, first: 2, second: \"\" }\n    let wrong: Pair = Pair { first: true, second: \"\" }\n    let field: int = number.value\n    let absent: int = wrong.missing\n    make().first = 2\n    number->value = 1\n    let indexed: int = number[0]\n    let bad_index: char = \"x\"[true]\n    @mut let text: string = \"x\"\n    text[0] = 'y'\n    let missing: pointer = null\n    let impossible: pointer<void> = null\n    let untyped: int = null\n    if integers == flags then\n        return\n    end\n    return\nend"
    )

    if !semantic_test_parse_valid(source) then
        destroy_semantic_test_source(source)
        return 1
    end

    let program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.structs",
        source,
        false
    )
    let expected: pointer<Vector<string>> = create_vector<string>()
    vector_push<string>(expected, "SOL-S027")
    vector_push<string>(expected, "SOL-S028")
    vector_push<string>(expected, "SOL-S029")
    vector_push<string>(expected, "SOL-S030")
    vector_push<string>(expected, "SOL-S031")
    vector_push<string>(expected, "SOL-S032")
    vector_push<string>(expected, "SOL-S033")
    vector_push<string>(expected, "SOL-S034")
    vector_push<string>(expected, "SOL-S035")
    vector_push<string>(expected, "SOL-S036")
    vector_push<string>(expected, "SOL-S037")
    vector_push<string>(expected, "SOL-S038")
    vector_push<string>(expected, "SOL-S039")
    vector_push<string>(expected, "SOL-S040")
    vector_push<string>(expected, "SOL-S041")
    vector_push<string>(expected, "SOL-S043")
    vector_push<string>(expected, "SOL-S044")
    vector_push<string>(expected, "SOL-S045")
    vector_push<string>(expected, "SOL-S046")
    vector_push<string>(expected, "SOL-S047")
    @mut let failure: int = 0

    if program == null || !semantic_test_has_all_codes(program, expected) then
        failure = 2
    end

    destroy_vector<string>(expected)
    destroy_semantic_program(program)
    destroy_semantic_test_source(source)

    if failure != 0 then
        return failure
    end

    let generic_source: ParsedSemanticSource = parse_semantic_test_source(
        "fn recurse<T>(value: int) -> int\n    return recurse<pointer<T>>(value)\nend\nfn root() -> int\n    return recurse<int>(1)\nend"
    )

    if !semantic_test_parse_valid(generic_source) then
        destroy_semantic_test_source(generic_source)
        return 3
    end

    let generic_program: pointer<SemanticProgram> = semantic_test_analyze(
        "invalid.generics",
        generic_source,
        false
    )

    if generic_program == null || !semantic_test_has_code(generic_program, "SOL-S042") then
        failure = 4
    end

    destroy_semantic_program(generic_program)
    destroy_semantic_test_source(generic_source)
    return failure
end

fn test_program_diagnostics() -> int
    let base: ParsedSemanticSource = parse_semantic_test_source(
        "fn ping() -> int\n    return 1\nend"
    )
    let application: ParsedSemanticSource = parse_semantic_test_source(
        "inject missing\ninject base only absent\ninject namespace base as b\nfn base() -> int\n    return 0\nend\nfn run() -> int\n    base::ping()\n    b::absent()\n    return 0\nend"
    )

    if !semantic_test_parse_valid(base) || !semantic_test_parse_valid(application) then
        destroy_semantic_test_source(application)
        destroy_semantic_test_source(base)
        return 1
    end

    let modules: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    vector_push<SourceModule>(modules, source_module("application", application.parsed.root))
    vector_push<SourceModule>(modules, source_module("base", base.parsed.root))
    let module_program: pointer<SemanticProgram> = analyze_library_modules(modules)
    destroy_vector<SourceModule>(modules)
    @mut let failure: int = 0

    if module_program == null || !semantic_test_has_code(module_program, "SOL-S019") || !semantic_test_has_code(module_program, "SOL-S020") || !semantic_test_has_code(module_program, "SOL-S021") || !semantic_test_has_code(module_program, "SOL-S022") || !semantic_test_code_has_span(module_program, "SOL-S019", 7, 14) then
        failure = 2
    end

    destroy_semantic_program(module_program)
    destroy_semantic_test_source(application)
    destroy_semantic_test_source(base)

    if failure != 0 then
        return failure
    end

    let missing_entry: ParsedSemanticSource = parse_semantic_test_source(
        "fn library() -> int\n    return 0\nend"
    )

    if !semantic_test_parse_valid(missing_entry) then
        destroy_semantic_test_source(missing_entry)
        return 3
    end

    let missing_program: pointer<SemanticProgram> = semantic_test_analyze(
        "library",
        missing_entry,
        true
    )

    if missing_program == null || !semantic_test_has_code(missing_program, "SOL-S023") || missing_program->entry_function != null then
        failure = 4
    end

    destroy_semantic_program(missing_program)
    destroy_semantic_test_source(missing_entry)

    if failure != 0 then
        return failure
    end

    let invalid_entries: ParsedSemanticSource = parse_semantic_test_source(
        "@init\n@fn native<T>() -> void\n@init\nfn second() -> int\n    return 0\nend"
    )

    if !semantic_test_parse_valid(invalid_entries) then
        destroy_semantic_test_source(invalid_entries)
        return 5
    end

    let entry_program: pointer<SemanticProgram> = semantic_test_analyze(
        "entries",
        invalid_entries,
        true
    )

    if entry_program == null || !semantic_test_has_code(entry_program, "SOL-S024") || !semantic_test_has_code(entry_program, "SOL-S025") || !semantic_test_has_code(entry_program, "SOL-S026") || entry_program->entry_function != null then
        failure = 6
    end

    destroy_semantic_program(entry_program)
    destroy_semantic_test_source(invalid_entries)

    let empty: pointer<Vector<SourceModule>> = create_vector<SourceModule>()
    let empty_program: pointer<SemanticProgram> = analyze_library_modules(empty)
    destroy_vector<SourceModule>(empty)

    if empty_program == null || !semantic_program_successful(empty_program) || semantic_program_module_count(empty_program) != 0 then
        failure = 7
    end

    destroy_semantic_program(empty_program)
    return failure
end

fn semantic_test_has_all_codes(
    program: pointer<SemanticProgram>,
    expected: pointer<Vector<string>>
) -> boolean
    @mut let index: int = 0
    let count: int = vector_length<string>(expected)

    while index < count do
        if !semantic_test_has_code(program, vector_get<string>(expected, index)) then
            return false
        end

        index = index + 1
    end

    return true
end

fn semantic_test_diagnostics_ordered(program: pointer<SemanticProgram>) -> boolean
    @mut let index: int = 1
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        let previous: SemanticDiagnostic = semantic_program_diagnostic(program, index - 1)
        let current: SemanticDiagnostic = semantic_program_diagnostic(program, index)

        if previous.module_name == current.module_name then
            if previous.diagnostic.span.start.offset > current.diagnostic.span.start.offset then
                return false
            end
        end

        index = index + 1
    end

    return true
end

fn semantic_test_code_has_span(
    program: pointer<SemanticProgram>,
    code: string,
    start_offset: int,
    end_offset: int
) -> boolean
    @mut let index: int = 0
    let count: int = semantic_program_diagnostic_count(program)

    while index < count do
        let item: SemanticDiagnostic = semantic_program_diagnostic(program, index)

        if item.diagnostic.code == code then
            return item.diagnostic.span.start.offset == start_offset && item.diagnostic.span.end_position.offset == end_offset
        end

        index = index + 1
    end

    return false
end
