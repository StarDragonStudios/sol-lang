package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallArgumentListParserTest {
    @Test
    void acceptsSingleLineArguments() {
        var call = parseReturnedCall(
            """
            fn test() -> int
                return combine(1, 2, true)
            end
            """
        );

        assertEquals(3, call.arguments().size());
    }

    @Test
    void acceptsMultilineArguments() {
        var call = parseReturnedCall(
            """
            fn test() -> int
                return combine(
                    1,
                    2,
                    true
                )
            end
            """
        );

        assertEquals(3, call.arguments().size());
    }

    @Test
    void acceptsEmptyMultilineArgumentList() {
        var call = parseReturnedCall(
            """
            fn test() -> int
                return empty(
                )
            end
            """
        );

        assertTrue(call.arguments().isEmpty());
    }

    @Test
    void acceptsNewlineImmediatelyAfterOpeningParenthesis() {
        var call = parseReturnedCall(
            """
            fn test() -> int
                return identity(
                    1)
            end
            """
        );

        assertEquals(1, call.arguments().size());
    }

    @Test
    void acceptsNewlineImmediatelyBeforeClosingParenthesis() {
        var call = parseReturnedCall(
            """
            fn test() -> int
                return identity(1
                )
            end
            """
        );

        assertEquals(1, call.arguments().size());
    }

    @Test
    void acceptsBlankLinesInsideArgumentList() {
        var call = parseReturnedCall(
            """
            fn test() -> int
                return combine(

                    1,

                    2,

                )
            end
            """
        );

        assertEquals(2, call.arguments().size());
    }

    @Test
    void acceptsSingleLineTrailingArgumentComma() {
        var call = parseReturnedCall(
            """
            fn test() -> int
                return combine(1, 2,)
            end
            """
        );

        assertEquals(2, call.arguments().size());
    }

    @Test
    void acceptsMultilineTrailingArgumentComma() {
        var call = parseReturnedCall(
            """
            fn test() -> int
                return combine(
                    1,
                    2,
                )
            end
            """
        );

        assertEquals(2, call.arguments().size());
    }

    @Test
    void acceptsNestedMultilineCalls() {
        var outer = parseReturnedCall(
            """
            fn test() -> int
                return outer(
                    inner(
                        1,
                        2,
                    ),
                    3,
                )
            end
            """
        );

        assertEquals(2, outer.arguments().size());

        var inner = assertInstanceOf(
            CallExpression.class,
            outer.arguments().getFirst()
        );

        assertEquals(2, inner.arguments().size());
    }

    @Test
    void preservesArgumentOrderAndSourceSpans() {
        var source = """
            fn test() -> int
                return combine(
                    10,
                    20,
                    true
                )
            end
            """;

        var call = parseReturnedCall(source);
        var arguments = call.arguments();

        assertEquals(3, arguments.size());

        assertSpan(
            source,
            arguments.get(0).span(),
            "10"
        );

        assertSpan(
            source,
            arguments.get(1).span(),
            "20"
        );

        assertSpan(
            source,
            arguments.get(2).span(),
            "true"
        );

        assertTrue(
            arguments.get(0).span().start().offset()
                < arguments.get(1).span().start().offset()
        );

        assertTrue(
            arguments.get(1).span().start().offset()
                < arguments.get(2).span().start().offset()
        );
    }

    @Test
    void preservesCompleteCallExpressionSourceSpan() {
        var source = """
            fn test() -> int
                return combine(
                    1,
                    2,
                )
            end
            """;

        var call = parseReturnedCall(source);

        var start =
            source.indexOf("combine(");

        var closingParenthesis =
            source.indexOf(
                ")",
                source.indexOf("2,")
            );

        assertEquals(
            start,
            call.span().start().offset()
        );

        assertEquals(
            closingParenthesis + 1,
            call.span().end().offset()
        );
    }

    @Test
    void rejectsMissingArgumentComma() {
        assertParserError(
            """
            fn test() -> int
                return call(
                    1
                    2
                )
            end
            """
        );
    }

    @Test
    void rejectsLeadingArgumentComma() {
        assertParserError(
            """
            fn test() -> int
                return call(
                    ,
                    1
                )
            end
            """
        );
    }

    @Test
    void rejectsDoubledArgumentComma() {
        assertParserError(
            """
            fn test() -> int
                return call(
                    1,,
                    2
                )
            end
            """
        );
    }

    @Test
    void rejectsMultipleTrailingArgumentCommas() {
        assertParserError(
            """
            fn test() -> int
                return call(
                    1,,
                )
            end
            """
        );
    }

    @Test
    void rejectsListContainingOnlyComma() {
        assertParserError(
            """
            fn test() -> int
                return call(
                    ,
                )
            end
            """
        );
    }

    @Test
    void keepsNewlinesSignificantOutsideArgumentLists() {
        assertParserError(
            """
            fn test() -> int
                let value: int =
                    1
                return value
            end
            """
        );
    }

    @Test
    void doesNotAddGeneralExpressionContinuation() {
        assertParserError(
            """
            fn test() -> int
                return combine(
                    1 +
                    2
                )
            end
            """
        );
    }

    private static CallExpression parseReturnedCall(
        String source
    ) {
        var unit = Parser.parse(
            Lexer.scan(source)
        );

        assertEquals(
            1,
            unit.declarations().size()
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var returnStatement = assertInstanceOf(
            ReturnStatement.class,
            function.body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        return assertInstanceOf(
            CallExpression.class,
            returnStatement.expression().orElseThrow()
        );
    }

    private static void assertSpan(
        String source,
        SourceSpan span,
        String expectedText
    ) {
        var start =
            source.indexOf(expectedText);

        assertTrue(
            start >= 0,
            "Expected text was not found in source."
        );

        assertEquals(
            start,
            span.start().offset()
        );

        assertEquals(
            start + expectedText.length(),
            span.end().offset()
        );
    }

    private static void assertParserError(
        String source
    ) {
        var exception = assertThrows(
            ParsingException.class,
            () -> Parser.parse(
                Lexer.scan(source)
            )
        );

        assertEquals(
            "SOL-P002",
            exception.diagnostic().code()
        );
    }
}
