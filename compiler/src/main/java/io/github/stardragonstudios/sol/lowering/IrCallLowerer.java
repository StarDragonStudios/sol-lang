package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.*;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.CallExpression;

import java.util.ArrayList;
import java.util.Objects;

final class IrCallLowerer {
    private IrCallLowerer() {}

    static IrCallInstruction lower(CallExpression expression, SemanticModel model, IrFunctionLoweringContext context) {
        Objects.requireNonNull(expression, "Lowered call expression must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");
        Objects.requireNonNull(context, "Function lowering context must not be null.");

        var function = model.calledFunctionOf(expression).orElseThrow(() -> new IrLoweringException("Call expression has no resolved canonical function symbol."));
        var target = context.functionReference(function);

        validateReturnType(expression, target.returnType(), model, context);

        var arguments = new ArrayList<IrValue>(expression.arguments().size());

        for (var argument : expression.arguments()) arguments.add(IrExpressionLowerer.lower(argument, model, context));

        final IrCallInstruction instruction;

        try {
            if (target.returnsValue()) instruction = new IrValueCallInstruction(context.nextValueId(), target, arguments);
            else instruction = new IrVoidCallInstruction(target, arguments);
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException("Semantically validated call to function '%s' produced invalid IR: %s".formatted(function.name(), exception.getMessage()));
        }

        context.emit(instruction);

        return instruction;
    }

    private static void validateReturnType(CallExpression expression, IrType targetReturnType, SemanticModel model, IrFunctionLoweringContext context) {
        var semanticType = model.typeOf(expression).orElseThrow(() -> new IrLoweringException("Call expression has no resolved semantic result type."));
        var loweredSemanticType = context.lowerType(semanticType);

        if (!targetReturnType.equals(loweredSemanticType)) throw new IrLoweringException("Called function IR return type '%s' does not match semantic call type '%s'.".formatted(
            targetReturnType.displayName(),
            loweredSemanticType.displayName()
        ));
    }
}
