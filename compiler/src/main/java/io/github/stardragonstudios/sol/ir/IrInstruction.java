package io.github.stardragonstudios.sol.ir;

import java.util.List;

public interface IrInstruction extends IrValue {
    default List<IrValue> operands() {
        return List.of();
    }
}
