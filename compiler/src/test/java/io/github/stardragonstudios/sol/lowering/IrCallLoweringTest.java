package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrTextFormatter;
import io.github.stardragonstudios.sol.ir.IrValueCallInstruction;
import io.github.stardragonstudios.sol.ir.IrVoidCallInstruction;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrCallLoweringTest {
    @Test
    void lowersForwardDirectValueCalls() {
        var program = lower(module(
            "application",
            """
            fn start() -> int
                return add(1, 2)
            end

            fn add(left: int, right: int) -> int
                return left + right
            end
            """
        ));

        var module = program.modules().getFirst();
        var start = module.function("start").orElseThrow();
        var add = module.function("add").orElseThrow();
        var call = assertInstanceOf(IrValueCallInstruction.class, start.entryBlock().orElseThrow().instructions().getFirst());

        assertEquals(add.id(), call.target().id());
        assertEquals("add", call.target().name());
        assertEquals(2, call.arguments().size());

        var returnTerminator = assertInstanceOf(IrReturnTerminator.class, start.entryBlock().orElseThrow().terminator());

        assertSame(call, returnTerminator.value().orElseThrow());
    }

    @Test
    void lowersVoidCallsAsStatements() {
        var program = lower(module(
            "application",
            """
            @fn perform(value: int) -> void

            fn start() -> int
                perform(7)
                return 0
            end
            """
        ));

        var module = program.modules().getFirst();
        var perform = module.function("perform").orElseThrow();
        var start = module.function("start").orElseThrow();

        assertFalse(perform.hasBody());

        var call = assertInstanceOf(IrVoidCallInstruction.class, start.entryBlock().orElseThrow().instructions().getFirst());

        assertEquals(perform.id(), call.target().id());
        assertEquals(1, call.arguments().size());
    }

    @Test
    void sharesCanonicalTargetAcrossDirectAndQualifiedCalls() {
        var program = lower(
            module(
                "math",
                """
                fn add(left: int, right: int) -> int
                    return left + right
                end
                """
            ),
            module(
                "application",
                """
                inject math only add
                inject namespace math as numbers

                fn start() -> int
                    let first: int = add(1, 2)
                    return first + numbers::add(3, 4)
                end
                """
            )
        );

        var application = program.modules().get(1);
        var start = application.function("start").orElseThrow();

        var calls = start.blocks()
            .stream()
            .flatMap(block -> block.instructions().stream())
            .filter(IrValueCallInstruction.class::isInstance)
            .map(IrValueCallInstruction.class::cast)
            .toList();

        assertEquals(2, calls.size());
        assertSame(calls.get(0).target(), calls.get(1).target());

        var mathFunction = program.modules()
            .getFirst()
            .function("add")
            .orElseThrow();

        assertEquals(mathFunction.id(), calls.getFirst().target().id());
    }

    @Test
    void formatsCallsDeterministically() {
        var program = lower(module(
            "application",
            """
            @fn write(value: int) -> void

            fn identity(value: int) -> int
                return value
            end

            fn start() -> int
                write(1)
                return identity(2)
            end
            """
        ));

        var first = IrTextFormatter.format(program);
        var second = IrTextFormatter.format(program);

        assertEquals(first, second);
        assertTrue(first.contains("call @function0 write("));
        assertTrue(first.contains("= call @function1 identity("));
    }

    private static IrProgram lower(SourceModule... modules) {
        var semanticProgram = SemanticAnalyzer.analyzeModules(List.of(modules));

        for (var moduleName : semanticProgram.moduleNames()) {
            var analysis = semanticProgram.analysisOf(moduleName).orElseThrow();

            assertTrue(
                analysis.diagnostics().isEmpty(),
                () -> analysis.diagnostics().toString()
            );
        }

        assertTrue(
            semanticProgram.programDiagnostics().isEmpty(),
            () -> semanticProgram.programDiagnostics().toString()
        );

        return IrProgramLowerer.lower(semanticProgram);
    }

    private static SourceModule module(String name, String source) {
        return new SourceModule(new ModuleName(List.of(name)), Parser.parse(Lexer.scan(source)));
    }
}