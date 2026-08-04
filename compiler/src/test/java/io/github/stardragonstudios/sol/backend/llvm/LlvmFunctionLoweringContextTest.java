package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmFunctionLoweringContextTest {
    @Test
    void registersParametersAndPredeclaresBlocks() {
        var parameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.INT);
        var firstBlock = new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(parameter));
        var function = IrFunction.definition(new IrFunctionId(0), "identity", List.of(parameter), PrimitiveIrType.INT, List.of(firstBlock));

        try (var module = LlvmModule.create("sol.function-context")) {
            var programContext = new LlvmProgramLoweringContext(module);

            LlvmFunctionDeclarationLowerer.lower(function, programContext);

            var context = LlvmFunctionLoweringContext.create(function, programContext);

            try {
                assertEquals(1, context.blockCount());
                assertEquals(1, context.valueCount());
                assertSame(context.value(parameter), context.value(parameter));
                assertTrue(context.block(firstBlock.target()).address() != 0);
            } finally {
                context.close();
            }

            assertThrows(LlvmBackendException.class, context::builder);

            context.close();
        }
    }

    @Test
    void rejectsBodylessFunctions() {
        var function = IrFunction.declaration(new IrFunctionId(0), "external", List.of(), PrimitiveIrType.VOID);

        try (var module = LlvmModule.create("sol.bodyless-context")) {
            var programContext = new LlvmProgramLoweringContext(module);

            LlvmFunctionDeclarationLowerer.lower(function, programContext);

            assertThrows(
                LlvmBackendException.class,
                () -> LlvmFunctionLoweringContext.create(function, programContext)
            );
        }
    }
}