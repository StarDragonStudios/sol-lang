package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrLocalInitializeInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrStructField;
import io.github.stardragonstudios.sol.ir.IrStructFieldStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrStructType;
import io.github.stardragonstudios.sol.ir.IrPointerStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrPointerIndexStoreInstruction;
import io.github.stardragonstudios.sol.semantics.LocalVariableSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.AssignmentStatement;
import io.github.stardragonstudios.sol.syntax.CallStatement;
import io.github.stardragonstudios.sol.syntax.Statement;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;
import io.github.stardragonstudios.sol.syntax.FieldAccessExpression;
import io.github.stardragonstudios.sol.syntax.FieldAssignmentStatement;
import io.github.stardragonstudios.sol.syntax.PointerAssignmentStatement;
import io.github.stardragonstudios.sol.syntax.PointerDereferenceExpression;
import io.github.stardragonstudios.sol.syntax.PointerIndexExpression;

import java.util.ArrayList;
import java.util.List;
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
            case FieldAssignmentStatement fieldAssignment -> lowerFieldAssignment(fieldAssignment, model, context);
            case PointerAssignmentStatement pointerAssignment -> lowerPointerAssignment(pointerAssignment, model, context);
            case CallStatement call -> IrCallLowerer.lower(call.call(), model, context);

            default -> throw new IrLoweringException(
                "Unsupported statement syntax '%s' in function '%s' during IR lowering.".formatted(statement.getClass().getSimpleName(), context.function().name())
            );
        }
    }

    private static void lowerPointerAssignment(
        PointerAssignmentStatement assignment,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        switch (assignment.target()) {
            case PointerDereferenceExpression dereference -> {
                var pointer = IrExpressionLowerer.lower(dereference.pointer(), model, context);
                var value = IrExpressionLowerer.lower(assignment.value(), model, context);

                context.emit(new IrPointerStoreInstruction(pointer, value));
            }

            case PointerIndexExpression index -> {
                var pointer = IrExpressionLowerer.lower(index.pointer(), model, context);
                var loweredIndex = IrExpressionLowerer.lower(index.index(), model, context);
                var value = IrExpressionLowerer.lower(assignment.value(), model, context);

                context.emit(new IrPointerIndexStoreInstruction(pointer, loweredIndex, value));
            }

            default -> throw new IrLoweringException(
                "Unsupported pointer assignment target '%s'.".formatted(assignment.target().getClass().getSimpleName())
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
        var local = context.declareLocal(symbol, context.lowerType(semanticType));

        final IrLocalInitializeInstruction instruction;

        try {
            instruction = new IrLocalInitializeInstruction(local, initializer);
        } catch (IllegalArgumentException exception) {
            throw invalidLocalOperation(declaration.name(), exception);
        }

        context.emit(instruction);
    }

    private static void lowerFieldAssignment(
        FieldAssignmentStatement assignment,
        SemanticModel model,
        IrFunctionLoweringContext context
    ) {
        var target = model.assignmentTargetOf(assignment).orElseThrow(() -> new IrLoweringException(
            "Field assignment has no resolved root symbol."
        ));

        if (!(target instanceof LocalVariableSymbol localSymbol))
            throw new IrLoweringException("Resolved field assignment root '%s' is not a local variable.".formatted(target.name()));

        var path = new ArrayList<IrStructField>();

        collectFieldPath(assignment.target(), model, context, path);

        var value = IrExpressionLowerer.lower(assignment.value(), model, context);

        context.emit(new IrStructFieldStoreInstruction(context.local(localSymbol), path, value));
    }

    private static void collectFieldPath(
        FieldAccessExpression access,
        SemanticModel model,
        IrFunctionLoweringContext context,
        List<IrStructField> path
    ) {
        if (access.target() instanceof FieldAccessExpression parent) collectFieldPath(parent, model, context, path);

        var semanticField = model.accessedFieldOf(access).orElseThrow(() -> new IrLoweringException(
            "Field assignment path '%s' has no resolved semantic field.".formatted(access.fieldName())
        ));

        var semanticTargetType = model.typeOf(access.target()).orElseThrow(() -> new IrLoweringException(
            "Field assignment path '%s' has no resolved semantic target type.".formatted(access.fieldName())
        ));
        var targetType = context.lowerType(semanticTargetType);

        if (!(targetType instanceof IrStructType structType)) throw new IrLoweringException(
            "Field assignment path '%s' lowered a non-struct target type '%s'."
                .formatted(access.fieldName(), targetType.displayName())
        );

        path.add(structType.fields().get(semanticField.index()));
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
