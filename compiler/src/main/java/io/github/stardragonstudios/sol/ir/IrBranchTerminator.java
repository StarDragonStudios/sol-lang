package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrBranchTerminator(IrBlockTarget target) implements IrTerminator {
    public IrBranchTerminator {
        Objects.requireNonNull(target, "IR branch target must not be null.");
    }

    @Override
    public List<IrBlockTarget> targets() {
        return List.of(target);
    }
}
