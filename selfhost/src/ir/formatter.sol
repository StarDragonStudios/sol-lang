inject std.collections.vector
inject namespace std.string as strings
inject ir.model

fn format_ir_program(program: pointer<IrProgram>) -> string
    if program == null then
        return "<invalid IR program>\n"
    end
    if !program->sealed then
        return "<invalid IR program>\n"
    end

    @mut let text: string = "program {\n"
    if program->entry_module == null then
        text = text + "  entry none\n"
    else
        text = text + "  entry @" + program->entry_module->name + "::function" + format_ir_int(program->entry_function->id) + "\n"
    end

    if vector_length<pointer<IrModule>>(program->modules) > 0 then
        text = text + "\n"
    end

    @mut let index: int = 0
    while index < vector_length<pointer<IrModule>>(program->modules) do
        text = text + format_ir_module(vector_get<pointer<IrModule>>(program->modules, index))
        if index + 1 < vector_length<pointer<IrModule>>(program->modules) then
            text = text + "\n"
        end
        index = index + 1
    end
    return text + "}\n"
end

fn format_ir_module(module: pointer<IrModule>) -> string
    @mut let text: string = "  module @" + module->name + " {\n"
    @mut let index: int = 0
    while index < vector_length<pointer<IrType>>(module->structs) do
        text = text + format_ir_struct(vector_get<pointer<IrType>>(module->structs, index))
        if index + 1 < vector_length<pointer<IrType>>(module->structs) || vector_length<pointer<IrFunction>>(module->functions) > 0 then
            text = text + "\n"
        end
        index = index + 1
    end
    index = 0
    while index < vector_length<pointer<IrFunction>>(module->functions) do
        text = text + format_ir_function(vector_get<pointer<IrFunction>>(module->functions, index))
        if index + 1 < vector_length<pointer<IrFunction>>(module->functions) then
            text = text + "\n"
        end
        index = index + 1
    end
    return text + "  }\n"
end

fn format_ir_struct(type: pointer<IrType>) -> string
    @mut let text: string = "    struct " + type->name + " {\n"
    @mut let index: int = 0
    while index < vector_length<pointer<IrStructField>>(type->fields) do
        let field: pointer<IrStructField> = vector_get<pointer<IrStructField>>(type->fields, index)
        text = text + "      " + field->name + ": " + field->type->name + "\n"
        index = index + 1
    end
    return text + "    }\n"
end

fn format_ir_function(function: pointer<IrFunction>) -> string
    @mut let text: string = "    "
    if function->has_body then
        text = text + "define "
    else
        text = text + "declare "
    end
    text = text + format_ir_signature(function)
    if !function->has_body then
        return text + "\n"
    end

    text = text + " {\n"
    let emitted: pointer<Vector<pointer<IrValue>>> = create_vector<pointer<IrValue>>()
    @mut let index: int = 0
    while index < vector_length<pointer<IrBasicBlock>>(function->blocks) do
        text = text + format_ir_block(vector_get<pointer<IrBasicBlock>>(function->blocks, index), emitted)
        if index + 1 < vector_length<pointer<IrBasicBlock>>(function->blocks) then
            text = text + "\n"
        end
        index = index + 1
    end
    destroy_vector<pointer<IrValue>>(emitted)
    return text + "    }\n"
end

fn format_ir_signature(function: pointer<IrFunction>) -> string
    @mut let text: string = "@function" + format_ir_int(function->id) + " " + function->name + "("
    @mut let index: int = 0
    while index < vector_length<pointer<IrParameter>>(function->parameters) do
        let parameter: pointer<IrParameter> = vector_get<pointer<IrParameter>>(function->parameters, index)
        if index > 0 then
            text = text + ", "
        end
        text = text + "%" + format_ir_int(parameter->value->id) + " " + parameter->name + ": " + parameter->value->type->name
        index = index + 1
    end
    return text + ") -> " + function->return_type->name
end

fn format_ir_block(block: pointer<IrBasicBlock>, emitted: pointer<Vector<pointer<IrValue>>>) -> string
    @mut let text: string = "      block" + format_ir_int(block->target->id) + ":\n"
    @mut let index: int = 0
    while index < vector_length<pointer<IrInstruction>>(block->instructions) do
        let instruction: pointer<IrInstruction> = vector_get<pointer<IrInstruction>>(block->instructions, index)
        text = text + format_ir_required_constants(instruction->operands, emitted)
        text = text + "        " + format_ir_instruction(instruction) + "\n"
        index = index + 1
    end
    if block->terminator->value != null then
        text = text + format_ir_required_constant(block->terminator->value, emitted)
    end
    if block->terminator->condition != null then
        text = text + format_ir_required_constant(block->terminator->condition, emitted)
    end
    return text + "        " + format_ir_terminator(block->terminator) + "\n"
end

fn format_ir_required_constants(values: pointer<Vector<pointer<IrValue>>>, emitted: pointer<Vector<pointer<IrValue>>>) -> string
    @mut let text: string = ""
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(values) do
        text = text + format_ir_required_constant(vector_get<pointer<IrValue>>(values, index), emitted)
        index = index + 1
    end
    return text
end

fn format_ir_required_constant(value: pointer<IrValue>, emitted: pointer<Vector<pointer<IrValue>>>) -> string
    if !ir_value_is_constant(value) || ir_formatter_value_exists(emitted, value) then
        return ""
    end
    vector_push<pointer<IrValue>>(emitted, value)
    return "        %" + format_ir_int(value->id) + ": " + value->type->name + " = const " + format_ir_constant(value) + "\n"
end

fn format_ir_instruction(instruction: pointer<IrInstruction>) -> string
    if instruction->kind == ir_instruction_local_initialize() then
        return "initialize local" + format_ir_int(instruction->local->id) + " " + format_ir_local_kind(instruction->local->kind) + " " + instruction->local->name + ": " + instruction->local->type->name + ", %" + format_ir_int(ir_instruction_operand(instruction, 0)->id)
    end
    if instruction->kind == ir_instruction_local_load() then
        return format_ir_result(instruction) + "load local" + format_ir_int(instruction->local->id)
    end
    if instruction->kind == ir_instruction_local_store() then
        return "store local" + format_ir_int(instruction->local->id) + ", %" + format_ir_int(ir_instruction_operand(instruction, 0)->id)
    end
    if instruction->kind == ir_instruction_struct_field_store() then
        return "store_field local" + format_ir_int(instruction->local->id) + "." + format_ir_field_path(instruction->field_path) + ", %" + format_ir_int(ir_instruction_operand(instruction, 0)->id)
    end
    if instruction->kind == ir_instruction_struct_construct() then
        return format_ir_result(instruction) + "construct " + format_ir_operands(instruction->operands)
    end
    if instruction->kind == ir_instruction_struct_extract() then
        return format_ir_result(instruction) + "extract %" + format_ir_int(ir_instruction_operand(instruction, 0)->id) + "." + instruction->field->name
    end
    if instruction->kind == ir_instruction_value_call() then
        return format_ir_result(instruction) + "call @function" + format_ir_int(instruction->target->id) + " " + instruction->target->name + "(" + format_ir_operands(instruction->operands) + ")"
    end
    if instruction->kind == ir_instruction_void_call() then
        return "call @function" + format_ir_int(instruction->target->id) + " " + instruction->target->name + "(" + format_ir_operands(instruction->operands) + ")"
    end
    if instruction->kind == ir_instruction_unary() then
        return format_ir_result(instruction) + format_ir_unary_operator(instruction->operator) + " %" + format_ir_int(ir_instruction_operand(instruction, 0)->id)
    end
    if instruction->kind == ir_instruction_binary() then
        return format_ir_result(instruction) + format_ir_binary_operator(instruction->operator) + " " + format_ir_operands(instruction->operands)
    end
    if instruction->kind == ir_instruction_pointer_load() then
        return format_ir_result(instruction) + "pointer_load %" + format_ir_int(ir_instruction_operand(instruction, 0)->id)
    end
    if instruction->kind == ir_instruction_pointer_store() then
        return "pointer_store " + format_ir_operands(instruction->operands)
    end
    if instruction->kind == ir_instruction_pointer_index_load() then
        return format_ir_result(instruction) + "pointer_index_load " + format_ir_operands(instruction->operands)
    end
    if instruction->kind == ir_instruction_pointer_index_store() then
        return "pointer_index_store " + format_ir_operands(instruction->operands)
    end
    if instruction->kind == ir_instruction_pointer_field_load() then
        return format_ir_result(instruction) + "pointer_field_load %" + format_ir_int(ir_instruction_operand(instruction, 0)->id) + "." + instruction->field->name
    end
    if instruction->kind == ir_instruction_pointer_field_store() then
        return "pointer_field_store %" + format_ir_int(ir_instruction_operand(instruction, 0)->id) + "." + instruction->field->name + ", %" + format_ir_int(ir_instruction_operand(instruction, 1)->id)
    end
    if instruction->kind == ir_instruction_string_index() then
        return format_ir_result(instruction) + "string_index " + format_ir_operands(instruction->operands)
    end
    return "<unknown instruction>"
end

fn format_ir_terminator(terminator: pointer<IrTerminator>) -> string
    if terminator->kind == ir_terminator_return() then
        if terminator->value == null then
            return "return"
        end
        return "return %" + format_ir_int(terminator->value->id)
    end
    if terminator->kind == ir_terminator_branch() then
        return "branch block" + format_ir_int(terminator->true_target->id)
    end
    if terminator->kind == ir_terminator_conditional_branch() then
        return "branch_if %" + format_ir_int(terminator->condition->id) + ", block" + format_ir_int(terminator->true_target->id) + ", block" + format_ir_int(terminator->false_target->id)
    end
    return "<unknown terminator>"
end

fn format_ir_result(instruction: pointer<IrInstruction>) -> string
    return "%" + format_ir_int(instruction->result->id) + ": " + instruction->result->type->name + " = "
end

fn format_ir_operands(values: pointer<Vector<pointer<IrValue>>>) -> string
    @mut let text: string = ""
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(values) do
        if index > 0 then
            text = text + ", "
        end
        text = text + "%" + format_ir_int(vector_get<pointer<IrValue>>(values, index)->id)
        index = index + 1
    end
    return text
end

fn format_ir_field_path(fields: pointer<Vector<pointer<IrStructField>>>) -> string
    @mut let text: string = ""
    @mut let index: int = 0
    while index < vector_length<pointer<IrStructField>>(fields) do
        if index > 0 then
            text = text + "."
        end
        text = text + vector_get<pointer<IrStructField>>(fields, index)->name
        index = index + 1
    end
    return text
end

fn format_ir_constant(value: pointer<IrValue>) -> string
    if value->kind == ir_value_int_constant() then
        return format_ir_int(value->int_value)
    end
    if value->kind == ir_value_float_constant() || value->kind == ir_value_char_constant() then
        return value->string_value
    end
    if value->kind == ir_value_boolean_constant() then
        if value->boolean_value then
            return "true"
        end
        return "false"
    end
    if value->kind == ir_value_string_constant() then
        return quote_ir_string(value->string_value)
    end
    if value->kind == ir_value_null_constant() then
        return "null"
    end
    return "<not a constant>"
end

fn quote_ir_string(value: string) -> string
    @mut let result: string = "\""
    @mut let index: int = 0
    while index < strings::length(value) do
        let scalar: char = value[index]
        if scalar == '\\' then
            result = result + "\\\\"
        else
            if scalar == '"' then
                result = result + "\\\""
            else
                if scalar == '\n' then
                    result = result + "\\n"
                else
                    if scalar == '\r' then
                        result = result + "\\r"
                    else
                        if scalar == '\t' then
                            result = result + "\\t"
                        else
                            result = result + strings::slice(value, index, index + 1)
                        end
                    end
                end
            end
        end
        index = index + 1
    end
    return result + "\""
end

fn format_ir_int(value: int) -> string
    if value == 0 then
        return "0"
    end
    @mut let remaining: int = value
    @mut let negative: boolean = value < 0
    if remaining > 0 then
        remaining = -remaining
    end
    @mut let digits: string = ""
    while remaining < 0 do
        let digit: int = -(remaining % 10)
        digits = strings::slice("0123456789", digit, digit + 1) + digits
        remaining = remaining / 10
    end
    if negative then
        return "-" + digits
    end
    return digits
end

fn format_ir_local_kind(kind: int) -> string
    if kind == ir_local_constant() then
        return "const"
    end
    if kind == ir_local_immutable() then
        return "let"
    end
    return "mut"
end

fn format_ir_unary_operator(operator: int) -> string
    if operator == ir_unary_logical_not() then
        return "logical_not"
    end
    if operator == ir_unary_negate() then
        return "negate"
    end
    return "positive"
end

fn format_ir_binary_operator(operator: int) -> string
    if operator == ir_binary_multiply() then
        return "multiply"
    end
    if operator == ir_binary_divide() then
        return "divide"
    end
    if operator == ir_binary_remainder() then
        return "remainder"
    end
    if operator == ir_binary_add() then
        return "add"
    end
    if operator == ir_binary_subtract() then
        return "subtract"
    end
    if operator == ir_binary_less_than() then
        return "less_than"
    end
    if operator == ir_binary_less_than_or_equal() then
        return "less_than_or_equal"
    end
    if operator == ir_binary_greater_than() then
        return "greater_than"
    end
    if operator == ir_binary_greater_than_or_equal() then
        return "greater_than_or_equal"
    end
    if operator == ir_binary_equal() then
        return "equal"
    end
    if operator == ir_binary_not_equal() then
        return "not_equal"
    end
    if operator == ir_binary_logical_and() then
        return "logical_and"
    end
    return "logical_or"
end

fn ir_instruction_operand(instruction: pointer<IrInstruction>, index: int) -> pointer<IrValue>
    return vector_get<pointer<IrValue>>(instruction->operands, index)
end

fn ir_value_is_constant(value: pointer<IrValue>) -> boolean
    if value == null then
        return false
    end
    return value->kind >= ir_value_int_constant() && value->kind <= ir_value_null_constant()
end

fn ir_formatter_value_exists(values: pointer<Vector<pointer<IrValue>>>, value: pointer<IrValue>) -> boolean
    @mut let index: int = 0
    while index < vector_length<pointer<IrValue>>(values) do
        if vector_get<pointer<IrValue>>(values, index) == value then
            return true
        end
        index = index + 1
    end
    return false
end
