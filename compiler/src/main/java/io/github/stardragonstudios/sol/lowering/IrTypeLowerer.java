package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;

import java.util.Objects;

final class IrTypeLowerer {
    private IrTypeLowerer() {}

    static IrType lower(TypeSymbol type) {
        Objects.requireNonNull(type, "Lowered semantic type must not be null.");

        if (type == BuiltInTypes.INT)       return PrimitiveIrType.INT;
        if (type == BuiltInTypes.FLOAT)     return PrimitiveIrType.FLOAT;
        if (type == BuiltInTypes.BOOLEAN)   return PrimitiveIrType.BOOLEAN;
        if (type == BuiltInTypes.CHAR)      return PrimitiveIrType.CHAR;
        if (type == BuiltInTypes.STRING)    return PrimitiveIrType.STRING;
        if (type == BuiltInTypes.VOID)      return PrimitiveIrType.VOID;

        throw new IrLoweringException("Unsupported semantic type '%s' during IR lowering.".formatted(type.name()));
    }

    public static class IrOperatorLowerer {
    }
}
