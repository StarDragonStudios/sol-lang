package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrStructConstructInstruction;
import io.github.stardragonstudios.sol.ir.IrStructFieldExtractInstruction;
import io.github.stardragonstudios.sol.ir.IrStructFieldStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrTextFormatter;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructIrLoweringTest {
    @Test
    void lowersStructTypesConstructionAccessAndNestedMutation() {
        var program = lower(
            """
            struct Point
                x: int
                y: int
            end

            struct Box
                point: Point
            end

            @init
            fn launch() -> int
                let point: Point = Point { y: 2, x: 1 }
                @mut let box: Box = Box { point: point }
                box.point.y = 41
                return box.point.x + box.point.y
            end
            """
        );

        var module = program.modules().getFirst();

        assertEquals(List.of("application::Point", "application::Box"), module.structs().stream().map(type -> type.displayName()).toList());
        assertEquals(List.of("x", "y"), module.structs().getFirst().fields().stream().map(field -> field.name()).toList());
        assertEquals("application::Point", module.structs().get(1).fields().getFirst().type().displayName());

        var instructions = module.functions().getFirst().blocks().stream().flatMap(block -> block.instructions().stream()).toList();

        assertEquals(2, instructions.stream().filter(IrStructConstructInstruction.class::isInstance).count());
        assertTrue(instructions.stream().anyMatch(IrStructFieldExtractInstruction.class::isInstance));

        var store = instructions.stream()
            .filter(IrStructFieldStoreInstruction.class::isInstance)
            .map(IrStructFieldStoreInstruction.class::cast)
            .findFirst()
            .orElseThrow();

        assertEquals(List.of("point", "y"), store.path().stream().map(field -> field.name()).toList());

        var text = IrTextFormatter.format(program);

        assertTrue(text.contains("struct application::Point"), text);
        assertTrue(text.contains("store_field local1.point.y"), text);
    }

    @Test
    void sharesInjectedStructTypesAcrossModules() {
        var core = new SourceModule(
            new ModuleName(List.of("core")),
            Parser.parse(Lexer.scan(
                """
                struct Result
                    value: int
                end

                fn make(value: int) -> Result
                    return Result { value: value }
                end
                """
            ))
        );
        var application = new SourceModule(
            new ModuleName(List.of("application")),
            Parser.parse(Lexer.scan(
                """
                inject core only Result, make

                @init
                fn launch() -> int
                    let result: Result = make(42)
                    return result.value
                end
                """
            ))
        );
        var analyzed = SemanticAnalyzer.analyzeProgram(List.of(core, application));

        assertNoDiagnostics(analyzed);

        var lowered = IrProgramLowerer.lower(analyzed);
        var resultType = lowered.modules().getFirst().structs().getFirst();
        var launch = lowered.modules().get(1).functions().getFirst();

        assertEquals(resultType, launch.blocks().getFirst().instructions().stream()
            .filter(instruction -> instruction instanceof io.github.stardragonstudios.sol.ir.IrLocalInitializeInstruction)
            .map(instruction -> ((io.github.stardragonstudios.sol.ir.IrLocalInitializeInstruction) instruction).local().type())
            .findFirst()
            .orElseThrow());
    }

    private static io.github.stardragonstudios.sol.ir.IrProgram lower(String source) {
        var module = new SourceModule(
            new ModuleName(List.of("application")),
            Parser.parse(Lexer.scan(source))
        );
        var analyzed = SemanticAnalyzer.analyzeProgram(List.of(module));

        assertNoDiagnostics(analyzed);

        return IrProgramLowerer.lower(analyzed);
    }

    private static void assertNoDiagnostics(io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult analyzed) {
        assertTrue(analyzed.programDiagnostics().isEmpty(), analyzed.programDiagnostics().toString());

        for (var moduleName : analyzed.moduleNames()) {
            var diagnostics = analyzed.analysisOf(moduleName).orElseThrow().diagnostics();

            assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        }
    }
}
