package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionTypeTest {
    private record Analysis(
        CompilationUnit unit,
        SemanticAnalysisResult result
    ) {}

    private static Analysis analyze(String source) {
        var unit = Parser.parse(Lexer.scan(source));

        return new Analysis(
            unit,
            SemanticAnalyzer.analyze(unit)
        );
    }

    @Test
    void infersEveryLiteralType() {
        var analysis = analyze(
            """
            fn test() -> void
                let integer: int = 1
                let floating: float = 1.5
                let logical: boolean = true
                let character: char = 'x'
                let text: string = "sol"
                return
            end
            """
        );

        var function = functionAt(analysis.unit(), 0);
        var statements = function.body().orElseThrow().statements();
        var model = analysis.result().model();

        assertInitializerType(
            statements,
            0,
            BuiltInTypes.INT,
            model
        );

        assertInitializerType(
            statements,
            1,
            BuiltInTypes.FLOAT,
            model
        );

        assertInitializerType(
            statements,
            2,
            BuiltInTypes.BOOLEAN,
            model
        );

        assertInitializerType(
            statements,
            3,
            BuiltInTypes.CHAR,
            model
        );

        assertInitializerType(
            statements,
            4,
            BuiltInTypes.STRING,
            model
        );

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void infersParameterLocalAndParenthesizedTypes() {
        var analysis = analyze(
            """
            fn test(value: int) -> int
                let copy: int = (value)
                return copy
            end
            """
        );

        var function = functionAt(analysis.unit(), 0);
        var statements = function.body().orElseThrow().statements();

        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(0)
        );

        var parenthesized = assertInstanceOf(
            ParenthesizedExpression.class,
            declaration.initializer()
        );

        var parameterName = assertInstanceOf(
            NameExpression.class,
            parenthesized.expression()
        );

        var returnStatement = assertInstanceOf(
            ReturnStatement.class,
            statements.get(1)
        );

        var localName = assertInstanceOf(
            NameExpression.class,
            returnStatement.expression().orElseThrow()
        );

        var model = analysis.result().model();

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(parameterName).orElseThrow()
        );

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(parenthesized).orElseThrow()
        );

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(localName).orElseThrow()
        );

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void assignsErrorTypeToUnresolvedNames() {
        var analysis = analyze(
            """
            fn test() -> int
                return missing
            end
            """
        );

        var function = functionAt(analysis.unit(), 0);

        var returnedName = assertInstanceOf(
            NameExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                function.body()
                    .orElseThrow()
                    .statements()
                    .getFirst()
            ).expression().orElseThrow()
        );

        assertSame(
            BuiltInTypes.ERROR,
            analysis.result()
                .model()
                .typeOf(returnedName)
                .orElseThrow()
        );

        assertEquals(
            "SOL-S002",
            analysis.result()
                .diagnostics()
                .getFirst()
                .code()
        );
    }

    @Test
    void infersForwardAndBodylessFunctionCallResults() {
        var analysis = analyze(
            """
            fn first(value: int) -> float
                return second(value)
            end

            @fn second(value: int) -> float
            """
        );

        var first = functionAt(analysis.unit(), 0);

        var call = assertInstanceOf(
            CallExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                first.body()
                    .orElseThrow()
                    .statements()
                    .getFirst()
            ).expression().orElseThrow()
        );

        var callee = assertInstanceOf(
            NameExpression.class,
            call.callee()
        );

        var argument = assertInstanceOf(
            NameExpression.class,
            call.arguments().getFirst()
        );

        var model = analysis.result().model();

        assertSame(
            BuiltInTypes.FLOAT,
            model.typeOf(call).orElseThrow()
        );

        assertSame(
            BuiltInTypes.ERROR,
            model.typeOf(callee).orElseThrow()
        );

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(argument).orElseThrow()
        );

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void recoversWhenCallingANonFunctionValue() {
        var analysis = analyze(
            """
            fn test(value: int) -> int
                return value()
            end
            """
        );

        var function = functionAt(analysis.unit(), 0);

        var call = assertInstanceOf(
            CallExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                function.body()
                    .orElseThrow()
                    .statements()
                    .getFirst()
            ).expression().orElseThrow()
        );

        var callee = assertInstanceOf(
            NameExpression.class,
            call.callee()
        );

        var model = analysis.result().model();

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(callee).orElseThrow()
        );

        assertSame(
            BuiltInTypes.ERROR,
            model.typeOf(call).orElseThrow()
        );

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void usesExpressionIdentityAndRejectsNullQueries() {
        var analysis = analyze(
            """
            fn test() -> int
                return 1
            end
            """
        );

        var function = functionAt(analysis.unit(), 0);

        var literal = assertInstanceOf(
            LiteralExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                function.body()
                    .orElseThrow()
                    .statements()
                    .getFirst()
            ).expression().orElseThrow()
        );

        var clone = new LiteralExpression(
            literal.kind(),
            literal.lexeme(),
            literal.span()
        );

        assertEquals(literal, clone);

        var model = analysis.result().model();

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(literal).orElseThrow()
        );

        assertTrue(
            model.typeOf(clone).isEmpty()
        );

        assertThrows(
            NullPointerException.class,
            () -> model.typeOf((Expression) null)
        );
    }

    private static FunctionDeclaration functionAt(
        CompilationUnit unit,
        int index
    ) {
        return assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().get(index)
        );
    }

    private static void assertInitializerType(
        java.util.List<Statement> statements,
        int index,
        io.github.stardragonstudios.sol.semantics.types.TypeSymbol expected,
        SemanticModel model
    ) {
        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(index)
        );

        assertSame(
            expected,
            model.typeOf(
                declaration.initializer()
            ).orElseThrow()
        );
    }
}
