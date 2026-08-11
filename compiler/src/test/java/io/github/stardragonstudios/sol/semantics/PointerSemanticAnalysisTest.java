package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.PointerType;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PointerSemanticAnalysisTest {
    @Test
    void typesPointersNullDereferenceIndexingAndRecursivePointerLayouts() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct Node
                value: int
                next: pointer<Node>
            end

            fn use(values: pointer<int>, node: pointer<Node>) -> int
                @mut let current: pointer<int> = null
                values[0] = 40
                *values = 42
                current = values
                if current != null then
                    return node[0].value + *current
                end
                return 0
            end
            """
        ));
        var analysis = SemanticAnalyzer.analyze(unit);

        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());

        var function = assertInstanceOf(FunctionDeclaration.class, unit.declarations().get(1));
        var pointer = assertInstanceOf(PointerType.class, analysis.model().typeOf(function.parameters().getFirst().type()).orElseThrow());
        var declaration = assertInstanceOf(VariableDeclarationStatement.class, function.body().orElseThrow().statements().getFirst());
        var nullExpression = assertInstanceOf(NullExpression.class, declaration.initializer());

        assertEquals(BuiltInTypes.INT, pointer.elementType());
        assertEquals(pointer, analysis.model().typeOf(nullExpression).orElseThrow());
    }

    @Test
    void rejectsInvalidPointerTypesOperationsAndConversions() {
        var unit = Parser.parse(Lexer.scan(
            """
            struct pointer
                value: int
            end

            fn invalid(number: int, integers: pointer<int>, flags: pointer<boolean>) -> void
                let missing: pointer = null
                let impossible: pointer<void> = null
                let untyped: int = null
                let mismatch: pointer<int> = flags
                number[0] = 1
                integers[true] = 1
                *number = 1
                if integers == flags then
                    return
                end
                return
            end
            """
        ));
        var codes = SemanticAnalyzer.analyze(unit).diagnostics().stream()
            .map(diagnostic -> diagnostic.code())
            .collect(Collectors.toSet());

        assertEquals(
            Set.of("SOL-S005", "SOL-S008", "SOL-S038", "SOL-S040", "SOL-S041", "SOL-S043", "SOL-S044", "SOL-S045", "SOL-S046"),
            codes
        );
    }

    @Test
    void resolvesGenericStandardMemorySignatures() {
        var application = new SourceModule(
            new ModuleName(List.of("application")),
            Parser.parse(Lexer.scan(
                """
                inject namespace std.memory as memory

                @init
                fn launch() -> int
                    @mut let values: pointer<int> = memory::allocate<int>(2)
                    values = memory::reallocate<int>(values, 4)
                    memory::free<int>(values)
                    return 0
                end
                """
            ))
        );
        var memory = io.github.stardragonstudios.sol.std.StandardLibrary.sourceModule(
            io.github.stardragonstudios.sol.std.StandardLibrary.MEMORY
        ).orElseThrow();
        var program = SemanticAnalyzer.analyzeProgram(List.of(application, memory));

        assertTrue(program.programDiagnostics().isEmpty(), program.programDiagnostics().toString());
        for (var name : program.moduleNames()) assertTrue(
            program.analysisOf(name).orElseThrow().diagnostics().isEmpty(),
            program.analysisOf(name).orElseThrow().diagnostics().toString()
        );
    }
}
