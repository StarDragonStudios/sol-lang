package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrProgramLoweringContextTest {
    @Test
    void assignsFunctionIdentifiersInTraversalOrder() {
        var symbols = functionSymbols(
            """
            @fn first() -> void
            @fn second() -> int
            """
        );

        var context = new IrProgramLoweringContext();

        assertEquals(new IrFunctionId(0), context.assignFunctionId(symbols.get(0)));
        assertEquals(new IrFunctionId(1), context.assignFunctionId(symbols.get(1)));
        assertEquals(new IrFunctionId(0), context.functionId(symbols.get(0)));
        assertEquals(new IrFunctionId(1), context.functionId(symbols.get(1)));
    }

    @Test
    void usesCanonicalFunctionSymbolIdentity() {
        var symbol = functionSymbols(
            """
            @fn calculate() -> int
            """
        ).getFirst();

        var equivalentWrapper = new FunctionSymbol(symbol.declaration());
        var context = new IrProgramLoweringContext();

        context.assignFunctionId(symbol);

        assertThrows(
            IrLoweringException.class,
            () -> context.functionId(equivalentWrapper)
        );
    }

    @Test
    void rejectsDuplicateFunctionAssignment() {
        var symbol = functionSymbols(
            """
            @fn calculate() -> int
            """
        ).getFirst();

        var context = new IrProgramLoweringContext();
        context.assignFunctionId(symbol);

        assertThrows(
            IrLoweringException.class,
            () -> context.assignFunctionId(symbol)
        );
    }

    @Test
    void rejectsUnknownAndNullFunctionSymbols() {
        var symbol = functionSymbols(
            """
            @fn calculate() -> int
            """
        ).getFirst();

        var context = new IrProgramLoweringContext();

        assertThrows(
            IrLoweringException.class,
            () -> context.functionId(symbol)
        );

        assertThrows(
            NullPointerException.class,
            () -> context.assignFunctionId(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> context.functionId(null)
        );
    }

    private static List<FunctionSymbol> functionSymbols(String source) {
        var unit = Parser.parse(Lexer.scan(source));
        var result = SemanticAnalyzer.analyze(unit);

        return unit.declarations()
            .stream()
            .map(declaration -> (FunctionDeclaration) declaration)
            .map(declaration -> result.model().symbolOf(declaration).orElseThrow())
            .toList();
    }
}
