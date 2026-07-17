package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.lexer.Token;
import io.github.stardragonstudios.sol.lexer.TokenKind;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
