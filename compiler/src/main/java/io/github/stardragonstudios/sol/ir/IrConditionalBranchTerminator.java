package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;

public record IrConditionalBranchTerminator(IrValue condition, IrBlockTarget trueTarget, IrBlockTarget falseTarget) implements IrTerminator {
    public IrConditionalBranchTerminator {
        Objects.requireNonNull(condition, "IR conditional branch condition must not be null.");
        Objects.requireNonNull(trueTarget, "IR conditional branch true target must not be null.");
        Objects.requireNonNull(falseTarget, "IR conditional branch false target must not be null.");

        if (!PrimitiveIrType.BOOLEAN.equals(condition.type())) {
            throw new IllegalArgumentException(
                "IR conditional branch condition must have type 'boolean', but got '%s'.".formatted(condition.type().displayName())
            );
        }
    }

    @Override
    public List<IrValue> operands() {
        return List.of(condition);
    }

    @Override
    public List<IrBlockTarget> targets() {
        return List.of(trueTarget, falseTarget);
    }
}
