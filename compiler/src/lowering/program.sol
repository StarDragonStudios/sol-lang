inject namespace std.memory as memory
inject std.collections.vector
inject semantics.model
inject semantics.symbol only SemanticSymbol
inject ir.model
inject ir.validation
inject lowering.model
inject lowering.plan
inject lowering.function

struct LoweringModuleEntry
    semantic: pointer<SemanticModule>
    module: pointer<IrModule>
end

fn lower_semantic_program(semantic: pointer<SemanticProgram>) -> IrLoweringResult
    if semantic == null then
        return lowering_result_failure("semantic program must not be null")
    end
    if !semantic->complete then
        return lowering_result_failure("semantic program must be complete before IR lowering")
    end
    if !semantic_program_successful(semantic) then
        return lowering_result_failure("semantic program contains diagnostics and cannot be lowered")
    end

    let context: pointer<LoweringContext> = create_lowering_context(semantic)
    if context == null then
        return lowering_result_failure("IR lowering allocation failed")
    end
    if !lowering_collect_owners(context) then
        return finish_lowering_failure(context, null, context->error)
    end
    let plan: pointer<LoweringPlan> = create_lowering_plan(context)
    if plan == null then
        return finish_lowering_failure(context, null, context->error)
    end
    @mut let assigned: boolean = lowering_assign_structs(context, plan)
    if assigned then
        assigned = lowering_assign_functions(context, plan)
    end
    if !assigned then
        let message: string = context->error
        destroy_lowering_plan(plan)
        return finish_lowering_failure(context, null, message)
    end

    @mut let function_index: int = 0
    while function_index < vector_length<pointer<LoweringFunctionEntry>>(context->functions) do
        if !lower_function(context, vector_get<pointer<LoweringFunctionEntry>>(context->functions, function_index)) then
            let message: string = context->error
            destroy_lowering_plan(plan)
            return finish_lowering_failure(context, null, message)
        end
        function_index = function_index + 1
    end

    let program: pointer<IrProgram> = create_ir_program(context->arena)
    if program == null then
        let message: string = context->arena->error
        destroy_lowering_plan(plan)
        return finish_lowering_failure(context, null, message)
    end
    let modules: pointer<Vector<LoweringModuleEntry>> = create_vector<LoweringModuleEntry>()
    @mut let module_index: int = 0
    while module_index < semantic_program_module_count(semantic) do
        let semantic_module: pointer<SemanticModule> = semantic_program_module_at(semantic, module_index)
        let module: pointer<IrModule> = create_ir_module(context->arena, semantic_module->name)
        if module == null then
            let message: string = context->arena->error
            destroy_vector<LoweringModuleEntry>(modules)
            destroy_lowering_plan(plan)
            return finish_lowering_failure(context, program, message)
        end
        vector_push<LoweringModuleEntry>(modules, LoweringModuleEntry { semantic: semantic_module, module: module })
        module_index = module_index + 1
    end

    if !populate_lowered_modules(context, modules) then
        let message: string = context->error
        destroy_vector<LoweringModuleEntry>(modules)
        destroy_lowering_plan(plan)
        return finish_lowering_failure(context, program, message)
    end

    module_index = 0
    while module_index < vector_length<LoweringModuleEntry>(modules) do
        let module_entry: LoweringModuleEntry = vector_get<LoweringModuleEntry>(modules, module_index)
        @mut let added: boolean = seal_ir_module(context->arena, module_entry.module)
        if added then
            added = ir_program_add_module(program, module_entry.module)
        end
        if !added then
            let message: string = context->arena->error
            destroy_vector<LoweringModuleEntry>(modules)
            destroy_lowering_plan(plan)
            return finish_lowering_failure(context, program, message)
        end
        module_index = module_index + 1
    end

    if semantic->entry_function != null then
        let entry: pointer<LoweringFunctionEntry> = lowering_plain_function_entry(context, semantic->entry_function)
        let entry_module: pointer<IrModule> = lowering_ir_module(modules, semantic->entry_module)
        @mut let entry_valid: boolean = entry != null && entry_module != null
        if entry_valid then
            entry_valid = ir_program_set_entry(program, entry_module, entry->function)
        end
        if !entry_valid then
            @mut let message: string = context->arena->error
            if message == "" then
                message = "semantic entry point has no canonical lowered definition"
            end
            destroy_vector<LoweringModuleEntry>(modules)
            destroy_lowering_plan(plan)
            return finish_lowering_failure(context, program, message)
        end
    end
    if !seal_ir_program(program) then
        let message: string = context->arena->error
        destroy_vector<LoweringModuleEntry>(modules)
        destroy_lowering_plan(plan)
        return finish_lowering_failure(context, program, message)
    end

    destroy_vector<LoweringModuleEntry>(modules)
    destroy_lowering_plan(plan)
    destroy_lowering_context(context, true)
    return lowering_result_success(program)
end

fn populate_lowered_modules(context: pointer<LoweringContext>, modules: pointer<Vector<LoweringModuleEntry>>) -> boolean
    @mut let struct_index: int = 0
    while struct_index < vector_length<pointer<LoweringStructEntry>>(context->structs) do
        let entry: pointer<LoweringStructEntry> = vector_get<pointer<LoweringStructEntry>>(context->structs, struct_index)
        let symbol: pointer<SemanticSymbol> = semantic_model_declared_symbol(context->semantic, entry->type->identity)
        let owner: pointer<SemanticModule> = lowering_struct_owner(context, symbol)
        let module: pointer<IrModule> = lowering_ir_module(modules, owner)
        if symbol == null || owner == null || module == null then
            return lowering_fail(context, "lowered struct has no canonical module owner")
        end
        if !ir_module_add_struct(context->arena, module, entry->ir_type) then
            return lowering_fail(context, context->arena->error)
        end
        struct_index = struct_index + 1
    end

    @mut let function_index: int = 0
    while function_index < vector_length<pointer<LoweringFunctionEntry>>(context->functions) do
        let entry: pointer<LoweringFunctionEntry> = vector_get<pointer<LoweringFunctionEntry>>(context->functions, function_index)
        let owner: pointer<SemanticModule> = lowering_function_owner(context, entry->instantiation->function)
        let module: pointer<IrModule> = lowering_ir_module(modules, owner)
        if owner == null || module == null then
            return lowering_fail(context, "lowered function has no canonical module owner")
        end
        if !ir_module_add_function(context->arena, module, entry->function) then
            return lowering_fail(context, context->arena->error)
        end
        function_index = function_index + 1
    end
    return true
end

fn lowering_ir_module(modules: pointer<Vector<LoweringModuleEntry>>, semantic: pointer<SemanticModule>) -> pointer<IrModule>
    @mut let index: int = 0
    while index < vector_length<LoweringModuleEntry>(modules) do
        let entry: LoweringModuleEntry = vector_get<LoweringModuleEntry>(modules, index)
        if entry.semantic == semantic then
            return entry.module
        end
        index = index + 1
    end
    return null
end

fn lowering_plain_function_entry(context: pointer<LoweringContext>, symbol: pointer<SemanticSymbol>) -> pointer<LoweringFunctionEntry>
    @mut let index: int = 0
    while index < vector_length<pointer<LoweringFunctionEntry>>(context->functions) do
        let entry: pointer<LoweringFunctionEntry> = vector_get<pointer<LoweringFunctionEntry>>(context->functions, index)
        if entry->instantiation->function == symbol && vector_length<pointer<LoweringType>>(entry->instantiation->arguments) == 0 then
            return entry
        end
        index = index + 1
    end
    return null
end

fn finish_lowering_failure(context: pointer<LoweringContext>, program: pointer<IrProgram>, message: string) -> IrLoweringResult
    @mut let stable_message: string = message
    if stable_message == "" then
        stable_message = "IR lowering failed"
    end
    if program == null then
        destroy_lowering_context(context, false)
    else
        destroy_ir_program(program)
        destroy_lowering_context(context, true)
    end
    return lowering_result_failure(stable_message)
end
