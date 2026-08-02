package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrLocalTextFormatterTest {
    @Test
    void formatsLocalStorageOperationsDeterministically() {
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

        var function =
            IrFunction.definition(
                new IrFunctionId(0),
                "update",
                List.of(),
                PrimitiveIrType.INT,
                List.of(
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
                    )
                )
            );

        var program =
            IrProgram.library(
                List.of(
                    new IrModule(
                        new IrModuleName(
                            List.of("locals")
                        ),
                        List.of(function)
                    )
                )
            );

        assertEquals(
            """
            program {
              entry none

              module @locals {
                define @function0 update() -> int {
                  block0:
                    %0: int = const 1
                    initialize local0 mut value: int, %0
                    %1: int = load local0
                    %2: int = const 2
                    store local0, %2
                    %3: int = load local0
                    return %3
                }
              }
            }
            """,
            IrTextFormatter.format(
                program
            )
        );
    }
}
