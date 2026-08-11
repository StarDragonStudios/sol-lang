package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.StructType;
import io.github.stardragonstudios.sol.semantics.types.TypeParameterType;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;
import io.github.stardragonstudios.sol.syntax.StructDeclaration;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenericSemanticAnalysisTest {
    @Test
    void resolvesOpenTypesAndSubstitutesConcreteStructsAndFunctions() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Box<T>
                value: T
            end

            fn wrap<T>(value: T) -> Box<T>
                return Box<T> { value: value }
            end

            fn identity<T>(value: T) -> T
                return value
            end

            fn use() -> int
                let boxed: Box<int> = wrap<int>(42)
                return identity<int>(boxed.value)
            end
            """
        ));
        var analysis = SemanticAnalyzer.analyze(unit);

        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());

        var box = assertInstanceOf(StructDeclaration.class, unit.declarations().getFirst());
        var boxSymbol = analysis.model().symbolOf(box).orElseThrow();

        assertEquals(SymbolKind.TYPE_PARAMETER, boxSymbol.typeParameters().getFirst().kind());
        assertInstanceOf(TypeParameterType.class, analysis.model().typeOf(box.fields().getFirst().type()).orElseThrow());

        var use = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(3));
        var declaration = assertInstanceOf(VariableDeclarationStatement.class, use.body().orElseThrow().statements().getFirst());
        var declaredType = assertInstanceOf(StructType.class, analysis.model().typeOf(declaration.type()).orElseThrow());

        assertSame(boxSymbol, declaredType.symbol());
        assertEquals(List.of(BuiltInTypes.INT), declaredType.arguments());

        var wrapCall = assertInstanceOf(CallExpression.class, declaration.initializer());
        var returned = assertInstanceOf(ReturnStatement.class, use.body().orElseThrow().statements().get(1));
        var identityCall = assertInstanceOf(CallExpression.class, returned.expression().orElseThrow());

        assertEquals(List.of(BuiltInTypes.INT), analysis.model().typeArgumentsOf(wrapCall));
        assertEquals(List.of(BuiltInTypes.INT), analysis.model().typeArgumentsOf(identityCall));
        assertSame(BuiltInTypes.INT, analysis.model().typeOf(identityCall).orElseThrow());
    }

    @Test
    void resolvesCrossModuleGenericInstantiations() {
        var core = module(
            "core",
            """
            struct Result<T>
                value: T
            end

            fn result<T>(value: T) -> Result<T>
                return Result<T> { value: value }
            end
            """
        );
        var application = module(
            "application",
            """
            inject core only Result, result

            @init
            fn launch() -> int
                let answer: Result<int> = result<int>(42)
                return answer.value
            end
            """
        );
        var program = SemanticAnalyzer.analyzeProgram(List.of(core, application));

        assertTrue(program.programDiagnostics().isEmpty(), program.programDiagnostics().toString());
        assertTrue(program.analysisOf(core.name()).orElseThrow().diagnostics().isEmpty());
        assertTrue(program.analysisOf(application.name()).orElseThrow().diagnostics().isEmpty());
    }

    @Test
    void reportsGenericDeclarationArityAndTypeArgumentErrors() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Box<T, T>
                value: T
            end

            fn identity<T>(value: T) -> T
                return value
            end

            fn invalid<U>(missing: Missing) -> void
                let bare: Box = Box<int, string> { value: 1 }
                let primitive: int<string> = 1
                identity(1)
                identity<int, string>(1)
                identity<void>(1)
                return
            end
            """
        ));
        var analysis = SemanticAnalyzer.analyze(unit);
        var codes = analysis.diagnostics().stream().map(diagnostic -> diagnostic.code()).collect(Collectors.toSet());

        assertEquals(Set.of("SOL-S003", "SOL-S039", "SOL-S040", "SOL-S041"), codes);
    }

    @Test
    void validatesConcreteSubstitutedValueTypes() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Box<T>
                value: T
            end

            fn identity<T>(value: T) -> T
                return value
            end

            fn invalid() -> void
                let box: Box<int> = Box<int> { value: true }
                identity<int>(true)
                return
            end
            """
        ));
        var analysis = SemanticAnalyzer.analyze(unit);
        var codes = analysis.diagnostics().stream().map(diagnostic -> diagnostic.code()).collect(Collectors.toSet());

        assertEquals(Set.of("SOL-S015", "SOL-S034"), codes);
    }

    @Test
    void detectsSubstitutionInducedRecursiveLayoutsWithoutRejectingFiniteNesting() {
        var recursive = Parser.parse(Lexer.scan(
            """
            struct Outer<T>
                nested: Inner<Outer<T>>
            end

            struct Inner<T>
                value: T
            end
            """
        ));
        var recursiveAnalysis = SemanticAnalyzer.analyze(recursive);

        assertEquals(
            List.of("SOL-S029"),
            recursiveAnalysis.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList()
        );

        var finite = Parser.parse(Lexer.scan(
            """
            struct Box<T>
                value: T
            end

            fn preserve(value: Box<Box<int>>) -> Box<Box<int>>
                return value
            end
            """
        ));
        var finiteAnalysis = SemanticAnalyzer.analyze(finite);

        assertTrue(finiteAnalysis.diagnostics().isEmpty(), finiteAnalysis.diagnostics().toString());
    }

    @Test
    void rejectsGenericExecutableEntryPoints() {
        var application = module(
            "application",
            """
            @init
            fn launch<T>() -> int
                return 0
            end
            """
        );
        var program = SemanticAnalyzer.analyzeProgram(List.of(application));

        assertTrue(program.entryPoint().isEmpty());
        assertEquals(
            List.of("SOL-S040"),
            program.analysisOf(application.name()).orElseThrow().diagnostics().stream()
                .map(diagnostic -> diagnostic.code())
                .toList()
        );
    }

    private static SourceModule module(String name, String source) {
        return new SourceModule(new ModuleName(List.of(name)), Parser.parse(Lexer.scan(source)));
    }
}
