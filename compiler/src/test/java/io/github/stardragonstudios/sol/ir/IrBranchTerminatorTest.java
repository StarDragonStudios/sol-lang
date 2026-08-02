package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrBranchTerminatorTest {
    @Test
    void exposesCanonicalTarget() {
        var target = new IrBlockTarget(new IrBlockId(1));
        var terminator = new IrBranchTerminator(target);

        assertSame(target, terminator.target());
        assertEquals(List.of(target), terminator.targets());
        assertEquals(List.of(), terminator.operands());
    }

    @Test
    void rejectsNullTarget() {
        assertThrows(
            NullPointerException.class,
            () -> new IrBranchTerminator(null)
        );
    }
}
