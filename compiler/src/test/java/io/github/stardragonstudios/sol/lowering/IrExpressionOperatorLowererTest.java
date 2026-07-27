package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.*;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrExpressionOperatorLowererTest {
    @Test
    void lowersUnaryExpressionsInEvaluationOrder() {
        var lowered = lowerReturnedExpression(
            """
            fn invert() -> boolean
                return !(!true)
            end
            """
        );

        var inner = assertInstanceOf(IrUnaryInstruction.class, lowered.instructions().get(0));
        var outer = assertInstanceOf(IrUnaryInstruction.class, lowered.instructions().get(1));

        assertEquals(IrUnaryOperator.LOGICAL_NOT, inner.operator());
        assertEquals(IrUnaryOperator.LOGICAL_NOT, outer.operator());
        assertSame(inner, outer.operand());
        assertSame(outer, lowered.value());
        assertEquals(new IrValueId(1), inner.id());
        assertEquals(new IrValueId(2), outer.id());
        assertEquals(PrimitiveIrType.BOOLEAN, outer.type());
    }

    @Test
    void lowersArithmeticByExpressionTreeOrder() {
        var lowered = lowerReturnedExpression(
            """
            fn calculate() -> int
                return 1 + 2 * 3
            end
            """
        );

        assertEquals(2, lowered.instructions().size());

        var multiply = assertInstanceOf(IrBinaryInstruction.class, lowered.instructions().get(0));
        var add = assertInstanceOf(IrBinaryInstruction.class, lowered.instructions().get(1));

        assertEquals(IrBinaryOperator.MULTIPLY, multiply.operator());
        assertEquals(IrBinaryOperator.ADD, add.operator());
        assertSame(multiply, add.right());
        assertSame(add, lowered.value());
        assertEquals(new IrValueId(3), multiply.id());
        assertEquals(new IrValueId(4), add.id());
        assertEquals(PrimitiveIrType.INT, add.type());
    }

    @Test
    void lowersRelationalEqualityAndLogicalOperators() {
        var relational = lowerReturnedExpression(
            """
            fn compare(left: float, right: float) -> boolean
                return left <= right
            end
            """
        );

        var equality = lowerReturnedExpression(
            """
            fn compare(left: char, right: char) -> boolean
                return left != right
            end
            """
        );

        var logical = lowerReturnedExpression(
            """
            fn combine(left: boolean, right: boolean) -> boolean
                return left && !right
            end
            """
        );

        assertEquals(
            IrBinaryOperator.LESS_THAN_OR_EQUAL,
            assertInstanceOf(IrBinaryInstruction.class, relational.value()).operator()
        );

        assertEquals(
            IrBinaryOperator.NOT_EQUAL,
            assertInstanceOf(IrBinaryInstruction.class, equality.value()).operator()
        );

        var logicalResult = assertInstanceOf(IrBinaryInstruction.class, logical.value());

        assertEquals(IrBinaryOperator.LOGICAL_AND, logicalResult.operator());
        assertEquals(
            IrUnaryOperator.LOGICAL_NOT,
            assertInstanceOf(IrUnaryInstruction.class, logicalResult.right()).operator()
        );

        assertEquals(2, logical.instructions().size());
    }

    @Test
    void supportsEveryArithmeticOperator() {
        assertBinaryOperator("left * right", IrBinaryOperator.MULTIPLY);
        assertBinaryOperator("left / right", IrBinaryOperator.DIVIDE);
        assertBinaryOperator("left % right", IrBinaryOperator.REMAINDER);
        assertBinaryOperator("left + right", IrBinaryOperator.ADD);
        assertBinaryOperator("left - right", IrBinaryOperator.SUBTRACT);
    }

    @Test
    void supportsEveryRelationalOperator() {
        assertRelationalOperator("left < right", IrBinaryOperator.LESS_THAN);
        assertRelationalOperator("left <= right", IrBinaryOperator.LESS_THAN_OR_EQUAL);
        assertRelationalOperator("left > right", IrBinaryOperator.GREATER_THAN);
        assertRelationalOperator("left >= right", IrBinaryOperator.GREATER_THAN_OR_EQUAL);
    }

    @Test
    void supportsEqualityAndLogicalOperators() {
        assertBooleanBinaryOperator("left == right", IrBinaryOperator.EQUAL);
        assertBooleanBinaryOperator("left != right", IrBinaryOperator.NOT_EQUAL);
        assertBooleanBinaryOperator("left && right", IrBinaryOperator.LOGICAL_AND);
        assertBooleanBinaryOperator("left || right", IrBinaryOperator.LOGICAL_OR);
    }

    @Test
    void supportsEveryUnaryOperator() {
        assertUnaryOperator("+value", "int", IrUnaryOperator.POSITIVE);
        assertUnaryOperator("-value", "int", IrUnaryOperator.NEGATE);
        assertUnaryOperator("!value", "boolean", IrUnaryOperator.LOGICAL_NOT);
    }

    private static void assertBinaryOperator(String expression, IrBinaryOperator expected) {
        var lowered = lowerReturnedExpression(
            """
            fn calculate(left: int, right: int) -> int
                return %s
            end
            """.formatted(expression)
        );

        assertEquals(
            expected,
            assertInstanceOf(IrBinaryInstruction.class, lowered.value()).operator()
        );
    }

    private static void assertBooleanBinaryOperator(String expression, IrBinaryOperator expected) {
        var lowered = lowerReturnedExpression(
            """
            fn calculate(left: boolean, right: boolean) -> boolean
                return %s
            end
            """.formatted(expression)
        );

        assertEquals(
            expected,
            assertInstanceOf(IrBinaryInstruction.class, lowered.value()).operator()
        );
    }

    private static void assertUnaryOperator(String expression, String type, IrUnaryOperator expected) {
        var lowered = lowerReturnedExpression(
            """
            fn calculate(value: %s) -> %s
                return %s
            end
            """.formatted(type, type, expression)
        );

        assertEquals(
            expected,
            assertInstanceOf(IrUnaryInstruction.class, lowered.value()).operator()
        );
    }

    private static LoweredExpression lowerReturnedExpression(String source) {
        var unit = Parser.parse(Lexer.scan(source));
        var result = SemanticAnalyzer.analyze(unit);

        assertTrue(
            result.diagnostics().isEmpty(),
            () -> result.diagnostics().toString()
        );

        var declaration = (FunctionDeclaration) unit.declarations().getFirst();
        var function = result.model().symbolOf(declaration).orElseThrow();
        var programContext = new IrProgramLoweringContext();

        programContext.assignFunctionId(function);

        var signature = IrFunctionSignatureLowerer.lower(function, result.model(), programContext);
        var returnStatement = (ReturnStatement) declaration.body().orElseThrow().statements().getLast();

        IrValue value = IrExpressionLowerer.lower(
            returnStatement.expression().orElseThrow(),
            result.model(),
            signature.context()
        );

        return new LoweredExpression(value, signature.context().instructions());
    }

    private static void assertRelationalOperator(String expression, IrBinaryOperator expected) {
        var lowered = lowerReturnedExpression(
            """
            fn calculate(left: int, right: int) -> boolean
                return %s
            end
            """.formatted(expression)
        );

        assertEquals(
            expected,
            assertInstanceOf(IrBinaryInstruction.class, lowered.value()).operator()
        );
    }

    private record LoweredExpression(IrValue value, List<IrInstruction> instructions) {}
}
