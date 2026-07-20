package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.syntax.CompilationUnit;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableValidationTest {
    private record Analysis(
        CompilationUnit unit,
        SemanticAnalysisResult result
    ) {}

    private static Analysis analyze(String source) {
        var unit = Parser.parse(Lexer.scan(source));

        return new Analysis(
            unit,
            SemanticAnalyzer.analyze(unit)
        );
    }

    @Test
    void exposesVariableMutabilityMetadata() {
        var analysis = analyze(
            """
            fn test() -> void
                const constant: int = 1
                let immutable: int = 2
                @mut let mutable: int = 3
                return
            end
            """
        );

        var declarations =
            function(analysis).body()
                .orElseThrow()
                .statements();

        var constant = variableAt(declarations, 0);
        var immutable = variableAt(declarations, 1);
        var mutable = variableAt(declarations, 2);

        var constantSymbol = analysis.result()
            .model()
            .symbolOf(constant)
            .orElseThrow();

        var immutableSymbol = analysis.result()
            .model()
            .symbolOf(immutable)
            .orElseThrow();

        var mutableSymbol = analysis.result()
            .model()
            .symbolOf(mutable)
            .orElseThrow();

        assertTrue(constantSymbol.isConstant());
        assertFalse(constantSymbol.isMutable());

        assertFalse(immutableSymbol.isConstant());
        assertFalse(immutableSymbol.isMutable());

        assertFalse(mutableSymbol.isConstant());
        assertTrue(mutableSymbol.isMutable());

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void acceptsMatchingPrimitiveInitializers() {
        var analysis = analyze(
            """
            fn test() -> void
                let integer: int = 1
                let floating: float = 1.5
                let logical: boolean = true
                let character: char = 'x'
                let text: string = "sol"
                return
            end
            """
        );

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void rejectsIncompatibleInitializers() {
        var analysis = analyze(
            """
            fn test() -> void
                let integer: int = 1.5
                let floating: float = 1
                let logical: boolean = 1
                return
            end
            """
        );

        var statements =
            function(analysis).body()
                .orElseThrow()
                .statements();

        var integer = variableAt(statements, 0);
        var floating = variableAt(statements, 1);
        var logical = variableAt(statements, 2);

        var diagnostics =
            analysis.result().diagnostics();

        assertEquals(
            List.of(
                "SOL-S008",
                "SOL-S008",
                "SOL-S008"
            ),
            diagnostics.stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                "Cannot initialize variable 'integer' of type "
                    + "'int' with value of type 'float'.",
                "Cannot initialize variable 'floating' of type "
                    + "'float' with value of type 'int'.",
                "Cannot initialize variable 'logical' of type "
                    + "'boolean' with value of type 'int'."
            ),
            diagnostics.stream()
                .map(Diagnostic::message)
                .toList()
        );

        assertEquals(
            List.of(
                integer.initializer().span(),
                floating.initializer().span(),
                logical.initializer().span()
            ),
            diagnostics.stream()
                .map(Diagnostic::span)
                .toList()
        );

        assertTrue(
            diagnostics.stream().allMatch(
                diagnostic ->
                    diagnostic.severity()
                        == DiagnosticSeverity.ERROR
            )
        );
    }

    @Test
    void rejectsVoidForEveryVariableKind() {
        var analysis = analyze(
            """
            @fn noValue() -> void

            fn test() -> void
                const first: void = noValue()
                let second: void = noValue()
                @mut let third: void = noValue()
                return
            end
            """
        );

        var statements =
            function(analysis, 1).body()
                .orElseThrow()
                .statements();

        var first = variableAt(statements, 0);
        var second = variableAt(statements, 1);
        var third = variableAt(statements, 2);

        var diagnostics =
            analysis.result().diagnostics();

        assertEquals(
            List.of(
                "SOL-S007",
                "SOL-S007",
                "SOL-S007"
            ),
            diagnostics.stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                first.type().span(),
                second.type().span(),
                third.type().span()
            ),
            diagnostics.stream()
                .map(Diagnostic::span)
                .toList()
        );

        assertTrue(
            diagnostics.stream().allMatch(
                diagnostic ->
                    diagnostic.message()
                        .contains(
                            "cannot have non-value type 'void'"
                        )
            )
        );
    }

    @Test
    void suppressesDependentInitializerDiagnostics() {
        var analysis = analyze(
            """
            fn test() -> void
                let unknownType: Missing = 1
                let erroneousValue: int = missing
                return
            end
            """
        );

        assertEquals(
            List.of(
                "SOL-S003",
                "SOL-S002"
            ),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        var statements =
            function(analysis).body()
                .orElseThrow()
                .statements();

        var unknownType =
            variableAt(statements, 0);

        assertSame(
            BuiltInTypes.ERROR,
            analysis.result()
                .model()
                .typeOf(unknownType.type())
                .orElseThrow()
        );
    }

    private static FunctionDeclaration function(
        Analysis analysis
    ) {
        return function(analysis, 0);
    }

    private static FunctionDeclaration function(
        Analysis analysis,
        int index
    ) {
        return assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .get(index)
        );
    }

    private static VariableDeclarationStatement variableAt(
        List<?> statements,
        int index
    ) {
        return assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(index)
        );
    }
}
