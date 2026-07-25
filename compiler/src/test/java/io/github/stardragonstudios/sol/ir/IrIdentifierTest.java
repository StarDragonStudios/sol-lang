package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrIdentifierTest {
    @Test
    void createsDeterministicValueIdentifiers() {
        var first = new IrValueId(0);
        var equivalent = new IrValueId(0);
        var second = new IrValueId(1);

        assertEquals(first, equivalent);
        assertNotEquals(first, second);
        assertEquals("%0", first.toString());
        assertEquals("%1", second.toString());
    }

    @Test
    void createsDeterministicBlockIdentifiers() {
        var first = new IrBlockId(0);
        var equivalent = new IrBlockId(0);
        var second = new IrBlockId(1);

        assertEquals(first, equivalent);
        assertNotEquals(first, second);
        assertEquals("block0", first.toString());
        assertEquals("block1", second.toString());
    }

    @Test
    void rejectsNegativeIdentifierIndexes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new IrValueId(-1)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrBlockId(-1)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrFunctionId(-1)
        );
    }

    @Test
    void createsDeterministicFunctionIdentifiers() {
        var first = new IrFunctionId(0);
        var equivalent = new IrFunctionId(0);
        var second = new IrFunctionId(1);

        assertEquals(first, equivalent);
        assertNotEquals(first, second);
        assertEquals("function0", first.toString());
        assertEquals("function1", second.toString());
    }
}
