package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrBlockTarget;
import io.github.stardragonstudios.sol.ir.IrBooleanConstant;
import io.github.stardragonstudios.sol.ir.IrBranchTerminator;
import io.github.stardragonstudios.sol.ir.IrConditionalBranchTerminator;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmControlFlowLowererTest {
    @Test
    void lowersConditionalBranchesAndForwardTargets() {
        var condition = new IrParameter(new IrValueId(0), "condition", PrimitiveIrType.BOOLEAN);
        var result = new IrIntConstant(new IrValueId(1), 7);
        var entry = new IrBlockTarget(new IrBlockId(0));
        var whenTrue = new IrBlockTarget(new IrBlockId(1));
        var whenFalse = new IrBlockTarget(new IrBlockId(2));
        var merge = new IrBlockTarget(new IrBlockId(3));

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "choose",
            List.of(condition),
            PrimitiveIrType.INT,
            List.of(
                new IrBasicBlock(entry, List.of(), new IrConditionalBranchTerminator(condition, whenTrue, whenFalse)),
                new IrBasicBlock(whenTrue, List.of(), new IrBranchTerminator(merge)),
                new IrBasicBlock(whenFalse, List.of(), new IrBranchTerminator(merge)),
                new IrBasicBlock(merge, List.of(), IrReturnTerminator.returning(result))
            )
        );

        try (var module = LlvmBackend.generate(program(function), "sol.conditional-control-flow")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("br i1 %condition, label %block1, label %block2"));
            assertTrue(text.contains(
                """
                block1:
                  br label %block3
                """
            ) || text.contains(
                """
                block1:                                           ; preds = %block0
                  br label %block3
                """
            ));

            assertTrue(text.contains("block2:"));
            assertTrue(text.contains("br label %block3"));
            assertTrue(text.contains("ret i64 7"));

            module.verify();
        }
    }

    @Test
    void lowersLoopBackEdges() {
        var condition = new IrParameter(new IrValueId(0), "continue_loop", PrimitiveIrType.BOOLEAN);
        var entry = new IrBlockTarget(new IrBlockId(0));
        var loop = new IrBlockTarget(new IrBlockId(1));
        var exit = new IrBlockTarget(new IrBlockId(2));

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "loop",
            List.of(condition),
            PrimitiveIrType.VOID,
            List.of(
                new IrBasicBlock(entry, List.of(), new IrBranchTerminator(loop)),
                new IrBasicBlock(loop, List.of(), new IrConditionalBranchTerminator(condition, loop, exit)),
                new IrBasicBlock(exit, List.of(), IrReturnTerminator.bare())
            )
        );

        try (var module = LlvmBackend.generate(program(function), "sol.loop-control-flow")) {
            var text = normalizeNewlines(module.text());

            assertTrue(
                text.contains(
                    """
                    block0:
                      br label %block1
                    """
                )
            );

            assertTrue(
                text.contains(
                    "br i1 %continue_loop, label %block1, label %block2"
                )
            );

            assertTrue(
                text.contains(
                    "ret void"
                )
            );

            module.verify();
        }
    }

    @Test
    void lowersConstantBranchConditions() {
        var condition = new IrBooleanConstant(new IrValueId(0), true);
        var entry = new IrBlockTarget(new IrBlockId(0));
        var trueTarget = new IrBlockTarget(new IrBlockId(1));
        var falseTarget = new IrBlockTarget(new IrBlockId(2));

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "constant_condition",
            List.of(),
            PrimitiveIrType.VOID,
            List.of(
                new IrBasicBlock(entry, List.of(), new IrConditionalBranchTerminator(condition, trueTarget, falseTarget)),
                new IrBasicBlock(trueTarget, List.of(), IrReturnTerminator.bare()),
                new IrBasicBlock(falseTarget, List.of(), IrReturnTerminator.bare())
            )
        );

        try (var module = LlvmBackend.generate(program(function), "sol.constant-branch")) {
            assertTrue(normalizeNewlines(module.text()).contains("br i1 true, label %block1, label %block2"));

            module.verify();
        }
    }

    private static IrProgram program(IrFunction function) {
        return IrProgram.library(List.of(new IrModule(new IrModuleName(List.of("control_flow")), List.of(function))));
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }
}
