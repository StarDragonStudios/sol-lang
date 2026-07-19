package io.github.stardragonstudios.sol.semantics.types;

import io.github.stardragonstudios.sol.syntax.LiteralKind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BuiltInTypes {
    public static final PrimitiveType INT =
        new PrimitiveType(
            "int",
            true,
            true,
            true
        );

    public static final PrimitiveType FLOAT =
        new PrimitiveType(
            "float",
            true,
            true,
            false
        );

    public static final PrimitiveType BOOLEAN =
        new PrimitiveType(
            "boolean",
            true,
            false,
            false
        );

    public static final PrimitiveType CHAR =
        new PrimitiveType(
            "char",
            true,
            false,
            false
        );

    public static final PrimitiveType STRING =
        new PrimitiveType(
            "string",
            true,
            false,
            false
        );

    public static final PrimitiveType VOID =
        new PrimitiveType(
            "void",
            false,
            false,
            false
        );

    public static final ErrorType ERROR =
        ErrorType.INSTANCE;

    private static final List<PrimitiveType>
        PRIMITIVE_TYPES = List.of(
        INT,
        FLOAT,
        BOOLEAN,
        CHAR,
        STRING,
        VOID
    );

    private static final Map<String, PrimitiveType>
        PRIMITIVES_BY_NAME =
        createPrimitiveLookup();

    private BuiltInTypes() {
    }

    public static List<PrimitiveType>
    primitiveTypes() {
        return PRIMITIVE_TYPES;
    }

    public static Optional<PrimitiveType> lookup(
        String name
    ) {
        Objects.requireNonNull(
            name,
            "Type lookup name must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Type lookup name must not be blank."
            );
        }

        return Optional.ofNullable(
            PRIMITIVES_BY_NAME.get(name)
        );
    }

    public static PrimitiveType typeOf(
        LiteralKind kind
    ) {
        Objects.requireNonNull(
            kind,
            "Literal kind must not be null."
        );

        return switch (kind) {
            case INTEGER -> INT;
            case FLOAT -> FLOAT;
            case BOOLEAN -> BOOLEAN;
            case CHARACTER -> CHAR;
            case STRING -> STRING;
        };
    }

    private static Map<String, PrimitiveType>
    createPrimitiveLookup() {
        var types =
            new LinkedHashMap<String, PrimitiveType>();

        for (var type : PRIMITIVE_TYPES) {
            types.put(
                type.name(),
                type
            );
        }

        return Map.copyOf(types);
    }
}
