package io.github.stardragonstudios.sol.lexer;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LexerTest {
    @Test
    void scansIdentifiersKeywordsAndNewlines() {
        var tokens = Lexer.scan("fn iniciar\nlet valor_42");

        assertEquals(
            List.of(
                TokenKind.FN,
                TokenKind.IDENTIFIER,
                TokenKind.NEWLINE,
                TokenKind.LET,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of("fn", "iniciar", "\n", "let", "valor_42", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void recognizesAllInitialKeywords() {
        var tokens = Lexer.scan(
            "fn let const if else while return then do end inject true false and or not"
        );

        assertEquals(
            List.of(
                TokenKind.FN,
                TokenKind.LET,
                TokenKind.CONST,
                TokenKind.IF,
                TokenKind.ELSE,
                TokenKind.WHILE,
                TokenKind.RETURN,
                TokenKind.THEN,
                TokenKind.DO,
                TokenKind.END,
                TokenKind.INJECT,
                TokenKind.TRUE,
                TokenKind.FALSE,
                TokenKind.AND,
                TokenKind.OR,
                TokenKind.NOT,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );
    }

    @Test
    void keepsKeywordLikeNamesAsIdentifiersWhenTheyDifferInCase() {
        var tokens = Lexer.scan("Fn Initialize true_value");

        assertEquals(
            List.of(
                TokenKind.IDENTIFIER,
                TokenKind.IDENTIFIER,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of("Fn", "Initialize", "true_value", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void preservesSpansAndTracksNewlines() {
        var tokens = Lexer.scan("fn\nx");

        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(2, 1, 3)
            ),
            tokens.get(0).span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(2, 1, 3),
                new SourcePosition(3, 2, 1)
            ),
            tokens.get(1).span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(3, 2, 1),
                new SourcePosition(4, 2, 2)
            ),
            tokens.get(2).span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(4, 2, 2),
                new SourcePosition(4, 2, 2)
            ),
            tokens.get(3).span()
        );
    }

    @Test
    void recognizesWindowsNewlines() {
        var tokens = Lexer.scan("a\r\nb");

        assertEquals(
            List.of(
                TokenKind.IDENTIFIER,
                TokenKind.NEWLINE,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals("\r\n", tokens.get(1).lexeme());

        assertEquals(
            new SourcePosition(3, 2, 1),
            tokens.get(2).span().start()
        );
    }

    @Test
    void scansIntegerLiterals() {
        var tokens = Lexer.scan("0 42 123456");

        assertEquals(
            List.of(
                TokenKind.INTEGER_LITERAL,
                TokenKind.INTEGER_LITERAL,
                TokenKind.INTEGER_LITERAL,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of("0", "42", "123456", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void scansFloatingPointLiterals() {
        var tokens = Lexer.scan("0.0 3.14 10.5");

        assertEquals(
            List.of(
                TokenKind.FLOAT_LITERAL,
                TokenKind.FLOAT_LITERAL,
                TokenKind.FLOAT_LITERAL,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of("0.0", "3.14", "10.5", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void allowsDigitsInsideIdentifiersAfterTheirFirstCharacter() {
        var tokens = Lexer.scan("value42 42value");

        assertEquals(
            List.of(
                TokenKind.IDENTIFIER,
                TokenKind.INTEGER_LITERAL,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of("value42", "42", "value", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void preservesNumericLiteralSpans() {
        var tokens = Lexer.scan("42\n3.14");

        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(2, 1, 3)
            ),
            tokens.get(0).span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(3, 2, 1),
                new SourcePosition(7, 2, 5)
            ),
            tokens.get(2).span()
        );
    }

    @Test
    void scansStringAndCharacterLiterals() {
        var tokens = Lexer.scan("\"Hola\" 'a'");

        assertEquals(
            List.of(
                TokenKind.STRING_LITERAL,
                TokenKind.CHAR_LITERAL,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of("\"Hola\"", "'a'", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void scansSupportedStringEscapeSequences() {
        var source = "\"a\\n\\r\\t\\\\\\\"\\'\"";
        var tokens = Lexer.scan(source);

        assertEquals(
            List.of(
                TokenKind.STRING_LITERAL,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(source, tokens.getFirst().lexeme());
    }

    @Test
    void scansSupportedCharacterEscapeSequences() {
        var source = "'\\n' '\\r' '\\t' '\\'' '\\\\'";
        var tokens = Lexer.scan(source);

        assertEquals(
            List.of(
                TokenKind.CHAR_LITERAL,
                TokenKind.CHAR_LITERAL,
                TokenKind.CHAR_LITERAL,
                TokenKind.CHAR_LITERAL,
                TokenKind.CHAR_LITERAL,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of(
                "'\\n'",
                "'\\r'",
                "'\\t'",
                "'\\''",
                "'\\\\'",
                ""
            ),
            lexemesOf(tokens)
        );
    }

    @Test
    void preservesLiteralSpans() {
        var tokens = Lexer.scan("\"hi\"\n'a'");

        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(4, 1, 5)
            ),
            tokens.get(0).span()
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(5, 2, 1),
                new SourcePosition(8, 2, 4)
            ),
            tokens.get(2).span()
        );
    }

    @Test
    void rejectsUnterminatedStringLiterals() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Lexer.scan("\"unterminated")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> Lexer.scan("\"first line\nsecond line\"")
        );
    }

    @Test
    void rejectsInvalidStringEscapeSequences() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Lexer.scan("\"invalid\\x\"")
        );
    }

    @Test
    void rejectsInvalidCharacterLiterals() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Lexer.scan("''")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> Lexer.scan("'ab'")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> Lexer.scan("'a")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> Lexer.scan("'\\x'")
        );
    }

    private static List<TokenKind> kindsOf(List<Token> tokens) {
        return tokens.stream()
            .map(Token::kind)
            .toList();
    }

    private static List<String> lexemesOf(List<Token> tokens) {
        return tokens.stream()
            .map(Token::lexeme)
            .toList();
    }
}
