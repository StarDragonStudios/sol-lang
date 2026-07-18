package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Token;
import io.github.stardragonstudios.sol.lexer.TokenKind;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.*;

import java.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Parser {
    private static final String UNEXPECTED_TOP_LEVEL_TOKEN_CODE = "SOL-P001";
    private static final String EXPECTED_TOKEN_CODE = "SOL-P002";

    private final List<Token> tokens;
    private int current;

    private Parser(List<Token> tokens) {
        this.tokens = copyAndValidateTokens(tokens);
    }

    public static CompilationUnit parse(List<Token> tokens) {
        return new Parser(tokens).parseCompilationUnit();
    }

    private CompilationUnit parseCompilationUnit() {
        var declarations = new ArrayList<Declaration>();

        skipNewlines();

        while (!isAtEnd()) {
            declarations.add(parseTopLevelDeclaration());
            skipNewlines();
        }

        return new CompilationUnit(
            declarations,
            completeSourceSpan()
        );
    }

    private void skipNewlines() {
        while (match(TokenKind.NEWLINE)) {
            // Newlines are allowed between top-level declarations.
        }
    }

    private boolean match(TokenKind kind) {
        if (!check(kind)) return false;

        advance();
        return true;
    }

    private boolean check(TokenKind kind) {
        return peek().kind() == kind;
    }

    private Token advance() {
        var token = peek();

        if (!isAtEnd()) current++;

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

        return new SourceSpan(firstToken.span().start(), eofToken.span().end());
    }

    private static ParsingException unexpectedTopLevelToken(Token token) {
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

    private static List<Token> copyAndValidateTokens(List<Token> tokens) {
        Objects.requireNonNull(tokens, "Parser token stream must not be null.");

        if (tokens.isEmpty()) throw new IllegalArgumentException("Parser token stream must not be empty.");

        var copy = List.copyOf(tokens);
        var lastIndex = copy.size() - 1;

        if (copy.get(lastIndex).kind() != TokenKind.EOF) throw new IllegalArgumentException("Parser token stream must terminate with EOF.");

        for (var index = 0; index < lastIndex; index++) if (copy.get(index).kind() == TokenKind.EOF) throw new IllegalArgumentException("Parser token stream must not contain EOF before its end.");

        return copy;
    }

    private Declaration parseTopLevelDeclaration() {
        return switch (peek().kind()) {
            case FN -> parseFunctionDeclaration();
            default -> throw unexpectedTopLevelToken(peek());
        };
    }

    private FunctionDeclaration parseFunctionDeclaration() {
        var functionToken = consume(
            TokenKind.FN,
            "'fn'"
        );

        var nameToken = consume(
            TokenKind.IDENTIFIER,
            "a function name after 'fn'"
        );

        consume(
            TokenKind.LEFT_PAREN,
            "'(' after the function name"
        );

        var parameters = parseParameterList();

        consume(
            TokenKind.RIGHT_PAREN,
            "')' after the function parameter list"
        );

        consume(
            TokenKind.ARROW,
            "'->' before the function return type"
        );

        var returnTypeToken = consume(
            TokenKind.IDENTIFIER,
            "a return type after '->'"
        );

        var headerNewline = consume(
            TokenKind.NEWLINE,
            "a newline after the function declaration header"
        );

        var statements = parseFunctionBodyStatements();

        var endToken = consume(
            TokenKind.END,
            "'end' to close the function declaration"
        );

        var returnType = new TypeReference(
            returnTypeToken.lexeme(),
            returnTypeToken.span()
        );

        var body = new Block(
            statements,
            new SourceSpan(
                headerNewline.span().end(),
                endToken.span().end()
            )
        );

        return new FunctionDeclaration(
            nameToken.lexeme(),
            parameters,
            returnType,
            body,
            new SourceSpan(
                functionToken.span().start(),
                endToken.span().end()
            )
        );
    }

    private Token consume(TokenKind kind, String expectation) {
        if (check(kind)) {
            return advance();
        }

        throw expectedToken(
            expectation,
            peek()
        );
    }

    private static ParsingException expectedToken(String expectation, Token actual) {
        return new ParsingException(
            new Diagnostic(
                EXPECTED_TOKEN_CODE,
                DiagnosticSeverity.ERROR,
                "Expected %s, but found %s."
                    .formatted(
                        expectation,
                        describeToken(actual)
                    ),
                actual.span()
            )
        );
    }

    private static String describeToken(Token token) {
        return switch (token.kind()) {
            case EOF -> "end of file";
            case NEWLINE -> "newline";
            default -> "'%s'".formatted(token.lexeme());
        };
    }

    private List<Parameter> parseParameterList() {
        var parameters = new ArrayList<Parameter>();

        if (check(TokenKind.RIGHT_PAREN)) {
            return parameters;
        }

        while (true) {
            parameters.add(parseParameter());

            if (!match(TokenKind.COMMA)) {
                break;
            }

            if (check(TokenKind.RIGHT_PAREN)) {
                throw expectedToken(
                    "a parameter after ','",
                    peek()
                );
            }
        }

        return parameters;
    }

    private List<Statement> parseFunctionBodyStatements() {
        var statements = new ArrayList<Statement>();

        skipNewlines();

        while (
            !check(TokenKind.END)
                && !isAtEnd()
        ) {
            statements.add(parseStatement());

            if (match(TokenKind.NEWLINE)) {
                skipNewlines();
            } else if (!check(TokenKind.END)) {
                throw expectedToken(
                    "a newline or 'end' after the statement",
                    peek()
                );
            }
        }

        return statements;
    }

    private Statement parseStatement() {
        return switch (peek().kind()) {
            case RETURN -> parseReturnStatement();

            default -> throw expectedToken(
                "a statement",
                peek()
            );
        };
    }

    private ReturnStatement parseReturnStatement() {
        var returnToken = consume(
            TokenKind.RETURN,
            "'return'"
        );

        Optional<Expression> expression;

        if (check(TokenKind.NEWLINE) || check(TokenKind.END)) expression = Optional.empty();
        else expression = Optional.of(parseExpression());

        var endPosition = expression
            .map(Expression::span)
            .map(SourceSpan::end)
            .orElse(returnToken.span().end());

        return new ReturnStatement(
            expression,
            new SourceSpan(
                returnToken.span().start(),
                endPosition
            )
        );
    }

    private LiteralExpression parseLiteralExpression() {
        var token = peek();

        var kind = switch (token.kind()) {
            case INTEGER_LITERAL -> LiteralKind.INTEGER;
            case FLOAT_LITERAL -> LiteralKind.FLOAT;
            case TRUE, FALSE -> LiteralKind.BOOLEAN;
            case CHAR_LITERAL -> LiteralKind.CHARACTER;
            case STRING_LITERAL -> LiteralKind.STRING;

            default -> throw expectedToken(
                "a literal expression",
                token
            );
        };

        advance();

        return new LiteralExpression(
            kind,
            token.lexeme(),
            token.span()
        );
    }

    private Parameter parseParameter() {
        var nameToken = consume(
            TokenKind.IDENTIFIER,
            "a parameter name"
        );

        consume(
            TokenKind.COLON,
            "':' after the parameter name"
        );

        var typeToken = consume(
            TokenKind.IDENTIFIER,
            "a parameter type after ':'"
        );

        var type = new TypeReference(
            typeToken.lexeme(),
            typeToken.span()
        );

        return new Parameter(
            nameToken.lexeme(),
            type,
            new SourceSpan(
                nameToken.span().start(),
                typeToken.span().end()
            )
        );
    }

    private Expression parseExpression() {
        return parseUnaryExpression();
    }

    private Expression parseUnaryExpression() {
        if (
            check(TokenKind.BANG)
                || check(TokenKind.MINUS)
                || check(TokenKind.PLUS)
        ) {
            var operatorToken = advance();

            var operator = switch (operatorToken.kind()) {
                case BANG -> UnaryOperator.LOGICAL_NOT;
                case MINUS -> UnaryOperator.NEGATE;
                case PLUS -> UnaryOperator.POSITIVE;

                default -> throw new IllegalStateException(
                    "Unexpected unary operator token: "
                        + operatorToken.kind()
                );
            };

            var operand = parseUnaryExpression();

            return new UnaryExpression(
                operator,
                operand,
                new SourceSpan(
                    operatorToken.span().start(),
                    operand.span().end()
                )
            );
        }

        return parsePrimaryExpression();
    }

    private Expression parsePrimaryExpression() {
        return switch (peek().kind()) {
            case INTEGER_LITERAL,
                 FLOAT_LITERAL,
                 TRUE,
                 FALSE,
                 CHAR_LITERAL,
                 STRING_LITERAL ->
                parseLiteralExpression();

            case IDENTIFIER -> parseNameExpression();

            case LEFT_PAREN ->
                parseParenthesizedExpression();

            default -> throw expectedToken(
                "an expression",
                peek()
            );
        };
    }

    private NameExpression parseNameExpression() {
        var token = consume(
            TokenKind.IDENTIFIER,
            "an identifier"
        );

        return new NameExpression(
            token.lexeme(),
            token.span()
        );
    }

    private ParenthesizedExpression
    parseParenthesizedExpression() {
        var leftParenthesis = consume(
            TokenKind.LEFT_PAREN,
            "'('"
        );

        var expression = parseExpression();

        var rightParenthesis = consume(
            TokenKind.RIGHT_PAREN,
            "')' after the parenthesized expression"
        );

        return new ParenthesizedExpression(
            expression,
            new SourceSpan(
                leftParenthesis.span().start(),
                rightParenthesis.span().end()
            )
        );
    }
}
