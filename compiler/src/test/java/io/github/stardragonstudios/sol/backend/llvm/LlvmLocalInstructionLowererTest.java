package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrBinaryOperator;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrLocal;
import io.github.stardragonstudios.sol.ir.IrLocalId;
import io.github.stardragonstudios.sol.ir.IrLocalInitializeInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalKind;
import io.github.stardragonstudios.sol.ir.IrLocalLoadInstruction;
import io.github.stardragonstudios.sol.ir.IrLocalStoreInstruction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmLocalInstructionLowererTest {
    @Test
    void lowersLocalInitializationLoadsAndStores() {
        var initial = new IrParameter(new IrValueId(0), "initial", PrimitiveIrType.INT);
        var local = new IrLocal(new IrLocalId(0), "counter", PrimitiveIrType.INT, IrLocalKind.MUTABLE);
        var one = new IrIntConstant(new IrValueId(1), 1);
        var initialization = new IrLocalInitializeInstruction(local, initial);
        var firstLoad = new IrLocalLoadInstruction(new IrValueId(2), local);
        var increment = new IrBinaryInstruction(new IrValueId(3), IrBinaryOperator.ADD, firstLoad, one);
        var update = new IrLocalStoreInstruction(local, increment);
        var secondLoad = new IrLocalLoadInstruction(new IrValueId(4), local);

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "increment",
            List.of(initial),
            PrimitiveIrType.INT,
            List.of(new IrBasicBlock(new IrBlockId(0), List.of(initialization, firstLoad, increment, update, secondLoad), IrReturnTerminator.returning(secondLoad)))
        );

        var program = program(function);

        try (var module = LlvmBackend.generate(program, "sol.locals")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("%local0 = alloca i64"));
            assertTrue(text.contains("store i64 %initial, ptr %local0"));
            assertTrue(text.contains("%value2 = load i64, ptr %local0"));
            assertTrue(text.contains("%value3 = add i64 %value2, 1"));
            assertTrue(text.contains("store i64 %value3, ptr %local0"));
            assertTrue(text.contains("%value4 = load i64, ptr %local0"));
            assertTrue(text.contains("ret i64 %value4"));

            module.verify();
        }
    }

    @Test
    void placesAllLocalAllocationsInEntryBlock() {
        var local = new IrLocal(new IrLocalId(0), "later", PrimitiveIrType.INT, IrLocalKind.IMMUTABLE);
        var firstResult = new IrIntConstant(new IrValueId(0), 0);
        var initializer = new IrIntConstant(new IrValueId(1), 42);
        var initialization = new IrLocalInitializeInstruction(local, initializer);
        var load = new IrLocalLoadInstruction(new IrValueId(2), local);

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "later_local",
            List.of(),
            PrimitiveIrType.INT,
            List.of(
                new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(firstResult)),
                new IrBasicBlock(new IrBlockId(1), List.of(initialization, load), IrReturnTerminator.returning(load))
            )
        );

        try (var module = LlvmBackend.generate(program(function), "sol.entry-allocas")) {
            var text = normalizeNewlines(module.text());
            var allocationIndex = text.indexOf("%local0 = alloca i64");
            var secondBlockIndex = text.indexOf("block1:");

            assertTrue(allocationIndex >= 0);
            assertTrue(secondBlockIndex >= 0);
            assertTrue(allocationIndex < secondBlockIndex);

            module.verify();
        }
    }

    @Test
    void resolvesLocalSlotsByCanonicalIdentity() {
        var parameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.INT);
        var local = new IrLocal(new IrLocalId(0), "stored", PrimitiveIrType.INT, IrLocalKind.IMMUTABLE);
        var initialization = new IrLocalInitializeInstruction(local, parameter);

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "identity_check",
            List.of(parameter),
            PrimitiveIrType.INT,
            List.of(new IrBasicBlock(new IrBlockId(0), List.of(initialization), IrReturnTerminator.returning(parameter)))
        );

        try (var module = LlvmModule.create("sol.local-identities")) {
            var programContext = new LlvmProgramLoweringContext(module);

            LlvmFunctionDeclarationLowerer.lower(function, programContext);

            try (var context = LlvmFunctionLoweringContext.create(function, programContext)) {
                assertEquals(1, context.localCount());
                assertTrue(context.localSlot(local).address() != 0);

                var equivalentCopy = new IrLocal(local.id(), local.name(), local.type(), local.kind());

                assertThrows(
                    LlvmBackendException.class,
                    () -> context.localSlot(equivalentCopy)
                );
            }
        }
    }

    private static IrProgram program(IrFunction function) {
        return IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("locals")), List.of(function))));
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }
}
