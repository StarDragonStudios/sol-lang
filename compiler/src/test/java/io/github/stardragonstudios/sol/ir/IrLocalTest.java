package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IrLocalTest {
    @Test
    void createsDeterministicLocalIdentifiers() {
        var id = new IrLocalId(3);

        assertEquals(3, id.index());
        assertEquals("local3", id.toString());
    }

    @Test
    void rejectsNegativeLocalIdentifiers() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new IrLocalId(-1)
        );
    }

    @Test
    void createsTypedLocalStorage() {
        var constant = new IrLocal(new IrLocalId(0), "constant", PrimitiveIrType.INT, IrLocalKind.CONSTANT);
        var immutable = new IrLocal(new IrLocalId(1), "value", PrimitiveIrType.FLOAT, IrLocalKind.IMMUTABLE);
        var mutable = new IrLocal(new IrLocalId(2), "value", PrimitiveIrType.BOOLEAN, IrLocalKind.MUTABLE);

        assertTrue(constant.isConstant());
        assertFalse(constant.isMutable());
        assertFalse(immutable.isConstant());
        assertFalse(immutable.isMutable());
        assertTrue(mutable.isMutable());
        assertEquals(PrimitiveIrType.BOOLEAN, mutable.type());
    }

    @Test
    void allowsShadowedDiagnosticNames() {
        var outer = new IrLocal(new IrLocalId(0), "value", PrimitiveIrType.INT, IrLocalKind.IMMUTABLE);
        var inner = new IrLocal(new IrLocalId(1), "value", PrimitiveIrType.INT, IrLocalKind.IMMUTABLE);

        assertEquals(outer.name(), inner.name());
        assertNotEquals(outer.id(), inner.id());
    }

    @Test
    void rejectsInvalidLocalStorage() {
        assertThrows(
            NullPointerException.class,
            () -> new IrLocal(null, "value", PrimitiveIrType.INT, IrLocalKind.IMMUTABLE)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrLocal(new IrLocalId(0), null, PrimitiveIrType.INT, IrLocalKind.IMMUTABLE)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrLocal(new IrLocalId(0), " ", PrimitiveIrType.INT, IrLocalKind.IMMUTABLE)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrLocal(new IrLocalId(0), "value", null, IrLocalKind.IMMUTABLE)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrLocal(new IrLocalId(0), "value", PrimitiveIrType.INT, null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrLocal(new IrLocalId(0), "value", PrimitiveIrType.VOID, IrLocalKind.IMMUTABLE)
        );
    }
}
