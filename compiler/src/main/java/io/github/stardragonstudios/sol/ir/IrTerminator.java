package io.github.stardragonstudios.sol.ir;

import java.util.List;

public interface IrTerminator {
    default List<IrValue> operands() {
        return List.of();
    }

    default List<IrBlockTarget> targets() {
        return List.of();
    }
}
