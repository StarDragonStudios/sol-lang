package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrConditionalBranchTerminatorTest {
    @Test
    void preservesConditionAndTargets() {
        var condition = new IrBooleanConstant(new IrValueId(0), true);
        var trueTarget = new IrBlockTarget(new IrBlockId(1));
        var falseTarget = new IrBlockTarget(new IrBlockId(2));
        var terminator = new IrConditionalBranchTerminator(condition, trueTarget, falseTarget);

        assertSame(condition, terminator.condition());
        assertSame(trueTarget, terminator.trueTarget());
        assertSame(falseTarget, terminator.falseTarget());
        assertEquals(List.of(condition), terminator.operands());
        assertEquals(List.of(trueTarget, falseTarget), terminator.targets());
    }

    @Test
    void permitsEqualBranchTargets() {
        var condition = new IrBooleanConstant(new IrValueId(0), false);
        var target = new IrBlockTarget(new IrBlockId(1));
        var terminator = new IrConditionalBranchTerminator(condition, target, target);

        assertSame(target, terminator.trueTarget());
        assertSame(target, terminator.falseTarget());
    }

    @Test
    void rejectsNonBooleanCondition() {
        var condition = new IrIntConstant(new IrValueId(0), 1);
        var target = new IrBlockTarget(new IrBlockId(1));

        var exception = assertThrows(
            IllegalArgumentException.class,
            () -> new IrConditionalBranchTerminator(condition, target, target)
        );

        assertEquals(
            "IR conditional branch condition must have type 'boolean', but got 'int'.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsNullInputs() {
        var condition = new IrBooleanConstant(new IrValueId(0), true);
        var target = new IrBlockTarget(new IrBlockId(1));

        assertThrows(
            NullPointerException.class,
            () -> new IrConditionalBranchTerminator(null, target, target)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrConditionalBranchTerminator(condition, null, target)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrConditionalBranchTerminator(condition, target, null)
        );
    }
}
