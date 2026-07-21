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

            case AT -> parseAnnotatedOrBodylessFunctionDeclaration();

            case INJECT -> parseInjectionDeclaration();

            default -> throw unexpectedTopLevelToken(peek());
        };
    }

    private FunctionDeclaration parseFunctionDeclaration() {
        var functionToken = consume(
            TokenKind.FN,
            "'fn'"
        );

        return parseFunctionDeclarationAfterMarker(
            List.of(),
            functionToken,
            false
        );
    }

    private FunctionDeclaration
    parseAnnotatedOrBodylessFunctionDeclaration() {
        var declarationStartToken = peek();
        var annotations = new ArrayList<Annotation>();

        while (check(TokenKind.AT)) {
            var atToken = advance();

            if (match(TokenKind.FN)) {
                return parseFunctionDeclarationAfterMarker(
                    annotations,
                    declarationStartToken,
                    true
                );
            }

            var nameToken = consume(
                TokenKind.IDENTIFIER,
                "an annotation name after '@'"
            );

            annotations.add(
                new Annotation(
                    nameToken.lexeme(),
                    new SourceSpan(
                        atToken.span().start(),
                        nameToken.span().end()
                    )
                )
            );

            consume(
                TokenKind.NEWLINE,
                "a newline after the function annotation"
            );
        }

        if (match(TokenKind.FN)) {
            return parseFunctionDeclarationAfterMarker(
                annotations,
                declarationStartToken,
                false
            );
        }

        throw expectedToken(
            "'fn' or '@fn' after the function annotations",
            peek()
        );
    }

    private FunctionDeclaration
    parseFunctionDeclarationAfterMarker(
        List<Annotation> annotations,
        Token declarationStartToken,
        boolean bodyless
    ) {
        var nameToken = consume(
            TokenKind.IDENTIFIER,
            bodyless
                ? "a function name after '@fn'"
                : "a function name after 'fn'"
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

        var returnType = new TypeReference(
            returnTypeToken.lexeme(),
            returnTypeToken.span()
        );

        if (bodyless) {
            requireBodylessFunctionTerminator();

            return new FunctionDeclaration(
                annotations,
                nameToken.lexeme(),
                parameters,
                returnType,
                Optional.empty(),
                new SourceSpan(
                    declarationStartToken.span().start(),
                    returnTypeToken.span().end()
                )
            );
        }

        var headerNewline = consume(
            TokenKind.NEWLINE,
            "a newline after the function declaration header"
        );

        var statements = parseFunctionBodyStatements();

        var endToken = consume(
            TokenKind.END,
            "'end' to close the function declaration"
        );

        var body = new Block(
            statements,
            new SourceSpan(
                headerNewline.span().end(),
                endToken.span().end()
            )
        );

        return new FunctionDeclaration(
            annotations,
            nameToken.lexeme(),
            parameters,
            returnType,
            Optional.of(body),
            new SourceSpan(
                declarationStartToken.span().start(),
                endToken.span().end()
            )
        );
    }

    private void requireBodylessFunctionTerminator() {
        if (
            !check(TokenKind.NEWLINE)
                && !isAtEnd()
        ) {
            throw expectedToken(
                "a newline or end of file after "
                    + "the bodyless function declaration",
                peek()
            );
        }
    }

    private InjectionDeclaration parseInjectionDeclaration() {
        var injectToken = consume(
            TokenKind.INJECT,
            "'inject'"
        );

        var kind = match(TokenKind.NAMESPACE)
            ? InjectionKind.NAMESPACE
            : InjectionKind.DIRECT;

        var modulePath = parseModulePath(
            kind == InjectionKind.NAMESPACE
                ? "a module path after 'namespace'"
                : "a module path after 'inject'"
        );

        var selectedNames = new ArrayList<String>();
        Optional<String> alias = Optional.empty();

        var endPosition = modulePath.span().end();

        if (
            kind == InjectionKind.DIRECT
                && match(TokenKind.ONLY)
        ) {
            var selectedName = consume(
                TokenKind.IDENTIFIER,
                "an injected name after 'only'"
            );

            selectedNames.add(selectedName.lexeme());
            endPosition = selectedName.span().end();

            while (match(TokenKind.COMMA)) {
                selectedName = consume(
                    TokenKind.IDENTIFIER,
                    "an injected name after ','"
                );

                selectedNames.add(selectedName.lexeme());
                endPosition = selectedName.span().end();
            }
        }

        if (
            kind == InjectionKind.NAMESPACE
                && match(TokenKind.AS)
        ) {
            var aliasToken = consume(
                TokenKind.IDENTIFIER,
                "a namespace alias after 'as'"
            );

            alias = Optional.of(aliasToken.lexeme());
            endPosition = aliasToken.span().end();
        }

        if (kind == InjectionKind.DIRECT) {
            requireInjectionTerminator(
                "a newline or end of file "
                    + "after the direct injection"
            );
        } else {
            requireInjectionTerminator(
                "a newline or end of file "
                    + "after the namespace injection"
            );
        }

        return new InjectionDeclaration(
            kind,
            modulePath,
            selectedNames,
            alias,
            new SourceSpan(
                injectToken.span().start(),
                endPosition
            )
        );
    }

    private ModulePath parseModulePath(
        String initialExpectation
    ) {
        var segments = new ArrayList<String>();

        var firstSegment = consume(
            TokenKind.IDENTIFIER,
            initialExpectation
        );

        segments.add(firstSegment.lexeme());

        var lastSegment = firstSegment;

        while (match(TokenKind.DOT)) {
            lastSegment = consume(
                TokenKind.IDENTIFIER,
                "a module path segment after '.'"
            );

            segments.add(lastSegment.lexeme());
        }

        return new ModulePath(
            segments,
            new SourceSpan(
                firstSegment.span().start(),
                lastSegment.span().end()
            )
        );
    }

    private void requireInjectionTerminator(
        String expectation
    ) {
        if (
            !check(TokenKind.NEWLINE)
                && !isAtEnd()
        ) {
            throw expectedToken(
                expectation,
                peek()
            );
        }
    }

    private Token consume(TokenKind kind, String expectation) {
        if (check(kind)) return advance();

        throw expectedToken(expectation, peek());
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
        return parseStatementsUntil("a newline or 'end' after the statement", TokenKind.END);
    }

    private List<Statement> parseStatementsUntil(String separatorExpectation, TokenKind... terminators) {
        var statements = new ArrayList<Statement>();

        skipNewlines();

        while (!checkAny(terminators) && !isAtEnd()) {
            statements.add(parseStatement());

            if (match(TokenKind.NEWLINE)) skipNewlines();
            else if (!checkAny(terminators)) throw expectedToken(separatorExpectation, peek());
        }

        return statements;
    }

    private boolean checkAny(TokenKind... kinds) {
        for (var kind : kinds) if (check(kind)) return true;

        return false;
    }

    private Statement parseStatement() {
        return switch (peek().kind()) {
            case RETURN -> parseReturnStatement();

            case CONST, LET, AT -> parseVariableDeclarationStatement();

            case IDENTIFIER -> parseAssignmentStatement();

            case IF -> parseConditionalStatement();

            case WHILE -> parseWhileStatement();

            default -> throw expectedToken("a statement", peek());
        };
    }

    private WhileStatement parseWhileStatement() {
        var whileToken = consume(
            TokenKind.WHILE,
            "'while'"
        );

        var condition = parseExpression();

        consume(
            TokenKind.DO,
            "'do' after the while condition"
        );

        var headerNewline = consume(
            TokenKind.NEWLINE,
            "a newline after 'do'"
        );

        var statements = parseStatementsUntil(
            "a newline or 'end' after the statement",
            TokenKind.END
        );

        var endToken = consume(
            TokenKind.END,
            "'end' to close the while statement"
        );

        var body = new Block(
            statements,
            new SourceSpan(
                headerNewline.span().end(),
                endToken.span().start()
            )
        );

        return new WhileStatement(
            condition,
            body,
            new SourceSpan(
                whileToken.span().start(),
                endToken.span().end()
            )
        );
    }

    private ConditionalStatement parseConditionalStatement() {
        var ifToken = consume(
            TokenKind.IF,
            "'if'"
        );

        var condition = parseExpression();

        consume(
            TokenKind.THEN,
            "'then' after the conditional condition"
        );

        var thenHeaderNewline = consume(
            TokenKind.NEWLINE,
            "a newline after 'then'"
        );

        var thenStatements = parseStatementsUntil(
            "a newline, 'else', or 'end' after the statement",
            TokenKind.ELSE,
            TokenKind.END
        );

        var thenBlock = new Block(
            thenStatements,
            new SourceSpan(
                thenHeaderNewline.span().end(),
                peek().span().start()
            )
        );

        Optional<Block> elseBlock = Optional.empty();

        if (match(TokenKind.ELSE)) {
            var elseHeaderNewline = consume(
                TokenKind.NEWLINE,
                "a newline after 'else'"
            );

            var elseStatements = parseStatementsUntil(
                "a newline or 'end' after the statement",
                TokenKind.END
            );

            elseBlock = Optional.of(
                new Block(
                    elseStatements,
                    new SourceSpan(
                        elseHeaderNewline.span().end(),
                        peek().span().start()
                    )
                )
            );
        }

        var endToken = consume(
            TokenKind.END,
            "'end' to close the conditional statement"
        );

        return new ConditionalStatement(
            condition,
            thenBlock,
            elseBlock,
            new SourceSpan(
                ifToken.span().start(),
                endToken.span().end()
            )
        );
    }

    private AssignmentStatement parseAssignmentStatement() {
        var targetToken = consume(
            TokenKind.IDENTIFIER,
            "an assignment target"
        );

        var target = new NameExpression(
            targetToken.lexeme(),
            targetToken.span()
        );

        consume(
            TokenKind.ASSIGN,
            "'=' after the assignment target"
        );

        var value = parseExpression();

        return new AssignmentStatement(
            target,
            value,
            new SourceSpan(
                target.span().start(),
                value.span().end()
            )
        );
    }

    private VariableDeclarationStatement parseVariableDeclarationStatement() {
        var startToken = peek();

        var kind = switch (peek().kind()) {
            case CONST -> {
                advance();
                yield VariableDeclarationKind.CONST;
            }

            case LET -> {
                advance();
                yield VariableDeclarationKind.LET;
            }

            case AT -> {
                advance();

                consumeIdentifierLexeme(
                    "mut",
                    "'mut' after '@'"
                );

                consume(
                    TokenKind.LET,
                    "'let' after '@mut'"
                );

                yield VariableDeclarationKind.MUTABLE_LET;
            }

            default -> throw expectedToken(
                "a variable declaration",
                peek()
            );
        };

        var nameToken = consume(
            TokenKind.IDENTIFIER,
            "a variable name"
        );

        consume(
            TokenKind.COLON,
            "':' after the variable name"
        );

        var typeToken = consume(
            TokenKind.IDENTIFIER,
            "a variable type after ':'"
        );

        consume(
            TokenKind.ASSIGN,
            "'=' after the variable type"
        );

        var initializer = parseExpression();

        var type = new TypeReference(
            typeToken.lexeme(),
            typeToken.span()
        );

        return new VariableDeclarationStatement(
            kind,
            nameToken.lexeme(),
            type,
            initializer,
            new SourceSpan(
                startToken.span().start(),
                initializer.span().end()
            )
        );
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
        return parseLogicalOrExpression();
    }

    private Expression parseLogicalOrExpression() {
        var expression = parseLogicalAndExpression();

        while (check(TokenKind.OR_OR)) {
            advance();

            var right = parseLogicalAndExpression();

            expression = createBinaryExpression(
                expression,
                BinaryOperator.LOGICAL_OR,
                right
            );
        }

        return expression;
    }

    private Expression parseLogicalAndExpression() {
        var expression = parseEqualityExpression();

        while (check(TokenKind.AND_AND)) {
            advance();

            var right = parseEqualityExpression();

            expression = createBinaryExpression(
                expression,
                BinaryOperator.LOGICAL_AND,
                right
            );
        }

        return expression;
    }

    private Expression parseEqualityExpression() {
        var expression = parseRelationalExpression();

        while (
            check(TokenKind.EQUAL_EQUAL)
                || check(TokenKind.NOT_EQUAL)
        ) {
            var operatorToken = advance();

            var operator = switch (operatorToken.kind()) {
                case EQUAL_EQUAL -> BinaryOperator.EQUAL;
                case NOT_EQUAL -> BinaryOperator.NOT_EQUAL;

                default -> throw new IllegalStateException(
                    "Unexpected equality operator token: "
                        + operatorToken.kind()
                );
            };

            var right = parseRelationalExpression();

            expression = createBinaryExpression(
                expression,
                operator,
                right
            );
        }

        return expression;
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

        return parsePostfixExpression();
    }

    private Expression parsePostfixExpression() {
        var expression = parsePrimaryExpression();

        while (check(TokenKind.LEFT_PAREN)) {
            expression = parseCallExpression(expression);
        }

        return expression;
    }

    private CallExpression parseCallExpression(
        Expression callee
    ) {
        consume(
            TokenKind.LEFT_PAREN,
            "'(' after the called expression"
        );

        var arguments = parseCallArgumentList();

        var rightParenthesis = consume(
            TokenKind.RIGHT_PAREN,
            "')' after the function call arguments"
        );

        return new CallExpression(
            callee,
            arguments,
            new SourceSpan(
                callee.span().start(),
                rightParenthesis.span().end()
            )
        );
    }

    private List<Expression> parseCallArgumentList() {
        var arguments = new ArrayList<Expression>();

        if (check(TokenKind.RIGHT_PAREN)) {
            return arguments;
        }

        while (true) {
            arguments.add(parseExpression());

            if (!match(TokenKind.COMMA)) {
                break;
            }

            if (check(TokenKind.RIGHT_PAREN)) {
                throw expectedToken(
                    "an argument after ','",
                    peek()
                );
            }
        }

        return arguments;
    }

    private Expression parsePrimaryExpression() {
        return switch (peek().kind()) {
            case INTEGER_LITERAL,
                 FLOAT_LITERAL,
                 TRUE,
                 FALSE,
                 CHAR_LITERAL,
                 STRING_LITERAL -> parseLiteralExpression();

            case IDENTIFIER -> parseNameOrQualifiedNameExpression();

            case LEFT_PAREN -> parseParenthesizedExpression();

            default -> throw expectedToken(
                "an expression",
                peek()
            );
        };
    }

    private Expression parseNameOrQualifiedNameExpression() {
        var qualifier = parseNameExpression();

        if (!match(TokenKind.DOUBLE_COLON)) return qualifier;

        var member = parseNameExpression();

        if (check(TokenKind.DOUBLE_COLON))
            throw expectedToken("a call or expression operator after the namespace-qualified name", peek());

        return new QualifiedNameExpression(
            qualifier,
            member,
            new SourceSpan(qualifier.span().start(), member.span().end())
        );
    }

    private NameExpression parseNameExpression() {
        var token = consume(TokenKind.IDENTIFIER, "an identifier");

        return new NameExpression(token.lexeme(), token.span());
    }

    private ParenthesizedExpression
    parseParenthesizedExpression() {
        var leftParenthesis = consume(TokenKind.LEFT_PAREN, "'('");
        var expression = parseExpression();
        var rightParenthesis = consume(TokenKind.RIGHT_PAREN, "')' after the parenthesized expression");

        return new ParenthesizedExpression(
            expression,
            new SourceSpan(leftParenthesis.span().start(), rightParenthesis.span().end())
        );
    }

    private Expression parseRelationalExpression() {
        var expression = parseAdditiveExpression();

        while (
            check(TokenKind.LESS)
                || check(TokenKind.LESS_EQUAL)
                || check(TokenKind.GREATER)
                || check(TokenKind.GREATER_EQUAL)
        ) {
            var operatorToken = advance();

            var operator = switch (operatorToken.kind()) {
                case LESS -> BinaryOperator.LESS_THAN;

                case LESS_EQUAL ->
                    BinaryOperator.LESS_THAN_OR_EQUAL;

                case GREATER ->
                    BinaryOperator.GREATER_THAN;

                case GREATER_EQUAL ->
                    BinaryOperator.GREATER_THAN_OR_EQUAL;

                default -> throw new IllegalStateException(
                    "Unexpected relational operator token: "
                        + operatorToken.kind()
                );
            };

            var right = parseAdditiveExpression();

            expression = createBinaryExpression(
                expression,
                operator,
                right
            );
        }

        return expression;
    }

    private Expression parseAdditiveExpression() {
        var expression = parseMultiplicativeExpression();

        while (
            check(TokenKind.PLUS)
                || check(TokenKind.MINUS)
        ) {
            var operatorToken = advance();

            var operator = switch (operatorToken.kind()) {
                case PLUS -> BinaryOperator.ADD;
                case MINUS -> BinaryOperator.SUBTRACT;

                default -> throw new IllegalStateException(
                    "Unexpected additive operator token: "
                        + operatorToken.kind()
                );
            };

            var right = parseMultiplicativeExpression();

            expression = createBinaryExpression(
                expression,
                operator,
                right
            );
        }

        return expression;
    }

    private Expression parseMultiplicativeExpression() {
        var expression = parseUnaryExpression();

        while (
            check(TokenKind.STAR)
                || check(TokenKind.SLASH)
                || check(TokenKind.PERCENT)
        ) {
            var operatorToken = advance();

            var operator = switch (operatorToken.kind()) {
                case STAR -> BinaryOperator.MULTIPLY;
                case SLASH -> BinaryOperator.DIVIDE;
                case PERCENT -> BinaryOperator.REMAINDER;

                default -> throw new IllegalStateException(
                    "Unexpected multiplicative operator token: "
                        + operatorToken.kind()
                );
            };

            var right = parseUnaryExpression();

            expression = createBinaryExpression(
                expression,
                operator,
                right
            );
        }

        return expression;
    }

    private static BinaryExpression createBinaryExpression(Expression left, BinaryOperator operator, Expression right) {
        return new BinaryExpression(
            left,
            operator,
            right,
            new SourceSpan(
                left.span().start(),
                right.span().end()
            )
        );
    }

    private Token consumeIdentifierLexeme(String expectedLexeme, String expectation) {
        var token = consume(
            TokenKind.IDENTIFIER,
            expectation
        );

        if (!token.lexeme().equals(expectedLexeme)) {
            throw expectedToken(
                expectation,
                token
            );
        }

        return token;
    }
}
