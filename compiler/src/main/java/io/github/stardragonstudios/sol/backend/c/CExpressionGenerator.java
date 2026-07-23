package io.github.stardragonstudios.sol.backend.c;

import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.ParameterSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.BinaryExpression;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.Expression;
import io.github.stardragonstudios.sol.syntax.LiteralExpression;
import io.github.stardragonstudios.sol.syntax.LiteralKind;
import io.github.stardragonstudios.sol.syntax.NameExpression;
import io.github.stardragonstudios.sol.syntax.ParenthesizedExpression;
import io.github.stardragonstudios.sol.syntax.QualifiedNameExpression;
import io.github.stardragonstudios.sol.syntax.UnaryExpression;

import java.util.Objects;

final class CExpressionGenerator {
    private final SemanticModel model;
    private final ModuleName moduleName;
    private final FunctionSymbol function;
    private final CParameterNameTable parameterNames;

    CExpressionGenerator(SemanticModel model, ModuleName moduleName, FunctionSymbol function, CParameterNameTable parameterNames) {
        this.model = Objects.requireNonNull(model, "Expression semantic model must not be null.");
        this.moduleName = Objects.requireNonNull(moduleName, "Expression module must not be null.");
        this.function = Objects.requireNonNull(function, "Expression function must not be null.");
        this.parameterNames = Objects.requireNonNull(parameterNames, "Expression parameter names must not be null.");
    }

    String generate(Expression expression) {
        Objects.requireNonNull(expression, "Generated expression must not be null.");

        if (expression instanceof CallExpression || expression instanceof QualifiedNameExpression)
            throw unsupportedExpression(expression);

        if (
            expression instanceof LiteralExpression literal
                && literal.kind()
                == LiteralKind.STRING
        ) throw unsupportedExpression(expression);

        var type = model.typeOf(expression).orElseThrow(
            () -> new IllegalStateException(
                "Semantic model has no type for %s in function '%s' of module '%s'."
                    .formatted(
                        expression.getClass()
                            .getSimpleName(),
                        function.name(),
                        moduleName.qualifiedName()
                    )
            )
        );

        CTypeGenerator.requireSupportedValueType(type, moduleName, function);

         return switch (expression) {
            case LiteralExpression literal -> generateLiteral(literal);
            case NameExpression name -> generateName(name);
            case ParenthesizedExpression parenthesized -> "(" + generate(parenthesized.expression()) + ")";
            case UnaryExpression unary -> generateUnary(unary);
            case BinaryExpression binary -> generateBinary(binary);
            default -> throw unsupportedExpression(expression);
        };

    }

    private String generateLiteral(LiteralExpression literal) {
        return switch (literal.kind()) {
            case INTEGER -> "((sol_int)%s)".formatted(literal.lexeme());
            case FLOAT -> "((sol_float)%s)".formatted(literal.lexeme());
            case BOOLEAN ->
                switch (literal.lexeme()) {
                    case "true" -> "((sol_boolean)1)";
                    case "false" -> "((sol_boolean)0)";
                    default -> throw new IllegalStateException(
                        "Unexpected boolean literal lexeme '%s'.".formatted(literal.lexeme())
                    );
                };

            case CHARACTER -> "((sol_char)%s)".formatted(literal.lexeme());
            case STRING -> throw unsupportedExpression(literal);
        };
    }

    private String generateName(NameExpression expression) {
        var symbol = model.symbolOf(expression).orElseThrow(
            () -> new IllegalStateException(
                "Semantic model has no symbol for name '%s' in function '%s' of module '%s'."
                    .formatted(expression.name(), function.name(), moduleName.qualifiedName())
            )
        );

        if (symbol instanceof ParameterSymbol parameter) return parameterNames.nameOf(parameter);

        throw new CCodeGenerationException(
            CCodeGenerationException.Reason.UNSUPPORTED_EXPRESSION,
            "Cannot generate NameExpression in function '%s' of module '%s': unsupported symbol kind '%s'."
                .formatted(function.name(), moduleName.qualifiedName(), symbol.kind())
        );
    }

    private String generateUnary(UnaryExpression expression) {
        var operator = switch (expression.operator()) {
                case LOGICAL_NOT -> "!";
                case NEGATE -> "-";
                case POSITIVE -> "+";
        };

        return "(%s%s)".formatted(operator, generate(expression.operand()));
    }

    private String generateBinary(BinaryExpression expression) {
        var operator = switch (expression.operator()) {
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

        return "(%s %s %s)".formatted(generate(expression.left()), operator, generate(expression.right()));
    }

    private CCodeGenerationException unsupportedExpression(Expression expression) {
        var description = expression instanceof LiteralExpression literal
                ? "LiteralExpression(%s)".formatted(literal.kind())
                : expression.getClass().getSimpleName();

        return new CCodeGenerationException(
            CCodeGenerationException.Reason.UNSUPPORTED_EXPRESSION,
            "Cannot generate %s in function '%s' of module '%s'."
                .formatted(description, function.name(), moduleName.qualifiedName())
        );
    }
}
