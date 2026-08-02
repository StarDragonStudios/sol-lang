package io.github.stardragonstudios.sol.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record IrReturnTerminator(Optional<IrValue> value) implements IrTerminator {
    public IrReturnTerminator {
        Objects.requireNonNull(value, "Returned IR value must not be null.");

        value.ifPresent(returnedValue -> {
            if (!returnedValue.type().isValue())
                throw new IllegalArgumentException("Returned IR value must have a value type.");
        });
    }

    public static IrReturnTerminator bare() {
        return new IrReturnTerminator(Optional.empty());
    }

    public static IrReturnTerminator returning(IrValue value) {
        Objects.requireNonNull(value, "Returned IR value must not be null.");

        return new IrReturnTerminator(Optional.of(value));
    }

    public boolean returnsValue() {
        return value.isPresent();
    }

    @Override
    public List<IrValue> operands() {
        if (value.isEmpty()) return List.of();

        return List.of(value.orElseThrow());
    }
}
