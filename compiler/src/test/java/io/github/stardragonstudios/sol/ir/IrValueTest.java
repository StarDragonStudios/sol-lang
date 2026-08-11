package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IrValueTest {
    @Test
    void createsTypedParameterValues() {
        var parameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.INT);

        assertEquals(new IrValueId(0), parameter.id());
        assertEquals("value", parameter.name());
        assertSame(PrimitiveIrType.INT, parameter.type());
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(
            NullPointerException.class,
            () -> new IrParameter(null, "value", PrimitiveIrType.INT)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrParameter(new IrValueId(0), null, PrimitiveIrType.INT)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrParameter(new IrValueId(0), " ", PrimitiveIrType.INT)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrParameter(new IrValueId(0), "value", null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrParameter(new IrValueId(0), "value", PrimitiveIrType.VOID)
        );
    }

    @Test
    void createsPrimitiveConstants() {
        var integer = new IrIntConstant(new IrValueId(0), 42);
        var floatingPoint = new IrFloatConstant(new IrValueId(1), 3.5);
        var booleanValue = new IrBooleanConstant(new IrValueId(2), true);
        var character = new IrCharConstant(new IrValueId(3), 'A');
        var string = new IrStringConstant(new IrValueId(4), "Sol");

        assertSame(PrimitiveIrType.INT, integer.type());
        assertEquals(42, integer.value());
        assertSame(PrimitiveIrType.FLOAT, floatingPoint.type());
        assertEquals(3.5, floatingPoint.value());
        assertSame(PrimitiveIrType.BOOLEAN, booleanValue.type());
        assertTrue(booleanValue.value());
        assertSame(PrimitiveIrType.CHAR, character.type());
        assertEquals('A', character.codePoint());
        assertSame(PrimitiveIrType.STRING, string.type());
        assertEquals("Sol", string.value());
    }

    @Test
    void acceptsUnicodeScalarCharacterValues() {
        var character = new IrCharConstant(new IrValueId(0), 0x1F409);

        assertEquals(0x1F409, character.codePoint());
    }

    @Test
    void rejectsInvalidConstantValues() {
        assertThrows(
            NullPointerException.class,
            () -> new IrIntConstant(null, 42)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrFloatConstant(null, 1.0)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrFloatConstant(new IrValueId(0), Double.NaN)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrFloatConstant(new IrValueId(0), Double.POSITIVE_INFINITY)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrCharConstant(new IrValueId(0), -1)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrCharConstant(new IrValueId(0), Character.MIN_SURROGATE)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrStringConstant(null, "Sol")
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrStringConstant(new IrValueId(0), null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrStringConstant(new IrValueId(0), "\uD800")
        );
    }

    @Test
    void permitsEmptyStringConstants() {
        var constant = new IrStringConstant(new IrValueId(0), "");

        assertEquals("", constant.value());
    }
}
