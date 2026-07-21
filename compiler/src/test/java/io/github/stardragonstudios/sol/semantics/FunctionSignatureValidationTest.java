package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.syntax.CompilationUnit;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionSignatureValidationTest {
    private record Analysis(CompilationUnit unit, SemanticAnalysisResult result) {}

    private static Analysis analyze(String source) {
        var unit = Parser.parse(Lexer.scan(source));
        return new Analysis(unit, SemanticAnalyzer.analyze(unit));
    }

    @Test
    void acceptsEveryValueParameterType() {
        var analysis = analyze(
            """
            @fn external(integer: int, floating: float, logical: boolean, character: char, text: string) -> void
            """
        );

        assertTrue(analysis.result().diagnostics().isEmpty());
    }

    @Test
    void rejectsVoidParametersInBodyfulAndBodylessFunctions() {
        var analysis = analyze(
            """
            fn first(value: void) -> void
                return
            end

            @fn second(value: void) -> void
            """
        );

        var first = functionAt(analysis.unit(), 0);
        var second = functionAt(analysis.unit(), 1);
        var diagnostics = analysis.result().diagnostics();

        assertEquals(
            List.of("SOL-S012", "SOL-S012"),
            diagnostics.stream().map(Diagnostic::code).toList()
        );

        assertEquals(
            List.of(
                "Parameter 'value' of function 'first' cannot have non-value type 'void'.",
                "Parameter 'value' of function 'second' cannot have non-value type 'void'."
            ),
            diagnostics.stream().map(Diagnostic::message).toList()
        );

        assertEquals(
            List.of(
                first.parameters().getFirst().type().span(),
                second.parameters().getFirst().type().span()
            ),
            diagnostics.stream().map(Diagnostic::span).toList()
        );

        assertTrue(
            diagnostics.stream().allMatch(
                diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR
            )
        );
    }

    @Test
    void suppressesInvalidParameterDiagnosticForUnknownTypes() {
        var analysis = analyze(
            """
            @fn external(value: Missing) -> void
            """
        );

        assertEquals(
            List.of("SOL-S003"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );
    }

    private static FunctionDeclaration functionAt(CompilationUnit unit, int index) {
        return assertInstanceOf(
            FunctionDeclaration.class,
            unit.declarations().get(index)
        );
    }
}
