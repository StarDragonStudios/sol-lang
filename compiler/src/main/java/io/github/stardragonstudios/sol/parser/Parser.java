package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Token;
import io.github.stardragonstudios.sol.lexer.TokenKind;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.CompilationUnit;

import java.util.List;
import java.util.Objects;

public final class Parser {
    private static final String UNEXPECTED_TOP_LEVEL_TOKEN_CODE =
        "SOL-P001";

    private final List<Token> tokens;
    private int current;

    private Parser(List<Token> tokens) {
        this.tokens = copyAndValidateTokens(tokens);
    }

    public static CompilationUnit parse(List<Token> tokens) {
        return new Parser(tokens).parseCompilationUnit();
    }

    private CompilationUnit parseCompilationUnit() {
        skipNewlines();

        if (!isAtEnd()) {
            throw unexpectedTopLevelToken(peek());
        }

        return new CompilationUnit(
            List.of(),
            completeSourceSpan()
        );
    }

    private void skipNewlines() {
        while (match(TokenKind.NEWLINE)) {
            // Newlines are allowed between top-level declarations.
        }
    }

    private boolean match(TokenKind kind) {
        if (!check(kind)) {
            return false;
        }

        advance();
        return true;
    }

    private boolean check(TokenKind kind) {
        return peek().kind() == kind;
    }

    private Token advance() {
        var token = peek();

        if (!isAtEnd()) {
            current++;
        }

        return token;
    }

    private boolean isAtEnd() {
        return peek().kind() == TokenKind.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private SourceSpan completeSourceSpan() {
        var firstToken = tokens.getFirst();
        var eofToken = tokens.getLast();

        return new SourceSpan(
            firstToken.span().start(),
            eofToken.span().end()
        );
    }

    private static ParsingException unexpectedTopLevelToken(
        Token token
    ) {
        return new ParsingException(
            new Diagnostic(
                UNEXPECTED_TOP_LEVEL_TOKEN_CODE,
                DiagnosticSeverity.ERROR,
                "Unexpected token '%s' at top level."
                    .formatted(token.lexeme()),
                token.span()
            )
        );
    }

    private static List<Token> copyAndValidateTokens(
        List<Token> tokens
    ) {
        Objects.requireNonNull(
            tokens,
            "Parser token stream must not be null."
        );

        if (tokens.isEmpty()) {
            throw new IllegalArgumentException(
                "Parser token stream must not be empty."
            );
        }

        var copy = List.copyOf(tokens);
        var lastIndex = copy.size() - 1;

        if (copy.get(lastIndex).kind() != TokenKind.EOF) {
            throw new IllegalArgumentException(
                "Parser token stream must terminate with EOF."
            );
        }

        for (var index = 0; index < lastIndex; index++) {
            if (copy.get(index).kind() == TokenKind.EOF) {
                throw new IllegalArgumentException(
                    "Parser token stream must not contain EOF before its end."
                );
            }
        }

        return copy;
    }
}
