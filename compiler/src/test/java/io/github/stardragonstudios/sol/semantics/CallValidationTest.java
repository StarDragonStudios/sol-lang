package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallValidationTest {
    private record Analysis(CompilationUnit unit, SemanticAnalysisResult result) {}

    private static Analysis analyze(String source) {
        var unit = Parser.parse(Lexer.scan(source));
        return new Analysis(unit, SemanticAnalyzer.analyze(unit));
    }

    @Test
    void resolvesForwardParenthesizedAndBodylessCalls() {
        var analysis = analyze(
            """
            fn first(value: int) -> int
                return (second)(value)
            end

            @fn second(value: int) -> int
            """
        );

        var first = functionAt(analysis.unit(), 0);
        var second = functionAt(analysis.unit(), 1);

        var call = assertInstanceOf(
            CallExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                first.body().orElseThrow().statements().getFirst()
            ).expression().orElseThrow()
        );

        var model = analysis.result().model();

        assertSame(model.symbolOf(second).orElseThrow(), model.calledFunctionOf(call).orElseThrow());
        assertSame(BuiltInTypes.INT, model.typeOf(call).orElseThrow());
        assertTrue(analysis.result().diagnostics().isEmpty());
    }

    @Test
    void callTargetQueriesUseIdentityAndRejectNull() {
        var analysis = analyze(
            """
            @fn external() -> int

            fn test() -> int
                return external()
            end
            """
        );

        var call = returnCall(functionAt(analysis.unit(), 1));
        var clone = new CallExpression(call.callee(), call.arguments(), call.span());

        assertEquals(call, clone);

        var model = analysis.result().model();

        assertTrue(model.calledFunctionOf(call).isPresent());
        assertTrue(model.calledFunctionOf(clone).isEmpty());

        assertThrows(NullPointerException.class, () -> model.calledFunctionOf(null));
    }

    @Test
    void rejectsCallsToLocalsAndParameters() {
        var analysis = analyze(
            """
            fn test(parameter: int) -> void
                let local: int = 1
                let first: int = local()
                let second: int = parameter()
                return
            end
            """
        );

        assertEquals(
            List.of("SOL-S013", "SOL-S013"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                "Expression of type 'int' is not callable.",
                "Expression of type 'int' is not callable."
            ),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::message)
                .toList()
        );
    }

    @Test
    void rejectsCallingTheResultOfAValueFunction() {
        var analysis = analyze(
            """
            @fn factory() -> int

            fn test() -> void
                let result: int = factory()()
                return
            end
            """
        );

        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            functionAt(analysis.unit(), 1)
                .body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        var outerCall = assertInstanceOf(CallExpression.class, declaration.initializer());
        var innerCall = assertInstanceOf(CallExpression.class, outerCall.callee());
        var model = analysis.result().model();

        assertTrue(model.calledFunctionOf(innerCall).isPresent());
        assertTrue(model.calledFunctionOf(outerCall).isEmpty());

        assertEquals(
            List.of("SOL-S013"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );
    }

    @Test
    void validatesArgumentCountAndCorrespondingTypes() {
        var analysis = analyze(
            """
            @fn add(left: int, right: int) -> int

            fn test() -> void
                let valid: int = add(1, 2)
                let tooFew: int = add(1.5)
                let tooMany: int = add(1, 2, 3)
                return
            end
            """
        );

        assertEquals(
            List.of(
                "SOL-S014",
                "SOL-S015",
                "SOL-S014"
            ),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                "Function 'add' expects 2 arguments, but received 1.",
                "Argument 1 of function 'add' expects type 'int', but found 'float'.",
                "Function 'add' expects 2 arguments, but received 3."
            ),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::message)
                .toList()
        );

        var tooFew = initializerCall(functionAt(analysis.unit(), 1), 1);
        var model = analysis.result().model();

        assertTrue(model.calledFunctionOf(tooFew).isPresent());
        assertSame(BuiltInTypes.INT, model.typeOf(tooFew).orElseThrow());
    }

    @Test
    void reportsMultipleIncompatibleArguments() {
        var analysis = analyze(
            """
            @fn combine(value: int, enabled: boolean) -> int

            fn test() -> void
                let result: int = combine(1.5, 1)
                return
            end
            """
        );

        assertEquals(
            List.of("SOL-S015", "SOL-S015"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );
    }

    @Test
    void unresolvedCalleesStillTraverseArguments() {
        var analysis = analyze(
            """
            fn test() -> void
                let result: int = missing(other)
                return
            end
            """
        );

        var call = initializerCall(functionAt(analysis.unit(), 0), 0);

        assertEquals(
            List.of("SOL-S002", "SOL-S002"),
            analysis.result()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            analysis.result()
                .model()
                .calledFunctionOf(call)
                .isEmpty()
        );
    }

    @Test
    void erroneousArgumentsSuppressTypeMismatchButPreserveCallTarget() {
        var analysis = analyze(
            """
            @fn accept(value: int) -> int

            fn test() -> void
                let result: int = accept(missing)
                return
            end
            """
        );

        var call = initializerCall(functionAt(analysis.unit(), 1), 0);

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
                .calledFunctionOf(call)
                .isPresent()
        );

        assertSame(
            BuiltInTypes.INT,
            analysis.result()
                .model()
                .typeOf(call)
                .orElseThrow()
        );
    }

    private static FunctionDeclaration functionAt(CompilationUnit unit, int index) {
        return assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(index));
    }

    private static CallExpression returnCall(FunctionDeclaration function) {
        return assertInstanceOf(
            CallExpression.class,
            assertInstanceOf(
                ReturnStatement.class,
                function.body().orElseThrow().statements().getFirst()
            ).expression().orElseThrow()
        );
    }

    private static CallExpression initializerCall(FunctionDeclaration function, int statementIndex) {
        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body()
                .orElseThrow()
                .statements()
                .get(statementIndex)
        );

        return assertInstanceOf(CallExpression.class, declaration.initializer());
    }
}
