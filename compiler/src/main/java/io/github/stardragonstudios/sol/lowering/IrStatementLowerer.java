package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrLocalInitializeInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalStoreInstruction;
import io.github.stardragonstudios.sol.semantics.LocalVariableSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.AssignmentStatement;
import io.github.stardragonstudios.sol.syntax.Statement;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import java.util.Objects;

final class IrStatementLowerer {
    private IrStatementLowerer() {}

    static void lower(Statement statement, SemanticModel model, IrFunctionLoweringContext context) {
        Objects.requireNonNull(statement, "Lowered statement must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");
        Objects.requireNonNull(context, "Function lowering context must not be null.");

        switch (statement) {
            case VariableDeclarationStatement declaration -> lowerVariableDeclaration(declaration, model, context);
            case AssignmentStatement assignment -> lowerAssignment(assignment, model, context);

            default -> throw new IrLoweringException(
                "Unsupported statement syntax '%s' in function '%s' during IR lowering."
                    .formatted(statement.getClass().getSimpleName(), context.function().name())
            );
        }
    }

    private static void lowerVariableDeclaration(VariableDeclarationStatement declaration, SemanticModel model, IrFunctionLoweringContext context) {
        var symbol = model.symbolOf(declaration).orElseThrow(() -> new IrLoweringException(
            "Variable declaration '%s' has no canonical local-variable symbol.".formatted(declaration.name()))
        );

        var semanticType = model.typeOf(declaration.type()).orElseThrow(() -> new IrLoweringException(
            "Variable declaration '%s' has no resolved semantic type.".formatted(declaration.name()))
        );

        /*
         * The initializer is lowered before the local is registered.
         * This prevents a malformed semantic model from allowing a
         * declaration to read its own not-yet-initialized storage.
         */
        var initializer = IrExpressionLowerer.lower(declaration.initializer(), model, context);
        var local = context.declareLocal(symbol, IrTypeLowerer.lower(semanticType));

        final IrLocalInitializeInstruction instruction;

        try {
            instruction = new IrLocalInitializeInstruction(local, initializer);
        } catch (IllegalArgumentException exception) {
            throw invalidLocalOperation(declaration.name(), exception);
        }

        context.emit(instruction);
    }

    private static void lowerAssignment(AssignmentStatement assignment, SemanticModel model, IrFunctionLoweringContext context) {
        var target = model.assignmentTargetOf(assignment).orElseThrow(() -> new IrLoweringException(
            "Assignment target '%s' has no resolved semantic symbol.".formatted(assignment.target().name()))
        );

        if (!(target instanceof LocalVariableSymbol localSymbol))
            throw new IrLoweringException("Resolved assignment target '%s' is not a local variable.".formatted(target.name()));

        var local = context.local(localSymbol);
        var value = IrExpressionLowerer.lower(assignment.value(), model, context);

        final IrLocalStoreInstruction instruction;

        try {
            instruction = new IrLocalStoreInstruction(local, value);
        } catch (IllegalArgumentException exception) {
            throw invalidLocalOperation(localSymbol.name(), exception);
        }

        context.emit(instruction);
    }

    private static IrLoweringException invalidLocalOperation(String localName, IllegalArgumentException cause) {
        return new IrLoweringException(
            "Semantically validated local operation for '%s' produced invalid IR: %s".formatted(localName, cause.getMessage())
        );
    }
}
