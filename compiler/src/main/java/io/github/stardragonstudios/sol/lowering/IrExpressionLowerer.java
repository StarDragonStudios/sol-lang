package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrUnaryInstruction;
import io.github.stardragonstudios.sol.ir.IrValue;
import io.github.stardragonstudios.sol.semantics.LocalVariableSymbol;
import io.github.stardragonstudios.sol.semantics.ParameterSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.BinaryExpression;
import io.github.stardragonstudios.sol.syntax.Expression;
import io.github.stardragonstudios.sol.syntax.LiteralExpression;
import io.github.stardragonstudios.sol.syntax.NameExpression;
import io.github.stardragonstudios.sol.syntax.ParenthesizedExpression;
import io.github.stardragonstudios.sol.syntax.UnaryExpression;

import java.util.Objects;

final class IrExpressionLowerer {
    private IrExpressionLowerer() {}

    static IrValue lower(Expression expression, SemanticModel model, IrFunctionLoweringContext context) {
        Objects.requireNonNull(expression, "Lowered expression must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");
        Objects.requireNonNull(context, "Function lowering context must not be null.");

        final IrValue lowered = switch (expression) {
            case LiteralExpression literal -> IrLiteralLowerer.lower(literal, context);
            case NameExpression name -> lowerName(name, model, context);
            case ParenthesizedExpression parenthesized -> lower(parenthesized.expression(), model, context);
            case UnaryExpression unary -> lowerUnary(unary, model, context);
            case BinaryExpression binary -> lowerBinary(binary, model, context);

            default -> throw new IrLoweringException(
                "Unsupported expression syntax '%s' during IR lowering.".formatted(expression.getClass().getSimpleName())
            );
        };

        validateSemanticType(expression, lowered, model);

        return lowered;
    }

    private static IrValue lowerName(NameExpression expression, SemanticModel model, IrFunctionLoweringContext context) {
        var symbol = model.symbolOf(expression).orElseThrow(() -> new IrLoweringException(
            "Name expression '%s' has no resolved semantic symbol.".formatted(expression.name()))
        );

        if (symbol instanceof ParameterSymbol parameter) return context.parameter(parameter);

        if (symbol instanceof LocalVariableSymbol local) {
            var loweredLocal = context.local(local);
            var instruction = new IrLocalLoadInstruction(context.nextValueId(), loweredLocal);

            context.emit(instruction);

            return instruction;
        }

        throw new IrLoweringException(
            "Resolved symbol '%s' is not supported as an IR value in the current lowering subset.".formatted(symbol.name())
        );
    }

    private static IrValue lowerUnary(UnaryExpression expression, SemanticModel model, IrFunctionLoweringContext context) {
        var operand = lower(expression.operand(), model, context);

        final IrUnaryInstruction instruction;

        try {
            instruction = new IrUnaryInstruction(
                context.nextValueId(),
                IrOperatorLowerer.lower(expression.operator()),
                operand
            );
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
            instruction = new IrBinaryInstruction(
                context.nextValueId(),
                IrOperatorLowerer.lower(expression.operator()),
                left,
                right
            );
        } catch (IllegalArgumentException exception) {
            throw invalidOperatorExpression("binary", exception);
        }

        context.emit(instruction);

        return instruction;
    }

    private static IrLoweringException invalidOperatorExpression(String kind, IllegalArgumentException cause) {
        return new IrLoweringException(
            "Semantically validated %s expression produced invalid IR: %s".formatted(kind, cause.getMessage())
        );
    }

    private static void validateSemanticType(Expression expression, IrValue value, SemanticModel model) {
        var semanticType = model.typeOf(expression).orElseThrow(() -> new IrLoweringException(
            "Expression syntax '%s' has no resolved semantic type.".formatted(expression.getClass().getSimpleName()))
        );

        var expectedType = IrTypeLowerer.lower(semanticType);

        if (!expectedType.equals(value.type())) {
            throw new IrLoweringException(
                "Lowered expression type '%s' does not match semantic type '%s'.".formatted(value.type().displayName(), expectedType.displayName())
            );
        }
    }
}
