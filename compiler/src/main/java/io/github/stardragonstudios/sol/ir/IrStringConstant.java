package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrStringConstant(IrValueId id, String value) implements IrValue {
    public IrStringConstant {
        Objects.requireNonNull(id, "IR string constant identifier must not be null.");
        Objects.requireNonNull(value, "IR string constant value must not be null.");

        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);

            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))
                    throw new IllegalArgumentException("IR string constant must contain only valid Unicode scalar values.");

                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("IR string constant must contain only valid Unicode scalar values.");
            }
        }
    }

    @Override
    public IrType type() {
        return PrimitiveIrType.STRING;
    }
}
