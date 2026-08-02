package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrBlockTargetTest {
    @Test
    void preservesBlockIdentifier() {
        var identifier = new IrBlockId(3);
        var target = new IrBlockTarget(identifier);

        assertSame(identifier, target.id());
        assertEquals("block3", target.toString());
    }

    @Test
    void hasDeterministicValueEqualityButDistinctIdentity() {
        var first = new IrBlockTarget(new IrBlockId(1));
        var second = new IrBlockTarget(new IrBlockId(1));

        assertEquals(first, second);
        assertNotSame(first, second);
    }

    @Test
    void basicBlockPreservesCanonicalTarget() {
        var target = new IrBlockTarget(new IrBlockId(2));
        var block = new IrBasicBlock(target, List.of(), IrReturnTerminator.bare());

        assertSame(target, block.target());
        assertEquals(new IrBlockId(2), block.id());
    }

    @Test
    void compatibilityConstructorCreatesTarget() {
        var block = new IrBasicBlock(new IrBlockId(4), List.of(), IrReturnTerminator.bare());

        assertEquals(new IrBlockId(4), block.id());
        assertEquals(new IrBlockTarget(new IrBlockId(4)), block.target());
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(
            NullPointerException.class,
            () -> new IrBlockTarget(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrBasicBlock((IrBlockTarget) null, List.of(), IrReturnTerminator.bare())
        );
    }
}
