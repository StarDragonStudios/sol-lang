package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrTypesTest {
    @Test
    void exposesPrimitiveTypesInCanonicalOrder() {
        assertEquals(
            List.of(
                PrimitiveIrType.INT,
                PrimitiveIrType.FLOAT,
                PrimitiveIrType.BOOLEAN,
                PrimitiveIrType.CHAR,
                PrimitiveIrType.STRING,
                PrimitiveIrType.VOID
            ),

            IrTypes.primitiveTypes()
        );

        assertEquals(
            List.of("int", "float", "boolean", "char", "string", "void"),

            IrTypes.primitiveTypes().stream().map(IrType::displayName).toList()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> IrTypes.primitiveTypes().clear()
        );
    }

    @Test
    void returnsCanonicalPrimitiveInstances() {
        assertSame(PrimitiveIrType.INT, IrTypes.lookup("int").orElseThrow());
        assertSame(PrimitiveIrType.FLOAT, IrTypes.lookup("float").orElseThrow());
        assertSame(PrimitiveIrType.BOOLEAN, IrTypes.lookup("boolean").orElseThrow());
        assertSame(PrimitiveIrType.CHAR, IrTypes.lookup("char").orElseThrow());
        assertSame(PrimitiveIrType.STRING, IrTypes.lookup("string").orElseThrow());
        assertSame(PrimitiveIrType.VOID, IrTypes.lookup("void").orElseThrow());
        assertSame(IrTypes.lookup("int").orElseThrow(), IrTypes.lookup("int").orElseThrow());
    }

    @Test
    void performsCaseSensitivePrimitiveLookup() {
        assertTrue(IrTypes.lookup("Integer").isEmpty());
        assertTrue(IrTypes.lookup("Int").isEmpty());
        assertTrue(IrTypes.lookup("VOID").isEmpty());
        assertTrue(IrTypes.lookup("<error>").isEmpty());
        assertTrue(IrTypes.lookup("missing").isEmpty());
    }

    @Test
    void exposesPrimitiveCategories() {
        assertTrue(PrimitiveIrType.INT.isValue());
        assertTrue(PrimitiveIrType.INT.isNumeric());
        assertTrue(PrimitiveIrType.INT.isIntegral());
        assertTrue(PrimitiveIrType.FLOAT.isValue());
        assertTrue(PrimitiveIrType.FLOAT.isNumeric());
        assertFalse(PrimitiveIrType.FLOAT.isIntegral());
        assertTrue(PrimitiveIrType.BOOLEAN.isValue());
        assertFalse(PrimitiveIrType.BOOLEAN.isNumeric());
        assertFalse(PrimitiveIrType.BOOLEAN.isIntegral());
        assertTrue(PrimitiveIrType.CHAR.isValue());
        assertFalse(PrimitiveIrType.CHAR.isNumeric());
        assertFalse(PrimitiveIrType.CHAR.isIntegral());
        assertTrue(PrimitiveIrType.STRING.isValue());
        assertFalse(PrimitiveIrType.STRING.isNumeric());
        assertFalse(PrimitiveIrType.STRING.isIntegral());
        assertFalse(PrimitiveIrType.VOID.isValue());
        assertFalse(PrimitiveIrType.VOID.isNumeric());
        assertFalse(PrimitiveIrType.VOID.isIntegral());
    }

    @Test
    void exposesPrimitiveNamesAsText() {
        assertEquals("int", PrimitiveIrType.INT.toString());
        assertEquals("float", PrimitiveIrType.FLOAT.toString());
        assertEquals("void", PrimitiveIrType.VOID.toString());
    }

    @Test
    void rejectsInvalidLookupNames() {
        assertThrows(
            NullPointerException.class,
            () -> IrTypes.lookup(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrTypes.lookup(" ")
        );
    }

    @Test
    void distinguishesEnumAndIrDisplayNames() {
        assertEquals("INT", PrimitiveIrType.INT.name());
        assertEquals("int", PrimitiveIrType.INT.displayName());
    }
}
