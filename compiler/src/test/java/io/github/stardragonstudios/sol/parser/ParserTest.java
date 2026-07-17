package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.lexer.Token;
import io.github.stardragonstudios.sol.lexer.TokenKind;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

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
}
