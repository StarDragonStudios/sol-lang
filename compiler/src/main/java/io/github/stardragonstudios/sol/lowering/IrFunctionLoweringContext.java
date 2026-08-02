package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrBlockTarget;
import io.github.stardragonstudios.sol.ir.IrFunctionReference;
import io.github.stardragonstudios.sol.ir.IrInstruction;
import io.github.stardragonstudios.sol.ir.IrLocal;
import io.github.stardragonstudios.sol.ir.IrLocalId;
import io.github.stardragonstudios.sol.ir.IrLocalKind;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrTerminator;
import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.LocalVariableSymbol;
import io.github.stardragonstudios.sol.semantics.ParameterSymbol;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class IrFunctionLoweringContext {
    private final FunctionSymbol function;
    private final IrProgramLoweringContext programContext;
    private final IdentityHashMap<ParameterSymbol, IrParameter> parameters = new IdentityHashMap<>();
    private final IdentityHashMap<LocalVariableSymbol, IrLocal> locals = new IdentityHashMap<>();
    private final List<IrBasicBlock> blocks = new ArrayList<>();
    private final Set<IrBlockTarget> allocatedBlockTargets = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<IrBlockTarget> startedBlockTargets = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<IrInstruction> emittedInstructions = Collections.newSetFromMap(new IdentityHashMap<>());

    private List<IrInstruction> instructions = new ArrayList<>();

    private IrBlockTarget currentBlockTarget;

    private int nextBlockIndex;
    private int nextValueIndex;
    private int nextLocalIndex;

    private boolean hasStartedBlock;

    IrFunctionLoweringContext(FunctionSymbol function) {
        this(function, new IrProgramLoweringContext());
    }

    IrFunctionLoweringContext(FunctionSymbol function, IrProgramLoweringContext programContext) {
        this.function = Objects.requireNonNull(function, "Lowered function symbol must not be null.");
        this.programContext = Objects.requireNonNull(programContext, "Program lowering context must not be null.");
    }

    FunctionSymbol function() {
        return function;
    }

    IrFunctionReference functionReference(FunctionSymbol target) {
        Objects.requireNonNull(target, "Queried called-function symbol must not be null.");

        return programContext.functionReference(target);
    }

    IrParameter declareParameter(ParameterSymbol parameter, IrType type) {
        Objects.requireNonNull(parameter, "Lowered parameter symbol must not be null.");
        Objects.requireNonNull(type, "Lowered parameter type must not be null.");

        if (!belongsToFunction(parameter))
            throw new IrLoweringException("Parameter '%s' does not belong to function '%s'.".formatted(parameter.name(), function.name()));

        if (parameters.containsKey(parameter))
            throw new IrLoweringException("Parameter '%s' has already been lowered in function '%s'.".formatted(parameter.name(), function.name()));

        var lowered = new IrParameter(nextValueId(), parameter.name(), type);

        parameters.put(parameter, lowered);

        return lowered;
    }

    IrParameter parameter(ParameterSymbol parameter) {
        Objects.requireNonNull(parameter, "Queried parameter symbol must not be null.");

        var lowered = parameters.get(parameter);

        if (lowered == null)
            throw new IrLoweringException("Parameter '%s' has not been lowered in function '%s'.".formatted(parameter.name(), function.name()));

        return lowered;
    }

    IrLocal declareLocal(LocalVariableSymbol local, IrType type) {
        Objects.requireNonNull(local, "Lowered local variable symbol must not be null.");
        Objects.requireNonNull(type, "Lowered local variable type must not be null.");

        if (locals.containsKey(local))
            throw new IrLoweringException("Local variable '%s' has already been lowered in function '%s'.".formatted(local.name(), function.name()));

        var lowered = new IrLocal(nextLocalId(), local.name(), type, lowerLocalKind(local.declarationKind()));

        locals.put(local, lowered);

        return lowered;
    }

    IrLocal local(LocalVariableSymbol local) {
        Objects.requireNonNull(local, "Queried local variable symbol must not be null.");

        var lowered = locals.get(local);

        if (lowered == null)
            throw new IrLoweringException("Local variable '%s' has not been lowered in function '%s'.".formatted(local.name(), function.name()));

        return lowered;
    }

    IrBlockId nextBlockId() {
        var identifier = new IrBlockId(nextBlockIndex);
        nextBlockIndex++;

        return identifier;
    }

    IrBlockTarget newBlockTarget() {
        var target = new IrBlockTarget(nextBlockId());

        allocatedBlockTargets.add(target);

        return target;
    }

    IrBlockTarget currentBlockTarget() {
        ensureInitialBlock();

        if (currentBlockTarget == null) throw new IrLoweringException("Function '%s' has no active IR basic block.".formatted(function.name()));

        return currentBlockTarget;
    }

    boolean hasActiveBlock() {
        return currentBlockTarget != null;
    }

    void beginBlock(IrBlockTarget target) {
        Objects.requireNonNull(target, "Started IR block target must not be null.");

        if (currentBlockTarget != null)
            throw new IrLoweringException("Function '%s' cannot begin IR block '%s' before terminating block '%s'.".formatted(function.name(), target.id(), currentBlockTarget.id()));

        if (!allocatedBlockTargets.contains(target))
            throw new IrLoweringException("IR block target '%s' was not allocated by function '%s'.".formatted(target.id(), function.name()));

        if (!startedBlockTargets.add(target))
            throw new IrLoweringException("IR block target '%s' has already been started in function '%s'.".formatted(target.id(), function.name()));

        currentBlockTarget = target;
        instructions = new ArrayList<>();
        hasStartedBlock = true;
    }

    IrValueId nextValueId() {
        var identifier = new IrValueId(nextValueIndex);
        nextValueIndex++;

        return identifier;
    }

    IrLocalId nextLocalId() {
        var identifier = new IrLocalId(nextLocalIndex);
        nextLocalIndex++;

        return identifier;
    }

    void emit(IrInstruction instruction) {
        Objects.requireNonNull(instruction, "Emitted IR instruction must not be null.");

        ensureActiveBlock();

        if (!emittedInstructions.add(instruction)) throw new IrLoweringException("The same IR instruction instance cannot be emitted more than once.");

        instructions.add(instruction);
    }

    List<IrInstruction> instructions() {
        return List.copyOf(instructions);
    }

    List<IrBasicBlock> blocks() {
        return List.copyOf(blocks);
    }

    IrBasicBlock finishBlock(IrTerminator terminator) {
        Objects.requireNonNull(terminator, "Lowered function terminator must not be null.");

        ensureActiveBlock();

        var block = new IrBasicBlock(currentBlockTarget, instructions, terminator);

        blocks.add(block);

        currentBlockTarget = null;
        instructions = new ArrayList<>();

        return block;
    }

    private void ensureInitialBlock() {
        if (currentBlockTarget == null && !hasStartedBlock) beginBlock(newBlockTarget());
    }

    private void ensureActiveBlock() {
        ensureInitialBlock();

        if (currentBlockTarget == null)
            throw new IrLoweringException("Function '%s' has no active IR basic block. Begin a block before emitting instructions or a terminator.".formatted(function.name()));
    }

    private boolean belongsToFunction(ParameterSymbol parameter) {
        return function.declaration().parameters().stream().anyMatch(declaration -> declaration == parameter.declaration());
    }

    private static IrLocalKind lowerLocalKind(VariableDeclarationKind kind) {
        Objects.requireNonNull(kind, "Lowered variable declaration kind must not be null.");

        return switch (kind) {
            case CONST -> IrLocalKind.CONSTANT;
            case LET -> IrLocalKind.IMMUTABLE;
            case MUTABLE_LET -> IrLocalKind.MUTABLE;
        };
    }
}
