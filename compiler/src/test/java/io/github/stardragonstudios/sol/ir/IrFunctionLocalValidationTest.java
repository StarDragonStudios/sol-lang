package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrFunctionLocalValidationTest {
    @Test
    void validatesInitializedLoadedAndUpdatedLocals() {
        var local =
            new IrLocal(
                new IrLocalId(0),
                "value",
                PrimitiveIrType.INT,
                IrLocalKind.MUTABLE
            );

        var initializer =
            new IrIntConstant(
                new IrValueId(0),
                1
            );

        var initialization =
            new IrLocalInitializeInstruction(
                local,
                initializer
            );

        var firstLoad =
            new IrLocalLoadInstruction(
                new IrValueId(1),
                local
            );

        var storedValue =
            new IrIntConstant(
                new IrValueId(2),
                2
            );

        var store =
            new IrLocalStoreInstruction(
                local,
                storedValue
            );

        var secondLoad =
            new IrLocalLoadInstruction(
                new IrValueId(3),
                local
            );

        var block =
            new IrBasicBlock(
                new IrBlockId(0),
                List.of(
                    initialization,
                    firstLoad,
                    store,
                    secondLoad
                ),
                IrReturnTerminator.returning(
                    secondLoad
                )
            );

        var function =
            IrFunction.definition(
                new IrFunctionId(0),
                "update",
                List.of(),
                PrimitiveIrType.INT,
                List.of(block)
            );

        assertEquals(
            List.of(
                initialization,
                firstLoad,
                store,
                secondLoad
            ),
            function.entryBlock()
                .orElseThrow()
                .instructions()
        );

        assertSame(
            secondLoad,
            (
                (IrReturnTerminator)
                    function.entryBlock()
                        .orElseThrow()
                        .terminator()
            ).value()
                .orElseThrow()
        );
    }

    @Test
    void rejectsReferencesToUndeclaredLocals() {
        var local =
            new IrLocal(
                new IrLocalId(0),
                "value",
                PrimitiveIrType.INT,
                IrLocalKind.IMMUTABLE
            );

        var load =
            new IrLocalLoadInstruction(
                new IrValueId(0),
                local
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrFunction.definition(
                    new IrFunctionId(0),
                    "undeclared",
                    List.of(),
                    PrimitiveIrType.INT,
                    List.of(
                        new IrBasicBlock(
                            new IrBlockId(0),
                            List.of(load),
                            IrReturnTerminator.returning(
                                load
                            )
                        )
                    )
                )
        );
    }

    @Test
    void rejectsDuplicateLocalIdentifiers() {
        var first =
            new IrLocal(
                new IrLocalId(0),
                "first",
                PrimitiveIrType.INT,
                IrLocalKind.IMMUTABLE
            );

        var second =
            new IrLocal(
                new IrLocalId(0),
                "second",
                PrimitiveIrType.INT,
                IrLocalKind.IMMUTABLE
            );

        var firstInitialization =
            new IrLocalInitializeInstruction(
                first,
                new IrIntConstant(
                    new IrValueId(0),
                    1
                )
            );

        var secondInitialization =
            new IrLocalInitializeInstruction(
                second,
                new IrIntConstant(
                    new IrValueId(1),
                    2
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrFunction.definition(
                    new IrFunctionId(0),
                    "duplicate",
                    List.of(),
                    PrimitiveIrType.VOID,
                    List.of(
                        new IrBasicBlock(
                            new IrBlockId(0),
                            List.of(
                                firstInitialization,
                                secondInitialization
                            ),
                            IrReturnTerminator.bare()
                        )
                    )
                )
        );
    }

    @Test
    void rejectsMultipleInitializationsOfSameLocal() {
        var local =
            new IrLocal(
                new IrLocalId(0),
                "value",
                PrimitiveIrType.INT,
                IrLocalKind.IMMUTABLE
            );

        var first =
            new IrLocalInitializeInstruction(
                local,
                new IrIntConstant(
                    new IrValueId(0),
                    1
                )
            );

        var second =
            new IrLocalInitializeInstruction(
                local,
                new IrIntConstant(
                    new IrValueId(1),
                    2
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrFunction.definition(
                    new IrFunctionId(0),
                    "multiple_initialization",
                    List.of(),
                    PrimitiveIrType.VOID,
                    List.of(
                        new IrBasicBlock(
                            new IrBlockId(0),
                            List.of(
                                first,
                                second
                            ),
                            IrReturnTerminator.bare()
                        )
                    )
                )
        );
    }

    @Test
    void permitsShadowedLocalNames() {
        var outer =
            new IrLocal(
                new IrLocalId(0),
                "value",
                PrimitiveIrType.INT,
                IrLocalKind.IMMUTABLE
            );

        var inner =
            new IrLocal(
                new IrLocalId(1),
                "value",
                PrimitiveIrType.INT,
                IrLocalKind.IMMUTABLE
            );

        var outerInitialization =
            new IrLocalInitializeInstruction(
                outer,
                new IrIntConstant(
                    new IrValueId(0),
                    1
                )
            );

        var innerInitialization =
            new IrLocalInitializeInstruction(
                inner,
                new IrIntConstant(
                    new IrValueId(1),
                    2
                )
            );

        var load =
            new IrLocalLoadInstruction(
                new IrValueId(2),
                inner
            );

        var function =
            IrFunction.definition(
                new IrFunctionId(0),
                "shadowing",
                List.of(),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(0),
                        List.of(
                            outerInitialization,
                            innerInitialization,
                            load
                        ),
                        IrReturnTerminator.returning(
                            load
                        )
                    )
                )
            );

        assertSame(
            inner,
            load.local()
        );

        assertEquals(
            3,
            function.entryBlock()
                .orElseThrow()
                .instructions()
                .size()
        );
    }
}
