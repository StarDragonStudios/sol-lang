package io.github.stardragonstudios.sol.ir;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record IrBasicBlock(IrBlockTarget target, List<IrInstruction> instructions, IrTerminator terminator) {
    public IrBasicBlock {
        Objects.requireNonNull(target, "IR basic block target must not be null.");
        Objects.requireNonNull(instructions, "IR basic block instructions must not be null.");
        Objects.requireNonNull(terminator, "IR basic block terminator must not be null.");

        instructions = List.copyOf(instructions);

        var instructionValueIds = new HashSet<IrValueId>();

        Set<IrInstruction> instances = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var instruction : instructions) {
            Objects.requireNonNull(instruction, "IR basic block instructions must not contain null values.");

            if (!instances.add(instruction))
                throw new IllegalArgumentException("IR basic block must not contain the same instruction instance more than once.");

            if (instruction instanceof IrValueInstruction valueInstruction && !instructionValueIds.add(valueInstruction.id()))
                throw new IllegalArgumentException("IR basic block must not contain duplicate instruction value identifier '%s'.".formatted(valueInstruction.id()));
        }
    }

    /*
     * Compatibility constructor for existing callers that do not need
     * forward or cyclic block references.
     */
    public IrBasicBlock(IrBlockId id, List<IrInstruction> instructions, IrTerminator terminator) {
        this(new IrBlockTarget(Objects.requireNonNull(id, "IR basic block identifier must not be null.")), instructions, terminator);
    }

    public IrBlockId id() {
        return target.id();
    }
}
