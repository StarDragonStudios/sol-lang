package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrBinaryOperator;
import io.github.stardragonstudios.sol.ir.IrBooleanConstant;
import io.github.stardragonstudios.sol.ir.IrCharConstant;
import io.github.stardragonstudios.sol.ir.IrFloatConstant;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrInstruction;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrTextFormatter;
import io.github.stardragonstudios.sol.ir.IrUnaryInstruction;
import io.github.stardragonstudios.sol.ir.IrUnaryOperator;
import io.github.stardragonstudios.sol.ir.IrValue;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrLoweringIntegrationTest {
    @Test
    void lowersCompleteSupportedSubsetEndToEnd() {
        var lowered = lowerSupportedProgram();

        assertEquals(
            List.of("core.primitives", "application"),
            lowered.modules().stream().map(module -> module.name().qualifiedName()).toList()
        );

        var functions = lowered.modules().stream().flatMap(module -> module.functions().stream()).toList();

        assertEquals(
            List.of(
                "external",
                "integer_ops",
                "float_literal",
                "boolean_literal",
                "character_literal",
                "comparisons",
                "equality",
                "logical",
                "perform",
                "start"
            ),
            functions.stream().map(IrFunction::name).toList()
        );

        for (var index = 0; index < functions.size(); index++) assertEquals(new IrFunctionId(index), functions.get(index).id());

        assertFalse(functions.getFirst().hasBody());
        assertTrue(lowered.hasEntryPoint());

        var application = lowered.modules().get(1);
        var start = application.function("start").orElseThrow();

        assertSame(application, lowered.entryModule().orElseThrow());
        assertSame(start, lowered.entryFunction().orElseThrow());
        assertEquals(EnumSet.allOf(IrUnaryOperator.class), collectUnaryOperators(lowered));
        assertEquals(EnumSet.allOf(IrBinaryOperator.class), collectBinaryOperators(lowered));

        assertEquals(
            Set.of(IrIntConstant.class, IrFloatConstant.class, IrBooleanConstant.class, IrCharConstant.class),
            collectConstantKinds(lowered)
        );

        var perform = lowered.modules().getFirst().function("perform").orElseThrow();
        var performReturn = assertInstanceOf(IrReturnTerminator.class, perform.entryBlock().orElseThrow().terminator());

        assertFalse(performReturn.returnsValue());

        var external = lowered.modules().getFirst().function("external").orElseThrow();

        assertFalse(external.hasBody());
        assertEquals(1, external.parameters().size());
    }

    @Test
    void producesDeterministicProgramAndText() {
        var first = lowerSupportedProgram();
        var second = lowerSupportedProgram();

        assertEquals(first, second);
        assertEquals(IrTextFormatter.format(first), IrTextFormatter.format(second));
    }

    private static EnumSet<IrUnaryOperator> collectUnaryOperators(IrProgram program) {
        var operators = EnumSet.noneOf(IrUnaryOperator.class);

        forEachInstruction(program, instruction -> {
            if (instruction instanceof IrUnaryInstruction unary) operators.add(unary.operator());
        });

        return operators;
    }

    private static EnumSet<IrBinaryOperator> collectBinaryOperators(IrProgram program) {
        var operators = EnumSet.noneOf(IrBinaryOperator.class);

        forEachInstruction(program, instruction -> {
            if (instruction instanceof IrBinaryInstruction binary) operators.add(binary.operator());
        });

        return operators;
    }

    private static void forEachInstruction(IrProgram program, Consumer<IrInstruction> consumer) {
        for (var module : program.modules())
            for (var function : module.functions())
                for (var block : function.blocks())
                    for (var instruction : block.instructions())
                        consumer.accept(instruction);
    }

    private static Set<Class<?>> collectConstantKinds(IrProgram program) {
        var kinds = new java.util.HashSet<Class<?>>();

        Set<IrValue> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var module : program.modules()) {
            for (var function : module.functions()) {
                for (var block : function.blocks()) {
                    for (var instruction : block.instructions()) collectValue(instruction, kinds, visited);

                    if (block.terminator() instanceof IrReturnTerminator(Optional<IrValue> value1))
                        value1.ifPresent(value -> collectValue(value, kinds, visited));
                }
            }
        }

        return Set.copyOf(kinds);
    }

    private static void collectValue(IrValue value, Set<Class<?>> kinds, Set<IrValue> visited) {
        if (!visited.add(value)) return;

        if (
            value instanceof IrIntConstant
                || value instanceof IrFloatConstant
                || value instanceof IrBooleanConstant
                || value instanceof IrCharConstant
        ) kinds.add(value.getClass());

        if (value instanceof IrInstruction instruction) for (var operand : instruction.operands()) collectValue(operand, kinds, visited);
    }

    private static IrProgram lowerSupportedProgram() {
        var semanticProgram = SemanticAnalyzer.analyzeProgram(
            List.of(
                sourceModule(
                    new ModuleName(List.of("core", "primitives")),
                    """
                    @fn external(value: int) -> int

                    fn integer_ops(value: int) -> int
                        return -(+value) + 2 * 3 / 1 % 2 - 4
                    end

                    fn float_literal() -> float
                        return 1.5
                    end

                    fn boolean_literal() -> boolean
                        return false
                    end

                    fn character_literal() -> char
                        return '\\n'
                    end

                    fn comparisons(left: float, right: float) -> boolean
                        return (left < right) || (left <= right) || (left > right) || (left >= right)
                    end

                    fn equality(left: char, right: char) -> boolean
                        return (left == right) && (left != right)
                    end

                    fn logical(left: boolean, right: boolean) -> boolean
                        return !left || right
                    end

                    fn perform() -> void
                        return
                    end
                    """
                ),
                sourceModule(
                    new ModuleName(
                        List.of("application")
                    ),
                    """
                    @init
                    fn start() -> int
                        return 0
                    end
                    """
                )
            )
        );

        assertTrue(
            semanticProgram.programDiagnostics().isEmpty(),
            () -> semanticProgram.programDiagnostics().toString()
        );

        for (var moduleName : semanticProgram.moduleNames()) {
            var analysis = semanticProgram.analysisOf(moduleName).orElseThrow();

            assertTrue(
                analysis.diagnostics().isEmpty(),
                () -> analysis.diagnostics().toString()
            );
        }

        return IrProgramLowerer.lower(semanticProgram);
    }

    private static SourceModule sourceModule(ModuleName name, String source) {
        return new SourceModule(name, Parser.parse(Lexer.scan(source)));
    }
}
