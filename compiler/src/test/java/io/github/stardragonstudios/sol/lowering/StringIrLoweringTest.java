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
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StringIrLoweringTest {
    @Test
    void lowersStringOperatorsIndexAndStandardCallsToTypedIr() {
        var application = new SourceModule(
            new ModuleName(List.of("application")),
            Parser.parse(Lexer.scan(
                """
                inject namespace std.string as strings

                @init
                fn launch() -> int
                    let text: string = "Sol" + "🐉"
                    let first: char = text[0]
                    let part: string = strings::slice(text, 0, 3)
                    if part == "Sol" then
                        return strings::length(part)
                    end
                    return 0
                end
                """
            ))
        );
        var analyzed = SemanticAnalyzer.analyzeProgram(List.of(
            application,
            StandardLibrary.sourceModule(StandardLibrary.STRING).orElseThrow()
        ));
        var program = IrProgramLowerer.lower(analyzed);
        var launch = program.modules().getFirst().function("launch").orElseThrow();
        var instructions = launch.blocks().stream().flatMap(block -> block.instructions().stream()).toList();
        var stringModule = program.modules().get(1);

        assertTrue(instructions.stream().anyMatch(IrStringIndexInstruction.class::isInstance));
        assertTrue(instructions.stream().filter(IrBinaryInstruction.class::isInstance)
            .map(IrBinaryInstruction.class::cast)
            .anyMatch(instruction -> instruction.operator() == IrBinaryOperator.ADD && instruction.type() == PrimitiveIrType.STRING));
        assertTrue(instructions.stream().filter(IrBinaryInstruction.class::isInstance)
            .map(IrBinaryInstruction.class::cast)
            .anyMatch(instruction -> instruction.operator() == IrBinaryOperator.EQUAL));
        assertEquals(List.of("length", "slice", "substring"), stringModule.functions().stream().map(IrFunction::name).toList());
        assertTrue(IrTextFormatter.format(program).contains(": char = string_index "));
    }
}
