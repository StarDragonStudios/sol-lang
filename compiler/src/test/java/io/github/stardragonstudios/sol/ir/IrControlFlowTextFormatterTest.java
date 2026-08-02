package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrControlFlowTextFormatterTest {
    @Test
    void formatsBranchesDeterministically() {
        var entry =
            new IrBlockTarget(
                new IrBlockId(0)
            );

        var thenTarget =
            new IrBlockTarget(
                new IrBlockId(1)
            );

        var exit =
            new IrBlockTarget(
                new IrBlockId(2)
            );

        var condition =
            new IrBooleanConstant(
                new IrValueId(0),
                true
            );

        var function =
            IrFunction.definition(
                new IrFunctionId(0),
                "choose",
                List.of(),
                PrimitiveIrType.VOID,
                List.of(
                    new IrBasicBlock(
                        entry,
                        List.of(),
                        new IrConditionalBranchTerminator(
                            condition,
                            thenTarget,
                            exit
                        )
                    ),
                    new IrBasicBlock(
                        thenTarget,
                        List.of(),
                        new IrBranchTerminator(
                            exit
                        )
                    ),
                    new IrBasicBlock(
                        exit,
                        List.of(),
                        IrReturnTerminator.bare()
                    )
                )
            );

        var program =
            IrProgram.library(
                List.of(
                    new IrModule(
                        new IrModuleName(
                            List.of(
                                "flow"
                            )
                        ),
                        List.of(
                            function
                        )
                    )
                )
            );

        assertEquals(
            """
            program {
              entry none

              module @flow {
                define @function0 choose() -> void {
                  block0:
                    %0: boolean = const true
                    branch_if %0, block1, block2

                  block1:
                    branch block2

                  block2:
                    return
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
