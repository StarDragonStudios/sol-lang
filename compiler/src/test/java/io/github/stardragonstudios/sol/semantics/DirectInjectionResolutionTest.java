package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.InjectionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectInjectionResolutionTest {
    @Test
    void resolvesSelectiveDirectInjectionsAndCalls() {
        var operations = module(
            "math.operations",
            """
            @fn add(left: int, right: int) -> int
            @fn negate(value: int) -> int
            """
        );

        var application = module(
            "application",
            """
            inject math.operations only add

            fn calculate() -> int
                return add(1, 2)
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(operations, application)
        );

        var operationsAnalysis = program
            .analysisOf(operations.name())
            .orElseThrow();

        var applicationAnalysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var addDeclaration = functionAt(
            operations,
            0
        );

        var addSymbol = operationsAnalysis
            .model()
            .symbolOf(addDeclaration)
            .orElseThrow();

        var injection = assertInstanceOf(
            InjectionDeclaration.class,
            application.unit()
                .declarations()
                .getFirst()
        );

        var call = returnCall(
            functionAt(application, 1)
        );

        var model = applicationAnalysis.model();

        assertSame(
            program.moduleOf(operations.name())
                .orElseThrow(),
            model.injectedModuleOf(injection)
                .orElseThrow()
        );

        assertEquals(
            List.of(addSymbol),
            model.directlyInjectedFunctionsOf(
                injection
            )
        );

        assertSame(
            addSymbol,
            model.calledFunctionOf(call)
                .orElseThrow()
        );

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(call)
                .orElseThrow()
        );

        assertTrue(
            operationsAnalysis.diagnostics()
                .isEmpty()
        );

        assertTrue(
            applicationAnalysis.diagnostics()
                .isEmpty()
        );
    }

    @Test
    void injectsEveryLocallyDeclaredFunction() {
        var operations = module(
            "math.operations",
            """
            @fn add(left: int, right: int) -> int
            @fn negate(value: int) -> int
            """
        );

        var application = module(
            "application",
            """
            inject math.operations

            fn calculate() -> int
                return negate(add(1, 2))
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(operations, application)
        );

        var analysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var injection = assertInstanceOf(
            InjectionDeclaration.class,
            application.unit()
                .declarations()
                .getFirst()
        );

        assertEquals(
            List.of("add", "negate"),
            analysis.model()
                .directlyInjectedFunctionsOf(injection)
                .stream()
                .map(FunctionSymbol::name)
                .toList()
        );

        assertTrue(
            analysis.diagnostics().isEmpty()
        );
    }

    @Test
    void reportsUnresolvedModulesAndUnknownSelectedFunctions() {
        var operations = module(
            "math.operations",
            """
            @fn add(left: int, right: int) -> int
            """
        );

        var application = module(
            "application",
            """
            inject missing.module
            inject math.operations only add, missing

            fn calculate() -> int
                return add(1, 2)
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(operations, application)
        );

        var analysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var diagnostics =
            analysis.diagnostics();

        assertEquals(
            List.of(
                "SOL-S019",
                "SOL-S020"
            ),
            diagnostics.stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                "Cannot resolve module 'missing.module'.",
                "Module 'math.operations' does not declare function 'missing'."
            ),
            diagnostics.stream()
                .map(Diagnostic::message)
                .toList()
        );

        var unresolvedInjection =
            assertInstanceOf(
                InjectionDeclaration.class,
                application.unit()
                    .declarations()
                    .get(0)
            );

        var partialInjection =
            assertInstanceOf(
                InjectionDeclaration.class,
                application.unit()
                    .declarations()
                    .get(1)
            );

        assertEquals(
            unresolvedInjection.modulePath()
                .span(),
            diagnostics.get(0).span()
        );

        assertEquals(
            partialInjection.span(),
            diagnostics.get(1).span()
        );

        assertTrue(
            analysis.model()
                .injectedModuleOf(
                    unresolvedInjection
                )
                .isEmpty()
        );

        assertEquals(
            List.of("add"),
            analysis.model()
                .directlyInjectedFunctionsOf(
                    partialInjection
                )
                .stream()
                .map(FunctionSymbol::name)
                .toList()
        );
    }

    @Test
    void localFunctionsTakePrecedenceOverInjectedFunctions() {
        var operations = module(
            "math.operations",
            """
            @fn add(left: int, right: int) -> int
            """
        );

        var application = module(
            "application",
            """
            inject math.operations only add

            fn add(left: int, right: int) -> int
                return left
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(operations, application)
        );

        var analysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var injection = assertInstanceOf(
            InjectionDeclaration.class,
            application.unit()
                .declarations()
                .getFirst()
        );

        assertEquals(
            List.of("SOL-S001"),
            analysis.diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            injection.span(),
            analysis.diagnostics()
                .getFirst()
                .span()
        );

        assertTrue(
            analysis.model()
                .directlyInjectedFunctionsOf(
                    injection
                )
                .isEmpty()
        );

        var localFunction = functionAt(
            application,
            1
        );

        assertSame(
            analysis.model()
                .symbolOf(localFunction)
                .orElseThrow(),
            analysis.model()
                .moduleScope()
                .lookupLocal("add")
                .orElseThrow()
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
