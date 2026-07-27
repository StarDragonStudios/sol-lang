package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBooleanConstant;
import io.github.stardragonstudios.sol.ir.IrCharConstant;
import io.github.stardragonstudios.sol.ir.IrFloatConstant;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrValue;
import io.github.stardragonstudios.sol.syntax.LiteralExpression;

import java.util.Objects;

final class IrLiteralLowerer {
    private IrLiteralLowerer() {}

    static IrValue lower(LiteralExpression expression, IrFunctionLoweringContext context) {
        Objects.requireNonNull(expression, "Lowered literal expression must not be null.");
        Objects.requireNonNull(context, "Function lowering context must not be null.");

        return switch (expression.kind()) {
            case INTEGER -> new IrIntConstant(context.nextValueId(), parseInteger(expression.lexeme()));
            case FLOAT -> new IrFloatConstant(context.nextValueId(), parseFloat(expression.lexeme()));
            case BOOLEAN -> new IrBooleanConstant(context.nextValueId(), parseBoolean(expression.lexeme()));
            case CHARACTER -> new IrCharConstant(context.nextValueId(), parseCharacter(expression.lexeme()));
            case STRING -> throw new IrLoweringException("String literals are not supported by the current Sol IR lowering subset.");
        };
    }

    private static long parseInteger(String lexeme) {
        try {
            return Long.parseLong(lexeme);
        } catch (NumberFormatException exception) {
            throw new IrLoweringException("Integer literal '%s' cannot be represented as a Sol int.".formatted(lexeme));
        }
    }

    private static double parseFloat(String lexeme) {
        final double value;

        try {
            value = Double.parseDouble(lexeme);
        } catch (NumberFormatException exception) {
            throw new IrLoweringException("Floating-point literal '%s' cannot be represented as a Sol float.".formatted(lexeme));
        }

        if (!Double.isFinite(value))
            throw new IrLoweringException("Floating-point literal '%s' cannot be represented as a finite Sol float.".formatted(lexeme));

        return value;
    }

    private static boolean parseBoolean(String lexeme) {
        return switch (lexeme) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IrLoweringException("Invalid boolean literal '%s' during IR lowering.".formatted(lexeme));
        };
    }

    private static int parseCharacter(String lexeme) {
        if (lexeme.length() < 3
            || lexeme.charAt(0) != '\''
            || lexeme.charAt(lexeme.length() - 1) != '\''
        ) throw invalidCharacter(lexeme);

        var content = lexeme.substring(1, lexeme.length() - 1);

        if (content.length() == 1) return content.charAt(0);
        if (content.length() != 2 || content.charAt(0) != '\\') throw invalidCharacter(lexeme);

        return switch (content.charAt(1)) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            default -> throw invalidCharacter(lexeme);
        };
    }

    private static IrLoweringException invalidCharacter(String lexeme) {
        return new IrLoweringException("Invalid character literal '%s' during IR lowering.".formatted(lexeme));
    }
}
