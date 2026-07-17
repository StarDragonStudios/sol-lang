package io.github.stardragonstudios.sol.lexer;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Lexer {
    private static final Map<String, TokenKind> KEYWORDS = Map.ofEntries(
        Map.entry("fn", TokenKind.FN),
        Map.entry("let", TokenKind.LET),
        Map.entry("const", TokenKind.CONST),
        Map.entry("if", TokenKind.IF),
        Map.entry("else", TokenKind.ELSE),
        Map.entry("while", TokenKind.WHILE),
        Map.entry("return", TokenKind.RETURN),
        Map.entry("then", TokenKind.THEN),
        Map.entry("do", TokenKind.DO),
        Map.entry("end", TokenKind.END),
        Map.entry("inject", TokenKind.INJECT),
        Map.entry("true", TokenKind.TRUE),
        Map.entry("false", TokenKind.FALSE),
        Map.entry("only", TokenKind.ONLY),
        Map.entry("namespace", TokenKind.NAMESPACE),
        Map.entry("as", TokenKind.AS)
    );

    private final String source;

    private int offset;
    private int line = 1;
    private int column = 1;

    public Lexer(String source) {
        this.source = Objects.requireNonNull(source, "Source cannot be null.");
    }

    public static List<Token> scan(String source) {
        return new Lexer(source).scan();
    }

    public List<Token> scan() {
        var tokens = new ArrayList<Token>();

        while (!isAtEnd()) scanNextToken(tokens);

        var position = currentPosition();
        tokens.add(new Token(
            TokenKind.EOF,
            "",
            new SourceSpan(position, position)
        ));

        return List.copyOf(tokens);
    }

    private void scanNextToken(List<Token> tokens) {
        var start = currentPosition();
        var current = peek();

        if (isIdentifierStart(current)) {
            scanIdentifier(start, tokens);
            return;
        }

        if (isDigit(current)) {
            scanNumber(start, tokens);
            return;
        }

        switch (current) {
            case ' ', '\t', '\f' -> advanceCharacter();
            case '\n' -> scanNewline(start, tokens);
            case '\r' -> scanCarriageReturn(start, tokens);

            case '"' -> scanStringLiteral(start, tokens);
            case '\'' -> scanCharacterLiteral(start, tokens);

            case '@' -> scanSingleCharacterToken(
                start,
                TokenKind.AT,
                tokens
            );

            case '(' -> scanSingleCharacterToken(
                start,
                TokenKind.LEFT_PAREN,
                tokens
            );

            case ')' -> scanSingleCharacterToken(
                start,
                TokenKind.RIGHT_PAREN,
                tokens
            );

            case ',' -> scanSingleCharacterToken(
                start,
                TokenKind.COMMA,
                tokens
            );

            case ':' -> scanOneOrTwoCharacterToken(
                start,
                ':',
                TokenKind.COLON,
                TokenKind.DOUBLE_COLON,
                tokens
            );

            case '.' -> scanSingleCharacterToken(
                start,
                TokenKind.DOT,
                tokens
            );

            case '+' -> scanSingleCharacterToken(
                start,
                TokenKind.PLUS,
                tokens
            );

            case '*' -> scanSingleCharacterToken(
                start,
                TokenKind.STAR,
                tokens
            );

            case '/' -> scanSlashOrComment(start, tokens);

            case '%' -> scanSingleCharacterToken(
                start,
                TokenKind.PERCENT,
                tokens
            );

            case '-' -> scanOneOrTwoCharacterToken(
                start,
                '>',
                TokenKind.MINUS,
                TokenKind.ARROW,
                tokens
            );

            case '=' -> scanOneOrTwoCharacterToken(
                start,
                '=',
                TokenKind.ASSIGN,
                TokenKind.EQUAL_EQUAL,
                tokens
            );

            case '<' -> scanOneOrTwoCharacterToken(
                start,
                '=',
                TokenKind.LESS,
                TokenKind.LESS_EQUAL,
                tokens
            );

            case '>' -> scanOneOrTwoCharacterToken(
                start,
                '=',
                TokenKind.GREATER,
                TokenKind.GREATER_EQUAL,
                tokens
            );

            case '!' -> scanOneOrTwoCharacterToken(
                start,
                '=',
                TokenKind.BANG,
                TokenKind.NOT_EQUAL,
                tokens
            );

            case '&' -> scanRequiredTwoCharacterToken(
                start,
                '&',
                TokenKind.AND_AND,
                tokens
            );

            case '|' -> scanRequiredTwoCharacterToken(
                start,
                '|',
                TokenKind.OR_OR,
                tokens
            );

            default -> throw new IllegalArgumentException(
                "Unexpected character '%s' at %d:%d."
                    .formatted(current, line, column)
            );
        }
    }

    private void scanSlashOrComment(SourcePosition start, List<Token> tokens) {
        var startOffset = offset;

        // Consume the first slash.
        advanceCharacter();

        if (isAtEnd()) {
            addToken(
                TokenKind.SLASH,
                startOffset,
                start,
                tokens
            );
            return;
        }

        if (peek() == '/') {
            scanLineComment();
            return;
        }

        if (peek() == '*') {
            scanBlockComment(start);
            return;
        }

        addToken(
            TokenKind.SLASH,
            startOffset,
            start,
            tokens
        );
    }

    private void scanLineComment() {
        do advanceCharacter(); while (!isAtEnd() && peek() != '\n' && peek() != '\r');
    }

    private void scanBlockComment(SourcePosition start) {
        // Consume the opening asterisk.
        advanceCharacter();

        while (!isAtEnd()) {
            if (peek() == '*' && hasNextCharacter() && peekNext() == '/') {
                advanceCharacter();
                advanceCharacter();
                return;
            }

            if (peek() == '\n' || peek() == '\r') {
                skipCommentLineBreak();
                continue;
            }

            advanceCharacter();
        }

        throw commentError(
            "Unterminated block comment",
            start
        );
    }

    private void skipCommentLineBreak() {
        if (peek() == '\r') {
            advanceCharacter();

            if (!isAtEnd() && peek() == '\n') {
                advanceCharacter();
            }
        } else {
            advanceCharacter();
        }

        line++;
        column = 1;
    }

    private static IllegalArgumentException commentError(String message, SourcePosition start) {
        return new IllegalArgumentException(
            "%s at %d:%d."
                .formatted(
                    message,
                    start.line(),
                    start.column()
                )
        );
    }

    private void scanStringLiteral(SourcePosition start, List<Token> tokens) {
        var startOffset = offset;

        // Opening quote.
        advanceCharacter();

        while (!isAtEnd()) {
            var current = peek();

            if (current == '"') {
                advanceCharacter();

                tokens.add(new Token(
                    TokenKind.STRING_LITERAL,
                    source.substring(startOffset, offset),
                    new SourceSpan(start, currentPosition())
                ));

                return;
            }

            if (current == '\n' || current == '\r') throw literalError("Unterminated string literal", start);

            if (current == '\\') {
                scanEscapeSequence("string", start);
                continue;
            }

            advanceCharacter();
        }

        throw literalError("Unterminated string literal", start);
    }

    private void scanCharacterLiteral(SourcePosition start, List<Token> tokens) {
        var startOffset = offset;

        // Opening quote.
        advanceCharacter();

        if (isAtEnd() || peek() == '\n' || peek() == '\r') throw literalError("Unterminated character literal", start);

        if (peek() == '\'') throw literalError("Character literal cannot be empty", start);

        if (peek() == '\\') scanEscapeSequence("character", start);
        else advanceCharacter();

        if (isAtEnd() || peek() == '\n' || peek() == '\r') throw literalError("Unterminated character literal", start);

        if (peek() != '\'') throw literalError("Character literal must contain exactly one character", start);

        // Closing quote.
        advanceCharacter();

        tokens.add(new Token(TokenKind.CHAR_LITERAL, source.substring(startOffset, offset), new SourceSpan(start, currentPosition())));
    }

    private void scanEscapeSequence(String literalKind, SourcePosition literalStart) {
        // Backslash.
        advanceCharacter();

        if (isAtEnd() || peek() == '\n' || peek() == '\r') throw literalError("Unterminated " + literalKind + " literal", literalStart);

        var escaped = peek();

        if (!isValidEscapeCharacter(escaped)) throw new IllegalArgumentException("Invalid escape sequence '\\%s' at %d:%d.".formatted(escaped, line, column));

        advanceCharacter();
    }

    private static boolean isValidEscapeCharacter(char character) {
        return switch (character) {
            case 'n', 'r', 't', '\\', '"', '\'' -> true;
            default -> false;
        };
    }

    private static IllegalArgumentException literalError(String message, SourcePosition start) {
        return new IllegalArgumentException("%s at %d:%d.".formatted(message, start.line(), start.column()));
    }

    private void scanNumber(SourcePosition start, List<Token> tokens) {
        var startOffset = offset;

        while (!isAtEnd() && isDigit(peek())) advanceCharacter();

        var kind = TokenKind.INTEGER_LITERAL;

        if (!isAtEnd() && peek() == '.' && hasNextCharacter() && isDigit(peekNext())) {
            kind = TokenKind.FLOAT_LITERAL;

            do advanceCharacter(); while (!isAtEnd() && isDigit(peek()));
        }

        var lexeme = source.substring(startOffset, offset);

        tokens.add(new Token(kind, lexeme, new SourceSpan(start, currentPosition())));
    }

    private void scanIdentifier(SourcePosition start, List<Token> tokens) {
        var startOffset = offset;

        do advanceCharacter(); while (!isAtEnd() && isIdentifierPart(peek()));

        var lexeme = source.substring(startOffset, offset);
        var kind = KEYWORDS.getOrDefault(lexeme, TokenKind.IDENTIFIER);

        tokens.add(new Token(kind, lexeme, new SourceSpan(start, currentPosition())));
    }

    private void scanNewline(SourcePosition start, List<Token> tokens) {
        var lexeme = String.valueOf(advanceCharacter());

        line++;
        column = 1;

        tokens.add(new Token(TokenKind.NEWLINE, lexeme, new SourceSpan(start, currentPosition())));
    }

    private void scanCarriageReturn(SourcePosition start, List<Token> tokens) {
        var startOffset = offset;

        advanceCharacter();

        if (!isAtEnd() && peek() == '\n') advanceCharacter();

        line++;
        column = 1;

        tokens.add(new Token(TokenKind.NEWLINE, source.substring(startOffset, offset), new SourceSpan(start, currentPosition())));
    }

    private void scanSingleCharacterToken(SourcePosition start, TokenKind kind, List<Token> tokens) {
        var startOffset = offset;

        advanceCharacter();

        addToken(kind, startOffset, start, tokens);
    }

    private void scanOneOrTwoCharacterToken(SourcePosition start, char expectedSecondCharacter, TokenKind singleCharacterKind, TokenKind twoCharacterKind, List<Token> tokens) {
        var startOffset = offset;

        advanceCharacter();

        var kind = singleCharacterKind;

        if (
            !isAtEnd()
                && peek() == expectedSecondCharacter
        ) {
            advanceCharacter();
            kind = twoCharacterKind;
        }

        addToken(kind, startOffset, start, tokens);
    }

    private void scanRequiredTwoCharacterToken(SourcePosition start, char expectedSecondCharacter, TokenKind kind, List<Token> tokens) {
        var startOffset = offset;
        var firstCharacter = advanceCharacter();

        if (isAtEnd() || peek() != expectedSecondCharacter) {
            throw new IllegalArgumentException(
                "Unexpected character '%s' at %d:%d."
                    .formatted(
                        firstCharacter,
                        start.line(),
                        start.column()
                    )
            );
        }

        advanceCharacter();

        addToken(kind, startOffset, start, tokens);
    }

    private void addToken(TokenKind kind, int startOffset, SourcePosition start, List<Token> tokens) {
        tokens.add(new Token(
            kind,
            source.substring(startOffset, offset),
            new SourceSpan(start, currentPosition())
        ));
    }

    private boolean isAtEnd() {
        return offset >= source.length();
    }

    private char peek() {
        return source.charAt(offset);
    }

    private char advanceCharacter() {
        var current = source.charAt(offset);
        offset++;
        column++;
        return current;
    }

    private SourcePosition currentPosition() {
        return new SourcePosition(offset, line, column);
    }

    private static boolean isIdentifierStart(char character) {
        return isAsciiLetter(character) || character == '_';
    }

    private static boolean isIdentifierPart(char character) {
        return isIdentifierStart(character) || isDigit(character);
    }

    private static boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z')
            || (character >= 'A' && character <= 'Z');
    }

    private boolean hasNextCharacter() {
        return offset + 1 < source.length();
    }

    private char peekNext() {
        return source.charAt(offset + 1);
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
