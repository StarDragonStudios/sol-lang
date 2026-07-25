package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrBasicBlockTest {
    @Test
    void createsBareReturnTerminators() {
        var terminator = IrReturnTerminator.bare();

        assertFalse(terminator.returnsValue());
        assertTrue(terminator.value().isEmpty());
    }

    @Test
    void createsValueReturnTerminators() {
        var value = new IrIntConstant(new IrValueId(0), 42);
        var terminator = IrReturnTerminator.returning(value);

        assertTrue(terminator.returnsValue());
        assertSame(value, terminator.value().orElseThrow());
    }

    @Test
    void rejectsInvalidReturnTerminators() {
        assertThrows(
            NullPointerException.class,
            () -> new IrReturnTerminator(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> IrReturnTerminator.returning(null)
        );
    }

    @Test
    void createsTerminatedBasicBlocks() {
        var left = new IrIntConstant(new IrValueId(0), 20);
        var right = new IrIntConstant(new IrValueId(1), 22);
        var instruction = new IrBinaryInstruction(new IrValueId(2), IrBinaryOperator.ADD, left, right);
        var terminator = IrReturnTerminator.returning(instruction);
        var block = new IrBasicBlock(new IrBlockId(0), List.of(instruction), terminator);

        assertEquals(new IrBlockId(0), block.id());
        assertEquals(List.of(instruction), block.instructions());
        assertSame(terminator, block.terminator());
    }

    @Test
    void permitsBlocksWithoutInstructions() {
        var terminator = IrReturnTerminator.bare();
        var block = new IrBasicBlock(new IrBlockId(0), List.of(), terminator);

        assertTrue(block.instructions().isEmpty());
        assertSame(terminator, block.terminator());
    }

    @Test
    void preservesInstructionOrderAndDefensiveCopying() {
        var first = new IrUnaryInstruction(
            new IrValueId(1),
            IrUnaryOperator.NEGATE,
            new IrIntConstant(new IrValueId(0), 1)
        );

        var second = new IrUnaryInstruction(
            new IrValueId(2),
            IrUnaryOperator.POSITIVE,
            first
        );

        var instructions = new ArrayList<IrInstruction>(List.of(first, second));

        var block = new IrBasicBlock(
            new IrBlockId(0),
            instructions,
            IrReturnTerminator.returning(second)
        );

        instructions.clear();

        assertEquals(
            List.of(first, second),
            block.instructions()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> block.instructions().clear()
        );
    }

    @Test
    void rejectsInvalidBasicBlockComponents() {
        var terminator = IrReturnTerminator.bare();

        assertThrows(
            NullPointerException.class,
            () -> new IrBasicBlock(null, List.of(), terminator)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrBasicBlock(new IrBlockId(0), null, terminator)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrBasicBlock(new IrBlockId(0), List.of(), null)
        );

        var instructions = new ArrayList<IrInstruction>();
        instructions.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new IrBasicBlock(
                new IrBlockId(0),
                instructions,
                terminator
            )
        );
    }

    @Test
    void rejectsDuplicateInstructionIdentifiers() {
        var first = new IrUnaryInstruction(
            new IrValueId(1),
            IrUnaryOperator.NEGATE,
            new IrIntConstant(
                new IrValueId(0),
                1
            )
        );

        var duplicate =
            new IrUnaryInstruction(
                new IrValueId(1),
                IrUnaryOperator.POSITIVE,
                new IrIntConstant(
                    new IrValueId(2),
                    2
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrBasicBlock(
                new IrBlockId(0),
                List.of(first, duplicate),
                IrReturnTerminator.returning(first)
            )
        );
    }

    @Test
    void preservesReturnTerminatorValueEquality() {
        var value = new IrBooleanConstant(new IrValueId(0), true);

        assertEquals(
            new IrReturnTerminator(Optional.of(value)),
            IrReturnTerminator.returning(value)
        );
    }
}
