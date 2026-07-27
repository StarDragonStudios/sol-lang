package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrModuleLowererTest {
    @Test
    void lowersFunctionsInModuleDeclarationOrder() {
        var name = new ModuleName(List.of("example", "math"));

        var program = SemanticAnalyzer.analyzeModules(
            List.of(
                sourceModule(
                    name,
                    """
                    @fn external(value: int) -> int

                    fn calculate() -> int
                        return 1 + 2
                    end
                    """
                )
            )
        );

        var module = program.moduleOf(name).orElseThrow();
        var analysis = program.analysisOf(name).orElseThrow();
        var context = new IrProgramLoweringContext();

        for (var function : module.exportedFunctions()) context.assignFunctionId(function);

        var loweredFunctions = new IdentityHashMap<FunctionSymbol, IrFunction>();
        var lowered = IrModuleLowerer.lower(module, analysis, context, loweredFunctions);

        assertEquals(new IrModuleName(List.of("example", "math")), lowered.name());
        assertEquals(List.of("external", "calculate"), lowered.functions().stream().map(IrFunction::name).toList());
        assertEquals(new IrFunctionId(0), lowered.functions().get(0).id());
        assertEquals(new IrFunctionId(1), lowered.functions().get(1).id());
        assertFalse(lowered.functions().get(0).hasBody());
        assertTrue(lowered.functions().get(1).hasBody());
        assertSame(lowered.functions().get(0), loweredFunctions.get(module.exportedFunctions().get(0)));
        assertSame(lowered.functions().get(1), loweredFunctions.get(module.exportedFunctions().get(1)));
    }

    @Test
    void rejectsAnalysisFromAnotherModule() {
        var firstName = new ModuleName(List.of("first"));
        var secondName = new ModuleName(List.of("second"));
        var program = SemanticAnalyzer.analyzeModules(
            List.of(
                sourceModule(
                    firstName,
                    """
                    @fn first() -> void
                    """
                ),
                sourceModule(
                    secondName,
                    """
                    @fn second() -> void
                    """
                )
            )
        );

        var first = program.moduleOf(firstName).orElseThrow();
        var secondAnalysis = program.analysisOf(secondName).orElseThrow();
        var context = new IrProgramLoweringContext();

        for (var function : first.exportedFunctions()) context.assignFunctionId(function);

        assertThrows(
            IrLoweringException.class,
            () -> IrModuleLowerer.lower(first, secondAnalysis, context, new IdentityHashMap<>())
        );
    }

    private static SourceModule sourceModule(ModuleName name, String source) {
        return new SourceModule(name, Parser.parse(Lexer.scan(source)));
    }
}
