package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrUnaryInstruction;
import io.github.stardragonstudios.sol.ir.IrValue;
import io.github.stardragonstudios.sol.ir.IrValueCallInstruction;
import io.github.stardragonstudios.sol.ir.IrStructConstructInstruction;
import io.github.stardragonstudios.sol.ir.IrStructFieldExtractInstruction;
import io.github.stardragonstudios.sol.ir.IrStructType;
import io.github.stardragonstudios.sol.ir.IrNullConstant;
import io.github.stardragonstudios.sol.ir.IrPointerType;
import io.github.stardragonstudios.sol.ir.IrPointerFieldLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrStringIndexInstruction;
import io.github.stardragonstudios.sol.semantics.LocalVariableSymbol;
import io.github.stardragonstudios.sol.semantics.ParameterSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.BinaryExpression;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.Expression;
import io.github.stardragonstudios.sol.syntax.LiteralExpression;
import io.github.stardragonstudios.sol.syntax.NameExpression;
import io.github.stardragonstudios.sol.syntax.ParenthesizedExpression;
import io.github.stardragonstudios.sol.syntax.UnaryExpression;
import io.github.stardragonstudios.sol.syntax.StructConstructionExpression;
import io.github.stardragonstudios.sol.syntax.FieldAccessExpression;
import io.github.stardragonstudios.sol.syntax.NullExpression;
import io.github.stardragonstudios.sol.syntax.PointerFieldAccessExpression;
import io.github.stardragonstudios.sol.syntax.IndexExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

final class IrExpressionLowerer {
    private IrExpressionLowerer() {}

    static IrValue lower(Expression expression, SemanticModel model, IrFunctionLoweringContext context) {
        Objects.requireNonNull(expression, "Lowered expression must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");
        Objects.requireNonNull(context, "Function lowering context must not be null.");

        final IrValue lowered = switch (expression) {
            case LiteralExpression literal -> IrLiteralLowerer.lower(literal, context);
            case NullExpression nullExpression -> lowerNull(nullExpression, model, context);
            case NameExpression name -> lowerName(name, model, context);
            case ParenthesizedExpression parenthesized -> lower(parenthesized.expression(), model, context);
            case UnaryExpression unary -> lowerUnary(unary, model, context);
            case BinaryExpression binary -> lowerBinary(binary, model, context);
            case CallExpression call -> lowerCall(call, model, context);
            case StructConstructionExpression construction -> lowerStructConstruction(construction, model, context);
            case FieldAccessExpression fieldAccess -> lowerFieldAccess(fieldAccess, model, context);
            case PointerFieldAccessExpression fieldAccess -> lowerPointerFieldAccess(fieldAccess, model, context);
            case IndexExpression index -> lowerIndex(index, model, context);

            default -> throw new IrLoweringException("Unsupported expression syntax '%s' during IR lowering.".formatted(expression.getClass().getSimpleName()));
        };

        validateSemanticType(expression, lowered, model, context);

        return lowered;
    }

    private static IrValue lowerNull(
        NullExpression expression,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        var semanticType = model.typeOf(expression).orElseThrow(() -> new IrLoweringException(
            "Null expression has no contextual semantic pointer type."
        ));
        var loweredType = context.lowerType(semanticType);

        if (!(loweredType instanceof IrPointerType pointerType)) throw new IrLoweringException(
            "Null expression lowered to non-pointer type '%s'.".formatted(loweredType.displayName())
        );

        return new IrNullConstant(context.nextValueId(), pointerType);
    }

    private static IrValue lowerPointerFieldAccess(
        PointerFieldAccessExpression expression,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        var pointer = lower(expression.pointer(), model, context);
        var semanticField = model.accessedFieldOf(expression).orElseThrow(() -> new IrLoweringException(
            "Pointer-field access '%s' has no resolved semantic field.".formatted(expression.fieldName())
        ));
        if (!(pointer.type() instanceof IrPointerType pointerType) || !(pointerType.elementType() instanceof IrStructType structType))
            throw new IrLoweringException("Pointer-field access lowered a non-pointer-to-struct target.");

        var instruction = new IrPointerFieldLoadInstruction(context.nextValueId(), pointer, structType.fields().get(semanticField.index()));

        context.emit(instruction);

        return instruction;
    }

    private static IrValue lowerIndex(
        IndexExpression expression,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        var target = lower(expression.target(), model, context);
        var index = lower(expression.index(), model, context);
        var instruction = new IrStringIndexInstruction(context.nextValueId(), target, index);

        context.emit(instruction);

        return instruction;
    }

    private static IrValue lowerStructConstruction(
        StructConstructionExpression expression,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        var struct = model.constructedStructOf(expression).orElseThrow(() -> new IrLoweringException(
            "Struct construction '%s' has no resolved semantic struct.".formatted(expression.type().name())
        ));
        var semanticType = model.typeOf(expression).orElseThrow(() -> new IrLoweringException(
            "Struct construction '%s' has no resolved semantic type.".formatted(expression.type().name())
        ));
        var loweredType = context.lowerType(semanticType);

        if (!(loweredType instanceof IrStructType type)) throw new IrLoweringException(
            "Struct construction '%s' lowered to non-struct type '%s'."
                .formatted(struct.name(), loweredType.displayName())
        );
        var valuesByFieldIndex = new HashMap<Integer, IrValue>();

        /* Lower in source order so initializer side effects retain source evaluation order. */
        for (var initializer : expression.fields()) {
            var field = model.initializedFieldOf(initializer).orElseThrow(() -> new IrLoweringException(
                "Initializer for field '%s' of struct '%s' has no resolved semantic field.".formatted(initializer.name(), struct.name())
            ));

            valuesByFieldIndex.put(field.index(), lower(initializer.value(), model, context));
        }

        var values = new ArrayList<IrValue>(type.fields().size());

        for (var field : type.fields()) {
            var value = valuesByFieldIndex.get(field.index());

            if (value == null) throw new IrLoweringException(
                "Construction of struct '%s' has no lowered value for field '%s'.".formatted(struct.name(), field.name())
            );

            values.add(value);
        }

        var instruction = new IrStructConstructInstruction(context.nextValueId(), type, values);

        context.emit(instruction);

        return instruction;
    }

    private static IrValue lowerFieldAccess(
        FieldAccessExpression expression,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        var target = lower(expression.target(), model, context);
        var semanticField = model.accessedFieldOf(expression).orElseThrow(() -> new IrLoweringException(
            "Field access '%s' has no resolved semantic field.".formatted(expression.fieldName())
        ));
        if (!(target.type() instanceof IrStructType targetType)) throw new IrLoweringException(
            "Field access '%s' lowered a non-struct target type '%s'."
                .formatted(expression.fieldName(), target.type().displayName())
        );

        var field = targetType.fields().get(semanticField.index());
        var instruction = new IrStructFieldExtractInstruction(context.nextValueId(), target, field);

        context.emit(instruction);

        return instruction;
    }

    private static IrValue lowerName(NameExpression expression, SemanticModel model, IrFunctionLoweringContext context) {
        var symbol = model.symbolOf(expression).orElseThrow(() -> new IrLoweringException("Name expression '%s' has no resolved semantic symbol.".formatted(expression.name())));

        if (symbol instanceof ParameterSymbol parameter) return context.parameter(parameter);

        if (symbol instanceof LocalVariableSymbol local) {
            var loweredLocal = context.local(local);
            var instruction = new IrLocalLoadInstruction(context.nextValueId(), loweredLocal);

            context.emit(instruction);

            return instruction;
        }

        throw new IrLoweringException("Resolved symbol '%s' is not supported as an IR value in the current lowering subset.".formatted(symbol.name()));
    }

    private static IrValue lowerUnary(UnaryExpression expression, SemanticModel model, IrFunctionLoweringContext context) {
        var operand = lower(expression.operand(), model, context);

        final IrUnaryInstruction instruction;

        try {
            instruction = new IrUnaryInstruction(context.nextValueId(), IrOperatorLowerer.lower(expression.operator()), operand);
        } catch (IllegalArgumentException exception) {
            throw invalidOperatorExpression("unary", exception);
        }

        context.emit(instruction);

        return instruction;
    }

    private static IrValue lowerBinary(BinaryExpression expression, SemanticModel model, IrFunctionLoweringContext context) {
        var left = lower(expression.left(), model, context);
        var right = lower(expression.right(), model, context);

        final IrBinaryInstruction instruction;

        try {
            instruction = new IrBinaryInstruction(context.nextValueId(), IrOperatorLowerer.lower(expression.operator()), left, right);
        } catch (IllegalArgumentException exception) {
            throw invalidOperatorExpression("binary", exception);
        }

        context.emit(instruction);

        return instruction;
    }

    private static IrValue lowerCall(CallExpression expression, SemanticModel model, IrFunctionLoweringContext context) {
        var instruction = IrCallLowerer.lower(expression, model, context);

        if (instruction instanceof IrValueCallInstruction valueCall) return valueCall;

        throw new IrLoweringException("Call to void function '%s' cannot be used as an IR value.".formatted(instruction.target().name()));
    }

    private static IrLoweringException invalidOperatorExpression(String kind, IllegalArgumentException cause) {
        return new IrLoweringException("Semantically validated %s expression produced invalid IR: %s".formatted(kind, cause.getMessage()));
    }

    private static void validateSemanticType(Expression expression, IrValue value, SemanticModel model, IrFunctionLoweringContext context) {
        var semanticType = model.typeOf(expression).orElseThrow(() -> new IrLoweringException(
            "Expression syntax '%s' has no resolved semantic type.".formatted(expression.getClass().getSimpleName()))
        );

        var expectedType = context.lowerType(semanticType);

        if (!expectedType.equals(value.type()))
            throw new IrLoweringException("Lowered expression type '%s' does not match semantic type '%s'.".formatted(value.type().displayName(), expectedType.displayName()));
    }
}
