package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class IrFunctionControlFlowValidationTest {
    @Test
    void acceptsForwardBranchesAndCycles() {
        var entry =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        var loop =
            new IrBlockTarget(
                new IrBlockId(1)
            );

        var exit =
            new IrBlockTarget(
                new IrBlockId(2)
            );

        var condition =
            new IrBooleanConstant(
                new IrValueId(0),
                true
            );

        new IrFunction(
            new IrFunctionId(0),
            "repeat",
            List.of(),
            PrimitiveIrType.VOID,
            java.util.Optional.of(
                List.of(
                    new IrBasicBlock(
                        entry,
                        List.of(),
                        new IrConditionalBranchTerminator(
                            condition,
                            loop,
                            exit
                        )
                    ),
                    new IrBasicBlock(
                        loop,
                        List.of(),
                        new IrBranchTerminator(
                            entry
                        )
                    ),
                    new IrBasicBlock(
                        exit,
                        List.of(),
                        IrReturnTerminator.bare()
                    )
                )
            )
        );
    }

    @Test
    void rejectsEqualButNonCanonicalTarget() {
        var entry =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        var continuation =
            new IrBlockTarget(
                new IrBlockId(1)
            );

        var equivalentContinuation =
            new IrBlockTarget(
                new IrBlockId(1)
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrFunction.definition(
                    new IrFunctionId(0),
                    "invalid",
                    List.of(),
                    PrimitiveIrType.VOID,
                    List.of(
                        new IrBasicBlock(
                            entry,
                            List.of(),
                            new IrBranchTerminator(
                                equivalentContinuation
                            )
                        ),
                        new IrBasicBlock(
                            continuation,
                            List.of(),
                            IrReturnTerminator.bare()
                        )
                    )
                )
        );
    }

    @Test
    void rejectsForeignTargetInstance() {
        var foreignTarget =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        IrFunction.definition(
            new IrFunctionId(0),
            "foreign",
            List.of(),
            PrimitiveIrType.VOID,
            List.of(
                new IrBasicBlock(
                    foreignTarget,
                    List.of(),
                    IrReturnTerminator.bare()
                )
            )
        );

        var localTarget =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrFunction.definition(
                    new IrFunctionId(1),
                    "invalid",
                    List.of(),
                    PrimitiveIrType.VOID,
                    List.of(
                        new IrBasicBlock(
                            localTarget,
                            List.of(),
                            new IrBranchTerminator(
                                foreignTarget
                            )
                        )
                    )
                )
        );
    }

    @Test
    void rejectsDuplicateBlockIdentifiers() {
        var first =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        var second =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrFunction.definition(
                    new IrFunctionId(0),
                    "invalid",
                    List.of(),
                    PrimitiveIrType.VOID,
                    List.of(
                        new IrBasicBlock(
                            first,
                            List.of(),
                            IrReturnTerminator.bare()
                        ),
                        new IrBasicBlock(
                            second,
                            List.of(),
                            IrReturnTerminator.bare()
                        )
                    )
                )
        );
    }

    @Test
    void rejectsRepeatedCanonicalTargetInstance() {
        var target =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrFunction.definition(
                    new IrFunctionId(0),
                    "invalid",
                    List.of(),
                    PrimitiveIrType.VOID,
                    List.of(
                        new IrBasicBlock(
                            target,
                            List.of(),
                            IrReturnTerminator.bare()
                        ),
                        new IrBasicBlock(
                            target,
                            List.of(),
                            IrReturnTerminator.bare()
                        )
                    )
                )
        );
    }

    @Test
    void validatesConditionalBranchOperandGraph() {
        var entry =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        var trueTarget =
            new IrBlockTarget(
                new IrBlockId(1)
            );

        var falseTarget =
            new IrBlockTarget(
                new IrBlockId(2)
            );

        var undeclaredCondition =
            new IrUnaryInstruction(
                new IrValueId(1),
                IrUnaryOperator.LOGICAL_NOT,
                new IrBooleanConstant(
                    new IrValueId(0),
                    false
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrFunction.definition(
                    new IrFunctionId(0),
                    "invalid",
                    List.of(),
                    PrimitiveIrType.VOID,
                    List.of(
                        new IrBasicBlock(
                            entry,
                            List.of(),
                            new IrConditionalBranchTerminator(
                                undeclaredCondition,
                                trueTarget,
                                falseTarget
                            )
                        ),
                        new IrBasicBlock(
                            trueTarget,
                            List.of(),
                            IrReturnTerminator.bare()
                        ),
                        new IrBasicBlock(
                            falseTarget,
                            List.of(),
                            IrReturnTerminator.bare()
                        )
                    )
                )
        );
    }
}
