package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.syntax.FieldAccessExpression;
import io.github.stardragonstudios.sol.syntax.FieldAssignmentStatement;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;
import io.github.stardragonstudios.sol.syntax.StructConstructionExpression;
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

final class StructSemanticAnalysisTest {
    private record Analysis(io.github.stardragonstudios.sol.syntax.CompilationUnit unit, SemanticAnalysisResult result) {}

    private static Analysis analyze(String source) {
        var unit = Parser.parse(Lexer.scan(source));

        return new Analysis(unit, SemanticAnalyzer.analyze(unit));
    }

    @Test
    void resolvesStructTypesFieldsConstructionAndNestedAccess() {
        var analysis = analyze(
            """
            struct Point
                x: int
                y: int
            end

            struct Box
                point: Point
            end

            fn read(box: Box) -> int
                return box.point.x
            end

            fn create() -> Box
                return Box { point: Point { x: 1, y: 2 } }
            end
            """
        );

        assertTrue(analysis.result().diagnostics().isEmpty(), analysis.result().diagnostics().toString());

        var point = assertInstanceOf(StructDeclaration.class, analysis.unit().declarations().get(0));
        var box = assertInstanceOf(StructDeclaration.class, analysis.unit().declarations().get(1));
        var model = analysis.result().model();
        var pointSymbol = model.symbolOf(point).orElseThrow();
        var boxSymbol = model.symbolOf(box).orElseThrow();

        assertEquals(SymbolKind.STRUCT, pointSymbol.kind());
        assertEquals(SymbolKind.STRUCT_FIELD, pointSymbol.fields().getFirst().kind());
        assertEquals(List.of(0, 1), pointSymbol.fields().stream().map(StructFieldSymbol::index).toList());
        assertSame(pointSymbol.type(), model.typeOf(box.fields().getFirst().type()).orElseThrow());

        var read = assertInstanceOf(FunctionDeclaration.class, analysis.unit().declarations().get(2));
        var outerAccess = assertInstanceOf(FieldAccessExpression.class,
            assertInstanceOf(ReturnStatement.class, read.body().orElseThrow().statements().getFirst()).expression().orElseThrow());
        var innerAccess = assertInstanceOf(FieldAccessExpression.class, outerAccess.target());

        assertSame(pointSymbol.field("x").orElseThrow(), model.accessedFieldOf(outerAccess).orElseThrow());
        assertSame(boxSymbol.field("point").orElseThrow(), model.accessedFieldOf(innerAccess).orElseThrow());

        var create = assertInstanceOf(FunctionDeclaration.class, analysis.unit().declarations().get(3));
        var outerConstruction = assertInstanceOf(StructConstructionExpression.class,
            assertInstanceOf(ReturnStatement.class, create.body().orElseThrow().statements().getFirst()).expression().orElseThrow());
        var innerConstruction = assertInstanceOf(StructConstructionExpression.class, outerConstruction.fields().getFirst().value());

        assertSame(boxSymbol, model.constructedStructOf(outerConstruction).orElseThrow());
        assertSame(pointSymbol, model.constructedStructOf(innerConstruction).orElseThrow());
    }

    @Test
    void reportsInvalidStructDeclarations() {
        var analysis = analyze(
            """
            struct Broken
                value: int
                value: float
                empty: void
            end

            struct Recursive
                self: Recursive
            end
            """
        );

        assertEquals(
            Set.of("SOL-S027", "SOL-S028", "SOL-S029"),
            analysis.result().diagnostics().stream().map(diagnostic -> diagnostic.code()).collect(Collectors.toSet())
        );
    }

    @Test
    void rejectsStructNamesReservedByBuiltInTypes() {
        var analysis = analyze(
            """
            struct int
                value: int
            end
            """
        );

        assertEquals(List.of("SOL-S038"), analysis.result().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    }

    @Test
    void reportsStructNameCollisionsInTheModuleDeclarationNamespace() {
        var analysis = analyze(
            """
            struct Duplicate
            end

            struct Duplicate
            end

            struct Shared
            end

            fn Shared() -> void
                return
            end
            """
        );

        assertEquals(
            List.of("SOL-S001", "SOL-S001"),
            analysis.result().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList()
        );
    }

    @Test
    void reportsCrossModuleCyclesAgainstTheOwningSourceModule() {
        var first = new SourceModule(
            new ModuleName(List.of("first")),
            Parser.parse(Lexer.scan(
                """
                inject second only Second

                struct First
                    second: Second
                end
                """
            ))
        );
        var second = new SourceModule(
            new ModuleName(List.of("second")),
            Parser.parse(Lexer.scan(
                """
                inject first only First

                struct Second
                    first: First
                end
                """
            ))
        );
        var program = SemanticAnalyzer.analyzeModules(List.of(first, second));

        assertTrue(program.analysisOf(first.name()).orElseThrow().diagnostics().isEmpty());
        assertEquals(
            List.of("SOL-S029"),
            program.analysisOf(second.name()).orElseThrow().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList()
        );
    }

    @Test
    void reportsInvalidStructConstruction() {
        var analysis = analyze(
            """
            struct Pair
                left: int
                right: int
            end

            fn test() -> void
                let primitive: int = int {}
                let pair: Pair = Pair { left: true, left: 1, extra: 2 }
                return
            end
            """
        );

        assertEquals(
            Set.of("SOL-S030", "SOL-S031", "SOL-S032", "SOL-S033", "SOL-S034"),
            analysis.result().diagnostics().stream().map(diagnostic -> diagnostic.code()).collect(Collectors.toSet())
        );
    }

    @Test
    void enforcesFieldAccessTypesAndRootMutability() {
        var analysis = analyze(
            """
            struct Pair
                left: int
                right: int
            end

            fn test(input: Pair) -> void
                let immutable: Pair = input
                immutable.left = 1
                @mut let mutable: Pair = input
                mutable.missing = 1
                mutable.left = true
                @mut let scalar: int = 1
                scalar.value = 2
                input.right = 3
                return
            end
            """
        );

        var codes = analysis.result().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();

        assertEquals(2, codes.stream().filter("SOL-S010"::equals).count());
        assertTrue(codes.contains("SOL-S011"));
        assertTrue(codes.contains("SOL-S035"));
        assertTrue(codes.contains("SOL-S036"));

        var function = assertInstanceOf(FunctionDeclaration.class, analysis.unit().declarations().get(1));
        var mutable = assertInstanceOf(VariableDeclarationStatement.class, function.body().orElseThrow().statements().get(2));
        var incompatible = assertInstanceOf(FieldAssignmentStatement.class, function.body().orElseThrow().statements().get(4));

        assertSame(analysis.result().model().symbolOf(mutable).orElseThrow(), analysis.result().model().assignmentTargetOf(incompatible).orElseThrow());
    }
}
