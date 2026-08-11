package io.github.stardragonstudios.sol.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SyntaxExpressions {
    private SyntaxExpressions() {}

    public static List<Expression> in(Block block) {
        Objects.requireNonNull(block, "Scanned syntax block must not be null.");

        var expressions = new ArrayList<Expression>();

        collect(block, expressions);

        return List.copyOf(expressions);
    }

    private static void collect(Block block, List<Expression> expressions) {
        for (var statement : block.statements()) collect(statement, expressions);
    }

    private static void collect(Statement statement, List<Expression> expressions) {
        switch (statement) {
            case ReturnStatement returned -> returned.expression().ifPresent(expression -> collect(expression, expressions));
            case VariableDeclarationStatement declaration -> collect(declaration.initializer(), expressions);
            case AssignmentStatement assignment -> collect(assignment.value(), expressions);
            case FieldAssignmentStatement assignment -> {
                collect(assignment.target(), expressions);
                collect(assignment.value(), expressions);
            }
            case PointerAssignmentStatement assignment -> {
                collect(assignment.target(), expressions);
                collect(assignment.value(), expressions);
            }
            case CallStatement call -> collect(call.call(), expressions);
            case ConditionalStatement conditional -> {
                collect(conditional.condition(), expressions);
                collect(conditional.thenBlock(), expressions);
                conditional.elseBlock().ifPresent(block -> collect(block, expressions));
            }
            case WhileStatement loop -> {
                collect(loop.condition(), expressions);
                collect(loop.body(), expressions);
            }
            default -> throw new IllegalArgumentException(
                "Unsupported statement syntax '%s' during expression traversal."
                    .formatted(statement.getClass().getSimpleName())
            );
        }
    }

    private static void collect(Expression expression, List<Expression> expressions) {
        expressions.add(expression);

        switch (expression) {
            case BinaryExpression binary -> {
                collect(binary.left(), expressions);
                collect(binary.right(), expressions);
            }
            case UnaryExpression unary -> collect(unary.operand(), expressions);
            case ParenthesizedExpression parenthesized -> collect(parenthesized.expression(), expressions);
            case FieldAccessExpression access -> collect(access.target(), expressions);
            case PointerDereferenceExpression dereference -> collect(dereference.pointer(), expressions);
            case PointerIndexExpression index -> {
                collect(index.pointer(), expressions);
                collect(index.index(), expressions);
            }
            case StructConstructionExpression construction -> {
                for (var field : construction.fields()) collect(field.value(), expressions);
            }
            case CallExpression call -> {
                collect(call.callee(), expressions);
                for (var argument : call.arguments()) collect(argument, expressions);
            }
            case LiteralExpression ignored -> {}
            case NullExpression ignored -> {}
            case NameExpression ignored -> {}
            case QualifiedNameExpression qualified -> {
                collect(qualified.qualifier(), expressions);
                collect(qualified.member(), expressions);
            }
            default -> throw new IllegalArgumentException(
                "Unsupported expression syntax '%s' during expression traversal."
                    .formatted(expression.getClass().getSimpleName())
            );
        }
    }
}
