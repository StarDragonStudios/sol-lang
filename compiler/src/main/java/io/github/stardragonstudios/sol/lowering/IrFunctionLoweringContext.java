package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
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
    private final IdentityHashMap<ParameterSymbol, IrParameter> parameters = new IdentityHashMap<>();
    private final IdentityHashMap<LocalVariableSymbol, IrLocal> locals = new IdentityHashMap<>();
    private final List<IrInstruction> instructions = new ArrayList<>();
    private final Set<IrInstruction> emittedInstructions = Collections.newSetFromMap(new IdentityHashMap<>());

    private int nextBlockIndex;
    private int nextValueIndex;
    private int nextLocalIndex;

    private boolean finished;

    IrFunctionLoweringContext(FunctionSymbol function) {
        this.function = Objects.requireNonNull(function, "Lowered function symbol must not be null.");
    }

    FunctionSymbol function() {
        return function;
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

        var lowered = new IrLocal(
            nextLocalId(),
            local.name(),
            type,
            lowerLocalKind(local.declarationKind()
            )
        );

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

        if (finished)
            throw new IrLoweringException("IR instructions cannot be emitted after the function block has been terminated.");

        if (!emittedInstructions.add(instruction))
            throw new IrLoweringException("The same IR instruction instance cannot be emitted more than once.");

        instructions.add(instruction);
    }

    List<IrInstruction> instructions() {
        return List.copyOf(instructions);
    }

    IrBasicBlock finishBlock(IrTerminator terminator) {
        Objects.requireNonNull(terminator, "Lowered function terminator must not be null.");

        if (finished)
            throw new IrLoweringException("Function '%s' has already finished its IR block.".formatted(function.name()));

        finished = true;

        return new IrBasicBlock(nextBlockId(), instructions, terminator);
    }

    private boolean belongsToFunction(ParameterSymbol parameter) {
        return function.declaration()
            .parameters()
            .stream()
            .anyMatch(declaration -> declaration == parameter.declaration());
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
