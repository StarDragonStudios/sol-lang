package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrBinaryOperator;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrFunctionReference;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueCallInstruction;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.IrVoidCallInstruction;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmCallInstructionLowererTest {
    @Test
    void lowersValueAndVoidCallsAcrossModules() {
        var addReference =
            new IrFunctionReference(
                new IrFunctionId(
                    0
                ),
                "add",
                List.of(
                    PrimitiveIrType.INT,
                    PrimitiveIrType.INT
                ),
                PrimitiveIrType.INT
            );

        var consumeReference =
            new IrFunctionReference(
                new IrFunctionId(
                    1
                ),
                "consume",
                List.of(
                    PrimitiveIrType.INT
                ),
                PrimitiveIrType.VOID
            );

        var addLeft =
            new IrParameter(
                new IrValueId(
                    0
                ),
                "left",
                PrimitiveIrType.INT
            );

        var addRight =
            new IrParameter(
                new IrValueId(
                    1
                ),
                "right",
                PrimitiveIrType.INT
            );

        var sum =
            new IrBinaryInstruction(
                new IrValueId(
                    2
                ),
                IrBinaryOperator.ADD,
                addLeft,
                addRight
            );

        var add =
            IrFunction.definition(
                new IrFunctionId(
                    0
                ),
                "add",
                List.of(
                    addLeft,
                    addRight
                ),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(
                            sum
                        ),
                        IrReturnTerminator.returning(
                            sum
                        )
                    )
                )
            );

        var consume =
            IrFunction.declaration(
                new IrFunctionId(
                    1
                ),
                "consume",
                List.of(
                    new IrParameter(
                        new IrValueId(
                            0
                        ),
                        "value",
                        PrimitiveIrType.INT
                    )
                ),
                PrimitiveIrType.VOID
            );

        var callerLeft =
            new IrParameter(
                new IrValueId(
                    0
                ),
                "left",
                PrimitiveIrType.INT
            );

        var callerRight =
            new IrParameter(
                new IrValueId(
                    1
                ),
                "right",
                PrimitiveIrType.INT
            );

        var callAdd =
            new IrValueCallInstruction(
                new IrValueId(
                    2
                ),
                addReference,
                List.of(
                    callerLeft,
                    callerRight
                )
            );

        var callConsume =
            new IrVoidCallInstruction(
                consumeReference,
                List.of(
                    callAdd
                )
            );

        var run =
            IrFunction.definition(
                new IrFunctionId(
                    2
                ),
                "run",
                List.of(
                    callerLeft,
                    callerRight
                ),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(
                            callAdd,
                            callConsume
                        ),
                        IrReturnTerminator.returning(
                            callAdd
                        )
                    )
                )
            );

        /*
         * The caller module deliberately precedes the module
         * containing both call targets.
         */
        var program =
            IrProgram.library(
                List.of(
                    new IrModule(
                        new IrModuleName(
                            List.of(
                                "application"
                            )
                        ),
                        List.of(
                            run
                        )
                    ),
                    new IrModule(
                        new IrModuleName(
                            List.of(
                                "library"
                            )
                        ),
                        List.of(
                            add,
                            consume
                        )
                    )
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    program,
                    "sol.calls"
                )
        ) {
            var text =
                normalizeNewlines(
                    module.text()
                );

            assertTrue(
                text.contains(
                    "%value2 = call i64 @sol.function0.add("
                        + "i64 %left, i64 %right)"
                )
            );

            assertTrue(
                text.contains(
                    "call void @sol.function1.consume(i64 %value2)"
                )
            );

            assertTrue(
                text.contains(
                    "ret i64 %value2"
                )
            );

            assertTrue(
                text.contains(
                    "define i64 @sol.function0.add("
                        + "i64 %left, i64 %right)"
                )
            );

            assertTrue(
                text.contains(
                    "declare void @sol.function1.consume(i64)"
                )
            );

            module.verify();
        }
    }

    @Test
    void lowersRecursiveCallsWithoutArguments() {
        var recursiveReference =
            new IrFunctionReference(
                new IrFunctionId(
                    0
                ),
                "recurse",
                List.of(),
                PrimitiveIrType.INT
            );

        var recursiveCall =
            new IrValueCallInstruction(
                new IrValueId(
                    0
                ),
                recursiveReference,
                List.of()
            );

        var recurse =
            IrFunction.definition(
                new IrFunctionId(
                    0
                ),
                "recurse",
                List.of(),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(
                            recursiveCall
                        ),
                        IrReturnTerminator.returning(
                            recursiveCall
                        )
                    )
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    IrProgram.library(
                        List.of(
                            new IrModule(
                                new IrModuleName(
                                    List.of(
                                        "recursive"
                                    )
                                ),
                                List.of(
                                    recurse
                                )
                            )
                        )
                    ),
                    "sol.recursion"
                )
        ) {
            var text =
                normalizeNewlines(
                    module.text()
                );

            assertTrue(
                text.contains(
                    "%value0 = call i64 @sol.function0.recurse()"
                )
            );

            assertTrue(
                text.contains(
                    "ret i64 %value0"
                )
            );

            module.verify();
        }
    }

    @Test
    void preservesArgumentOrder() {
        var targetReference =
            new IrFunctionReference(
                new IrFunctionId(
                    0
                ),
                "combine",
                List.of(
                    PrimitiveIrType.INT,
                    PrimitiveIrType.INT
                ),
                PrimitiveIrType.INT
            );

        var target =
            IrFunction.declaration(
                new IrFunctionId(
                    0
                ),
                "combine",
                List.of(
                    new IrParameter(
                        new IrValueId(
                            0
                        ),
                        "first",
                        PrimitiveIrType.INT
                    ),
                    new IrParameter(
                        new IrValueId(
                            1
                        ),
                        "second",
                        PrimitiveIrType.INT
                    )
                ),
                PrimitiveIrType.INT
            );

        var first =
            new IrParameter(
                new IrValueId(
                    0
                ),
                "first",
                PrimitiveIrType.INT
            );

        var second =
            new IrParameter(
                new IrValueId(
                    1
                ),
                "second",
                PrimitiveIrType.INT
            );

        var call =
            new IrValueCallInstruction(
                new IrValueId(
                    2
                ),
                targetReference,
                List.of(
                    second,
                    first
                )
            );

        var caller =
            IrFunction.definition(
                new IrFunctionId(
                    1
                ),
                "caller",
                List.of(
                    first,
                    second
                ),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(
                            call
                        ),
                        IrReturnTerminator.returning(
                            call
                        )
                    )
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    IrProgram.library(
                        List.of(
                            new IrModule(
                                new IrModuleName(
                                    List.of(
                                        "ordered_calls"
                                    )
                                ),
                                List.of(
                                    caller,
                                    target
                                )
                            )
                        )
                    ),
                    "sol.argument-order"
                )
        ) {
            assertTrue(
                normalizeNewlines(
                    module.text()
                )
                    .contains(
                        "%value2 = call i64 @sol.function0.combine("
                            + "i64 %second, i64 %first)"
                    )
            );

            module.verify();
        }
    }

    private static String normalizeNewlines(
        String text
    ) {
        return text.replace(
            "\r\n",
            "\n"
        );
    }
}
