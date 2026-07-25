package io.github.stardragonstudios.sol.ir;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record IrBasicBlock(IrBlockId id, List<IrInstruction> instructions, IrTerminator terminator) {
    public IrBasicBlock {
        Objects.requireNonNull(id, "IR basic block identifier must not be null.");
        Objects.requireNonNull(instructions, "IR basic block instructions must not be null.");
        Objects.requireNonNull(terminator, "IR basic block terminator must not be null.");

        instructions = List.copyOf(instructions);

        var instructionValueIds = new HashSet<IrValueId>();

        for (var instruction : instructions) {
            Objects.requireNonNull(instruction, "IR basic block instructions must not contain null values.");

            if (!instructionValueIds.add(instruction.id()))
                throw new IllegalArgumentException("IR basic block must not contain duplicate instruction value identifier '%s'.".formatted(instruction.id()));
        }
    }
}
