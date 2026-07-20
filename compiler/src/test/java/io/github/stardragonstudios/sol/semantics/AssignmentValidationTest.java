package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.syntax.AssignmentStatement;
import io.github.stardragonstudios.sol.syntax.CompilationUnit;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.Statement;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentValidationTest {
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
    void acceptsAssignmentsToMutableLocals() {
        var analysis = analyze(
            """
            fn test() -> void
                @mut let integer: int = 0
                @mut let floating: float = 0.0
                @mut let logical: boolean = false
                @mut let character: char = 'a'
                @mut let text: string = "before"

                integer = 1
                floating = 1.5
                logical = true
                character = 'b'
                text = "after"
                return
            end
            """
        );

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void rejectsAssignmentsToImmutableSymbols() {
        var analysis = analyze(
            """
            fn test(parameter: int) -> void
                const constant: int = 1
                let immutable: int = 2

                constant = 3
                immutable = 4
                parameter = 5
                return
            end
            """
        );

        var diagnostics =
            analysis.result().diagnostics();

        assertEquals(
            List.of(
                "SOL-S010",
                "SOL-S010",
                "SOL-S010"
            ),
            diagnostics.stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                "Cannot assign to immutable variable 'constant'.",
                "Cannot assign to immutable variable 'immutable'.",
                "Cannot assign to immutable parameter 'parameter'."
            ),
            diagnostics.stream()
                .map(Diagnostic::message)
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
    void rejectsFunctionAssignmentTargets() {
        var analysis = analyze(
            """
            fn calculate() -> int
                return 1
            end

            fn test() -> void
                calculate = 2
                return
            end
            """
        );

        var test = function(analysis, 1);

        var assignment = assertInstanceOf(
            AssignmentStatement.class,
            test.body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        var diagnostic =
            analysis.result()
                .diagnostics()
                .getFirst();

        assertEquals(
            "SOL-S009",
            diagnostic.code()
        );

        assertEquals(
            "Cannot assign to 'calculate' because it is not a variable.",
            diagnostic.message()
        );

        assertEquals(
            assignment.target().span(),
            diagnostic.span()
        );

        assertSame(
            analysis.result()
                .model()
                .symbolOf(
                    function(analysis, 0)
                )
                .orElseThrow(),
            analysis.result()
                .model()
                .assignmentTargetOf(assignment)
                .orElseThrow()
        );
    }

    @Test
    void preservesUnresolvedTargetBehavior() {
        var analysis = analyze(
            """
            fn test() -> void
                missing = 1
                return
            end
            """
        );

        var assignment = assertInstanceOf(
            AssignmentStatement.class,
            function(analysis, 0)
                .body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        assertEquals(
            List.of("SOL-S002"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            analysis.result()
                .model()
                .assignmentTargetOf(assignment)
                .isEmpty()
        );
    }

    @Test
    void rejectsIncompatibleAssignedValues() {
        var analysis = analyze(
            """
            fn test() -> void
                @mut let count: int = 0
                count = 1.5
                return
            end
            """
        );

        var assignment = assignmentAt(
            function(analysis, 0)
                .body()
                .orElseThrow()
                .statements(),
            1
        );

        var diagnostic =
            analysis.result()
                .diagnostics()
                .getFirst();

        assertEquals(
            "SOL-S011",
            diagnostic.code()
        );

        assertEquals(
            "Cannot assign value of type 'float' "
                + "to 'count' of type 'int'.",
            diagnostic.message()
        );

        assertEquals(
            assignment.value().span(),
            diagnostic.span()
        );
    }

    @Test
    void reportsMutabilityAndTypeIndependently() {
        var analysis = analyze(
            """
            fn test() -> void
                let count: int = 0
                count = 1.5
                return
            end
            """
        );

        assertEquals(
            List.of(
                "SOL-S010",
                "SOL-S011"
            ),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );
    }

    @Test
    void suppressesDependentAssignmentDiagnostics() {
        var analysis = analyze(
            """
            fn test() -> void
                @mut let first: int = 0
                @mut let second: Missing = 0

                first = missing
                second = 1
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
    }

    @Test
    void assignmentsRespectNestedShadowing() {
        var analysis = analyze(
            """
            fn test(flag: boolean) -> void
                @mut let value: int = 0

                if flag then
                    let value: int = 1
                    value = 2
                end

                value = 3
                return
            end
            """
        );

        var function = function(analysis, 0);
        var statements =
            function.body()
                .orElseThrow()
                .statements();

        var outerDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(0)
        );

        var conditional = assertInstanceOf(
            io.github.stardragonstudios.sol.syntax.ConditionalStatement.class,
            statements.get(1)
        );

        var innerStatements =
            conditional.thenBlock().statements();

        var innerDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            innerStatements.get(0)
        );

        var innerAssignment =
            assignmentAt(innerStatements, 1);

        var outerAssignment =
            assignmentAt(statements, 2);

        var model =
            analysis.result().model();

        assertSame(
            model.symbolOf(innerDeclaration)
                .orElseThrow(),
            model.assignmentTargetOf(innerAssignment)
                .orElseThrow()
        );

        assertSame(
            model.symbolOf(outerDeclaration)
                .orElseThrow(),
            model.assignmentTargetOf(outerAssignment)
                .orElseThrow()
        );

        assertEquals(
            List.of("SOL-S010"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );
    }

    @Test
    void assignedValuesKeepExpressionTypes() {
        var analysis = analyze(
            """
            fn test() -> void
                @mut let count: int = 0
                count = 1
                return
            end
            """
        );

        var assignment = assignmentAt(
            function(analysis, 0)
                .body()
                .orElseThrow()
                .statements(),
            1
        );

        assertSame(
            BuiltInTypes.INT,
            analysis.result()
                .model()
                .typeOf(assignment.value())
                .orElseThrow()
        );
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

    private static AssignmentStatement assignmentAt(
        List<Statement> statements,
        int index
    ) {
        return assertInstanceOf(
            AssignmentStatement.class,
            statements.get(index)
        );
    }
}
