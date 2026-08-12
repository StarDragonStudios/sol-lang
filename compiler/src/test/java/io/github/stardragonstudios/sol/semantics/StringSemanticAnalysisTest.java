package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.std.StandardLibrary;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StringSemanticAnalysisTest {
    @Test
    void typesIndexConcatenationAndContentEquality() {
        var unit = Parser.parse(Lexer.scan(
            """
            fn inspect(text: string) -> boolean
                let scalar: char = text[1]
                let combined: string = text + "🐉"
                return combined == "Sol🐉" && text != ""
            end
            """
        ));
        var analysis = SemanticAnalyzer.analyze(unit);

        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());

        var function = assertInstanceOf(FunctionDeclaration.class, unit.declarations().getFirst());
        var statements = function.body().orElseThrow().statements();
        var scalar = assertInstanceOf(IndexExpression.class,
            assertInstanceOf(VariableDeclarationStatement.class, statements.get(0)).initializer());
        var concatenation = assertInstanceOf(BinaryExpression.class,
            assertInstanceOf(VariableDeclarationStatement.class, statements.get(1)).initializer());

        assertEquals(BuiltInTypes.CHAR, analysis.model().typeOf(scalar).orElseThrow());
        assertEquals(BuiltInTypes.STRING, analysis.model().typeOf(concatenation).orElseThrow());
    }

    @Test
    void rejectsInvalidIndexTargetsTypesAndStringMutation() {
        var unit = Parser.parse(Lexer.scan(
            """
            fn invalid(text: string, number: int) -> void
                let bad_target: int = number[0]
                let bad_index: char = text[true]
                text[0] = 'S'
                return
            end
            """
        ));
        var diagnostics = SemanticAnalyzer.analyze(unit).diagnostics();

        assertEquals(List.of("SOL-S045", "SOL-S046", "SOL-S047"), diagnostics.stream().map(diagnostic -> diagnostic.code()).toList());
        assertEquals(
            "String values are immutable and cannot be assigned through indexing.",
            diagnostics.get(2).message()
        );
    }

    @Test
    void resolvesStandardStringSignatures() {
        var application = new SourceModule(
            new ModuleName(List.of("application")),
            Parser.parse(Lexer.scan(
                """
                inject namespace std.string as strings

                @init
                fn launch() -> int
                    let text: string = strings::slice("Aé🐉", 1, 3)
                    let copy: string = strings::substring(text, 0, strings::length(text))
                    if copy == "é🐉" then
                        return 0
                    end
                    return 1
                end
                """
            ))
        );
        var strings = StandardLibrary.sourceModule(StandardLibrary.STRING).orElseThrow();
        var program = SemanticAnalyzer.analyzeProgram(List.of(application, strings));

        assertTrue(program.programDiagnostics().isEmpty(), program.programDiagnostics().toString());
        for (var name : program.moduleNames()) assertTrue(
            program.analysisOf(name).orElseThrow().diagnostics().isEmpty(),
            program.analysisOf(name).orElseThrow().diagnostics().toString()
        );
    }
}
