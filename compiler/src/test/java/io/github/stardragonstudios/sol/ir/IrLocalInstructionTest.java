package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrLocalInstructionTest {
    @Test
    void initializesTypedLocalStorage() {
        var local =
            mutableIntegerLocal();

        var initializer =
            new IrIntConstant(
                new IrValueId(0),
                42
            );

        var instruction =
            new IrLocalInitializeInstruction(
                local,
                initializer
            );

        assertSame(
            local,
            instruction.local()
        );

        assertSame(
            initializer,
            instruction.initializer()
        );

        assertEquals(
            List.of(initializer),
            instruction.operands()
        );
    }

    @Test
    void loadsValuesFromLocalStorage() {
        var local =
            mutableIntegerLocal();

        var instruction =
            new IrLocalLoadInstruction(
                new IrValueId(1),
                local
            );

        assertEquals(
            new IrValueId(1),
            instruction.id()
        );

        assertSame(
            local,
            instruction.local()
        );

        assertEquals(
            PrimitiveIrType.INT,
            instruction.type()
        );

        assertTrue(
            instruction.operands()
                .isEmpty()
        );
    }

    @Test
    void storesValuesIntoMutableLocalStorage() {
        var local =
            mutableIntegerLocal();

        var value =
            new IrIntConstant(
                new IrValueId(0),
                42
            );

        var instruction =
            new IrLocalStoreInstruction(
                local,
                value
            );

        assertSame(
            local,
            instruction.local()
        );

        assertSame(
            value,
            instruction.value()
        );

        assertEquals(
            List.of(value),
            instruction.operands()
        );
    }

    @Test
    void rejectsInitializerTypeMismatches() {
        var local =
            mutableIntegerLocal();

        var value =
            new IrBooleanConstant(
                new IrValueId(0),
                true
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrLocalInitializeInstruction(
                    local,
                    value
                )
        );
    }

    @Test
    void rejectsUpdatesToImmutableStorage() {
        var value =
            new IrIntConstant(
                new IrValueId(0),
                42
            );

        var immutable =
            new IrLocal(
                new IrLocalId(0),
                "immutable",
                PrimitiveIrType.INT,
                IrLocalKind.IMMUTABLE
            );

        var constant =
            new IrLocal(
                new IrLocalId(1),
                "constant",
                PrimitiveIrType.INT,
                IrLocalKind.CONSTANT
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrLocalStoreInstruction(
                    immutable,
                    value
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrLocalStoreInstruction(
                    constant,
                    value
                )
        );
    }

    @Test
    void rejectsStoredValueTypeMismatches() {
        var local =
            mutableIntegerLocal();

        var value =
            new IrFloatConstant(
                new IrValueId(0),
                42.0
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrLocalStoreInstruction(
                    local,
                    value
                )
        );
    }

    private static IrLocal
    mutableIntegerLocal() {
        return new IrLocal(
            new IrLocalId(0),
            "value",
            PrimitiveIrType.INT,
            IrLocalKind.MUTABLE
        );
    }
}
