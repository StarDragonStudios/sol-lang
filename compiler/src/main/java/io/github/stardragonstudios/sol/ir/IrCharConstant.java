package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrCharConstant(IrValueId id, int codePoint) implements IrValue {
    public IrCharConstant {
        Objects.requireNonNull(id, "IR character constant identifier must not be null.");

        if (!Character.isValidCodePoint(codePoint))
            throw new IllegalArgumentException("IR character constant must contain a valid Unicode code point.");

        if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)
            throw new IllegalArgumentException("IR character constant must not contain a surrogate code point.");
    }

    @Override
    public IrType type() {
        return PrimitiveIrType.CHAR;
    }
}
