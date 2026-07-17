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
        Map.entry("and", TokenKind.AND),
        Map.entry("or", TokenKind.OR),
        Map.entry("not", TokenKind.NOT)
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

        while (!isAtEnd()) {
            scanNextToken(tokens);
        }

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

        switch (current) {
            case ' ', '\t', '\f' -> advanceCharacter();
            case '\n' -> scanNewline(start, tokens);
            case '\r' -> scanCarriageReturn(start, tokens);
            default -> throw new IllegalArgumentException(
                "Unexpected character '%s' at %d:%d."
                    .formatted(current, line, column)
            );
        }
    }

    private void scanIdentifier(SourcePosition start, List<Token> tokens) {
        var startOffset = offset;

        advanceCharacter();

        while (!isAtEnd() && isIdentifierPart(peek())) {
            advanceCharacter();
        }

        var lexeme = source.substring(startOffset, offset);
        var kind = KEYWORDS.getOrDefault(lexeme, TokenKind.IDENTIFIER);

        tokens.add(new Token(
            kind,
            lexeme,
            new SourceSpan(start, currentPosition())
        ));
    }

    private void scanNewline(SourcePosition start, List<Token> tokens) {
        var lexeme = String.valueOf(advanceCharacter());

        line++;
        column = 1;

        tokens.add(new Token(
            TokenKind.NEWLINE,
            lexeme,
            new SourceSpan(start, currentPosition())
        ));
    }

    private void scanCarriageReturn(SourcePosition start, List<Token> tokens) {
        var startOffset = offset;

        advanceCharacter();

        if (!isAtEnd() && peek() == '\n') {
            advanceCharacter();
        }

        line++;
        column = 1;

        tokens.add(new Token(
            TokenKind.NEWLINE,
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
        return isIdentifierStart(character)
            || (character >= '0' && character <= '9');
    }

    private static boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z')
            || (character >= 'A' && character <= 'Z');
    }
}
