package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticAnalysisResult;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrFunctionSignatureTest {
    @Test
    void createsBodylessFunctionDeclarations() {
        var analyzed = analyze(
            """
            @fn external(value: int) -> boolean
            """
        );

        var programContext = new IrProgramLoweringContext();
        programContext.assignFunctionId(analyzed.function());

        var signature = IrFunctionSignatureLowerer.lower(
            analyzed.function(),
            analyzed.result().model(),
            programContext
        );

        var declaration = signature.declaration();

        assertEquals(new IrFunctionId(0), declaration.id());
        assertEquals("external", declaration.name());
        assertEquals(PrimitiveIrType.BOOLEAN, declaration.returnType());
        assertFalse(declaration.hasBody());
        assertEquals(1, declaration.parameters().size());
        assertEquals("value", declaration.parameters().getFirst().name());
        assertEquals(PrimitiveIrType.INT, declaration.parameters().getFirst().type());
    }

    @Test
    void createsDefinitionsWithCanonicalParameters() {
        var analyzed = analyze(
            """
            fn perform(value: int) -> void
                return
            end
            """
        );

        var programContext = new IrProgramLoweringContext();

        programContext.assignFunctionId(analyzed.function());

        var signature = IrFunctionSignatureLowerer.lower(
            analyzed.function(),
            analyzed.result().model(),
            programContext
        );

        var parameterSymbol = analyzed.result()
            .model()
            .symbolOf(analyzed.declaration().parameters().getFirst())
            .orElseThrow();

        assertSame(signature.parameters().getFirst(), signature.context().parameter(parameterSymbol));

        var block = new IrBasicBlock(
            signature.context().nextBlockId(),
            List.of(),
            IrReturnTerminator.bare()
        );

        var definition = signature.definition(List.of(block));

        assertTrue(definition.hasBody());
        assertSame(signature.parameters().getFirst(), definition.parameters().getFirst());
        assertSame(block, definition.entryBlock().orElseThrow());
    }

    @Test
    void rejectsMismatchedFunctionContexts() {
        var first = analyze(
            """
            @fn first() -> void
            """
        );

        var second = analyze(
            """
            @fn second() -> void
            """
        );

        assertThrows(
            IrLoweringException.class,
            () -> new IrFunctionSignature(
                first.function(),
                new IrFunctionId(0),
                List.of(),
                PrimitiveIrType.VOID,
                new IrFunctionLoweringContext(second.function())
            )
        );
    }

    private static AnalyzedFunction analyze(String source) {
        var unit = Parser.parse(Lexer.scan(source));
        var result = SemanticAnalyzer.analyze(unit);

        assertTrue(result.diagnostics().isEmpty());

        var declaration = (FunctionDeclaration) unit.declarations().getFirst();
        var function = result.model().symbolOf(declaration).orElseThrow();

        return new AnalyzedFunction(declaration, function, result);
    }

    private record AnalyzedFunction(FunctionDeclaration declaration, FunctionSymbol function, SemanticAnalysisResult result) {}
}
