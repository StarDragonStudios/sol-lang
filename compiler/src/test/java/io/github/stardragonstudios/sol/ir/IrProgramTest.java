package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrProgramTest {
    @Test
    void createsEmptyLibraryPrograms() {
        var program = IrProgram.library(List.of());

        assertTrue(program.modules().isEmpty());
        assertFalse(program.hasEntryPoint());
        assertTrue(program.entryModule().isEmpty());
        assertTrue(program.entryFunction().isEmpty());
    }

    @Test
    void preservesModuleOrderAndSupportsLookups() {
        var firstFunction = IrFunction.declaration(
            new IrFunctionId(0),
            "first",
            List.of(),
            PrimitiveIrType.VOID
        );

        var secondFunction = IrFunction.declaration(
            new IrFunctionId(1),
            "second",
            List.of(),
            PrimitiveIrType.VOID
        );

        var firstModule = new IrModule(
            new IrModuleName(List.of("first")),
            List.of(firstFunction)
        );

        var secondModule = new IrModule(
            new IrModuleName(List.of("second")),
            List.of(secondFunction)
        );

        var modules = new ArrayList<>(List.of(firstModule, secondModule));
        var program = IrProgram.library(modules);

        modules.clear();

        assertEquals(List.of(firstModule, secondModule), program.modules());
        assertSame(
            firstModule,
            program.module(new IrModuleName(List.of("first"))).orElseThrow()
        );

        assertSame(
            secondFunction,
            program.function(new IrFunctionId(1)).orElseThrow()
        );

        assertTrue(program.module(new IrModuleName(List.of("missing"))).isEmpty());
        assertTrue(program.function(new IrFunctionId(99)).isEmpty());
        assertThrows(
            UnsupportedOperationException.class,
            () -> program.modules().clear()
        );
    }

    @Test
    void createsExecutablePrograms() {
        var parameter = new IrParameter(new IrValueId(0), "argument", PrimitiveIrType.STRING);
        var result = new IrIntConstant(new IrValueId(1), 0);

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "launch",
            List.of(parameter),
            PrimitiveIrType.INT,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(0),
                    List.of(),
                    IrReturnTerminator.returning(result)
                )
            )
        );

        var module = new IrModule(
            new IrModuleName(List.of("company", "product", "cli")),
            List.of(function)
        );

        var entryPoint = new IrEntryPoint(module, function);
        var program = IrProgram.executable(List.of(module), entryPoint);

        assertTrue(program.hasEntryPoint());
        assertSame(module, program.entryModule().orElseThrow());
        assertSame(function, program.entryFunction().orElseThrow());
        assertSame(entryPoint, program.entryPoint().orElseThrow());
    }

    @Test
    void rejectsStructurallyInvalidEntryPoints() {
        var declaration = IrFunction.declaration(
            new IrFunctionId(0),
            "external",
            List.of(),
            PrimitiveIrType.INT
        );

        var declarationModule = new IrModule(
            new IrModuleName(List.of("external")),
            List.of(declaration)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrEntryPoint(declarationModule, declaration)
        );

        var voidFunction = IrFunction.definition(
            new IrFunctionId(1),
            "void_start",
            List.of(),
            PrimitiveIrType.VOID,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(0),
                    List.of(),
                    IrReturnTerminator.bare()
                )
            )
        );

        var voidModule = new IrModule(
            new IrModuleName(List.of("void_application")),
            List.of(voidFunction)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrEntryPoint(voidModule, voidFunction)
        );

        var validFunction = intFunction(new IrFunctionId(2), "valid");
        var wrongModule = new IrModule(new IrModuleName(List.of("wrong")), List.of());

        assertThrows(
            IllegalArgumentException.class,
            () -> new IrEntryPoint(wrongModule, validFunction)
        );
    }

    @Test
    void requiresCanonicalEntryPointModule() {
        var function = intFunction(new IrFunctionId(0), "launch");
        var canonical = new IrModule(new IrModuleName(List.of("application")), List.of(function));
        var equivalentCopy = new IrModule(new IrModuleName(List.of("application")), List.of(function));
        var entryPoint = new IrEntryPoint(equivalentCopy, function);

        assertThrows(
            IllegalArgumentException.class,
            () -> IrProgram.executable(List.of(canonical), entryPoint)
        );
    }

    @Test
    void rejectsDuplicateProgramComponents() {
        var first = IrFunction.declaration(
            new IrFunctionId(0),
            "first",
            List.of(),
            PrimitiveIrType.VOID
        );

        var duplicateGlobalIdentifier = IrFunction.declaration(
            new IrFunctionId(0),
            "second",
            List.of(),
            PrimitiveIrType.VOID
        );

        var firstModule = new IrModule(
            new IrModuleName(List.of("first")),
            List.of(first)
        );

        var secondModule = new IrModule(
            new IrModuleName(List.of("second")),
            List.of(duplicateGlobalIdentifier)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrProgram.library(List.of(firstModule, secondModule))
        );

        var duplicateName = new IrModule(
            new IrModuleName(List.of("first")),
            List.of()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrProgram.library(List.of(firstModule, duplicateName))
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrProgram.library(List.of(firstModule, firstModule))
        );

        var sharedFunction = IrFunction.declaration(
            new IrFunctionId(2),
            "shared",
            List.of(),
            PrimitiveIrType.VOID
        );

        var left = new IrModule(
            new IrModuleName(List.of("left")),
            List.of(sharedFunction)
        );

        var right = new IrModule(
            new IrModuleName(List.of("right")),
            List.of(sharedFunction)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrProgram.library(List.of(left, right))
        );
    }

    @Test
    void rejectsInvalidProgramComponentsAndLookups() {
        assertThrows(
            NullPointerException.class,
            () -> new IrProgram(null, Optional.empty())
        );

        assertThrows(
            NullPointerException.class,
            () -> new IrProgram(List.of(), null)
        );

        var modules = new ArrayList<IrModule>();
        modules.add(null);

        assertThrows(
            NullPointerException.class,
            () -> IrProgram.library(modules)
        );

        var program = IrProgram.library(List.of());

        assertThrows(
            NullPointerException.class,
            () -> program.module(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> program.function(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> IrProgram.executable(List.of(), null)
        );
    }

    private static IrFunction intFunction(IrFunctionId id, String name) {
        var result = new IrIntConstant(new IrValueId(0), 0);

        return IrFunction.definition(
            id,
            name,
            List.of(),
            PrimitiveIrType.INT,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(0),
                    List.of(),
                    IrReturnTerminator.returning(result)
                )
            )
        );
    }
}
