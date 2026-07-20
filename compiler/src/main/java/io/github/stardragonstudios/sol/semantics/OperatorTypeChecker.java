package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.syntax.BinaryExpression;
import io.github.stardragonstudios.sol.syntax.BinaryOperator;
import io.github.stardragonstudios.sol.syntax.UnaryExpression;
import io.github.stardragonstudios.sol.syntax.UnaryOperator;

import java.util.List;
import java.util.Objects;

final class OperatorTypeChecker {
    private static final String INVALID_UNARY_OPERAND_CODE = "SOL-S004";
    private static final String INVALID_BINARY_OPERANDS_CODE = "SOL-S005";

    private OperatorTypeChecker() {}

    static TypeSymbol checkUnary(
        UnaryExpression expression,
        TypeSymbol operandType,
        List<Diagnostic> diagnostics
    ) {
        Objects.requireNonNull(expression, "Unary expression must not be null.");
        Objects.requireNonNull(operandType, "Unary operand type must not be null.");
        Objects.requireNonNull(diagnostics, "Diagnostic list must not be null.");

        if (operandType == BuiltInTypes.ERROR) return BuiltInTypes.ERROR;

        return switch (expression.operator()) {
            case LOGICAL_NOT ->
                operandType == BuiltInTypes.BOOLEAN
                    ? BuiltInTypes.BOOLEAN
                    : reportInvalidUnary(expression, operandType, diagnostics);

            case NEGATE, POSITIVE ->
                operandType.isNumeric()
                    ? operandType
                    : reportInvalidUnary(expression, operandType, diagnostics);
        };
    }

    static TypeSymbol checkBinary(
        BinaryExpression expression,
        TypeSymbol leftType,
        TypeSymbol rightType,
        List<Diagnostic> diagnostics
    ) {
        Objects.requireNonNull(expression, "Binary expression must not be null.");
        Objects.requireNonNull(leftType, "Left operand type must not be null.");
        Objects.requireNonNull(rightType, "Right operand type must not be null.");
        Objects.requireNonNull(diagnostics, "Diagnostic list must not be null.");

        if (
            leftType == BuiltInTypes.ERROR
                || rightType == BuiltInTypes.ERROR
        ) {
            return BuiltInTypes.ERROR;
        }

        return switch (expression.operator()) {
            case MULTIPLY, DIVIDE, ADD, SUBTRACT ->
                matchingNumericTypes(leftType, rightType)
                    ? leftType
                    : reportInvalidBinary(
                    expression,
                    leftType,
                    rightType,
                    diagnostics
                );

            case REMAINDER ->
                leftType == BuiltInTypes.INT
                    && rightType == BuiltInTypes.INT
                    ? BuiltInTypes.INT
                    : reportInvalidBinary(
                    expression,
                    leftType,
                    rightType,
                    diagnostics
                );

            case LESS_THAN,
                 LESS_THAN_OR_EQUAL,
                 GREATER_THAN,
                 GREATER_THAN_OR_EQUAL ->
                matchingNumericTypes(leftType, rightType)
                    ? BuiltInTypes.BOOLEAN
                    : reportInvalidBinary(
                    expression,
                    leftType,
                    rightType,
                    diagnostics
                );

            case EQUAL, NOT_EQUAL ->
                leftType == rightType && leftType.isValue()
                    ? BuiltInTypes.BOOLEAN
                    : reportInvalidBinary(
                    expression,
                    leftType,
                    rightType,
                    diagnostics
                );

            case LOGICAL_AND, LOGICAL_OR ->
                leftType == BuiltInTypes.BOOLEAN
                    && rightType == BuiltInTypes.BOOLEAN
                    ? BuiltInTypes.BOOLEAN
                    : reportInvalidBinary(
                    expression,
                    leftType,
                    rightType,
                    diagnostics
                );
        };
    }

    private static boolean matchingNumericTypes(
        TypeSymbol leftType,
        TypeSymbol rightType
    ) {
        return leftType == rightType
            && leftType.isNumeric();
    }

    private static TypeSymbol reportInvalidUnary(
        UnaryExpression expression,
        TypeSymbol operandType,
        List<Diagnostic> diagnostics
    ) {
        diagnostics.add(new Diagnostic(
            INVALID_UNARY_OPERAND_CODE,
            DiagnosticSeverity.ERROR,
            "Unary operator '%s' is not defined for type '%s'."
                .formatted(
                    spelling(expression.operator()),
                    operandType.name()
                ),
            expression.span()
        ));

        return BuiltInTypes.ERROR;
    }

    private static TypeSymbol reportInvalidBinary(
        BinaryExpression expression,
        TypeSymbol leftType,
        TypeSymbol rightType,
        List<Diagnostic> diagnostics
    ) {
        diagnostics.add(new Diagnostic(
            INVALID_BINARY_OPERANDS_CODE,
            DiagnosticSeverity.ERROR,
            "Binary operator '%s' is not defined for types '%s' and '%s'."
                .formatted(
                    spelling(expression.operator()),
                    leftType.name(),
                    rightType.name()
                ),
            expression.span()
        ));

        return BuiltInTypes.ERROR;
    }

    private static String spelling(UnaryOperator operator) {
        return switch (operator) {
            case LOGICAL_NOT -> "!";
            case NEGATE -> "-";
            case POSITIVE -> "+";
        };
    }

    private static String spelling(BinaryOperator operator) {
        return switch (operator) {
            case MULTIPLY -> "*";
            case DIVIDE -> "/";
            case REMAINDER -> "%";
            case ADD -> "+";
            case SUBTRACT -> "-";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case EQUAL -> "==";
            case NOT_EQUAL -> "!=";
            case LOGICAL_AND -> "&&";
            case LOGICAL_OR -> "||";
        };
    }
}
