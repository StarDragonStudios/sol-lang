package io.github.stardragonstudios.sol.ir;

import java.util.List;

public interface IrInstruction {
    default List<IrValue> operands() {
        return List.of();
    }
}
