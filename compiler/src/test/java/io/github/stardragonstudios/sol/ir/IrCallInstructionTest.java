package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrCallInstructionTest {
    @Test
    void representsValueProducingCalls() {
        var target =
            new IrFunctionReference(
                new IrFunctionId(1),
                "select",
                List.of(
                    PrimitiveIrType.INT,
                    PrimitiveIrType.BOOLEAN
                ),
                PrimitiveIrType.INT
            );

        var firstArgument =
            new IrIntConstant(
                new IrValueId(0),
                42
            );

        var secondArgument =
            new IrBooleanConstant(
                new IrValueId(1),
                true
            );

        var call =
            new IrValueCallInstruction(
                new IrValueId(2),
                target,
                List.of(
                    firstArgument,
                    secondArgument
                )
            );

        assertEquals(
            new IrValueId(2),
            call.id()
        );

        assertSame(
            target,
            call.target()
        );

        assertEquals(
            PrimitiveIrType.INT,
            call.type()
        );

        assertEquals(
            List.of(
                firstArgument,
                secondArgument
            ),
            call.arguments()
        );

        assertEquals(
            call.arguments(),
            call.operands()
        );
    }

    @Test
    void representsVoidCalls() {
        var target =
            new IrFunctionReference(
                new IrFunctionId(0),
                "write",
                List.of(
                    PrimitiveIrType.INT
                ),
                PrimitiveIrType.VOID
            );

        var argument =
            new IrIntConstant(
                new IrValueId(0),
                5
            );

        var call =
            new IrVoidCallInstruction(
                target,
                List.of(
                    argument
                )
            );

        assertSame(
            target,
            call.target()
        );

        assertEquals(
            List.of(
                argument
            ),
            call.arguments()
        );

        assertEquals(
            call.arguments(),
            call.operands()
        );
    }

    @Test
    void preservesArgumentOrderAndCopiesCollection() {
        var target =
            new IrFunctionReference(
                new IrFunctionId(0),
                "combine",
                List.of(
                    PrimitiveIrType.INT,
                    PrimitiveIrType.INT
                ),
                PrimitiveIrType.INT
            );

        var first =
            new IrIntConstant(
                new IrValueId(0),
                1
            );

        var second =
            new IrIntConstant(
                new IrValueId(1),
                2
            );

        var arguments =
            new ArrayList<IrValue>();

        arguments.add(
            first
        );

        arguments.add(
            second
        );

        var call =
            new IrValueCallInstruction(
                new IrValueId(2),
                target,
                arguments
            );

        arguments.clear();

        assertEquals(
            List.of(
                first,
                second
            ),
            call.arguments()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () ->
                call.arguments()
                    .clear()
        );
    }

    @Test
    void rejectsIncorrectArgumentCount() {
        var target =
            new IrFunctionReference(
                new IrFunctionId(0),
                "identity",
                List.of(
                    PrimitiveIrType.INT
                ),
                PrimitiveIrType.INT
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrValueCallInstruction(
                    new IrValueId(0),
                    target,
                    List.of()
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrValueCallInstruction(
                    new IrValueId(1),
                    target,
                    List.of(
                        new IrIntConstant(
                            new IrValueId(0),
                            1
                        ),
                        new IrIntConstant(
                            new IrValueId(2),
                            2
                        )
                    )
                )
        );
    }

    @Test
    void rejectsIncorrectArgumentTypes() {
        var target =
            new IrFunctionReference(
                new IrFunctionId(0),
                "negate",
                List.of(
                    PrimitiveIrType.BOOLEAN
                ),
                PrimitiveIrType.BOOLEAN
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrValueCallInstruction(
                    new IrValueId(1),
                    target,
                    List.of(
                        new IrIntConstant(
                            new IrValueId(0),
                            1
                        )
                    )
                )
        );
    }

    @Test
    void distinguishesValueAndVoidCalls() {
        var valueTarget =
            new IrFunctionReference(
                new IrFunctionId(0),
                "value",
                List.of(),
                PrimitiveIrType.INT
            );

        var voidTarget =
            new IrFunctionReference(
                new IrFunctionId(1),
                "perform",
                List.of(),
                PrimitiveIrType.VOID
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrVoidCallInstruction(
                    valueTarget,
                    List.of()
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrValueCallInstruction(
                    new IrValueId(0),
                    voidTarget,
                    List.of()
                )
        );
    }

    @Test
    void rejectsNullInputs() {
        var valueTarget =
            new IrFunctionReference(
                new IrFunctionId(0),
                "value",
                List.of(),
                PrimitiveIrType.INT
            );

        var voidTarget =
            new IrFunctionReference(
                new IrFunctionId(1),
                "perform",
                List.of(),
                PrimitiveIrType.VOID
            );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrValueCallInstruction(
                    null,
                    valueTarget,
                    List.of()
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrValueCallInstruction(
                    new IrValueId(0),
                    null,
                    List.of()
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrValueCallInstruction(
                    new IrValueId(0),
                    valueTarget,
                    null
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrVoidCallInstruction(
                    null,
                    List.of()
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrVoidCallInstruction(
                    voidTarget,
                    null
                )
        );
    }
}
