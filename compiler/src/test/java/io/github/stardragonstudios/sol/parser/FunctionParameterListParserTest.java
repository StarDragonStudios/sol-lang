package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionParameterListParserTest {
    @Test
    void acceptsSingleLineParameters() {
        var function = parseFunction(
            """
            fn combine(left: int, right: int) -> int
                return left
            end
            """
        );

        assertEquals(2, function.parameters().size());
        assertEquals("left", function.parameters().get(0).name());
        assertEquals("right", function.parameters().get(1).name());
    }

    @Test
    void acceptsMultilineParameters() {
        var function = parseFunction(
            """
            fn combine(
                left: int,
                right: int,
                enabled: boolean
            ) -> int
                return left
            end
            """
        );

        assertEquals(3, function.parameters().size());
        assertEquals("left", function.parameters().get(0).name());
        assertEquals("right", function.parameters().get(1).name());
        assertEquals("enabled", function.parameters().get(2).name());
    }

    @Test
    void acceptsEmptyMultilineParameterList() {
        var function = parseFunction(
            """
            fn empty(
            ) -> void
                return
            end
            """
        );

        assertTrue(function.parameters().isEmpty());
    }

    @Test
    void acceptsMultilineParametersInBodylessFunction() {
        var function = parseFunction(
            """
            @fn external(
                path: string,
                recursive: boolean
            ) -> int
            """
        );

        assertEquals(2, function.parameters().size());
        assertEquals("path", function.parameters().get(0).name());
        assertEquals(
            "recursive",
            function.parameters().get(1).name()
        );
        assertTrue(function.body().isEmpty());
    }

    @Test
    void acceptsNewlineImmediatelyAfterOpeningParenthesis() {
        var function = parseFunction(
            """
            fn identity(
                value: int) -> int
                return value
            end
            """
        );

        assertEquals(1, function.parameters().size());
        assertEquals(
            "value",
            function.parameters().getFirst().name()
        );
    }

    @Test
    void acceptsNewlineImmediatelyBeforeClosingParenthesis() {
        var function = parseFunction(
            """
            fn identity(value: int
            ) -> int
                return value
            end
            """
        );

        assertEquals(1, function.parameters().size());
        assertEquals(
            "value",
            function.parameters().getFirst().name()
        );
    }

    @Test
    void acceptsBlankLinesInsideParameterList() {
        var function = parseFunction(
            """
            fn combine(

                left: int,

                right: int,

            ) -> int
                return left
            end
            """
        );

        assertEquals(2, function.parameters().size());
        assertEquals("left", function.parameters().get(0).name());
        assertEquals("right", function.parameters().get(1).name());
    }

    @Test
    void acceptsSingleLineTrailingParameterComma() {
        var function = parseFunction(
            """
            fn combine(left: int, right: int,) -> int
                return left
            end
            """
        );

        assertEquals(2, function.parameters().size());
    }

    @Test
    void acceptsMultilineTrailingParameterComma() {
        var function = parseFunction(
            """
            fn combine(
                left: int,
                right: int,
            ) -> int
                return left
            end
            """
        );

        assertEquals(2, function.parameters().size());
    }

    @Test
    void preservesParameterOrderAndSourceSpans() {
        var source = """
            fn combine(
                left: int,
                right: float,
                enabled: boolean
            ) -> int
                return left
            end
            """;

        var function = parseFunction(source);
        var parameters = function.parameters();

        assertEquals(
            "left",
            parameters.get(0).name()
        );
        assertEquals(
            "right",
            parameters.get(1).name()
        );
        assertEquals(
            "enabled",
            parameters.get(2).name()
        );

        assertSpan(
            source,
            parameters.get(0).span(),
            "left: int"
        );

        assertSpan(
            source,
            parameters.get(1).span(),
            "right: float"
        );

        assertSpan(
            source,
            parameters.get(2).span(),
            "enabled: boolean"
        );
    }

    @Test
    void preservesCompleteBodyfulFunctionSourceSpan() {
        var source = """
            fn combine(
                left: int,
                right: int
            ) -> int
                return left
            end
            """;

        var function = parseFunction(source);

        assertEquals(
            source.indexOf("fn combine"),
            function.span().start().offset()
        );

        assertEquals(
            source.lastIndexOf("end") + "end".length(),
            function.span().end().offset()
        );
    }

    @Test
    void preservesCompleteBodylessFunctionSourceSpan() {
        var source = """
            @fn external(
                path: string,
                recursive: boolean
            ) -> int
            """;

        var function = parseFunction(source);

        assertEquals(
            source.indexOf("@fn"),
            function.span().start().offset()
        );

        var returnTypeStart =
            source.lastIndexOf("int");

        assertEquals(
            returnTypeStart + "int".length(),
            function.span().end().offset()
        );
    }

    @Test
    void rejectsMissingParameterComma() {
        assertParserError(
            """
            fn broken(
                first: int
                second: int
            ) -> void
                return
            end
            """
        );
    }

    @Test
    void rejectsLeadingParameterComma() {
        assertParserError(
            """
            fn broken(
                ,
                value: int
            ) -> void
                return
            end
            """
        );
    }

    @Test
    void rejectsDoubledParameterComma() {
        assertParserError(
            """
            fn broken(
                first: int,,
                second: int
            ) -> void
                return
            end
            """
        );
    }

    @Test
    void rejectsMultipleTrailingParameterCommas() {
        assertParserError(
            """
            fn broken(
                first: int,,
            ) -> void
                return
            end
            """
        );
    }

    @Test
    void rejectsListContainingOnlyComma() {
        assertParserError(
            """
            fn broken(
                ,
            ) -> void
                return
            end
            """
        );
    }

    private static FunctionDeclaration parseFunction(
        String source
    ) {
        var unit = Parser.parse(
            Lexer.scan(source)
        );

        assertEquals(
            1,
            unit.declarations().size()
        );

        return assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );
    }

    private static void assertSpan(
        String source,
        io.github.stardragonstudios.sol.source.SourceSpan span,
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
