package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.InjectionDeclaration;
import io.github.stardragonstudios.sol.syntax.QualifiedNameExpression;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NamespaceInjectionResolutionTest {
    @Test
    void resolvesAliasedNamespaceCalls() {
        var console = module(
            "std.console",
            """
            @fn read() -> int
            """
        );

        var application = module(
            "application",
            """
            inject namespace std.console as io

            fn test() -> int
                return io::read()
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(console, application)
        );

        var consoleAnalysis = program
            .analysisOf(console.name())
            .orElseThrow();

        var applicationAnalysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var readDeclaration = functionAt(
            console,
            0
        );

        var readSymbol = consoleAnalysis
            .model()
            .symbolOf(readDeclaration)
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

        var qualified = assertInstanceOf(
            QualifiedNameExpression.class,
            call.callee()
        );

        var model = applicationAnalysis.model();

        var namespace = model
            .injectedNamespaceOf(injection)
            .orElseThrow();

        assertEquals(
            "io",
            namespace.name()
        );

        assertSame(
            program.moduleOf(console.name())
                .orElseThrow(),
            namespace.targetModule()
        );

        assertSame(
            namespace,
            model.moduleScope()
                .lookupLocal("io")
                .orElseThrow()
        );

        assertSame(
            readSymbol,
            model.symbolOf(qualified)
                .orElseThrow()
        );

        assertSame(
            readSymbol,
            model.calledFunctionOf(call)
                .orElseThrow()
        );

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(call)
                .orElseThrow()
        );

        assertTrue(
            applicationAnalysis.diagnostics()
                .isEmpty()
        );
    }

    @Test
    void derivesDefaultNamespaceFromFinalModuleSegment() {
        var console = module(
            "std.console",
            """
            @fn read() -> int
            """
        );

        var application = module(
            "application",
            """
            inject namespace std.console

            fn test() -> int
                return console::read()
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(console, application)
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
            "console",
            analysis.model()
                .injectedNamespaceOf(injection)
                .orElseThrow()
                .name()
        );

        assertTrue(
            analysis.diagnostics().isEmpty()
        );
    }

    @Test
    void reportsUnknownMembersAndStillAnalyzesArguments() {
        var console = module(
            "std.console",
            """
            @fn read() -> int
            """
        );

        var application = module(
            "application",
            """
            inject namespace std.console as io

            fn test() -> void
                let result: int = io::missing(other)
                return
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(console, application)
        );

        var analysis = program
            .analysisOf(application.name())
            .orElseThrow();

        assertEquals(
            List.of(
                "SOL-S022",
                "SOL-S002"
            ),
            analysis.diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                "Module 'std.console' does not declare function 'missing'.",
                "Unresolved name 'other'."
            ),
            analysis.diagnostics()
                .stream()
                .map(Diagnostic::message)
                .toList()
        );
    }

    @Test
    void reportsNonNamespaceQualifiers() {
        var application = module(
            "application",
            """
            fn test(value: int) -> void
                let result: int = value::read()
                return
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(application)
        );

        var analysis = program
            .analysisOf(application.name())
            .orElseThrow();

        assertEquals(
            List.of("SOL-S021"),
            analysis.diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            "Name 'value' does not refer to an injected namespace.",
            analysis.diagnostics()
                .getFirst()
                .message()
        );
    }

    @Test
    void unresolvedQualifiersSuppressDependentDiagnostics() {
        var application = module(
            "application",
            """
            fn test() -> void
                let result: int = missing::read()
                return
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(application)
        );

        var analysis = program
            .analysisOf(application.name())
            .orElseThrow();

        assertEquals(
            List.of("SOL-S002"),
            analysis.diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            "Unresolved name 'missing'.",
            analysis.diagnostics()
                .getFirst()
                .message()
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

    @Test
    void allowsMultipleAliasesForTheSameModule() {
        var console = module(
            "std.console",
            """
            @fn read() -> int
            """
        );

        var application = module(
            "application",
            """
            inject namespace std.console as first
            inject namespace std.console as second

            fn test() -> int
                return first::read() + second::read()
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(console, application)
        );

        var analysis = program
            .analysisOf(application.name())
            .orElseThrow();

        var firstInjection = assertInstanceOf(
            InjectionDeclaration.class,
            application.unit()
                .declarations()
                .get(0)
        );

        var secondInjection = assertInstanceOf(
            InjectionDeclaration.class,
            application.unit()
                .declarations()
                .get(1)
        );

        var firstNamespace = analysis.model()
            .injectedNamespaceOf(firstInjection)
            .orElseThrow();

        var secondNamespace = analysis.model()
            .injectedNamespaceOf(secondInjection)
            .orElseThrow();

        assertEquals(
            "first",
            firstNamespace.name()
        );

        assertEquals(
            "second",
            secondNamespace.name()
        );

        assertSame(
            firstNamespace.targetModule(),
            secondNamespace.targetModule()
        );

        assertSame(
            program.moduleOf(console.name())
                .orElseThrow(),
            firstNamespace.targetModule()
        );

        assertTrue(
            analysis.diagnostics().isEmpty()
        );
    }

    @Test
    void namespaceAliasesConflictWithLocalFunctions() {
        var console = module(
            "std.console",
            """
            @fn read() -> int
            """
        );

        var application = module(
            "application",
            """
            inject namespace std.console as io

            fn io() -> int
                return 1
            end
            """
        );

        var program = SemanticAnalyzer.analyzeModules(
            List.of(console, application)
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
                .injectedNamespaceOf(injection)
                .isEmpty()
        );

        assertInstanceOf(
            FunctionSymbol.class,
            analysis.model()
                .moduleScope()
                .lookupLocal("io")
                .orElseThrow()
        );
    }

    @Test
    void semanticModelModuleQueriesRejectNull() {
        var application = module(
            "application",
            """
            fn test() -> void
                return
            end
            """
        );

        var model = SemanticAnalyzer.analyzeModules(
                List.of(application)
            ).analysisOf(application.name())
            .orElseThrow()
            .model();

        assertThrows(
            NullPointerException.class,
            () -> model.symbolOf(
                (QualifiedNameExpression) null
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> model.injectedModuleOf(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> model.directlyInjectedFunctionsOf(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> model.injectedNamespaceOf(null)
        );
    }
}
