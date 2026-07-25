package io.github.stardragonstudios.sol.ir;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class IrTypes {
    private static final List<PrimitiveIrType>
        PRIMITIVE_TYPES = List.of(
        PrimitiveIrType.INT,
        PrimitiveIrType.FLOAT,
        PrimitiveIrType.BOOLEAN,
        PrimitiveIrType.CHAR,
        PrimitiveIrType.STRING,
        PrimitiveIrType.VOID
    );

    private static final Map<String, PrimitiveIrType> PRIMITIVES_BY_NAME = createPrimitiveLookup();

    private IrTypes() {}

    public static List<PrimitiveIrType> primitiveTypes() {
        return PRIMITIVE_TYPES;
    }

    public static Optional<PrimitiveIrType> lookup(String name) {
        Objects.requireNonNull(name, "IR type lookup name must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("IR type lookup name must not be blank.");

        return Optional.ofNullable(PRIMITIVES_BY_NAME.get(name));
    }

    private static Map<String, PrimitiveIrType> createPrimitiveLookup() {
        var types = new LinkedHashMap<String, PrimitiveIrType>();

        for (var type : PRIMITIVE_TYPES) types.put(type.displayName(), type);

        return Map.copyOf(types);
    }
}
