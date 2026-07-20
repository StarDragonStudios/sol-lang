package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorTypeCheckingTest {
    private record TypedExpression(
        Expression expression,
        SemanticAnalysisResult result
    ) {}

    @Test
    void acceptsValidUnaryOperators() {
        assertExpressionType("!b", BuiltInTypes.BOOLEAN);
        assertExpressionType("-i", BuiltInTypes.INT);
        assertExpressionType("+i", BuiltInTypes.INT);
        assertExpressionType("-f", BuiltInTypes.FLOAT);
        assertExpressionType("+f", BuiltInTypes.FLOAT);
    }

    @Test
    void rejectsInvalidUnaryOperands() {
        assertInvalidExpression(
            "-b",
            "SOL-S004",
            "Unary operator '-' is not defined for type 'boolean'."
        );

        assertInvalidExpression(
            "!i",
            "SOL-S004",
            "Unary operator '!' is not defined for type 'int'."
        );

        assertInvalidExpression(
            "+s",
            "SOL-S004",
            "Unary operator '+' is not defined for type 'string'."
        );
    }

    @Test
    void acceptsArithmeticOperators() {
        for (var operator : List.of("*", "/", "+", "-")) {
            assertExpressionType(
                "i %s j".formatted(operator),
                BuiltInTypes.INT
            );

            assertExpressionType(
                "f %s g".formatted(operator),
                BuiltInTypes.FLOAT
            );
        }

        assertExpressionType(
            "i % j",
            BuiltInTypes.INT
        );
    }

    @Test
    void rejectsInvalidArithmeticOperands() {
        assertInvalidExpression(
            "i + f",
            "SOL-S005",
            "Binary operator '+' is not defined for types 'int' and 'float'."
        );

        assertInvalidExpression(
            "f % g",
            "SOL-S005",
            "Binary operator '%' is not defined for types 'float' and 'float'."
        );

        assertInvalidExpression(
            "s + s",
            "SOL-S005",
            "Binary operator '+' is not defined for types 'string' and 'string'."
        );
    }

    @Test
    void checksRelationalEqualityAndLogicalOperators() {
        assertExpressionType("i < j", BuiltInTypes.BOOLEAN);
        assertExpressionType("f <= g", BuiltInTypes.BOOLEAN);
        assertExpressionType("i > j", BuiltInTypes.BOOLEAN);
        assertExpressionType("f >= g", BuiltInTypes.BOOLEAN);

        assertExpressionType("i == j", BuiltInTypes.BOOLEAN);
        assertExpressionType("f != g", BuiltInTypes.BOOLEAN);
        assertExpressionType("b == true", BuiltInTypes.BOOLEAN);
        assertExpressionType("c == 'x'", BuiltInTypes.BOOLEAN);
        assertExpressionType("s != \"sol\"", BuiltInTypes.BOOLEAN);

        assertExpressionType("b && true", BuiltInTypes.BOOLEAN);
        assertExpressionType("b || false", BuiltInTypes.BOOLEAN);
    }

    @Test
    void rejectsInvalidComparisonAndLogicalOperands() {
        assertInvalidExpression(
            "c < c",
            "SOL-S005",
            "Binary operator '<' is not defined for types 'char' and 'char'."
        );

        assertInvalidExpression(
            "i == f",
            "SOL-S005",
            "Binary operator '==' is not defined for types 'int' and 'float'."
        );

        assertInvalidExpression(
            "i && j",
            "SOL-S005",
            "Binary operator '&&' is not defined for types 'int' and 'int'."
        );
    }

    @Test
    void typesNestedExpressions() {
        var typed = analyzeInitializer(
            "!(i < j && b)"
        );

        var unary = assertInstanceOf(
            UnaryExpression.class,
            typed.expression()
        );

        var parenthesized = assertInstanceOf(
            ParenthesizedExpression.class,
            unary.operand()
        );

        var logical = assertInstanceOf(
            BinaryExpression.class,
            parenthesized.expression()
        );

        var relational = assertInstanceOf(
            BinaryExpression.class,
            logical.left()
        );

        var model = typed.result().model();

        assertSame(
            BuiltInTypes.BOOLEAN,
            model.typeOf(relational).orElseThrow()
        );

        assertSame(
            BuiltInTypes.BOOLEAN,
            model.typeOf(logical).orElseThrow()
        );

        assertSame(
            BuiltInTypes.BOOLEAN,
            model.typeOf(parenthesized).orElseThrow()
        );

        assertSame(
            BuiltInTypes.BOOLEAN,
            model.typeOf(unary).orElseThrow()
        );

        assertTrue(
            typed.result().diagnostics().isEmpty()
        );
    }

    @Test
    void suppressesCascadingOperatorDiagnostics() {
        var typed = analyzeInitializer(
            "missing + 1"
        );

        assertSame(
            BuiltInTypes.ERROR,
            typed.result()
                .model()
                .typeOf(typed.expression())
                .orElseThrow()
        );

        assertEquals(
            List.of("SOL-S002"),
            typed.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );
    }

    @Test
    void validatesConditionalTypesAndSuppressesCascades() {
        var unit = Parser.parse(Lexer.scan(
            """
            fn test(flag: boolean) -> void
                if flag then
                    return
                end

                while true do
                    return
                end

                if 1 then
                    return
                end

                while missing do
                    return
                end

                return
            end
            """
        ));

        var result = SemanticAnalyzer.analyze(unit);

        assertEquals(
            List.of(
                "SOL-S006",
                "SOL-S002"
            ),
            result.diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            "Condition must have type 'boolean', but found 'int'.",
            result.diagnostics().get(0).message()
        );
    }

    @Test
    void preservesDiagnosticOrderSeverityAndSpans() {
        var unit = Parser.parse(Lexer.scan(
            """
            fn test() -> void
                let first: int = -true
                let second: int = 1 + 2.0

                if 1 then
                    return
                end

                return
            end
            """
        ));

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var statements =
            function.body().orElseThrow().statements();

        var firstDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(0)
        );

        var secondDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(1)
        );

        var conditional = assertInstanceOf(
            ConditionalStatement.class,
            statements.get(2)
        );

        var result = SemanticAnalyzer.analyze(unit);
        var diagnostics = result.diagnostics();

        assertEquals(
            List.of(
                "SOL-S004",
                "SOL-S005",
                "SOL-S006"
            ),
            diagnostics.stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            diagnostics.stream().allMatch(
                diagnostic ->
                    diagnostic.severity()
                        == DiagnosticSeverity.ERROR
            )
        );

        assertEquals(
            firstDeclaration.initializer().span(),
            diagnostics.get(0).span()
        );

        assertEquals(
            secondDeclaration.initializer().span(),
            diagnostics.get(1).span()
        );

        assertEquals(
            conditional.condition().span(),
            diagnostics.get(2).span()
        );
    }

    private static void assertExpressionType(
        String sourceExpression,
        TypeSymbol expectedType
    ) {
        var typed = analyzeInitializer(sourceExpression);

        assertSame(
            expectedType,
            typed.result()
                .model()
                .typeOf(typed.expression())
                .orElseThrow()
        );

        assertTrue(
            typed.result().diagnostics().isEmpty()
        );
    }

    private static void assertInvalidExpression(
        String sourceExpression,
        String expectedCode,
        String expectedMessage
    ) {
        var typed = analyzeInitializer(sourceExpression);

        assertSame(
            BuiltInTypes.ERROR,
            typed.result()
                .model()
                .typeOf(typed.expression())
                .orElseThrow()
        );

        var diagnostic =
            typed.result().diagnostics().getFirst();

        assertEquals(expectedCode, diagnostic.code());
        assertEquals(expectedMessage, diagnostic.message());

        assertEquals(
            typed.expression().span(),
            diagnostic.span()
        );

        assertEquals(
            DiagnosticSeverity.ERROR,
            diagnostic.severity()
        );
    }

    private static TypedExpression analyzeInitializer(
        String sourceExpression
    ) {
        var source = """
            fn test(i: int, j: int, f: float, g: float, b: boolean, c: char, s: string) -> void
                let result: int = %s
                return
            end
            """.formatted(sourceExpression);

        var unit = Parser.parse(Lexer.scan(source));
        var result = SemanticAnalyzer.analyze(unit);

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().getFirst()
        );

        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        return new TypedExpression(
            declaration.initializer(),
            result
        );
    }
}
