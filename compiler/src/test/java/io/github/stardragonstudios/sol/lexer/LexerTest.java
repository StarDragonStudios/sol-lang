package io.github.stardragonstudios.sol.lexer;

import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
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
            "fn let const if else while return then do end inject true false only namespace as"
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
                TokenKind.ONLY,
                TokenKind.NAMESPACE,
                TokenKind.AS,
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
            LexicalException.class,
            () -> Lexer.scan("\"unterminated")
        );

        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("\"first line\nsecond line\"")
        );
    }

    @Test
    void rejectsInvalidStringEscapeSequences() {
        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("\"invalid\\x\"")
        );
    }

    @Test
    void rejectsInvalidCharacterLiterals() {
        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("''")
        );

        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("'ab'")
        );

        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("'a")
        );

        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("'\\x'")
        );
    }

    @Test
    void scansPunctuationAndArithmeticOperators() {
        var tokens = Lexer.scan(
            "@ ( ) , : . -> = + - * / % ! && ||"
        );

        assertEquals(
            List.of(
                TokenKind.AT,
                TokenKind.LEFT_PAREN,
                TokenKind.RIGHT_PAREN,
                TokenKind.COMMA,
                TokenKind.COLON,
                TokenKind.DOT,
                TokenKind.ARROW,
                TokenKind.ASSIGN,
                TokenKind.PLUS,
                TokenKind.MINUS,
                TokenKind.STAR,
                TokenKind.SLASH,
                TokenKind.PERCENT,
                TokenKind.BANG,
                TokenKind.AND_AND,
                TokenKind.OR_OR,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of(
                "@",
                "(",
                ")",
                ",",
                ":",
                ".",
                "->",
                "=",
                "+",
                "-",
                "*",
                "/",
                "%",
                "!",
                "&&",
                "||",
                ""
            ),
            lexemesOf(tokens)
        );
    }

    @Test
    void scansComparisonOperators() {
        var tokens = Lexer.scan(
            "== != < <= > >="
        );

        assertEquals(
            List.of(
                TokenKind.EQUAL_EQUAL,
                TokenKind.NOT_EQUAL,
                TokenKind.LESS,
                TokenKind.LESS_EQUAL,
                TokenKind.GREATER,
                TokenKind.GREATER_EQUAL,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );
    }

    @Test
    void prefersLongestOperatorsWithoutWhitespace() {
        var tokens = Lexer.scan(
            "!a&&b||c!=d==e<=f>=g"
        );

        assertEquals(
            List.of(
                TokenKind.BANG,
                TokenKind.IDENTIFIER,
                TokenKind.AND_AND,
                TokenKind.IDENTIFIER,
                TokenKind.OR_OR,
                TokenKind.IDENTIFIER,
                TokenKind.NOT_EQUAL,
                TokenKind.IDENTIFIER,
                TokenKind.EQUAL_EQUAL,
                TokenKind.IDENTIFIER,
                TokenKind.LESS_EQUAL,
                TokenKind.IDENTIFIER,
                TokenKind.GREATER_EQUAL,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );
    }

    @Test
    void scansAnnotationsAndFunctionSignatures() {
        var tokens = Lexer.scan(
            "@init\nfn iniciar() -> int"
        );

        assertEquals(
            List.of(
                TokenKind.AT,
                TokenKind.IDENTIFIER,
                TokenKind.NEWLINE,
                TokenKind.FN,
                TokenKind.IDENTIFIER,
                TokenKind.LEFT_PAREN,
                TokenKind.RIGHT_PAREN,
                TokenKind.ARROW,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );
    }

    @Test
    void preservesOperatorSpans() {
        var tokens = Lexer.scan("a!=b");

        assertEquals(
            new SourceSpan(
                new SourcePosition(1, 1, 2),
                new SourcePosition(3, 1, 4)
            ),
            tokens.get(1).span()
        );
    }

    @Test
    void rejectsStandaloneBitwiseSymbols() {
        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("&")
        );

        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("|")
        );
    }

    @Test
    void treatsFormerLogicalKeywordsAsIdentifiers() {
        var tokens = Lexer.scan("and or not");

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
            List.of("and", "or", "not", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void ignoresSingleLineCommentsAndPreservesNewlines() {
        var tokens = Lexer.scan(
            "let // comment\nvalue"
        );

        assertEquals(
            List.of(
                TokenKind.LET,
                TokenKind.NEWLINE,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of(
                "let",
                "\n",
                "value",
                ""
            ),
            lexemesOf(tokens)
        );
    }

    @Test
    void ignoresBlockComments() {
        var tokens = Lexer.scan(
            "left /* comment */ right"
        );

        assertEquals(
            List.of(
                TokenKind.IDENTIFIER,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of("left", "right", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void recognizesCommentsAdjacentToTokens() {
        var tokens = Lexer.scan(
            "left/* comment */right"
        );

        assertEquals(
            List.of(
                TokenKind.IDENTIFIER,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of("left", "right", ""),
            lexemesOf(tokens)
        );
    }

    @Test
    void tracksPositionsAcrossMultilineBlockComments() {
        var tokens = Lexer.scan(
            "a/* one\n two\r\n*/b"
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(16, 3, 3),
                new SourcePosition(17, 3, 4)
            ),
            tokens.get(1).span()
        );
    }

    @Test
    void keepsSlashAsAnArithmeticOperator() {
        var tokens = Lexer.scan("a / b");

        assertEquals(
            List.of(
                TokenKind.IDENTIFIER,
                TokenKind.SLASH,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );
    }

    @Test
    void rejectsUnterminatedBlockComments() {
        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("/* unterminated")
        );

        assertThrows(
            LexicalException.class,
            () -> Lexer.scan("/* first\nsecond")
        );
    }

    @Test
    void scansModuleKeywords() {
        var tokens = Lexer.scan(
            "only namespace as"
        );

        assertEquals(
            List.of(
                TokenKind.ONLY,
                TokenKind.NAMESPACE,
                TokenKind.AS,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of(
                "only",
                "namespace",
                "as",
                ""
            ),
            lexemesOf(tokens)
        );
    }

    @Test
    void scansModuleInjectionSyntax() {
        var tokens = Lexer.scan(
            "inject namespace std.console as io"
        );

        assertEquals(
            List.of(
                TokenKind.INJECT,
                TokenKind.NAMESPACE,
                TokenKind.IDENTIFIER,
                TokenKind.DOT,
                TokenKind.IDENTIFIER,
                TokenKind.AS,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of(
                "inject",
                "namespace",
                "std",
                ".",
                "console",
                "as",
                "io",
                ""
            ),
            lexemesOf(tokens)
        );
    }

    @Test
    void scansSelectiveModuleInjection() {
        var tokens = Lexer.scan(
            "inject std.console only print, print_line"
        );

        assertEquals(
            List.of(
                TokenKind.INJECT,
                TokenKind.IDENTIFIER,
                TokenKind.DOT,
                TokenKind.IDENTIFIER,
                TokenKind.ONLY,
                TokenKind.IDENTIFIER,
                TokenKind.COMMA,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );
    }

    @Test
    void scansNamespaceResolutionOperator() {
        var tokens = Lexer.scan(
            "io::print_line"
        );

        assertEquals(
            List.of(
                TokenKind.IDENTIFIER,
                TokenKind.DOUBLE_COLON,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );

        assertEquals(
            List.of(
                "io",
                "::",
                "print_line",
                ""
            ),
            lexemesOf(tokens)
        );
    }

    @Test
    void distinguishesColonFromDoubleColon() {
        var tokens = Lexer.scan(
            "value:int io::print"
        );

        assertEquals(
            List.of(
                TokenKind.IDENTIFIER,
                TokenKind.COLON,
                TokenKind.IDENTIFIER,
                TokenKind.IDENTIFIER,
                TokenKind.DOUBLE_COLON,
                TokenKind.IDENTIFIER,
                TokenKind.EOF
            ),
            kindsOf(tokens)
        );
    }

    @Test
    void preservesDoubleColonSpan() {
        var tokens = Lexer.scan(
            "io::print"
        );

        assertEquals(
            new SourceSpan(
                new SourcePosition(2, 1, 3),
                new SourcePosition(4, 1, 5)
            ),
            tokens.get(1).span()
        );
    }

    @Test
    void reportsUnexpectedCharacterDiagnostic() {
        var exception = assertThrows(
            LexicalException.class,
            () -> Lexer.scan("&")
        );

        var diagnostic = exception.diagnostic();

        assertEquals("SOL-L001", diagnostic.code());
        assertEquals(
            DiagnosticSeverity.ERROR,
            diagnostic.severity()
        );
        assertEquals(
            "Unexpected character '&'.",
            diagnostic.message()
        );
        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(1, 1, 2)
            ),
            diagnostic.span()
        );
    }

    @Test
    void reportsUnterminatedStringDiagnostic() {
        var exception = assertThrows(
            LexicalException.class,
            () -> Lexer.scan("\"hello")
        );

        var diagnostic = exception.diagnostic();

        assertEquals("SOL-L002", diagnostic.code());
        assertEquals(
            "Unterminated string literal.",
            diagnostic.message()
        );
        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(6, 1, 7)
            ),
            diagnostic.span()
        );
    }

    @Test
    void reportsInvalidEscapeDiagnostic() {
        var exception = assertThrows(
            LexicalException.class,
            () -> Lexer.scan("\"\\q\"")
        );

        var diagnostic = exception.diagnostic();

        assertEquals("SOL-L003", diagnostic.code());
        assertEquals(
            "Invalid escape sequence '\\q'.",
            diagnostic.message()
        );
        assertEquals(
            new SourceSpan(
                new SourcePosition(1, 1, 2),
                new SourcePosition(3, 1, 4)
            ),
            diagnostic.span()
        );
    }

    @Test
    void reportsInvalidCharacterLiteralDiagnostic() {
        var exception = assertThrows(
            LexicalException.class,
            () -> Lexer.scan("'ab'")
        );

        var diagnostic = exception.diagnostic();

        assertEquals("SOL-L004", diagnostic.code());
        assertEquals(
            "Invalid character literal.",
            diagnostic.message()
        );
        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(4, 1, 5)
            ),
            diagnostic.span()
        );
    }

    @Test
    void reportsUnterminatedBlockCommentDiagnostic() {
        var exception = assertThrows(
            LexicalException.class,
            () -> Lexer.scan("/* hello")
        );

        var diagnostic = exception.diagnostic();

        assertEquals("SOL-L005", diagnostic.code());
        assertEquals(
            "Unterminated block comment.",
            diagnostic.message()
        );
        assertEquals(
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(8, 1, 9)
            ),
            diagnostic.span()
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
