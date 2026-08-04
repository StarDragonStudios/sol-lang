package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockTarget;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionReference;
import io.github.stardragonstudios.sol.ir.IrLocal;
import io.github.stardragonstudios.sol.ir.IrLocalInitializeInstruction;
import io.github.stardragonstudios.sol.ir.IrValue;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildAlloca;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBuilderInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBuilder;
import static org.bytedeco.llvm.global.LLVM.LLVMGetParam;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;

final class LlvmFunctionLoweringContext
    implements AutoCloseable {

    private final IrFunction function;
    private final LlvmProgramLoweringContext programContext;
    private final LlvmFunctionHandle functionHandle;
    private final LLVMBuilderRef builder;

    private final Map<IrBlockTarget, LLVMBasicBlockRef> blocks = new IdentityHashMap<>();
    private final Map<IrLocal, LLVMValueRef> localSlots = new IdentityHashMap<>();
    private final Map<IrValue, LLVMValueRef> values = new IdentityHashMap<>();

    private boolean closed;

    private LlvmFunctionLoweringContext(IrFunction function, LlvmProgramLoweringContext programContext, LlvmFunctionHandle functionHandle, LLVMBuilderRef builder) {
        this.function = function;
        this.programContext = programContext;
        this.functionHandle = functionHandle;
        this.builder = builder;
    }

    static LlvmFunctionLoweringContext create(IrFunction function, LlvmProgramLoweringContext programContext) {
        Objects.requireNonNull(function, "Lowered Sol IR function must not be null.");
        Objects.requireNonNull(programContext, "LLVM program lowering context must not be null.");

        if (!function.hasBody()) throw new LlvmBackendException("Cannot lower the body of bodyless Sol IR function '%s'.".formatted(function.id()));

        var functionHandle = programContext.function(function.id());
        var builder = LLVMCreateBuilderInContext(programContext.module().contextHandle());

        if (Pointer.isNull(builder)) throw new LlvmBackendException("LLVM failed to create an instruction builder for Sol IR function '%s'.".formatted(function.id()));

        var context = new LlvmFunctionLoweringContext(function, programContext, functionHandle, builder);

        try {
            context.registerParameters();
            context.predeclareBlocks();
            context.predeclareLocals();

            return context;
        } catch (RuntimeException | LinkageError exception) {
            context.close();

            throw exception;
        }
    }

    IrFunction function() {
        return function;
    }

    LlvmFunctionHandle functionHandle() {
        ensureOpen();

        return functionHandle;
    }

    LlvmFunctionHandle function(IrFunctionReference reference) {
        Objects.requireNonNull(reference, "Called Sol IR function reference must not be null.");

        ensureOpen();

        return programContext.function(reference.id());
    }

    LLVMContextRef llvmContext() {
        ensureOpen();

        return programContext.module().contextHandle();
    }

    LLVMBuilderRef builder() {
        ensureOpen();

        return builder;
    }

    void positionAtEnd(IrBasicBlock block) {
        Objects.requireNonNull(block, "Positioned Sol IR basic block must not be null.");

        ensureOpen();

        LLVMPositionBuilderAtEnd(builder, block(block.target()));
    }

    LLVMBasicBlockRef block(IrBlockTarget target) {
        Objects.requireNonNull(target, "Queried Sol IR block target must not be null.");

        ensureOpen();

        var lowered = blocks.get(target);

        if (lowered == null) throw new LlvmBackendException("Sol IR block '%s' has no LLVM basic block.".formatted(target.id()));

        return lowered;
    }

    LLVMValueRef localSlot(IrLocal local) {
        Objects.requireNonNull(local, "Queried Sol IR local must not be null.");

        ensureOpen();

        var slot = localSlots.get(local);

        if (slot == null) throw new LlvmBackendException("Sol IR local '%s' has no LLVM storage slot.".formatted(local.id()));

        return slot;
    }

    LLVMValueRef value(IrValue value) {
        Objects.requireNonNull(value, "Lowered Sol IR value must not be null.");

        ensureOpen();

        var lowered = values.get(value);

        if (lowered != null) return lowered;

        return LlvmConstantLowerer.lower(value, this);
    }

    LLVMValueRef registerValue(IrValue value, LLVMValueRef lowered) {
        Objects.requireNonNull(value, "Registered Sol IR value must not be null.");
        Objects.requireNonNull(lowered, "Registered LLVM value must not be null.");

        ensureOpen();

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("Registered LLVM value for Sol IR value '%s' must not be a null native pointer.".formatted(value.id()));

        var previous = values.putIfAbsent(value, lowered);

        if (previous != null) throw new LlvmBackendException("Sol IR value '%s' already has an LLVM value.".formatted(value.id()));

        return lowered;
    }

    int blockCount() {
        return blocks.size();
    }

    int localCount() {
        return localSlots.size();
    }

    int valueCount() {
        return values.size();
    }

    @Override
    public void close() {
        if (closed) return;

        closed = true;

        if (!Pointer.isNull(builder)) {
            LLVMDisposeBuilder(builder);

            builder.setNull();
        }
    }

    private void registerParameters() {
        for (var index = 0; index < function.parameters().size(); index++) {
            var parameter = function.parameters().get(index);
            var lowered = LLVMGetParam(functionHandle.value(), index);

            if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM function '%s' has no parameter at index %d.".formatted(function.id(), index));

            registerValue(parameter, lowered);
        }
    }

    private void predeclareBlocks() {
        for (var block : function.blocks()) {
            var lowered = LLVMAppendBasicBlockInContext(llvmContext(), functionHandle.value(), block.id().toString());

            if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to create basic block '%s' for Sol IR function '%s'.".formatted(block.id(), function.id()));

            var previous = blocks.putIfAbsent(block.target(), lowered);

            if (previous != null) throw new LlvmBackendException("Sol IR block '%s' already has an LLVM basic block.".formatted(block.id()));
        }
    }

    private void predeclareLocals() {
        var entryBlock = function.entryBlock().orElseThrow(() -> new LlvmBackendException("Defined Sol IR function '%s' has no entry block.".formatted(function.id())));

        LLVMPositionBuilderAtEnd(builder, block(entryBlock.target()));

        for (var block : function.blocks())
            for (var instruction : block.instructions())
                if (instruction instanceof IrLocalInitializeInstruction initialization) predeclareLocal(initialization.local());
    }

    private void predeclareLocal(IrLocal local) {
        if (localSlots.containsKey(local)) throw new LlvmBackendException("Sol IR local '%s' already has an LLVM storage slot.".formatted(local.id()));

        var loweredType = LlvmTypeLowerer.lower(local.type(), llvmContext());
        var slot = LLVMBuildAlloca(builder, loweredType, local.id().toString());

        if (Pointer.isNull(slot)) throw new LlvmBackendException("LLVM failed to allocate storage for Sol IR local '%s'.".formatted(local.id()));

        localSlots.put(local, slot);
    }

    private void ensureOpen() {
        if (closed) throw new LlvmBackendException("LLVM lowering context for Sol IR function '%s' has already been closed.".formatted(function.id()));
    }
}
