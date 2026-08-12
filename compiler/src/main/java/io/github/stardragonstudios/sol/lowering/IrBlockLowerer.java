package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.*;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.AssignmentStatement;
import io.github.stardragonstudios.sol.syntax.Block;
import io.github.stardragonstudios.sol.syntax.CallStatement;
import io.github.stardragonstudios.sol.syntax.ConditionalStatement;
import io.github.stardragonstudios.sol.syntax.Expression;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;
import io.github.stardragonstudios.sol.syntax.Statement;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;
import io.github.stardragonstudios.sol.syntax.WhileStatement;
import io.github.stardragonstudios.sol.syntax.FieldAssignmentStatement;
import io.github.stardragonstudios.sol.syntax.PointerFieldAssignmentStatement;

import java.util.Objects;

final class IrBlockLowerer {
    private IrBlockLowerer() {}

    static void lower(Block block, SemanticModel model, IrFunctionLoweringContext context) {
        Objects.requireNonNull(block, "Lowered syntax block must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");
        Objects.requireNonNull(context, "Function lowering context must not be null.");

        for (var statement : block.statements()) {
            if (!context.hasActiveBlock()) {
                throw new IrLoweringException(
                    "Semantically validated block in function '%s' contains unreachable statement syntax '%s'."
                        .formatted(context.function().name(), statement.getClass().getSimpleName())
                );
            }

            lowerStatement(statement, model, context);
        }
    }

    private static void lowerStatement(Statement statement, SemanticModel model, IrFunctionLoweringContext context) {
        switch (statement) {
            case VariableDeclarationStatement declaration -> IrStatementLowerer.lower(declaration, model, context);
            case AssignmentStatement assignment -> IrStatementLowerer.lower(assignment, model, context);
            case FieldAssignmentStatement fieldAssignment -> IrStatementLowerer.lower(fieldAssignment, model, context);
            case PointerFieldAssignmentStatement pointerAssignment -> IrStatementLowerer.lower(pointerAssignment, model, context);
            case ReturnStatement returnStatement -> lowerReturn(returnStatement, model, context);
            case ConditionalStatement conditional -> lowerConditional(conditional, model, context);
            case WhileStatement whileStatement -> lowerWhile(whileStatement, model, context);
            case CallStatement call -> IrStatementLowerer.lower(call, model, context);

            default -> throw new IrLoweringException(
                "Unsupported statement syntax '%s' in function '%s' during IR lowering.".formatted(statement.getClass().getSimpleName(), context.function().name())
            );
        }
    }

    private static void lowerReturn(ReturnStatement statement, SemanticModel model, IrFunctionLoweringContext context) {
        var terminator = statement.expression()
            .map((Expression expression) -> IrReturnTerminator.returning(IrExpressionLowerer.lower(expression, model, context)))
            .orElseGet(IrReturnTerminator::bare);

        context.finishBlock(terminator);
    }

    private static void lowerConditional(ConditionalStatement statement, SemanticModel model, IrFunctionLoweringContext context) {
        var condition = IrExpressionLowerer.lower(statement.condition(), model, context);
        var thenTarget = context.newBlockTarget();

        if (statement.elseBlock().isEmpty()) {
            lowerConditionalWithoutElse(statement, condition, thenTarget, model, context);

            return;
        }

        lowerConditionalWithElse(statement, condition, thenTarget, model, context);
    }

    private static void lowerConditionalWithoutElse(
        ConditionalStatement statement,
        IrValue condition,
        IrBlockTarget thenTarget,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        var continuationTarget = context.newBlockTarget();

        context.finishBlock(new IrConditionalBranchTerminator(condition, thenTarget, continuationTarget));
        context.beginBlock(thenTarget);

        lower(statement.thenBlock(), model, context);

        if (context.hasActiveBlock()) context.finishBlock(new IrBranchTerminator(continuationTarget));


        context.beginBlock(continuationTarget);
    }

    private static void lowerConditionalWithElse(
        ConditionalStatement statement,
        IrValue condition,
        IrBlockTarget thenTarget,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        var elseTarget = context.newBlockTarget();

        context.finishBlock(new IrConditionalBranchTerminator(condition, thenTarget, elseTarget));

        IrBlockTarget continuationTarget = null;

        context.beginBlock(thenTarget);

        lower(statement.thenBlock(), model, context);

        if (context.hasActiveBlock()) {
            continuationTarget = context.newBlockTarget();

            context.finishBlock(new IrBranchTerminator(continuationTarget));
        }

        context.beginBlock(elseTarget);

        lower(statement.elseBlock().orElseThrow(), model, context);

        if (context.hasActiveBlock()) {
            if (continuationTarget == null) continuationTarget = context.newBlockTarget();

            context.finishBlock(new IrBranchTerminator(continuationTarget));
        }

        if (continuationTarget != null) context.beginBlock(continuationTarget);
    }

    private static void lowerWhile(WhileStatement statement, SemanticModel model, IrFunctionLoweringContext context) {
        var conditionTarget = context.newBlockTarget();
        var bodyTarget = context.newBlockTarget();
        var continuationTarget = context.newBlockTarget();

        context.finishBlock(new IrBranchTerminator(conditionTarget));
        context.beginBlock(conditionTarget);

        var condition = IrExpressionLowerer.lower(statement.condition(), model, context);

        context.finishBlock(new IrConditionalBranchTerminator(condition, bodyTarget, continuationTarget));
        context.beginBlock(bodyTarget);

        lower(statement.body(), model, context);

        if (context.hasActiveBlock()) context.finishBlock(new IrBranchTerminator(conditionTarget));

        context.beginBlock(continuationTarget);
    }
}
