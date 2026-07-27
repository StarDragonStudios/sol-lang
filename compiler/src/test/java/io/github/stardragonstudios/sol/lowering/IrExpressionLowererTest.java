package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBooleanConstant;
import io.github.stardragonstudios.sol.ir.IrCharConstant;
import io.github.stardragonstudios.sol.ir.IrFloatConstant;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrValue;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrExpressionLowererTest {
    @Test
    void lowersSupportedPrimitiveLiterals() {
        var integer = assertInstanceOf(
            IrIntConstant.class,
            lowerReturnedExpression(
                """
                fn value() -> int
                    return 42
                end
                """
            ).value()
        );

        var floating = assertInstanceOf(
            IrFloatConstant.class,
            lowerReturnedExpression(
                """
                fn value() -> float
                    return 12.5
                end
                """
            ).value()
        );

        var logical = assertInstanceOf(
            IrBooleanConstant.class,
            lowerReturnedExpression(
                """
                fn value() -> boolean
                    return true
                end
                """
            ).value()
        );

        var character = assertInstanceOf(
            IrCharConstant.class,
            lowerReturnedExpression(
                """
                fn value() -> char
                    return 'S'
                end
                """
            ).value()
        );

        assertEquals(42L, integer.value());
        assertEquals(12.5, floating.value());
        assertTrue(logical.value());
        assertEquals('S', character.codePoint());
        assertEquals(new IrValueId(0), integer.id());
        assertEquals(new IrValueId(0), floating.id());
        assertEquals(new IrValueId(0), logical.id());
        assertEquals(new IrValueId(0), character.id());
    }

    @Test
    void decodesCharacterEscapes() {
        var newline = assertInstanceOf(
            IrCharConstant.class,
            lowerReturnedExpression(
                """
                fn value() -> char
                    return '\\n'
                end
                """
            ).value()
        );

        var tab = assertInstanceOf(
            IrCharConstant.class,
            lowerReturnedExpression(
                """
                fn value() -> char
                    return '\\t'
                end
                """
            ).value()
        );

        var slash = assertInstanceOf(
            IrCharConstant.class,
            lowerReturnedExpression(
                """
                fn value() -> char
                    return '\\\\'
                end
                """
            ).value()
        );

        assertEquals('\n', newline.codePoint());
        assertEquals('\t', tab.codePoint());
        assertEquals('\\', slash.codePoint());
    }

    @Test
    void reusesCanonicalParameterValues() {
        var lowered = lowerReturnedExpression(
            """
            fn identity(value: int) -> int
                return value
            end
            """
        );

        assertSame(lowered.signature().parameters().getFirst(), lowered.value());
        assertEquals(new IrValueId(0), lowered.value().id());
        assertEquals(new IrValueId(1), lowered.signature().context().nextValueId());
    }

    @Test
    void removesParenthesesWithoutCreatingValues() {
        var lowered = lowerReturnedExpression(
            """
            fn identity(value: int) -> int
                return (((value)))
            end
            """
        );

        assertSame(lowered.signature().parameters().getFirst(), lowered.value());
        assertEquals(new IrValueId(1), lowered.signature().context().nextValueId());
    }

    @Test
    void rejectsStringLiteralsExplicitly() {
        var exception = assertThrows(
            IrLoweringException.class,
            () -> lowerReturnedExpression(
                """
                fn value() -> string
                    return "Sol"
                end
                """
            )
        );

        assertEquals(
            "String literals are not supported by the current Sol IR lowering subset.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsUnsupportedCallExpressions() {
        var exception = assertThrows(
            IrLoweringException.class,
            () -> lowerReturnedExpression(
                """
                fn identity(value: int) -> int
                    return value
                end

                fn use() -> int
                    return identity(1)
                end
                """,
                1
            )
        );

        assertEquals(
            "Unsupported expression syntax 'CallExpression' during IR lowering.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsIntegerOverflowExplicitly() {
        var exception = assertThrows(
            IrLoweringException.class,
            () -> lowerReturnedExpression(
                """
                fn value() -> int
                    return 999999999999999999999999999999999999
                end
                """
            )
        );

        assertTrue(exception.getMessage().contains("cannot be represented as a Sol int"));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(
            NullPointerException.class,
            () -> IrExpressionLowerer.lower(null, null, null)
        );

        assertThrows(
            NullPointerException.class,
            () -> IrLiteralLowerer.lower(null, null)
        );
    }

    private static LoweredExpression lowerReturnedExpression(String source) {
        return lowerReturnedExpression(source, 0);
    }

    private static LoweredExpression lowerReturnedExpression(String source, int functionIndex) {
        var unit = Parser.parse(Lexer.scan(source));
        var result = SemanticAnalyzer.analyze(unit);

        assertTrue(
            result.diagnostics().isEmpty(),
            () -> result.diagnostics().toString()
        );

        var declaration = (FunctionDeclaration) unit.declarations().get(functionIndex);
        var function = result.model().symbolOf(declaration).orElseThrow();
        var programContext = new IrProgramLoweringContext();
        programContext.assignFunctionId(function);

        var signature = IrFunctionSignatureLowerer.lower(function, result.model(), programContext);
        var returnStatement = (ReturnStatement) declaration.body().orElseThrow().statements().getLast();
        var expression = returnStatement.expression().orElseThrow();

        IrValue value = IrExpressionLowerer.lower(expression, result.model(), signature.context());

        return new LoweredExpression(value, signature);
    }

    private record LoweredExpression(IrValue value, IrFunctionSignature signature) {}
}
