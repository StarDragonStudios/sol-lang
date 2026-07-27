package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrFunctionSignatureLowererTest {
    @Test
    void lowersParametersAndReturnTypeInDeclarationOrder() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                fn compare(left: int, right: float, enabled: boolean, marker: char) -> boolean
                    return enabled
                end
                """
            )
        );

        var result = SemanticAnalyzer.analyze(unit);

        assertTrue(result.diagnostics().isEmpty());

        var declaration = (FunctionDeclaration) unit.declarations().getFirst();
        var function = result.model().symbolOf(declaration).orElseThrow();
        var programContext = new IrProgramLoweringContext();
        programContext.assignFunctionId(function);

        var signature = IrFunctionSignatureLowerer.lower(function, result.model(), programContext);

        assertEquals(new IrFunctionId(0), signature.id());
        assertEquals("compare", signature.name());
        assertEquals(PrimitiveIrType.BOOLEAN, signature.returnType());
        assertEquals(4, signature.parameters().size());
        assertEquals(new IrValueId(0), signature.parameters().get(0).id());
        assertEquals(new IrValueId(1), signature.parameters().get(1).id());
        assertEquals(new IrValueId(2), signature.parameters().get(2).id());
        assertEquals(new IrValueId(3), signature.parameters().get(3).id());
        assertEquals(PrimitiveIrType.INT, signature.parameters().get(0).type());
        assertEquals(PrimitiveIrType.FLOAT, signature.parameters().get(1).type());
        assertEquals(PrimitiveIrType.BOOLEAN, signature.parameters().get(2).type());
        assertEquals(PrimitiveIrType.CHAR, signature.parameters().get(3).type());
    }

    @Test
    void rejectsNonCanonicalFunctionSymbols() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                @fn calculate() -> int
                """
            )
        );

        var result = SemanticAnalyzer.analyze(unit);
        var declaration = (FunctionDeclaration) unit.declarations().getFirst();
        var canonical = result.model().symbolOf(declaration).orElseThrow();
        var wrapper = new FunctionSymbol(declaration);
        var programContext = new IrProgramLoweringContext();
        programContext.assignFunctionId(wrapper);

        assertThrows(
            IrLoweringException.class,
            () -> IrFunctionSignatureLowerer.lower(
                wrapper,
                result.model(),
                programContext
            )
        );

        assertSame(canonical, result.model().symbolOf(declaration).orElseThrow());
    }

    @Test
    void requiresPreassignedFunctionIdentifiers() {
        var unit = Parser.parse(
            Lexer.scan(
                """
                @fn calculate() -> int
                """
            )
        );

        var result = SemanticAnalyzer.analyze(unit);
        var declaration = (FunctionDeclaration) unit.declarations().getFirst();
        var function = result.model().symbolOf(declaration).orElseThrow();

        assertThrows(
            IrLoweringException.class,
            () -> IrFunctionSignatureLowerer.lower(function, result.model(), new IrProgramLoweringContext())
        );
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(
            NullPointerException.class,
            () -> IrFunctionSignatureLowerer.lower(null, null, null)
        );
    }
}
