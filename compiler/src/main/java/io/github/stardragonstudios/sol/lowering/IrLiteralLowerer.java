package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.*;
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
            case STRING -> new IrStringConstant(context.nextValueId(), parseString(expression.lexeme()));
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

    private static String parseString(String lexeme) {
        if (lexeme.length() < 2 || lexeme.charAt(0) != '"' || lexeme.charAt(lexeme.length() - 1) != '"') throw invalidString(lexeme);

        var content = lexeme.substring(1, lexeme.length() - 1);
        var value = new StringBuilder(content.length());

        for (var index = 0; index < content.length(); index++) {
            var character = content.charAt(index);

            if (character != '\\') {
                value.append(character);

                continue;
            }

            if (++index >= content.length()) throw invalidString(lexeme);

            var escaped = content.charAt(index);

            value.append(
                switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '\\' -> '\\';
                    case '"' -> '"';
                    case '\'' -> '\'';

                    default -> throw invalidString(lexeme);
                }
            );
        }

        return value.toString();
    }

    private static IrLoweringException invalidCharacter(String lexeme) {
        return new IrLoweringException("Invalid character literal '%s' during IR lowering.".formatted(lexeme));
    }

    private static IrLoweringException invalidString(String lexeme) {
        return new IrLoweringException("Invalid string literal '%s' during IR lowering.".formatted(lexeme));
    }
}
