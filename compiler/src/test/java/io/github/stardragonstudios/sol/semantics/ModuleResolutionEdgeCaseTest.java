package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.InjectionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleResolutionEdgeCaseTest {
    @Test
    void preservesTheFirstFunctionWhenDirectImportsConflict() {
        var first = module(
            "first",
            """
            @fn shared() -> int
            """
        );

        var second = module(
            "second",
            """
            @fn shared() -> int
            """
        );

        var application = module(
            "application",
            """
            inject first
            inject second

            fn test() -> int
                return shared()
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(first, second, application)
        );

        var firstAnalysis = program
            .analysisOf(first.name())
            .orElseThrow();

        var applicationAnalysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var firstFunction = firstAnalysis
            .model()
            .symbolOf(functionAt(first, 0))
            .orElseThrow();

        var secondInjection = assertInstanceOf(
            InjectionDeclaration.class,
            application.unit()
                .declarations()
                .get(1)
        );

        var call = returnCall(
            functionAt(application, 2)
        );

        assertEquals(
            List.of("SOL-S001"),
            applicationAnalysis.diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            secondInjection.span(),
            applicationAnalysis.diagnostics()
                .getFirst()
                .span()
        );

        assertSame(
            firstFunction,
            applicationAnalysis.model()
                .calledFunctionOf(call)
                .orElseThrow()
        );

        assertTrue(
            applicationAnalysis.model()
                .directlyInjectedFunctionsOf(
                    secondInjection
                )
                .isEmpty()
        );
    }

    @Test
    void doesNotTransitivelyReExportInjectedFunctions() {
        var base = module(
            "base",
            """
            @fn original() -> int
            """
        );

        var middle = module(
            "middle",
            """
            inject base
            """
        );

        var application = module(
            "application",
            """
            inject middle

            fn test() -> void
                let value: int = original()
                return
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(base, middle, application)
        );

        var middleAnalysis = program
            .analysisOf(middle.name())
            .orElseThrow();

        var applicationAnalysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var middleInjection = assertInstanceOf(
            InjectionDeclaration.class,
            middle.unit()
                .declarations()
                .getFirst()
        );

        var applicationInjection = assertInstanceOf(
            InjectionDeclaration.class,
            application.unit()
                .declarations()
                .getFirst()
        );

        assertEquals(
            List.of("original"),
            middleAnalysis.model()
                .directlyInjectedFunctionsOf(
                    middleInjection
                )
                .stream()
                .map(FunctionSymbol::name)
                .toList()
        );

        assertTrue(
            applicationAnalysis.model()
                .directlyInjectedFunctionsOf(
                    applicationInjection
                )
                .isEmpty()
        );

        assertEquals(
            List.of("SOL-S002"),
            applicationAnalysis.diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            "Unresolved name 'original'.",
            applicationAnalysis.diagnostics()
                .getFirst()
                .message()
        );
    }

    @Test
    void resolvesMutuallyDependentModules() {
        var first = module(
            "modules.first",
            """
            inject namespace modules.second as second

            fn from_first() -> int
                return second::from_second()
            end
            """
        );

        var second = module(
            "modules.second",
            """
            inject namespace modules.first as first

            fn from_second() -> int
                return first::from_first()
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(first, second)
        );

        var firstAnalysis = program
            .analysisOf(first.name())
            .orElseThrow();

        var secondAnalysis = program
            .analysisOf(second.name())
            .orElseThrow();

        var firstCall = returnCall(
            functionAt(first, 1)
        );

        var secondCall = returnCall(
            functionAt(second, 1)
        );

        var firstFunction = firstAnalysis
            .model()
            .symbolOf(functionAt(first, 1))
            .orElseThrow();

        var secondFunction = secondAnalysis
            .model()
            .symbolOf(functionAt(second, 1))
            .orElseThrow();

        assertSame(
            secondFunction,
            firstAnalysis.model()
                .calledFunctionOf(firstCall)
                .orElseThrow()
        );

        assertSame(
            firstFunction,
            secondAnalysis.model()
                .calledFunctionOf(secondCall)
                .orElseThrow()
        );

        assertTrue(
            firstAnalysis.diagnostics().isEmpty()
        );

        assertTrue(
            secondAnalysis.diagnostics().isEmpty()
        );
    }

    @Test
    void freezesCompletedModuleAndFunctionScopes() {
        var application = module(
            "application",
            """
            fn test(value: int) -> int
                return value
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(application)
        );

        var module = program
            .moduleOf(application.name())
            .orElseThrow();

        var analysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var function = functionAt(
            application,
            0
        );

        var functionScope = analysis
            .model()
            .scopeOf(function)
            .orElseThrow();

        assertSame(
            module.scope(),
            analysis.model().moduleScope()
        );

        assertTrue(
            module.scope().isFrozen()
        );

        assertTrue(
            functionScope.isFrozen()
        );

        assertThrows(
            IllegalStateException.class,
            () -> module.scope().declare(
                new FunctionSymbol(function)
            )
        );
    }

    @Test
    void validatesProgramAnalysisInputs() {
        assertThrows(
            NullPointerException.class,
            () -> SemanticAnalyzer.analyzeModules(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> SemanticAnalyzer.analyzeModules(
                java.util.Arrays.asList(
                    module(
                        "valid",
                        """
                        @fn test() -> void
                        """
                    ),
                    null
                )
            )
        );
    }

    private static SourceModule module(
        String name,
        String source
    ) {
        return new SourceModule(
            new ModuleName(
                List.of(name.split("\\."))
            ),
            Parser.parse(
                Lexer.scan(source)
            )
        );
    }

    private static FunctionDeclaration functionAt(
        SourceModule module,
        int index
    ) {
        return assertInstanceOf(
            FunctionDeclaration.class,
            module.unit()
                .declarations()
                .get(index)
        );
    }

    private static CallExpression returnCall(
        FunctionDeclaration function
    ) {
        return assertInstanceOf(
            CallExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                function.body()
                    .orElseThrow()
                    .statements()
                    .getFirst()
            ).expression().orElseThrow()
        );
    }
}
