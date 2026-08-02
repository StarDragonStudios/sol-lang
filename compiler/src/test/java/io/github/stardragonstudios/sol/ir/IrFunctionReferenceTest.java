package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrFunctionReferenceTest {
    @Test
    void preservesTypedFunctionSignature() {
        var reference =
            new IrFunctionReference(
                new IrFunctionId(3),
                "calculate",
                List.of(
                    PrimitiveIrType.INT,
                    PrimitiveIrType.BOOLEAN
                ),
                PrimitiveIrType.FLOAT
            );

        assertEquals(
            new IrFunctionId(3),
            reference.id()
        );

        assertEquals(
            "calculate",
            reference.name()
        );

        assertEquals(
            List.of(
                PrimitiveIrType.INT,
                PrimitiveIrType.BOOLEAN
            ),
            reference.parameterTypes()
        );

        assertEquals(
            PrimitiveIrType.FLOAT,
            reference.returnType()
        );

        assertEquals(
            2,
            reference.arity()
        );

        assertTrue(
            reference.returnsValue()
        );
    }

    @Test
    void representsVoidFunctions() {
        var reference =
            new IrFunctionReference(
                new IrFunctionId(0),
                "perform",
                List.of(),
                PrimitiveIrType.VOID
            );

        assertEquals(
            0,
            reference.arity()
        );

        assertFalse(
            reference.returnsValue()
        );
    }

    @Test
    void defensivelyCopiesParameterTypes() {
        var parameterTypes =
            new ArrayList<IrType>();

        parameterTypes.add(
            PrimitiveIrType.INT
        );

        var reference =
            new IrFunctionReference(
                new IrFunctionId(0),
                "identity",
                parameterTypes,
                PrimitiveIrType.INT
            );

        parameterTypes.clear();

        assertEquals(
            List.of(
                PrimitiveIrType.INT
            ),
            reference.parameterTypes()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () ->
                reference.parameterTypes()
                    .clear()
        );
    }

    @Test
    void rejectsInvalidSignatures() {
        assertThrows(
            NullPointerException.class,
            () ->
                new IrFunctionReference(
                    null,
                    "function",
                    List.of(),
                    PrimitiveIrType.VOID
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrFunctionReference(
                    new IrFunctionId(0),
                    null,
                    List.of(),
                    PrimitiveIrType.VOID
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrFunctionReference(
                    new IrFunctionId(0),
                    " ",
                    List.of(),
                    PrimitiveIrType.VOID
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrFunctionReference(
                    new IrFunctionId(0),
                    "function",
                    null,
                    PrimitiveIrType.VOID
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrFunctionReference(
                    new IrFunctionId(0),
                    "function",
                    List.of(),
                    null
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new IrFunctionReference(
                    new IrFunctionId(0),
                    "function",
                    java.util.Arrays.asList(
                        PrimitiveIrType.INT,
                        null
                    ),
                    PrimitiveIrType.VOID
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrFunctionReference(
                    new IrFunctionId(0),
                    "function",
                    List.of(
                        PrimitiveIrType.VOID
                    ),
                    PrimitiveIrType.VOID
                )
        );
    }
}
