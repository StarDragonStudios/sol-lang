package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReturnValidationTest {
    private record Analysis(CompilationUnit unit, SemanticAnalysisResult result) {}

    private static Analysis analyze(String source) {
        var unit = Parser.parse(Lexer.scan(source));
        return new Analysis(unit, SemanticAnalyzer.analyze(unit));
    }

    @Test
    void acceptsEveryPrimitiveReturnType() {
        var analysis = analyze(
            """
            fn integer() -> int
                return 1
            end

            fn floating() -> float
                return 1.5
            end

            fn logical() -> boolean
                return true
            end

            fn character() -> char
                return 'x'
            end

            fn text() -> string
                return "sol"
            end

            fn nothing() -> void
                return
            end
            """
        );

        assertTrue(analysis.result().diagnostics().isEmpty());
    }

    @Test
    void rejectsBareReturnsFromValueFunctions() {
        var analysis = analyze(
            """
            fn answer() -> int
                return
            end
            """
        );

        var returnStatement = returnAt(functionAt(analysis.unit(), 0), 0);
        var diagnostic = analysis.result().diagnostics().getFirst();

        assertEquals("SOL-S016", diagnostic.code());
        assertEquals("Function 'answer' must return a value of type 'int'.", diagnostic.message());
        assertEquals(returnStatement.span(), diagnostic.span());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
    }

    @Test
    void rejectsValuesReturnedFromVoidFunctions() {
        var analysis = analyze(
            """
            fn log() -> void
                return 1
            end
            """
        );

        var returnStatement = returnAt(functionAt(analysis.unit(), 0), 0);
        var diagnostic = analysis.result().diagnostics().getFirst();

        assertEquals("SOL-S017", diagnostic.code());
        assertEquals("Function 'log' returns 'void' and cannot return a value.", diagnostic.message());
        assertEquals(returnStatement.expression().orElseThrow().span(), diagnostic.span());
    }

    @Test
    void rejectsIncompatibleReturnValues() {
        var analysis = analyze(
            """
            fn answer() -> int
                return 1.5
            end
            """
        );

        var returnStatement = returnAt(functionAt(analysis.unit(), 0), 0);
        var diagnostic = analysis.result().diagnostics().getFirst();

        assertEquals("SOL-S018", diagnostic.code());
        assertEquals("Cannot return value of type 'float' from function 'answer' returning 'int'.", diagnostic.message());
        assertEquals(returnStatement.expression().orElseThrow().span(), diagnostic.span());
    }

    @Test
    void suppressesDependentReturnDiagnostics() {
        var analysis = analyze(
            """
            fn first() -> int
                return missing
            end

            fn second() -> Missing
                return 1
            end
            """
        );

        assertEquals(
            List.of("SOL-S002", "SOL-S003"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );
    }

    @Test
    void voidReturnStillReportsUnexpectedErroneousValue() {
        var analysis = analyze(
            """
            fn log() -> void
                return missing
            end
            """
        );

        assertEquals(
            List.of("SOL-S002", "SOL-S017"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );
    }

    @Test
    void validatesReturnsInsideNestedBlocks() {
        var analysis = analyze(
            """
            fn choose(flag: boolean) -> int
                if flag then
                    return 1
                else
                    while flag do
                        return 2
                    end
                end

                return 3
            end
            """
        );

        assertTrue(analysis.result().diagnostics().isEmpty());
    }

    private static FunctionDeclaration functionAt(CompilationUnit unit, int index) {
        return assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(index));
    }

    private static ReturnStatement returnAt(FunctionDeclaration function, int statementIndex) {
        return assertInstanceOf(
            ReturnStatement.class,
            function.body()
                .orElseThrow()
                .statements()
                .get(statementIndex)
        );
    }
}
