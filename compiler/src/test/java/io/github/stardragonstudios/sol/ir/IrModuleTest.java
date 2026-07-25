package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrModuleTest {
    @Test
    void createsSingleAndMultiSegmentModuleNames() {
        var single = new IrModuleName(List.of("application"));
        var multiple = new IrModuleName(List.of("std", "console"));

        assertEquals("application", single.simpleName());
        assertEquals("application", single.qualifiedName());
        assertEquals("console", multiple.simpleName());
        assertEquals("std.console", multiple.qualifiedName());
        assertEquals("std.console", multiple.toString());
    }

    @Test
    void moduleNamesUseValueEqualityAndDefensiveCopying() {
        var segments = new ArrayList<>(List.of("std", "console"));
        var first = new IrModuleName(segments);
        var equivalent = new IrModuleName(List.of("std", "console"));
        segments.set(1, "file");

        assertEquals(first, equivalent);
        assertEquals(List.of("std", "console"), first.segments());
        assertThrows(
            UnsupportedOperationException.class,
            () -> first.segments().add("extra")
        );
    }

    @Test
    void rejectsInvalidModuleNames() {
        assertThrows(
            NullPointerException.class,
            () -> new IrModuleName(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrModuleName(List.of())
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrModuleName(List.of(""))
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrModuleName(List.of("std", " "))
        );

        var segments = new ArrayList<String>();
        segments.add("std");
        segments.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new IrModuleName(segments)
        );
    }

    @Test
    void createsModulesInDeterministicFunctionOrder() {
        var first = IrFunction.declaration(
            new IrFunctionId(0),
            "first",
            List.of(),
            PrimitiveIrType.VOID
        );

        var second = IrFunction.declaration(
            new IrFunctionId(1),
            "second",
            List.of(),
            PrimitiveIrType.INT
        );

        var functions = new ArrayList<>(List.of(first, second));

        var module = new IrModule(
            new IrModuleName(List.of("application")),
            functions
        );

        functions.clear();

        assertEquals(List.of(first, second), module.functions());
        assertSame(first, module.function(new IrFunctionId(0)).orElseThrow());
        assertSame(second, module.function("second").orElseThrow());
        assertTrue(module.function("missing").isEmpty());

        assertThrows(
            UnsupportedOperationException.class,
            () -> module.functions().clear()
        );
    }

    @Test
    void permitsModulesWithoutFunctions() {
        var module = new IrModule(
            new IrModuleName(List.of("empty")),
            List.of()
        );

        assertTrue(module.functions().isEmpty());
    }

    @Test
    void rejectsDuplicateModuleFunctions() {
        var first = IrFunction.declaration(
            new IrFunctionId(0),
            "first",
            List.of(),
            PrimitiveIrType.VOID
        );

        var duplicateIdentifier = IrFunction.declaration(
            new IrFunctionId(0),
            "second",
            List.of(),
            PrimitiveIrType.VOID
        );

        var duplicateName = IrFunction.declaration(
            new IrFunctionId(1),
            "first",
            List.of(),
            PrimitiveIrType.VOID
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrModule(
                new IrModuleName(List.of("duplicate_id")),
                List.of(first, duplicateIdentifier)
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrModule(
                new IrModuleName(List.of("duplicate_name")),
                List.of(first, duplicateName)
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrModule(
                new IrModuleName(List.of("duplicate_instance")),
                List.of(first, first)
            )
        );
    }

    @Test
    void rejectsInvalidModuleComponentsAndLookups() {
        var name = new IrModuleName(List.of("application"));

        assertThrows(
            NullPointerException.class,
            () -> new IrModule(null, List.of())
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrModule(name, null)
        );

        var functions = new ArrayList<IrFunction>();
        functions.add(null);

        assertThrows(
            NullPointerException.class,
            () -> new IrModule(name, functions)
        );

        var module = new IrModule(name, List.of());

        assertThrows(
            NullPointerException.class,
            () -> module.function((IrFunctionId) null)
        );

        assertThrows(
            NullPointerException.class,
            () -> module.function((String) null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> module.function(" ")
        );
    }
}
