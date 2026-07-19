package io.github.stardragonstudios.sol.semantics.types;

import io.github.stardragonstudios.sol.syntax.LiteralKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInTypesTest {
    @Test
    void exposesPrimitiveTypesInCanonicalOrder() {
        assertEquals(
            List.of(
                BuiltInTypes.INT,
                BuiltInTypes.FLOAT,
                BuiltInTypes.BOOLEAN,
                BuiltInTypes.CHAR,
                BuiltInTypes.STRING,
                BuiltInTypes.VOID
            ),
            BuiltInTypes.primitiveTypes()
        );

        assertEquals(
            List.of(
                "int",
                "float",
                "boolean",
                "char",
                "string",
                "void"
            ),
            BuiltInTypes.primitiveTypes()
                .stream()
                .map(TypeSymbol::name)
                .toList()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> BuiltInTypes
                .primitiveTypes()
                .clear()
        );
    }

    @Test
    void returnsCanonicalPrimitiveInstances() {
        assertSame(
            BuiltInTypes.INT,
            BuiltInTypes.lookup("int")
                .orElseThrow()
        );

        assertSame(
            BuiltInTypes.FLOAT,
            BuiltInTypes.lookup("float")
                .orElseThrow()
        );

        assertSame(
            BuiltInTypes.BOOLEAN,
            BuiltInTypes.lookup("boolean")
                .orElseThrow()
        );

        assertSame(
            BuiltInTypes.CHAR,
            BuiltInTypes.lookup("char")
                .orElseThrow()
        );

        assertSame(
            BuiltInTypes.STRING,
            BuiltInTypes.lookup("string")
                .orElseThrow()
        );

        assertSame(
            BuiltInTypes.VOID,
            BuiltInTypes.lookup("void")
                .orElseThrow()
        );

        assertSame(
            BuiltInTypes.lookup("int")
                .orElseThrow(),
            BuiltInTypes.lookup("int")
                .orElseThrow()
        );
    }

    @Test
    void performsCaseSensitivePrimitiveLookup() {
        assertTrue(
            BuiltInTypes.lookup("Integer").isEmpty()
        );

        assertTrue(
            BuiltInTypes.lookup("Int").isEmpty()
        );

        assertTrue(
            BuiltInTypes.lookup("VOID").isEmpty()
        );

        assertTrue(
            BuiltInTypes.lookup("<error>").isEmpty()
        );

        assertTrue(
            BuiltInTypes.lookup("missing").isEmpty()
        );
    }

    @Test
    void exposesPrimitiveCategories() {
        assertEquals(
            TypeKind.PRIMITIVE,
            BuiltInTypes.INT.kind()
        );

        assertTrue(BuiltInTypes.INT.isValue());
        assertTrue(BuiltInTypes.INT.isNumeric());
        assertTrue(BuiltInTypes.INT.isIntegral());

        assertTrue(BuiltInTypes.FLOAT.isValue());
        assertTrue(BuiltInTypes.FLOAT.isNumeric());
        assertFalse(BuiltInTypes.FLOAT.isIntegral());

        assertTrue(BuiltInTypes.BOOLEAN.isValue());
        assertFalse(BuiltInTypes.BOOLEAN.isNumeric());
        assertFalse(BuiltInTypes.BOOLEAN.isIntegral());

        assertTrue(BuiltInTypes.CHAR.isValue());
        assertFalse(BuiltInTypes.CHAR.isNumeric());
        assertFalse(BuiltInTypes.CHAR.isIntegral());

        assertTrue(BuiltInTypes.STRING.isValue());
        assertFalse(BuiltInTypes.STRING.isNumeric());
        assertFalse(BuiltInTypes.STRING.isIntegral());

        assertFalse(BuiltInTypes.VOID.isValue());
        assertFalse(BuiltInTypes.VOID.isNumeric());
        assertFalse(BuiltInTypes.VOID.isIntegral());
    }

    @Test
    void exposesCanonicalErrorType() {
        assertSame(
            ErrorType.INSTANCE,
            BuiltInTypes.ERROR
        );

        assertEquals(
            TypeKind.ERROR,
            BuiltInTypes.ERROR.kind()
        );

        assertEquals(
            "<error>",
            BuiltInTypes.ERROR.name()
        );

        assertFalse(BuiltInTypes.ERROR.isValue());
        assertFalse(BuiltInTypes.ERROR.isNumeric());
        assertFalse(BuiltInTypes.ERROR.isIntegral());
    }

    @Test
    void mapsLiteralKindsToPrimitiveTypes() {
        assertSame(
            BuiltInTypes.INT,
            BuiltInTypes.typeOf(
                LiteralKind.INTEGER
            )
        );

        assertSame(
            BuiltInTypes.FLOAT,
            BuiltInTypes.typeOf(
                LiteralKind.FLOAT
            )
        );

        assertSame(
            BuiltInTypes.BOOLEAN,
            BuiltInTypes.typeOf(
                LiteralKind.BOOLEAN
            )
        );

        assertSame(
            BuiltInTypes.CHAR,
            BuiltInTypes.typeOf(
                LiteralKind.CHARACTER
            )
        );

        assertSame(
            BuiltInTypes.STRING,
            BuiltInTypes.typeOf(
                LiteralKind.STRING
            )
        );
    }

    @Test
    void rejectsInvalidLookupAndLiteralInputs() {
        assertThrows(
            NullPointerException.class,
            () -> BuiltInTypes.lookup(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> BuiltInTypes.lookup(" ")
        );

        assertThrows(
            NullPointerException.class,
            () -> BuiltInTypes.typeOf(null)
        );
    }

    @Test
    void validatesPrimitiveTypeConstruction() {
        assertThrows(
            NullPointerException.class,
            () -> new PrimitiveType(
                null,
                true,
                false,
                false
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PrimitiveType(
                " ",
                true,
                false,
                false
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PrimitiveType(
                "invalid",
                true,
                false,
                true
            )
        );
    }
}
