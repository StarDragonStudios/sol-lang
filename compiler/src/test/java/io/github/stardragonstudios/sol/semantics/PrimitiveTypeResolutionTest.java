package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.syntax.CompilationUnit;
import io.github.stardragonstudios.sol.syntax.ConditionalStatement;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.TypeReference;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;
import io.github.stardragonstudios.sol.syntax.WhileStatement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimitiveTypeResolutionTest {
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
    void resolvesPrimitiveTypesAcrossDeclarations() {
        var analysis = analyze(
            """
            @fn external(value: string) -> void

            fn test(flag: boolean, value: int) -> float
                let text: string = "sol"

                if flag then
                    let character: char = 'x'
                end

                while flag do
                    let count: int = value
                end

                return 0.0
            end
            """
        );

        var external = assertInstanceOf(
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

        var model = analysis.result().model();

        assertSame(
            BuiltInTypes.STRING,
            model.typeOf(
                external.parameters()
                    .getFirst()
                    .type()
            ).orElseThrow()
        );

        assertSame(
            BuiltInTypes.VOID,
            model.typeOf(
                external.returnType()
            ).orElseThrow()
        );

        assertSame(
            BuiltInTypes.BOOLEAN,
            model.typeOf(
                test.parameters()
                    .get(0)
                    .type()
            ).orElseThrow()
        );

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(
                test.parameters()
                    .get(1)
                    .type()
            ).orElseThrow()
        );

        assertSame(
            BuiltInTypes.FLOAT,
            model.typeOf(
                test.returnType()
            ).orElseThrow()
        );

        var statements = test.body()
            .orElseThrow()
            .statements();

        var textDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            statements.get(0)
        );

        var conditional = assertInstanceOf(
            ConditionalStatement.class,
            statements.get(1)
        );

        var whileStatement = assertInstanceOf(
            WhileStatement.class,
            statements.get(2)
        );

        var characterDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            conditional.thenBlock()
                .statements()
                .getFirst()
        );

        var countDeclaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            whileStatement.body()
                .statements()
                .getFirst()
        );

        assertSame(
            BuiltInTypes.STRING,
            model.typeOf(
                textDeclaration.type()
            ).orElseThrow()
        );

        assertSame(
            BuiltInTypes.CHAR,
            model.typeOf(
                characterDeclaration.type()
            ).orElseThrow()
        );

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(
                countDeclaration.type()
            ).orElseThrow()
        );

        assertSame(
            model.typeOf(
                test.parameters()
                    .get(1)
                    .type()
            ).orElseThrow(),
            model.typeOf(
                countDeclaration.type()
            ).orElseThrow()
        );

        assertTrue(
            analysis.result()
                .diagnostics()
                .isEmpty()
        );
    }

    @Test
    void resolvesTypesIndependentlyFromValueSymbols() {
        var analysis = analyze(
            """
            fn int(int: int) -> int
                let boolean: boolean = true
                return int
            end
            """
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .getFirst()
        );

        var declaration = assertInstanceOf(
            VariableDeclarationStatement.class,
            function.body()
                .orElseThrow()
                .statements()
                .getFirst()
        );

        var model = analysis.result().model();

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(
                function.parameters()
                    .getFirst()
                    .type()
            ).orElseThrow()
        );

        assertSame(
            BuiltInTypes.INT,
            model.typeOf(
                function.returnType()
            ).orElseThrow()
        );

        assertSame(
            BuiltInTypes.BOOLEAN,
            model.typeOf(
                declaration.type()
            ).orElseThrow()
        );

        assertTrue(
            analysis.result()
                .diagnostics()
                .isEmpty()
        );
    }

    @Test
    void reportsUnknownTypesAndAssociatesErrorType() {
        var analysis = analyze(
            """
            @fn external(value: MissingParameter) -> MissingReturn

            fn test() -> UnknownReturn
                let value: UnknownLocal = 0

                if true then
                    let nested: AnotherUnknown = 1
                end

                return 0
            end
            """
        );

        var external = assertInstanceOf(
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

        var local = assertInstanceOf(
            VariableDeclarationStatement.class,
            test.body()
                .orElseThrow()
                .statements()
                .get(0)
        );

        var conditional = assertInstanceOf(
            ConditionalStatement.class,
            test.body()
                .orElseThrow()
                .statements()
                .get(1)
        );

        var nested = assertInstanceOf(
            VariableDeclarationStatement.class,
            conditional.thenBlock()
                .statements()
                .getFirst()
        );

        var references = List.of(
            external.parameters()
                .getFirst()
                .type(),
            external.returnType(),
            test.returnType(),
            local.type(),
            nested.type()
        );

        var diagnostics =
            analysis.result().diagnostics();

        assertEquals(
            List.of(
                "SOL-S003",
                "SOL-S003",
                "SOL-S003",
                "SOL-S003",
                "SOL-S003"
            ),
            diagnostics.stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                "Unknown type 'MissingParameter'.",
                "Unknown type 'MissingReturn'.",
                "Unknown type 'UnknownReturn'.",
                "Unknown type 'UnknownLocal'.",
                "Unknown type 'AnotherUnknown'."
            ),
            diagnostics.stream()
                .map(Diagnostic::message)
                .toList()
        );

        assertTrue(
            diagnostics.stream()
                .allMatch(
                    diagnostic ->
                        diagnostic.severity()
                            == DiagnosticSeverity.ERROR
                )
        );

        assertEquals(
            references.stream()
                .map(TypeReference::span)
                .toList(),
            diagnostics.stream()
                .map(Diagnostic::span)
                .toList()
        );

        for (var reference : references) {
            assertSame(
                BuiltInTypes.ERROR,
                analysis.result()
                    .model()
                    .typeOf(reference)
                    .orElseThrow()
            );
        }
    }

    @Test
    void usesTypeReferenceIdentityAndRejectsNullQueries() {
        var analysis = analyze(
            "@fn test() -> int"
        );

        var function = assertInstanceOf(
            FunctionDeclaration.class,
            analysis.unit()
                .declarations()
                .getFirst()
        );

        var reference =
            function.returnType();

        var structurallyEqualClone =
            new TypeReference(
                reference.name(),
                reference.span()
            );

        assertEquals(
            reference,
            structurallyEqualClone
        );

        assertSame(
            BuiltInTypes.INT,
            analysis.result()
                .model()
                .typeOf(reference)
                .orElseThrow()
        );

        assertTrue(
            analysis.result()
                .model()
                .typeOf(structurallyEqualClone)
                .isEmpty()
        );

        assertThrows(
            NullPointerException.class,
            () -> analysis.result()
                .model()
                .typeOf(null)
        );
    }
}
