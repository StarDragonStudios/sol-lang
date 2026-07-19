package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.lexer.Token;
import io.github.stardragonstudios.sol.lexer.TokenKind;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {
    @Test
    void parsesEmptySourceFile() {
        var unit = Parser.parse(
            Lexer.scan("")
        );

        assertTrue(unit.declarations().isEmpty());

        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(0, 1, 1)
            ),
            unit.span()
        );
    }

    @Test
    void parsesSourceFileContainingOnlyNewlines() {
        var unit = Parser.parse(
            Lexer.scan("\n\r\n")
        );

        assertTrue(unit.declarations().isEmpty());

        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(3, 3, 1)
            ),
            unit.span()
        );
    }

    @Test
    void reportsUnexpectedTopLevelToken() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan("let")
            )
        );

        var diagnostic = exception.diagnostic();

        assertEquals("SOL-P001", diagnostic.code());
        assertEquals(
            DiagnosticSeverity.ERROR,
            diagnostic.severity()
        );
        assertEquals(
            "Unexpected token 'let' at top level.",
            diagnostic.message()
        );
        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(3, 1, 4)
            ),
            diagnostic.span()
        );
    }

    @Test
    void reportsTokenAfterLeadingNewlines() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan("\n\nvalue")
            )
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(2, 3, 1),
                new SourcePosition(7, 3, 6)
            ),
            exception.diagnostic().span()
        );
    }

    @Test
    void rejectsNullTokenStream() {
        assertThrows(
            NullPointerException.class,
            () -> Parser.parse(null)
        );
    }

    @Test
    void rejectsEmptyTokenStream() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Parser.parse(List.of())
        );
    }

    @Test
    void rejectsTokenStreamWithoutEof() {
        var span = new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(5, 1, 6)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> Parser.parse(
                List.of(
                    new Token(
                        TokenKind.IDENTIFIER,
                        "value",
                        span
                    )
                )
            )
        );
    }

    @Test
    void rejectsEofBeforeEndOfTokenStream() {
        var position = new SourcePosition(0, 1, 1);
        var emptySpan = new SourceSpan(
            position,
            position
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> Parser.parse(
                List.of(
                    new Token(
                        TokenKind.EOF,
                        "",
                        emptySpan
                    ),
                    new Token(
                        TokenKind.EOF,
                        "",
                        emptySpan
                    )
                )
            )
        );
    }

    @Test
    void parsesBasicFunctionDeclaration() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn initialize() -> void\nend"
            )
        );

        assertEquals(1, unit.declarations().size());

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals("initialize", function.name());
        assertTrue(function.parameters().isEmpty());
        assertEquals("void", function.returnType().name());
        assertTrue(function.body().statements().isEmpty());

        assertEquals(
            new SourceSpan(
                new SourcePosition(19, 1, 20),
                new SourcePosition(23, 1, 24)
            ),
            function.returnType().span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(24, 2, 1),
                new SourcePosition(27, 2, 4)
            ),
            function.body().span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(27, 2, 4)
            ),
            function.span()
        );
    }

    @Test
    void acceptsBlankLinesInsideFunctionBody() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn calculate() -> int\n\n\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertTrue(function.body().statements().isEmpty());

        assertEquals(
            new SourceSpan(
                new SourcePosition(22, 2, 1),
                new SourcePosition(27, 4, 4)
            ),
            function.body().span()
        );
    }

    @Test
    void parsesMultipleFunctionDeclarations() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn first() -> void
                end

                fn second() -> int
                end
                """
            )
        );

        assertEquals(2, unit.declarations().size());

        var first = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().get(0)
        );

        var second = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().get(1)
        );

        assertEquals("first", first.name());
        assertEquals("second", second.name());
    }

    @Test
    void reportsMissingFunctionName() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan("fn () -> void\nend")
            )
        );

        var diagnostic = exception.diagnostic();

        assertEquals("SOL-P002", diagnostic.code());
        assertEquals(
            DiagnosticSeverity.ERROR,
            diagnostic.severity()
        );
        assertEquals(
            "Expected a function name after 'fn', but found '('.",
            diagnostic.message()
        );
        assertEquals(
            new SourceSpan(
                new SourcePosition(3, 1, 4),
                new SourcePosition(4, 1, 5)
            ),
            diagnostic.span()
        );
    }

    @Test
    void reportsMissingHeaderNewline() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn initialize() -> void end"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected a newline after the function declaration header, "
                + "but found 'end'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingFunctionEnd() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn initialize() -> void\n"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected 'end' to close the function declaration, "
                + "but found end of file.",
            exception.diagnostic().message()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(24, 2, 1),
                new SourcePosition(24, 2, 1)
            ),
            exception.diagnostic().span()
        );
    }

    @Test
    void parsesSingleFunctionParameter() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn print_value(value: int) -> void\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals(1, function.parameters().size());

        var parameter = function.parameters().getFirst();

        assertEquals("value", parameter.name());
        assertEquals("int", parameter.type().name());
    }

    @Test
    void parsesMultipleFunctionParametersAndSpans() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn add(left: int, right: int) -> int\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals(2, function.parameters().size());

        var left = function.parameters().get(0);
        var right = function.parameters().get(1);

        assertEquals("left", left.name());
        assertEquals("int", left.type().name());

        assertEquals(
            new SourceSpan(
                new SourcePosition(7, 1, 8),
                new SourcePosition(16, 1, 17)
            ),
            left.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(13, 1, 14),
                new SourcePosition(16, 1, 17)
            ),
            left.type().span()
        );

        assertEquals("right", right.name());
        assertEquals("int", right.type().name());

        assertEquals(
            new SourceSpan(
                new SourcePosition(18, 1, 19),
                new SourcePosition(28, 1, 29)
            ),
            right.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(25, 1, 26),
                new SourcePosition(28, 1, 29)
            ),
            right.type().span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(40, 2, 4)
            ),
            function.span()
        );
    }

    @Test
    void reportsMissingParameterName() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn print(: int) -> void\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected a parameter name, but found ':'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingParameterColon() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn print(value int) -> void\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected ':' after the parameter name, "
                + "but found 'int'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingParameterType() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn print(value:) -> void\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected a parameter type after ':', "
                + "but found ')'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void rejectsTrailingParameterComma() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn add(left: int,) -> int\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected a parameter after ',', "
                + "but found ')'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingParameterComma() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn add(left: int right: int) -> int\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected ')' after the function parameter list, "
                + "but found 'right'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void parsesReturnStatementWithLiteralAndSpans() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn answer() -> int\n    return 42\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals(1, function.body().statements().size());

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var expression = assertInstanceOf(
            LiteralExpression.class,
            statement.expression().orElseThrow()
        );

        assertEquals(
            LiteralKind.INTEGER,
            expression.kind()
        );
        assertEquals("42", expression.lexeme());

        assertEquals(
            new SourceSpan(
                new SourcePosition(30, 2, 12),
                new SourcePosition(32, 2, 14)
            ),
            expression.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(23, 2, 5),
                new SourcePosition(32, 2, 14)
            ),
            statement.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(19, 2, 1),
                new SourcePosition(36, 3, 4)
            ),
            function.body().span()
        );
    }

    @Test
    void parsesBareReturnStatement() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn finish() -> void\n    return\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        assertTrue(statement.expression().isEmpty());

        assertEquals(
            new SourceSpan(
                new SourcePosition(24, 2, 5),
                new SourcePosition(30, 2, 11)
            ),
            statement.span()
        );
    }

    @Test
    void parsesEverySupportedLiteralKind() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn values() -> void
                    return 42
                    return 3.14
                    return true
                    return 'a'
                    return "hello"
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals(5, function.body().statements().size());

        var expectedKinds = List.of(
            LiteralKind.INTEGER,
            LiteralKind.FLOAT,
            LiteralKind.BOOLEAN,
            LiteralKind.CHARACTER,
            LiteralKind.STRING
        );

        var expectedLexemes = List.of(
            "42",
            "3.14",
            "true",
            "'a'",
            "\"hello\""
        );

        for (
            var index = 0;
            index < expectedKinds.size();
            index++
        ) {
            var statement = assertInstanceOf(
                ReturnStatement.class,
                function.body().statements().get(index)
            );

            var expression = assertInstanceOf(
                LiteralExpression.class,
                statement.expression().orElseThrow()
            );

            assertEquals(
                expectedKinds.get(index),
                expression.kind()
            );

            assertEquals(
                expectedLexemes.get(index),
                expression.lexeme()
            );
        }
    }

    @Test
    void acceptsBlankLinesBetweenReturnStatements() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn values() -> int

                    return 1


                    return 2

                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals(2, function.body().statements().size());
    }

    @Test
    void acceptsBareReturnImmediatelyBeforeEnd() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn finish() -> void\nreturn end"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        assertTrue(statement.expression().isEmpty());
    }

    @Test
    void parsesNameExpressionInReturnStatement() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn value\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var expression = assertInstanceOf(
            NameExpression.class,
            statement.expression().orElseThrow()
        );

        assertEquals("value", expression.name());

        assertEquals(
            new SourceSpan(
                new SourcePosition(25, 2, 8),
                new SourcePosition(30, 2, 13)
            ),
            expression.span()
        );
    }

    @Test
    void reportsTokensAfterReturnLiteral() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\nreturn 42 43\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected a newline or 'end' after the statement, "
                + "but found '43'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsUnsupportedFunctionBodyStatement() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\n42\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected a statement, but found '42'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void parsesParenthesizedExpressionAndSpans() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn (42)\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var parenthesized = assertInstanceOf(
            ParenthesizedExpression.class,
            statement.expression().orElseThrow()
        );

        var literal = assertInstanceOf(
            LiteralExpression.class,
            parenthesized.expression()
        );

        assertEquals("42", literal.lexeme());

        assertEquals(
            new SourceSpan(
                new SourcePosition(26, 2, 9),
                new SourcePosition(28, 2, 11)
            ),
            literal.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(25, 2, 8),
                new SourcePosition(29, 2, 12)
            ),
            parenthesized.span()
        );
    }

    @Test
    void parsesEveryUnaryOperator() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn values() -> void
                    return !enabled
                    return -42
                    return +3.14
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var expectedOperators = List.of(
            UnaryOperator.LOGICAL_NOT,
            UnaryOperator.NEGATE,
            UnaryOperator.POSITIVE
        );

        for (
            var index = 0;
            index < expectedOperators.size();
            index++
        ) {
            var statement = assertInstanceOf(
                ReturnStatement.class,
                function.body().statements().get(index)
            );

            var expression = assertInstanceOf(
                UnaryExpression.class,
                statement.expression().orElseThrow()
            );

            assertEquals(
                expectedOperators.get(index),
                expression.operator()
            );
        }
    }

    @Test
    void parsesRecursiveUnaryExpressionsRightToLeft() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> boolean\nreturn !!enabled\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var outer = assertInstanceOf(
            UnaryExpression.class,
            statement.expression().orElseThrow()
        );

        var inner = assertInstanceOf(
            UnaryExpression.class,
            outer.operand()
        );

        var name = assertInstanceOf(
            NameExpression.class,
            inner.operand()
        );

        assertEquals(
            UnaryOperator.LOGICAL_NOT,
            outer.operator()
        );
        assertEquals(
            UnaryOperator.LOGICAL_NOT,
            inner.operator()
        );
        assertEquals("enabled", name.name());

        assertEquals(
            new SourceSpan(
                new SourcePosition(29, 2, 8),
                new SourcePosition(38, 2, 17)
            ),
            outer.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(30, 2, 9),
                new SourcePosition(38, 2, 17)
            ),
            inner.span()
        );
    }

    @Test
    void parsesUnaryParenthesizedExpression() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn -(value)\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var unary = assertInstanceOf(
            UnaryExpression.class,
            statement.expression().orElseThrow()
        );

        var parenthesized = assertInstanceOf(
            ParenthesizedExpression.class,
            unary.operand()
        );

        var name = assertInstanceOf(
            NameExpression.class,
            parenthesized.expression()
        );

        assertEquals(UnaryOperator.NEGATE, unary.operator());
        assertEquals("value", name.name());

        assertEquals(
            new SourceSpan(
                new SourcePosition(25, 2, 8),
                new SourcePosition(33, 2, 16)
            ),
            unary.span()
        );
    }

    @Test
    void reportsMissingUnaryOperand() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> boolean\nreturn !\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected an expression, but found newline.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsEmptyParenthesizedExpression() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\nreturn ()\nend"
                )
            )
        );

        assertEquals(
            "Expected an expression, but found ')'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingClosingParenthesis() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\nreturn (42\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected ')' after the parenthesized expression, "
                + "but found newline.",
            exception.diagnostic().message()
        );
    }

    @Test
    void respectsMultiplicativePrecedenceOverAddition() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn 2 + 3 * 4\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var addition = assertInstanceOf(
            BinaryExpression.class,
            statement.expression().orElseThrow()
        );

        assertEquals(
            BinaryOperator.ADD,
            addition.operator()
        );

        var left = assertInstanceOf(
            LiteralExpression.class,
            addition.left()
        );

        var multiplication = assertInstanceOf(
            BinaryExpression.class,
            addition.right()
        );

        assertEquals("2", left.lexeme());

        assertEquals(
            BinaryOperator.MULTIPLY,
            multiplication.operator()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(25, 2, 8),
                new SourcePosition(34, 2, 17)
            ),
            addition.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(29, 2, 12),
                new SourcePosition(34, 2, 17)
            ),
            multiplication.span()
        );
    }

    @Test
    void associatesBinaryOperatorsFromLeftToRight() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn 10 - 3 - 2\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var outer = assertInstanceOf(
            BinaryExpression.class,
            statement.expression().orElseThrow()
        );

        var inner = assertInstanceOf(
            BinaryExpression.class,
            outer.left()
        );

        assertEquals(
            BinaryOperator.SUBTRACT,
            outer.operator()
        );

        assertEquals(
            BinaryOperator.SUBTRACT,
            inner.operator()
        );

        var finalOperand = assertInstanceOf(
            LiteralExpression.class,
            outer.right()
        );

        assertEquals("2", finalOperand.lexeme());
    }

    @Test
    void parenthesesOverrideBinaryPrecedence() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn (2 + 3) * 4\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var multiplication = assertInstanceOf(
            BinaryExpression.class,
            statement.expression().orElseThrow()
        );

        assertEquals(
            BinaryOperator.MULTIPLY,
            multiplication.operator()
        );

        var parenthesized = assertInstanceOf(
            ParenthesizedExpression.class,
            multiplication.left()
        );

        var addition = assertInstanceOf(
            BinaryExpression.class,
            parenthesized.expression()
        );

        assertEquals(
            BinaryOperator.ADD,
            addition.operator()
        );
    }

    @Test
    void unaryExpressionsHaveHigherPrecedenceThanBinaryOperators() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn -value * 2\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var multiplication = assertInstanceOf(
            BinaryExpression.class,
            statement.expression().orElseThrow()
        );

        assertEquals(
            BinaryOperator.MULTIPLY,
            multiplication.operator()
        );

        var unary = assertInstanceOf(
            UnaryExpression.class,
            multiplication.left()
        );

        assertEquals(
            UnaryOperator.NEGATE,
            unary.operator()
        );
    }

    @Test
    void respectsLogicalOperatorPrecedence() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn allowed() -> boolean
                    return !enabled || ready && authorized
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var logicalOr = assertInstanceOf(
            BinaryExpression.class,
            statement.expression().orElseThrow()
        );

        assertEquals(
            BinaryOperator.LOGICAL_OR,
            logicalOr.operator()
        );

        var logicalNot = assertInstanceOf(
            UnaryExpression.class,
            logicalOr.left()
        );

        assertEquals(
            UnaryOperator.LOGICAL_NOT,
            logicalNot.operator()
        );

        var logicalAnd = assertInstanceOf(
            BinaryExpression.class,
            logicalOr.right()
        );

        assertEquals(
            BinaryOperator.LOGICAL_AND,
            logicalAnd.operator()
        );
    }

    @Test
    void parsesEverySupportedBinaryOperator() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn operators() -> void
                    return 2 * 3
                    return 6 / 2
                    return 7 % 3
                    return 2 + 3
                    return 5 - 2
                    return 1 < 2
                    return 1 <= 2
                    return 2 > 1
                    return 2 >= 1
                    return 1 == 1
                    return 1 != 2
                    return true && false
                    return true || false
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var expectedOperators = List.of(
            BinaryOperator.MULTIPLY,
            BinaryOperator.DIVIDE,
            BinaryOperator.REMAINDER,
            BinaryOperator.ADD,
            BinaryOperator.SUBTRACT,
            BinaryOperator.LESS_THAN,
            BinaryOperator.LESS_THAN_OR_EQUAL,
            BinaryOperator.GREATER_THAN,
            BinaryOperator.GREATER_THAN_OR_EQUAL,
            BinaryOperator.EQUAL,
            BinaryOperator.NOT_EQUAL,
            BinaryOperator.LOGICAL_AND,
            BinaryOperator.LOGICAL_OR
        );

        assertEquals(
            expectedOperators.size(),
            function.body().statements().size()
        );

        for (
            var index = 0;
            index < expectedOperators.size();
            index++
        ) {
            var statement = assertInstanceOf(
                ReturnStatement.class,
                function.body().statements().get(index)
            );

            var expression = assertInstanceOf(
                BinaryExpression.class,
                statement.expression().orElseThrow()
            );

            assertEquals(
                expectedOperators.get(index),
                expression.operator()
            );
        }
    }

    @Test
    void reportsMissingBinaryRightOperand() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\nreturn 1 +\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected an expression, but found newline.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingBinaryLeftOperand() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> boolean\nreturn || true\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected an expression, but found '||'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void parsesFunctionCallWithoutArguments() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn initialize()\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var call = assertInstanceOf(
            CallExpression.class,
            statement.expression().orElseThrow()
        );

        var callee = assertInstanceOf(
            NameExpression.class,
            call.callee()
        );

        assertEquals("initialize", callee.name());
        assertTrue(call.arguments().isEmpty());
    }

    @Test
    void parsesCallArgumentsAndSourceSpans() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn add(1, 2 + 3)\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var call = assertInstanceOf(
            CallExpression.class,
            statement.expression().orElseThrow()
        );

        var callee = assertInstanceOf(
            NameExpression.class,
            call.callee()
        );

        assertEquals("add", callee.name());
        assertEquals(2, call.arguments().size());

        var firstArgument = assertInstanceOf(
            LiteralExpression.class,
            call.arguments().get(0)
        );

        var secondArgument = assertInstanceOf(
            BinaryExpression.class,
            call.arguments().get(1)
        );

        assertEquals("1", firstArgument.lexeme());
        assertEquals(
            BinaryOperator.ADD,
            secondArgument.operator()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(25, 2, 8),
                new SourcePosition(38, 2, 21)
            ),
            call.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(29, 2, 12),
                new SourcePosition(30, 2, 13)
            ),
            firstArgument.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(32, 2, 15),
                new SourcePosition(37, 2, 20)
            ),
            secondArgument.span()
        );
    }

    @Test
    void parsesNestedFunctionCalls() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn outer(inner(42))\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var outerCall = assertInstanceOf(
            CallExpression.class,
            statement.expression().orElseThrow()
        );

        var outerName = assertInstanceOf(
            NameExpression.class,
            outerCall.callee()
        );

        var innerCall = assertInstanceOf(
            CallExpression.class,
            outerCall.arguments().getFirst()
        );

        var innerName = assertInstanceOf(
            NameExpression.class,
            innerCall.callee()
        );

        var argument = assertInstanceOf(
            LiteralExpression.class,
            innerCall.arguments().getFirst()
        );

        assertEquals("outer", outerName.name());
        assertEquals("inner", innerName.name());
        assertEquals("42", argument.lexeme());
    }

    @Test
    void parsesRepeatedPostfixCalls() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn factory()()\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var outerCall = assertInstanceOf(
            CallExpression.class,
            statement.expression().orElseThrow()
        );

        var innerCall = assertInstanceOf(
            CallExpression.class,
            outerCall.callee()
        );

        var callee = assertInstanceOf(
            NameExpression.class,
            innerCall.callee()
        );

        assertEquals("factory", callee.name());
        assertTrue(innerCall.arguments().isEmpty());
        assertTrue(outerCall.arguments().isEmpty());

        assertEquals(
            new SourceSpan(
                new SourcePosition(25, 2, 8),
                new SourcePosition(34, 2, 17)
            ),
            innerCall.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(25, 2, 8),
                new SourcePosition(36, 2, 19)
            ),
            outerCall.span()
        );
    }

    @Test
    void parsesCallOnParenthesizedExpression() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn (factory)()\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var call = assertInstanceOf(
            CallExpression.class,
            statement.expression().orElseThrow()
        );

        var parenthesized = assertInstanceOf(
            ParenthesizedExpression.class,
            call.callee()
        );

        var name = assertInstanceOf(
            NameExpression.class,
            parenthesized.expression()
        );

        assertEquals("factory", name.name());
        assertTrue(call.arguments().isEmpty());
    }

    @Test
    void callsHaveHigherPrecedenceThanUnaryAndBinaryOperators() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\nreturn -calculate(1) * 2\nend"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statement = assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().getFirst()
        );

        var multiplication = assertInstanceOf(
            BinaryExpression.class,
            statement.expression().orElseThrow()
        );

        var unary = assertInstanceOf(
            UnaryExpression.class,
            multiplication.left()
        );

        var call = assertInstanceOf(
            CallExpression.class,
            unary.operand()
        );

        assertEquals(
            BinaryOperator.MULTIPLY,
            multiplication.operator()
        );

        assertEquals(
            UnaryOperator.NEGATE,
            unary.operator()
        );

        assertEquals(1, call.arguments().size());
    }

    @Test
    void rejectsTrailingCallArgumentComma() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\nreturn add(1,)\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected an argument after ',', "
                + "but found ')'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingCallArgumentComma() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\nreturn add(1 2)\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected ')' after the function call arguments, "
                + "but found '2'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingCallClosingParenthesis() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\nreturn add(1\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected ')' after the function call arguments, "
                + "but found newline.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingCallArgument() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    "fn value() -> int\nreturn add(, 1)\nend"
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected an expression, but found ','.",
            exception.diagnostic().message()
        );
    }

    @Test
    void parsesEveryVariableDeclarationKind() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn values() -> void
                    const answer: int = 42
                    let enabled: boolean = true
                    @mut let counter: int = 0
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals(
            3,
            function.body().statements().size()
        );

        var constant = assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().get(0)
        );

        var variable = assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().get(1)
        );

        var mutableVariable = assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().get(2)
        );

        assertEquals(
            VariableDeclarationKind.CONST,
            constant.kind()
        );

        assertEquals(
            VariableDeclarationKind.LET,
            variable.kind()
        );

        assertEquals(
            VariableDeclarationKind.MUTABLE_LET,
            mutableVariable.kind()
        );

        assertEquals("answer", constant.name());
        assertEquals("enabled", variable.name());
        assertEquals("counter", mutableVariable.name());
    }

    @Test
    void parsesCompleteExpressionAsVariableInitializer() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn value() -> int
                    let result: int = calculate(1 + 2)
                    return result
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().getFirst()
        );

        var call = assertInstanceOf(
            CallExpression.class,
            declaration.initializer()
        );

        var callee = assertInstanceOf(
            NameExpression.class,
            call.callee()
        );

        var argument = assertInstanceOf(
            BinaryExpression.class,
            call.arguments().getFirst()
        );

        assertEquals("result", declaration.name());
        assertEquals("int", declaration.type().name());
        assertEquals("calculate", callee.name());
        assertEquals(
            BinaryOperator.ADD,
            argument.operator()
        );
    }

    @Test
    void preservesMutableVariableDeclarationSpans() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn value() -> int
                    @mut let result: int = 1 + 2
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().getFirst()
        );

        var initializer = assertInstanceOf(
            BinaryExpression.class,
            declaration.initializer()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(39, 2, 22),
                new SourcePosition(42, 2, 25)
            ),
            declaration.type().span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(45, 2, 28),
                new SourcePosition(50, 2, 33)
            ),
            initializer.span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(22, 2, 5),
                new SourcePosition(50, 2, 33)
            ),
            declaration.span()
        );
    }

    @Test
    void parsesVariableDeclarationsAlongsideReturnStatements() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn calculate() -> int
                    const base: int = 40
                    let increment: int = 2
                    @mut let result: int = base + increment

                    return result
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals(
            4,
            function.body().statements().size()
        );

        assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().get(0)
        );

        assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().get(1)
        );

        assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().get(2)
        );

        assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().get(3)
        );
    }

    @Test
    void rejectsUnsupportedVariableModifier() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn value() -> int
                        @other let value: int = 1
                    end
                    """
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected 'mut' after '@', "
                + "but found 'other'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingLetAfterMutableModifier() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn value() -> int
                        @mut value: int = 1
                    end
                    """
                )
            )
        );

        assertEquals(
            "Expected 'let' after '@mut', "
                + "but found 'value'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingVariableName() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn value() -> int
                        let : int = 1
                    end
                    """
                )
            )
        );

        assertEquals(
            "Expected a variable name, but found ':'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingVariableColon() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn value() -> int
                        let value int = 1
                    end
                    """
                )
            )
        );

        assertEquals(
            "Expected ':' after the variable name, "
                + "but found 'int'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingVariableType() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn value() -> int
                        let value: = 1
                    end
                    """
                )
            )
        );

        assertEquals(
            "Expected a variable type after ':', "
                + "but found '='.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingVariableAssignmentOperator() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn value() -> int
                        let value: int 1
                    end
                    """
                )
            )
        );

        assertEquals(
            "Expected '=' after the variable type, "
                + "but found '1'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingVariableInitializer() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn value() -> int
                        let value: int =
                    end
                    """
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected an expression, but found newline.",
            exception.diagnostic().message()
        );
    }

    @Test
    void parsesLiteralAssignmentStatement() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn update() -> void
                    counter = 42
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var assignment = assertInstanceOf(
            AssignmentStatement.class,
            function.body().statements().getFirst()
        );

        assertEquals(
            "counter",
            assignment.target().name()
        );

        var value = assertInstanceOf(
            LiteralExpression.class,
            assignment.value()
        );

        assertEquals(LiteralKind.INTEGER, value.kind());
        assertEquals("42", value.lexeme());
    }

    @Test
    void parsesCompleteExpressionAsAssignedValue() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn update() -> void
                    counter = counter + 1 * 2
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var assignment = assertInstanceOf(
            AssignmentStatement.class,
            function.body().statements().getFirst()
        );

        var addition = assertInstanceOf(
            BinaryExpression.class,
            assignment.value()
        );

        var multiplication = assertInstanceOf(
            BinaryExpression.class,
            addition.right()
        );

        assertEquals(
            BinaryOperator.ADD,
            addition.operator()
        );

        assertEquals(
            BinaryOperator.MULTIPLY,
            multiplication.operator()
        );
    }

    @Test
    void parsesFunctionCallAsAssignedValue() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn update() -> void
                    result = calculate(left, right)
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var assignment = assertInstanceOf(
            AssignmentStatement.class,
            function.body().statements().getFirst()
        );

        var call = assertInstanceOf(
            CallExpression.class,
            assignment.value()
        );

        var callee = assertInstanceOf(
            NameExpression.class,
            call.callee()
        );

        assertEquals("result", assignment.target().name());
        assertEquals("calculate", callee.name());
        assertEquals(2, call.arguments().size());
    }

    @Test
    void parsesAssignmentsAlongsideOtherStatements() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn calculate() -> int
                    @mut let result: int = 0
                    result = 40 + 2
                    return result
                end
                """
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        assertEquals(
            3,
            function.body().statements().size()
        );

        assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body().statements().get(0)
        );

        assertInstanceOf(
            AssignmentStatement.class,
            function.body().statements().get(1)
        );

        assertInstanceOf(
            ReturnStatement.class,
            function.body().statements().get(2)
        );
    }

    @Test
    void preservesAssignmentSourceSpans() {
        var unit = Parser.parse(
            Lexer.scan(
                "fn value() -> int\n"
                    + "counter = calculate(1 + 2)\n"
                    + "end"
            )
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var assignment = assertInstanceOf(
            AssignmentStatement.class,
            function.body().statements().getFirst()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(18, 2, 1),
                new SourcePosition(25, 2, 8)
            ),
            assignment.target().span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(28, 2, 11),
                new SourcePosition(44, 2, 27)
            ),
            assignment.value().span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(18, 2, 1),
                new SourcePosition(44, 2, 27)
            ),
            assignment.span()
        );
    }

    @Test
    void reportsMissingAssignmentOperator() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn update() -> void
                        counter 42
                    end
                    """
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected '=' after the assignment target, "
                + "but found '42'.",
            exception.diagnostic().message()
        );
    }

    @Test
    void reportsMissingAssignedValue() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn update() -> void
                        counter =
                    end
                    """
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected an expression, but found newline.",
            exception.diagnostic().message()
        );
    }

    @Test
    void rejectsFunctionCallsUsedAsStatements() {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(
                    """
                    fn update() -> void
                        update_value()
                    end
                    """
                )
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );

        assertEquals(
            "Expected '=' after the assignment target, "
                + "but found '('.",
            exception.diagnostic().message()
        );
    }
}
