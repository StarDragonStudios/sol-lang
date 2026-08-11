package io.github.stardragonstudios.sol.ir;

import java.util.*;

public final class IrTextFormatter {
    private static final String INDENT = "  ";

    private final StringBuilder text = new StringBuilder();

    private IrTextFormatter() {}

    public static String format(IrProgram program) {
        Objects.requireNonNull(program, "Formatted IR program must not be null.");

        var formatter = new IrTextFormatter();

        formatter.writeProgram(program);

        return formatter.text.toString();
    }

    private void writeProgram(IrProgram program) {
        line(0, "program {");

        if (program.entryPoint().isPresent()) {
            var entryPoint = program.entryPoint().orElseThrow();

            line(1, "entry @%s::%s".formatted(
                    entryPoint.module().name().qualifiedName(),
                    entryPoint.function().id()
                )
            );
        } else {
            line(1, "entry none");
        }

        if (!program.modules().isEmpty()) blankLine();

        for (var index = 0; index < program.modules().size(); index++) {
            writeModule(program.modules().get(index));

            if (index < program.modules().size() - 1) blankLine();
        }

        line(0, "}");
    }

    private void writeModule(IrModule module) {
        line(1, "module @%s {".formatted(module.name().qualifiedName()));

        for (var index = 0; index < module.structs().size(); index++) {
            writeStruct(module.structs().get(index));

            if (index < module.structs().size() - 1 || !module.functions().isEmpty()) blankLine();
        }

        for (var index = 0; index < module.functions().size(); index++) {
            writeFunction(module.functions().get(index));

            if (index < module.functions().size() - 1) blankLine();
        }

        line(1, "}");
    }

    private void writeStruct(IrStructType struct) {
        line(2, "struct %s {".formatted(struct.displayName()));

        for (var field : struct.fields()) line(3, "%s: %s".formatted(field.name(), field.type().displayName()));

        line(2, "}");
    }

    private void writeFunction(IrFunction function) {
        if (!function.hasBody()) {
            line(2, "declare %s".formatted(signature(function)));

            return;
        }

        line(2, "define %s {".formatted(signature(function)));

        Set<IrValue> emittedConstants = Collections.newSetFromMap(new IdentityHashMap<>());

        var blocks = function.blocks();

        for (var index = 0; index < blocks.size(); index++) {
            writeBlock(blocks.get(index), emittedConstants);

            if (index < blocks.size() - 1) blankLine();
        }

        line(2, "}");
    }

    private void writeBlock(IrBasicBlock block, Set<IrValue> emittedConstants) {
        line(3, block.id() + ":");

        for (var instruction : block.instructions()) {
            for (var operand : instruction.operands()) emitRequiredConstants(operand, emittedConstants);

            line(4, formatInstruction(instruction));
        }

        for (var operand : block.terminator().operands()) emitRequiredConstants(operand, emittedConstants);

        line(4, formatTerminator(block.terminator()));
    }

    private void emitRequiredConstants(IrValue value, Set<IrValue> emittedConstants) {
        Objects.requireNonNull(value, "Formatted IR value must not be null.");

        if (value instanceof IrValueInstruction instruction) for (var operand : instruction.operands()) emitRequiredConstants(operand, emittedConstants);
        if (isConstant(value) && emittedConstants.add(value)) line(4, formatConstant(value));
    }

    private String signature(IrFunction function) {
        var parameters = new StringJoiner(", ");

        for (var parameter : function.parameters()) {
            parameters.add("%s %s: %s".formatted(
                    parameter.id(),
                    parameter.name(),
                    parameter.type().displayName()
                )
            );
        }

        return "@%s %s(%s) -> %s".formatted(
            function.id(),
            function.name(),
            parameters,
            function.returnType().displayName()
        );
    }

    private String formatInstruction(IrInstruction instruction) {
        if (instruction instanceof IrLocalInitializeInstruction(IrLocal local, IrValue initializer))
            return "initialize %s %s %s: %s, %s".formatted(local.id(), localKindName(local.kind()), local.name(), local.type().displayName(), initializer.id());

        if (instruction instanceof IrLocalLoadInstruction load)
            return "%s: %s = load %s".formatted(load.id(), load.type().displayName(), load.local().id());

        if (instruction instanceof IrLocalStoreInstruction(IrLocal local, IrValue value))
            return "store %s, %s".formatted(local.id(), value.id());

        if (instruction instanceof IrStructFieldStoreInstruction store)
            return "store_field %s.%s, %s".formatted(store.local().id(), formatFieldPath(store.path()), store.value().id());

        if (instruction instanceof IrStructConstructInstruction construction)
            return "%s: %s = construct %s".formatted(construction.id(), construction.type().displayName(), formatCallArguments(construction.fields()));

        if (instruction instanceof IrStructFieldExtractInstruction extraction)
            return "%s: %s = extract %s.%s".formatted(extraction.id(), extraction.type().displayName(), extraction.target().id(), extraction.field().name());

        if (instruction instanceof IrValueCallInstruction call)
            return "%s: %s = call @%s %s(%s)".formatted(call.id(), call.type().displayName(), call.target().id(), call.target().name(), formatCallArguments(call.arguments()));

        if (instruction instanceof IrVoidCallInstruction(IrFunctionReference target, List<IrValue> arguments))
            return "call @%s %s(%s)".formatted(target.id(), target.name(), formatCallArguments(arguments));

        if (instruction instanceof IrUnaryInstruction unary)
            return "%s: %s = %s %s".formatted(unary.id(), unary.type().displayName(), unaryOperationName(unary.operator()), unary.operand().id());

        if (instruction instanceof IrBinaryInstruction binary)
            return "%s: %s = %s %s, %s".formatted(binary.id(), binary.type().displayName(), binaryOperationName(binary.operator()), binary.left().id(), binary.right().id());

        throw new IllegalArgumentException("Unsupported IR instruction type '%s'.".formatted(instruction.getClass().getSimpleName()));
    }

    private String formatFieldPath(List<IrStructField> path) {
        return String.join(".", path.stream().map(IrStructField::name).toList());
    }

    private String formatCallArguments(List<IrValue> arguments) {
        var formatted = new StringJoiner(", ");

        for (var argument : arguments) formatted.add(argument.id().toString());

        return formatted.toString();
    }

    private String formatTerminator(IrTerminator terminator) {
        if (terminator instanceof IrReturnTerminator(java.util.Optional<IrValue> value1))
            return value1.map(value -> "return " + value.id()).orElse("return");

        if (terminator instanceof IrBranchTerminator(IrBlockTarget target))
            return "branch %s".formatted(target);

        if (terminator instanceof IrConditionalBranchTerminator(IrValue condition, IrBlockTarget trueTarget, IrBlockTarget falseTarget))
            return "branch_if %s, %s, %s".formatted(condition.id(), trueTarget, falseTarget);

        throw new IllegalArgumentException(
            "Unsupported IR terminator type '%s'.".formatted(terminator.getClass().getSimpleName())
        );
    }

    private String localKindName(IrLocalKind kind) {
        return switch (kind) {
            case CONSTANT -> "const";
            case IMMUTABLE -> "let";
            case MUTABLE -> "mut";
        };
    }

    private String formatConstant(IrValue value) {
        return "%s: %s = const %s".formatted(
            value.id(),
            value.type().displayName(),
            constantLiteral(value)
        );
    }

    private boolean isConstant(IrValue value) {
        return value instanceof IrIntConstant
            || value instanceof IrFloatConstant
            || value instanceof IrBooleanConstant
            || value instanceof IrCharConstant
            || value instanceof IrStringConstant;
    }

    private String constantLiteral(IrValue value) {
        if (value instanceof IrIntConstant constant) return Long.toString(constant.value());
        if (value instanceof IrFloatConstant constant) return Double.toString(constant.value());
        if (value instanceof IrBooleanConstant constant) return Boolean.toString(constant.value());
        if (value instanceof IrCharConstant constant) return "U+%04X".formatted(constant.codePoint());
        if (value instanceof IrStringConstant constant) return quoteString(constant.value());

        throw new IllegalArgumentException("Unsupported IR constant type '%s'.".formatted(value.getClass().getSimpleName()));
    }

    private String unaryOperationName(IrUnaryOperator operator) {
        return switch (operator) {
            case LOGICAL_NOT -> "logical_not";
            case NEGATE -> "negate";
            case POSITIVE -> "positive";
        };
    }

    private String binaryOperationName(IrBinaryOperator operator) {
        return switch (operator) {
            case MULTIPLY -> "multiply";
            case DIVIDE -> "divide";
            case REMAINDER -> "remainder";
            case ADD -> "add";
            case SUBTRACT -> "subtract";
            case LESS_THAN -> "less_than";
            case LESS_THAN_OR_EQUAL -> "less_than_or_equal";
            case GREATER_THAN -> "greater_than";
            case GREATER_THAN_OR_EQUAL -> "greater_than_or_equal";
            case EQUAL -> "equal";
            case NOT_EQUAL -> "not_equal";
            case LOGICAL_AND -> "logical_and";
            case LOGICAL_OR -> "logical_or";
        };
    }

    private String quoteString(String value) {
        var result = new StringBuilder("\"");

        for (var offset = 0; offset < value.length(); ) {
            var codePoint = value.codePointAt(offset);

            offset += Character.charCount(codePoint);

            switch (codePoint) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");

                default -> {
                    if (codePoint >= 0x20 && codePoint <= 0x7E) result.appendCodePoint(codePoint);
                    else result.append("\\u{%X}".formatted(codePoint));
                }
            }
        }

        return result.append('"').toString();
    }

    private void line(int indentation, String content) {
        text.repeat(INDENT, indentation);
        text.append(content);
        text.append('\n');
    }

    private void blankLine() {
        text.append('\n');
    }
}
