package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBranchTerminator;
import io.github.stardragonstudios.sol.ir.IrConditionalBranchTerminator;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrLocalInitializeInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrTextFormatter;
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

class IrControlFlowLoweringTest {
    @Test
    void lowersConditionalWithoutElse() {
        var function =
            lowerFunction(
                """
                fn choose(flag: boolean) -> int
                    @mut let result: int = 0

                    if flag then
                        result = 1
                    end

                    return result
                end
                """
            );

        assertEquals(
            3,
            function.blocks()
                .size()
        );

        var entry =
            function.blocks()
                .get(0);

        var thenBlock =
            function.blocks()
                .get(1);

        var continuation =
            function.blocks()
                .get(2);

        assertInstanceOf(
            IrLocalInitializeInstruction.class,
            entry.instructions()
                .getFirst()
        );

        var branch =
            assertInstanceOf(
                IrConditionalBranchTerminator.class,
                entry.terminator()
            );

        assertSame(
            function.parameters()
                .getFirst(),
            branch.condition()
        );

        assertSame(
            thenBlock.target(),
            branch.trueTarget()
        );

        assertSame(
            continuation.target(),
            branch.falseTarget()
        );

        assertInstanceOf(
            IrLocalStoreInstruction.class,
            thenBlock.instructions()
                .getFirst()
        );

        var thenTerminator =
            assertInstanceOf(
                IrBranchTerminator.class,
                thenBlock.terminator()
            );

        assertSame(
            continuation.target(),
            thenTerminator.target()
        );

        assertInstanceOf(
            IrLocalLoadInstruction.class,
            continuation.instructions()
                .getFirst()
        );

        assertInstanceOf(
            IrReturnTerminator.class,
            continuation.terminator()
        );
    }

    @Test
    void avoidsContinuationWhenBothBranchesReturn() {
        var function =
            lowerFunction(
                """
                fn choose(flag: boolean) -> int
                    if flag then
                        return 1
                    else
                        return 2
                    end
                end
                """
            );

        assertEquals(
            3,
            function.blocks()
                .size()
        );

        var entry =
            function.blocks()
                .get(0);

        var thenBlock =
            function.blocks()
                .get(1);

        var elseBlock =
            function.blocks()
                .get(2);

        var branch =
            assertInstanceOf(
                IrConditionalBranchTerminator.class,
                entry.terminator()
            );

        assertSame(
            thenBlock.target(),
            branch.trueTarget()
        );

        assertSame(
            elseBlock.target(),
            branch.falseTarget()
        );

        assertInstanceOf(
            IrReturnTerminator.class,
            thenBlock.terminator()
        );

        assertInstanceOf(
            IrReturnTerminator.class,
            elseBlock.terminator()
        );

        assertFalse(
            function.blocks()
                .stream()
                .anyMatch(
                    block ->
                        block.terminator()
                            instanceof
                            IrBranchTerminator
                )
        );
    }

    @Test
    void lowersWhileToConditionBodyAndContinuation() {
        var function =
            lowerFunction(
                """
                fn count(limit: int) -> int
                    @mut let value: int = 0

                    while value < limit do
                        value = value + 1
                    end

                    return value
                end
                """
            );

        assertEquals(
            4,
            function.blocks()
                .size()
        );

        var entry =
            function.blocks()
                .get(0);

        var condition =
            function.blocks()
                .get(1);

        var body =
            function.blocks()
                .get(2);

        var continuation =
            function.blocks()
                .get(3);

        var entryBranch =
            assertInstanceOf(
                IrBranchTerminator.class,
                entry.terminator()
            );

        assertSame(
            condition.target(),
            entryBranch.target()
        );

        var conditionalBranch =
            assertInstanceOf(
                IrConditionalBranchTerminator.class,
                condition.terminator()
            );

        assertSame(
            body.target(),
            conditionalBranch.trueTarget()
        );

        assertSame(
            continuation.target(),
            conditionalBranch.falseTarget()
        );

        assertTrue(
            body.instructions()
                .stream()
                .anyMatch(
                    IrLocalStoreInstruction.class::isInstance
                )
        );

        var loopBranch =
            assertInstanceOf(
                IrBranchTerminator.class,
                body.terminator()
            );

        assertSame(
            condition.target(),
            loopBranch.target()
        );

        assertInstanceOf(
            IrLocalLoadInstruction.class,
            continuation.instructions()
                .getFirst()
        );

        assertInstanceOf(
            IrReturnTerminator.class,
            continuation.terminator()
        );
    }

    @Test
    void lowersNestedConditionals() {
        var function =
            lowerFunction(
                """
                fn choose(first: boolean, second: boolean) -> int
                    if first then
                        if second then
                            return 1
                        else
                            return 2
                        end
                    else
                        return 3
                    end
                end
                """
            );

        assertEquals(
            5,
            function.blocks()
                .size()
        );

        assertEquals(
            2,
            function.blocks()
                .stream()
                .filter(
                    block ->
                        block.terminator()
                            instanceof
                            IrConditionalBranchTerminator
                )
                .count()
        );

        assertEquals(
            3,
            function.blocks()
                .stream()
                .filter(
                    block ->
                        block.terminator()
                            instanceof
                            IrReturnTerminator
                )
                .count()
        );
    }

    @Test
    void producesDeterministicControlFlowIr() {
        var source =
            """
            fn count(limit: int) -> int
                @mut let value: int = 0

                while value < limit do
                    if value == 2 then
                        value = value + 2
                    else
                        value = value + 1
                    end
                end

                return value
            end
            """;

        assertEquals(
            lowerFunction(source),
            lowerFunction(source)
        );
    }

    @Test
    void lowersNestedLoopsToIndependentCycles() {
        var function =
            lowerFunction(
                """
                fn count(outerLimit: int, innerLimit: int) -> int
                    @mut let outer: int = 0

                    while outer < outerLimit do
                        @mut let inner: int = 0

                        while inner < innerLimit do
                            inner = inner + 1
                        end

                        outer = outer + 1
                    end

                    return outer
                end
                """
            );

        assertEquals(
            7,
            function.blocks()
                .size()
        );

        var conditionBlocks =
            function.blocks()
                .stream()
                .filter(
                    block ->
                        block.terminator()
                            instanceof
                            IrConditionalBranchTerminator
                )
                .toList();

        var branchTargets =
            function.blocks()
                .stream()
                .filter(
                    block ->
                        block.terminator()
                            instanceof
                            IrBranchTerminator
                )
                .map(
                    block ->
                        (
                            (IrBranchTerminator)
                                block.terminator()
                        ).target()
                )
                .toList();

        assertEquals(
            2,
            conditionBlocks.size()
        );

        /*
         * Each loop condition must receive a canonical back-edge from
         * one of the blocks in the generated control-flow graph.
         */
        for (var conditionBlock : conditionBlocks) {
            assertTrue(
                branchTargets.stream()
                    .anyMatch(
                        target ->
                            target
                                == conditionBlock.target()
                    )
            );
        }

        assertEquals(
            1,
            function.blocks()
                .stream()
                .filter(
                    block ->
                        block.terminator()
                            instanceof
                            IrReturnTerminator
                )
                .count()
        );
    }

    @Test
    void formatsLoweredControlFlowDeterministically() {
        var function =
            lowerFunction(
                """
                fn choose(flag: boolean) -> int
                    if flag then
                        return 1
                    else
                        return 2
                    end
                end
                """
            );

        var program =
            IrProgram.library(
                List.of(
                    new IrModule(
                        new IrModuleName(
                            List.of(
                                "control_flow"
                            )
                        ),
                        List.of(
                            function
                        )
                    )
                )
            );

        assertEquals(
            """
            program {
              entry none

              module @control_flow {
                define @function0 choose(%0 flag: boolean) -> int {
                  block0:
                    branch_if %0, block1, block2

                  block1:
                    %1: int = const 1
                    return %1

                  block2:
                    %2: int = const 2
                    return %2
                }
              }
            }
            """,
            IrTextFormatter.format(
                program
            )
        );
    }

    private static IrFunction lowerFunction(String source) {
        var program = SemanticAnalyzer.analyzeModules(
            List.of(new SourceModule(new ModuleName(List.of("control_flow")), Parser.parse(Lexer.scan(source))))
        );

        for (var moduleName : program.moduleNames()) {
            var analysis = program.analysisOf(moduleName).orElseThrow();

            assertTrue(
                analysis.diagnostics().isEmpty(),
                () -> analysis.diagnostics().toString()
            );
        }

        return IrProgramLowerer.lower(program).modules().getFirst().functions().getFirst();
    }
}
