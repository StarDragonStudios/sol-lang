package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.*;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SourceModule;
import io.github.stardragonstudios.sol.std.StandardLibrary;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PointerIrLoweringTest {
    @Test
    void lowersConcreteMemorySpecializationsAndPointerInstructions() {
        var application = module(
            "application",
            """
            inject namespace std.memory as memory

            @init
            fn launch() -> int
                @mut let values: pointer<int> = memory::allocate<int>(2)
                values[0] = 40
                *values = 42
                let result: int = values[0]
                memory::free<int>(values)
                return result
            end
            """
        );
        var memory = StandardLibrary.sourceModule(StandardLibrary.MEMORY).orElseThrow();
        var analyzed = SemanticAnalyzer.analyzeProgram(List.of(application, memory));

        assertTrue(analyzed.programDiagnostics().isEmpty(), analyzed.programDiagnostics().toString());
        for (var name : analyzed.moduleNames()) assertTrue(
            analyzed.analysisOf(name).orElseThrow().diagnostics().isEmpty(),
            analyzed.analysisOf(name).orElseThrow().diagnostics().toString()
        );

        var program = IrProgramLowerer.lower(analyzed);
        var launch = program.modules().getFirst().function("launch").orElseThrow();
        var instructions = launch.blocks().stream().flatMap(block -> block.instructions().stream()).toList();
        var memoryModule = program.modules().get(1);

        assertEquals(List.of("allocate$int", "free$int"), memoryModule.functions().stream().map(IrFunction::name).toList());
        assertInstanceOf(IrPointerType.class, memoryModule.function("allocate$int").orElseThrow().returnType());
        assertTrue(instructions.stream().anyMatch(IrPointerIndexStoreInstruction.class::isInstance));
        assertTrue(instructions.stream().anyMatch(IrPointerStoreInstruction.class::isInstance));
        assertTrue(instructions.stream().anyMatch(IrPointerIndexLoadInstruction.class::isInstance));
    }

    @Test
    void materializesRecursiveStructsThroughOpaquePointers() {
        var program = IrProgramLowerer.lower(SemanticAnalyzer.analyzeProgram(List.of(module(
            "application",
            """
            struct Node
                value: int
                next: pointer<Node>
            end

            @init
            fn launch() -> int
                let node: pointer<Node> = null
                if node == null then
                    return 42
                end
                return (*node).value
            end
            """
        ))));
        var node = program.modules().getFirst().structs().getFirst();
        var next = assertInstanceOf(IrPointerType.class, node.fields().get(1).type());

        assertEquals(node, next.elementType());
    }

    private static SourceModule module(String name, String source) {
        return new SourceModule(new ModuleName(List.of(name)), Parser.parse(Lexer.scan(source)));
    }
}
