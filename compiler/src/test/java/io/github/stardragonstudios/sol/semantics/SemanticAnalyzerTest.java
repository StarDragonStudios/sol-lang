package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticAnalyzerTest {
    private record Analysis(
        CompilationUnit unit,
        SemanticAnalysisResult result
    ) {
    }

    private static Analysis analyze(
        String source
    ) {
        var unit = Parser.parse(
            Lexer.scan(source)
        );

        return new Analysis(
            unit,
            SemanticAnalyzer.analyze(unit)
        );
    }

    @Test
    void createsModuleAndBodylessFunctionScopes() {
        var analysis = analyze(
            "@fn calculate(value: int) -> int"
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .getFirst()
        );

        var parameter =
            function.parameters().getFirst();

        var model = analysis.result().model();
        var moduleScope = model.moduleScope();

        assertEquals(
            ScopeKind.MODULE,
            moduleScope.kind()
        );

        assertTrue(moduleScope.parent().isEmpty());
        assertTrue(moduleScope.isFrozen());

        var functionSymbol = model
            .symbolOf(function)
            .orElseThrow();

        assertSame(
            functionSymbol,
            moduleScope
                .lookupLocal("calculate")
                .orElseThrow()
        );

        var functionScope = model
            .scopeOf(function)
            .orElseThrow();

        assertEquals(
            ScopeKind.FUNCTION,
            functionScope.kind()
        );

        assertEquals(
            moduleScope,
            functionScope.parent().orElseThrow()
        );

        assertTrue(functionScope.isFrozen());

        var parameterSymbol = model
            .symbolOf(parameter)
            .orElseThrow();

        assertSame(
            parameterSymbol,
            functionScope
                .lookupLocal("value")
                .orElseThrow()
        );

        assertTrue(function.body().isEmpty());
        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void resolvesForwardFunctionReferences() {
        var analysis = analyze(
            """
            fn first() -> int
                return second()
            end

            fn second() -> int
                return 42
            end
            """
        );

        var first = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .get(0)
        );

        var second = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .get(1)
        );

        var returnStatement = assertInstanceOf(
            ReturnStatement.class,
            first.body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        var call = assertInstanceOf(
            CallExpression.class,
            returnStatement
                .expression()
                .orElseThrow()
        );

        var callee = assertInstanceOf(
            NameExpression.class,
            call.callee()
        );

        var resolvedCallee = analysis.result()
            .model()
            .symbolOf(callee)
            .orElseThrow();

        var secondSymbol = analysis.result()
            .model()
            .symbolOf(second)
            .orElseThrow();

        assertSame(
            secondSymbol,
            resolvedCallee
        );

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void declaresLocalVariableAfterItsInitializer() {
        var analysis = analyze(
            """
            fn test() -> int
                let value: int = value
                return value
            end
            """
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .getFirst()
        );

        var statements = function.body()
            .orElseThrow()
            .statements();

        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(0)
        );

        var initializer = assertInstanceOf(
            NameExpression.class,
            declaration.initializer()
        );

        var returnStatement = assertInstanceOf(
            ReturnStatement.class,
            statements.get(1)
        );

        var returnedName = assertInstanceOf(
            NameExpression.class,
            returnStatement
                .expression()
                .orElseThrow()
        );

        var model = analysis.result().model();

        assertTrue(
            model.symbolOf(initializer).isEmpty()
        );

        var localSymbol = model
            .symbolOf(declaration)
            .orElseThrow();

        assertSame(
            localSymbol,
            model.symbolOf(returnedName)
                .orElseThrow()
        );

        assertEquals(
            1,
            analysis.result().diagnostics().size()
        );

        var diagnostic =
            analysis.result().diagnostics().getFirst();

        assertEquals("SOL-S002", diagnostic.code());

        assertEquals(
            "Unresolved name 'value'.",
            diagnostic.message()
        );

        assertEquals(
            initializer.span(),
            diagnostic.span()
        );
    }

    @Test
    void createsIndependentConditionalBranchScopes() {
        var analysis = analyze(
            """
            fn test(value: int) -> int
                if value then
                    let value: int = value
                    return value
                else
                    let value: int = value
                    return value
                end

                return value
            end
            """
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .getFirst()
        );

        var parameter =
            function.parameters().getFirst();

        var functionStatements = function.body()
            .orElseThrow()
            .statements();

        var conditional = assertInstanceOf(
            ConditionalStatement.class,
            functionStatements.get(0)
        );

        var finalReturn = assertInstanceOf(
            ReturnStatement.class,
            functionStatements.get(1)
        );

        var thenDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            conditional.thenBlock()
                .statements()
                .get(0)
        );

        var thenReturn = assertInstanceOf(
            ReturnStatement.class,
            conditional.thenBlock()
                .statements()
                .get(1)
        );

        var elseBlock =
            conditional.elseBlock().orElseThrow();

        var elseDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            elseBlock.statements().get(0)
        );

        var elseReturn = assertInstanceOf(
            ReturnStatement.class,
            elseBlock.statements().get(1)
        );

        var model = analysis.result().model();

        var parameterSymbol = model
            .symbolOf(parameter)
            .orElseThrow();

        var conditionName = assertInstanceOf(
            NameExpression.class,
            conditional.condition()
        );

        assertSame(
            parameterSymbol,
            model.symbolOf(conditionName)
                .orElseThrow()
        );

        var thenInitializer = assertInstanceOf(
            NameExpression.class,
            thenDeclaration.initializer()
        );

        var elseInitializer = assertInstanceOf(
            NameExpression.class,
            elseDeclaration.initializer()
        );

        assertSame(
            parameterSymbol,
            model.symbolOf(thenInitializer)
                .orElseThrow()
        );

        assertSame(
            parameterSymbol,
            model.symbolOf(elseInitializer)
                .orElseThrow()
        );

        var thenLocal = model
            .symbolOf(thenDeclaration)
            .orElseThrow();

        var elseLocal = model
            .symbolOf(elseDeclaration)
            .orElseThrow();

        var thenReturnedName = assertInstanceOf(
            NameExpression.class,
            thenReturn.expression().orElseThrow()
        );

        var elseReturnedName = assertInstanceOf(
            NameExpression.class,
            elseReturn.expression().orElseThrow()
        );

        assertSame(
            thenLocal,
            model.symbolOf(thenReturnedName)
                .orElseThrow()
        );

        assertSame(
            elseLocal,
            model.symbolOf(elseReturnedName)
                .orElseThrow()
        );

        var finalReturnedName = assertInstanceOf(
            NameExpression.class,
            finalReturn.expression().orElseThrow()
        );

        assertSame(
            parameterSymbol,
            model.symbolOf(finalReturnedName)
                .orElseThrow()
        );

        var functionScope = model
            .scopeOf(function)
            .orElseThrow();

        var thenScope = model
            .scopeOf(conditional.thenBlock())
            .orElseThrow();

        var elseScope = model
            .scopeOf(elseBlock)
            .orElseThrow();

        assertEquals(
            functionScope,
            thenScope.parent().orElseThrow()
        );

        assertEquals(
            functionScope,
            elseScope.parent().orElseThrow()
        );

        assertFalse(thenScope == elseScope);
        assertTrue(thenScope.isFrozen());
        assertTrue(elseScope.isFrozen());

        assertTrue(
            analysis.result().diagnostics().isEmpty()
        );
    }

    @Test
    void limitsWhileVariablesToTheirBlockScope() {
        var analysis = analyze(
            """
            fn test() -> int
                while condition do
                    let counter: int = 1
                end

                return counter
            end
            """
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .getFirst()
        );

        var statements = function.body()
            .orElseThrow()
            .statements();

        var whileStatement = assertInstanceOf(
            WhileStatement.class,
            statements.get(0)
        );

        var returnedName = assertInstanceOf(
            NameExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                statements.get(1)
            ).expression().orElseThrow()
        );

        var model = analysis.result().model();

        var whileScope = model
            .scopeOf(whileStatement.body())
            .orElseThrow();

        assertEquals(
            ScopeKind.BLOCK,
            whileScope.kind()
        );

        assertEquals(
            "counter",
            whileScope
                .declaredSymbols()
                .getFirst()
                .name()
        );

        assertTrue(
            model.symbolOf(returnedName).isEmpty()
        );

        assertEquals(
            List.of(
                "Unresolved name 'condition'.",
                "Unresolved name 'counter'."
            ),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::message)
                .toList()
        );
    }

    @Test
    void resolvesAssignmentsCallsAndNestedExpressions() {
        var analysis = analyze(
            """
            fn helper(value: int) -> int
                return value
            end

            fn test(input: int) -> int
                @mut let result: int = helper((input + missing))
                result = helper(result)
                unknown = result
                return result
            end
            """
        );

        var helper = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .get(0)
        );

        var test = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .get(1)
        );

        var statements = test.body()
            .orElseThrow()
            .statements();

        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(0)
        );

        var firstAssignment = assertInstanceOf(
            AssignmentStatement.class,
            statements.get(1)
        );

        var secondAssignment = assertInstanceOf(
            AssignmentStatement.class,
            statements.get(2)
        );

        var initializerCall = assertInstanceOf(
            CallExpression.class,
            declaration.initializer()
        );

        var helperName = assertInstanceOf(
            NameExpression.class,
            initializerCall.callee()
        );

        var parenthesized = assertInstanceOf(
            ParenthesizedExpression.class,
            initializerCall.arguments().getFirst()
        );

        var addition = assertInstanceOf(
            BinaryExpression.class,
            parenthesized.expression()
        );

        var inputName = assertInstanceOf(
            NameExpression.class,
            addition.left()
        );

        var missingName = assertInstanceOf(
            NameExpression.class,
            addition.right()
        );

        var model = analysis.result().model();

        assertSame(
            model.symbolOf(helper).orElseThrow(),
            model.symbolOf(helperName).orElseThrow()
        );

        assertSame(
            model.symbolOf(
                test.parameters().getFirst()
            ).orElseThrow(),
            model.symbolOf(inputName).orElseThrow()
        );

        assertTrue(
            model.symbolOf(missingName).isEmpty()
        );

        var resultSymbol = model
            .symbolOf(declaration)
            .orElseThrow();

        assertSame(
            resultSymbol,
            model.assignmentTargetOf(firstAssignment)
                .orElseThrow()
        );

        assertSame(
            resultSymbol,
            model.symbolOf(firstAssignment.target())
                .orElseThrow()
        );

        assertTrue(
            model.assignmentTargetOf(secondAssignment)
                .isEmpty()
        );

        assertTrue(
            model.symbolOf(secondAssignment.target())
                .isEmpty()
        );

        var secondValueName = assertInstanceOf(
            NameExpression.class,
            secondAssignment.value()
        );

        assertSame(
            resultSymbol,
            model.symbolOf(secondValueName)
                .orElseThrow()
        );

        assertEquals(
            List.of(
                "Unresolved name 'missing'.",
                "Unresolved name 'unknown'."
            ),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::message)
                .toList()
        );
    }

    @Test
    void reportsDuplicatesAndPreservesOriginalSymbols() {
        var analysis = analyze(
            """
            fn duplicate(value: int, value: int) -> void
                let value: int = 1
                let local: int = 1
                let local: int = 2
                return
            end

            fn duplicate() -> void
                return
            end
            """
        );

        var firstFunction = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .get(0)
        );

        var secondFunction = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .get(1)
        );

        var statements = firstFunction.body()
            .orElseThrow()
            .statements();

        var conflictingParameter =
            firstFunction.parameters().get(1);

        var conflictingLocal = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(0)
        );

        var firstLocal = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(1)
        );

        var duplicateLocal = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(2)
        );

        var diagnostics =
            analysis.result().diagnostics();

        assertEquals(
            List.of(
                "Duplicate declaration of 'value'.",
                "Duplicate declaration of 'value'.",
                "Duplicate declaration of 'local'.",
                "Duplicate declaration of 'duplicate'."
            ),
            diagnostics.stream()
                .map(Diagnostic::message)
                .toList()
        );

        assertTrue(
            diagnostics.stream()
                .allMatch(
                    diagnostic ->
                        diagnostic.code()
                            .equals("SOL-S001")
                )
        );

        assertEquals(
            conflictingParameter.span(),
            diagnostics.get(0).span()
        );

        assertEquals(
            conflictingLocal.span(),
            diagnostics.get(1).span()
        );

        assertEquals(
            duplicateLocal.span(),
            diagnostics.get(2).span()
        );

        assertEquals(
            secondFunction.span(),
            diagnostics.get(3).span()
        );

        var model = analysis.result().model();

        assertSame(
            model.symbolOf(firstFunction)
                .orElseThrow(),
            model.moduleScope()
                .lookupLocal("duplicate")
                .orElseThrow()
        );

        var functionScope = model
            .scopeOf(firstFunction)
            .orElseThrow();

        assertSame(
            model.symbolOf(
                firstFunction.parameters().getFirst()
            ).orElseThrow(),
            functionScope
                .lookupLocal("value")
                .orElseThrow()
        );

        assertSame(
            model.symbolOf(firstLocal)
                .orElseThrow(),
            functionScope
                .lookupLocal("local")
                .orElseThrow()
        );

        assertTrue(
            model.scopeOf(secondFunction).isPresent()
        );

        assertTrue(
            model.symbolOf(secondFunction).isPresent()
        );
    }

    @Test
    void usesSyntaxNodeIdentityAndExposesImmutableResults() {
        var analysis = analyze(
            """
            fn test(value: int) -> int
                return value
            end
            """
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .getFirst()
        );

        var returnedName = assertInstanceOf(
            NameExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                function.body()
                    .orElseThrow()
                    .statements()
                    .getFirst()
            ).expression().orElseThrow()
        );

        var structurallyEqualClone =
            new NameExpression(
                returnedName.name(),
                returnedName.span()
            );

        assertEquals(
            returnedName,
            structurallyEqualClone
        );

        assertTrue(
            analysis.result()
                .model()
                .symbolOf(returnedName)
                .isPresent()
        );

        assertTrue(
            analysis.result()
                .model()
                .symbolOf(structurallyEqualClone)
                .isEmpty()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> analysis.result()
                .diagnostics()
                .clear()
        );

        var moduleScope =
            analysis.result().model().moduleScope();

        assertTrue(moduleScope.isFrozen());

        assertThrows(
            IllegalStateException.class,
            () -> moduleScope.declare(
                analysis.result()
                    .model()
                    .symbolOf(function)
                    .orElseThrow()
            )
        );
    }

    @Test
    void rejectsNullSemanticInputs() {
        assertThrows(
            NullPointerException.class,
            () -> SemanticAnalyzer.analyze(null)
        );

        var analysis = analyze(
            "@fn test() -> void"
        );

        assertThrows(
            NullPointerException.class,
            () -> new SemanticAnalysisResult(
                null,
                List.of()
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new SemanticAnalysisResult(
                analysis.result().model(),
                null
            )
        );
    }
}
