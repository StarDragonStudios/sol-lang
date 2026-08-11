package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class IrStructInstructionTest {
    private static final IrStructField X = new IrStructField("x", PrimitiveIrType.INT, 0);
    private static final IrStructField Y = new IrStructField("y", PrimitiveIrType.INT, 1);
    private static final IrStructType POINT = new IrStructType("application::Point", List.of(X, Y));

    @Test
    void validatesCanonicalStructDefinitions() {
        assertEquals(List.of("x", "y"), POINT.fields().stream().map(IrStructField::name).toList());

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrStructType(
                "application::InvalidIndex",
                List.of(new IrStructField("x", PrimitiveIrType.INT, 1))
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new IrStructType(
                "application::Duplicate",
                List.of(
                    new IrStructField("value", PrimitiveIrType.INT, 0),
                    new IrStructField("value", PrimitiveIrType.INT, 1)
                )
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new IrStructField("empty", PrimitiveIrType.VOID, 0)
        );
    }

    @Test
    void validatesConstructionFieldOrderAndTypes() {
        var one = new IrIntConstant(new IrValueId(0), 1);
        var two = new IrIntConstant(new IrValueId(1), 2);
        var point = new IrStructConstructInstruction(new IrValueId(2), POINT, List.of(one, two));

        assertEquals(POINT, point.type());
        assertEquals(List.of(one, two), point.operands());

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrStructConstructInstruction(new IrValueId(3), POINT, List.of(one))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new IrStructConstructInstruction(
                new IrValueId(3),
                POINT,
                List.of(one, new IrBooleanConstant(new IrValueId(4), true))
            )
        );
    }

    @Test
    void validatesCanonicalExtractionAndNestedStorePaths() {
        var one = new IrIntConstant(new IrValueId(0), 1);
        var two = new IrIntConstant(new IrValueId(1), 2);
        var point = new IrStructConstructInstruction(new IrValueId(2), POINT, List.of(one, two));
        var extracted = new IrStructFieldExtractInstruction(new IrValueId(3), point, X);
        var pointField = new IrStructField("point", POINT, 0);
        var box = new IrStructType("application::Box", List.of(pointField));
        var local = new IrLocal(new IrLocalId(0), "box", box, IrLocalKind.MUTABLE);
        var store = new IrStructFieldStoreInstruction(local, List.of(pointField, Y), one);

        assertEquals(PrimitiveIrType.INT, extracted.type());
        assertEquals(List.of("point", "y"), store.path().stream().map(IrStructField::name).toList());

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrStructFieldExtractInstruction(
                new IrValueId(4),
                point,
                new IrStructField("x", PrimitiveIrType.INT, 0)
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new IrStructFieldStoreInstruction(
                new IrLocal(new IrLocalId(1), "immutable", box, IrLocalKind.IMMUTABLE),
                List.of(pointField, Y),
                one
            )
        );
    }
}
