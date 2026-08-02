package io.github.stardragonstudios.sol.ir;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record IrFunction(IrFunctionId id, String name, List<IrParameter> parameters, IrType returnType, Optional<List<IrBasicBlock>> body) {
    public IrFunction {
        Objects.requireNonNull(id, "IR function identifier must not be null.");
        Objects.requireNonNull(name, "IR function name must not be null.");
        Objects.requireNonNull(parameters, "IR function parameters must not be null.");
        Objects.requireNonNull(returnType, "IR function return type must not be null.");
        Objects.requireNonNull(body, "IR function body must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("IR function name must not be blank.");

        validateParameters(parameters);

        parameters = List.copyOf(parameters);

        if (body.isPresent()) {
            var blocks = body.orElseThrow();

            if (blocks.isEmpty()) throw new IllegalArgumentException("Defined IR function must contain at least one basic block.");

            for (var block : blocks) Objects.requireNonNull(block, "IR function body must not contain null basic blocks.");

            body = Optional.of(List.copyOf(blocks));
        }

        validateBody(parameters, returnType, body);
    }

    public static IrFunction declaration(IrFunctionId id, String name, List<IrParameter> parameters, IrType returnType) {
        return new IrFunction(id, name, parameters, returnType, Optional.empty());
    }

    public static IrFunction definition(
        IrFunctionId id,
        String name,
        List<IrParameter> parameters,
        IrType returnType,
        List<IrBasicBlock> blocks
    ) {
        Objects.requireNonNull(blocks, "Defined IR function blocks must not be null.");

        return new IrFunction(id, name, parameters, returnType, Optional.of(blocks));
    }

    public boolean hasBody() {
        return body.isPresent();
    }

    public List<IrBasicBlock> blocks() {
        return body.orElseGet(List::of);
    }

    public Optional<IrBasicBlock> entryBlock() {
        return body.map(List::getFirst);
    }

    private static void validateParameters(List<IrParameter> parameters) {
        var identifiers = new HashSet<IrValueId>();
        var names = new HashSet<String>();

        Set<IrParameter> instances = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var parameter : parameters) {
            Objects.requireNonNull(parameter, "IR function parameters must not contain null values.");

            if (!instances.add(parameter))
                throw new IllegalArgumentException("IR function must not contain the same parameter instance more than once.");

            if (!identifiers.add(parameter.id()))
                throw new IllegalArgumentException("IR function must not contain duplicate parameter identifier '%s'.".formatted(parameter.id()));

            if (!names.add(parameter.name()))
                throw new IllegalArgumentException("IR function must not contain duplicate parameter name '%s'.".formatted(parameter.name()));
        }
    }

    private static void validateBody(List<IrParameter> parameters, IrType returnType, Optional<List<IrBasicBlock>> body) {
        if (body.isEmpty()) return;

        var blockIdentifiers = new HashSet<IrBlockId>();

        Set<IrBlockTarget> declaredBlockTargets = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IrParameter> declaredParameters = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IrInstruction> declaredInstructions = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IrLocal> declaredLocals = Collections.newSetFromMap(new IdentityHashMap<>());

        var localsByIdentifier = new HashMap<IrLocalId, IrLocal>();

        declaredParameters.addAll(parameters);

        for (var block : body.orElseThrow()) {
            if (!declaredBlockTargets.add(block.target())) throw new IllegalArgumentException("IR function must not contain the same basic block target instance more than once.");
            if (!blockIdentifiers.add(block.id())) throw new IllegalArgumentException("IR function must not contain duplicate basic block identifier '%s'.".formatted(block.id()));

            for (var instruction : block.instructions()) {
                if (!declaredInstructions.add(instruction)) throw new IllegalArgumentException("IR function must not contain the same instruction instance more than once.");

                if (instruction instanceof IrLocalInitializeInstruction initialization) declareLocal(initialization.local(), declaredLocals, localsByIdentifier);
            }
        }

        var valuesByIdentifier = new HashMap<IrValueId, IrValue>();

        Set<IrValue> visitedValues = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var parameter : parameters) validateValueGraph(parameter, declaredParameters, declaredInstructions, declaredLocals, valuesByIdentifier, visitedValues);

        for (var block : body.orElseThrow()) {
            for (var instruction : block.instructions()) {
                validateInstruction(
                    instruction,
                    declaredParameters,
                    declaredInstructions,
                    declaredLocals,
                    valuesByIdentifier,
                    visitedValues
                );
            }

            validateTerminator(
                block.terminator(),
                returnType,
                declaredBlockTargets,
                declaredParameters,
                declaredInstructions,
                declaredLocals,
                valuesByIdentifier,
                visitedValues
            );
        }
    }

    private static void declareLocal(IrLocal local, Set<IrLocal> declaredLocals, Map<IrLocalId, IrLocal> localsByIdentifier) {
        if (!declaredLocals.add(local))
            throw new IllegalArgumentException("IR local '%s' is initialized more than once.".formatted(local.id()));

        var previous = localsByIdentifier.putIfAbsent(local.id(), local);

        if (previous != null && previous != local)
            throw new IllegalArgumentException("IR function must not contain duplicate local identifier '%s'.".formatted(local.id()));
    }

    private static void validateInstruction(
        IrInstruction instruction,
        Set<IrParameter> declaredParameters,
        Set<IrInstruction> declaredInstructions,
        Set<IrLocal> declaredLocals,
        Map<IrValueId, IrValue> valuesByIdentifier,
        Set<IrValue> visitedValues
    ) {
        Objects.requireNonNull(instruction, "IR instruction must not be null.");

        if (!declaredInstructions.contains(instruction)) throw new IllegalArgumentException("IR function references an undeclared instruction instance.");

        validateLocalReference(instruction, declaredLocals);

        if (instruction instanceof IrValueInstruction valueInstruction) {
            validateValueGraph(
                valueInstruction,
                declaredParameters,
                declaredInstructions,
                declaredLocals,
                valuesByIdentifier,
                visitedValues
            );

            return;
        }

        validateOperands(
            instruction,
            declaredParameters,
            declaredInstructions,
            declaredLocals,
            valuesByIdentifier,
            visitedValues
        );
    }

    private static void validateTerminator(
        IrTerminator terminator,
        IrType returnType,
        Set<IrBlockTarget> declaredBlockTargets,
        Set<IrParameter> declaredParameters,
        Set<IrInstruction> declaredInstructions,
        Set<IrLocal> declaredLocals,
        Map<IrValueId, IrValue> valuesByIdentifier,
        Set<IrValue> visitedValues
    ) {
        Objects.requireNonNull(terminator, "IR terminator must not be null.");

        var targets = Objects.requireNonNull(terminator.targets(), "IR terminator targets must not be null.");

        for (var target : targets) {
            Objects.requireNonNull(target, "IR terminator targets must not contain null values.");

            if (!declaredBlockTargets.contains(target))
                throw new IllegalArgumentException("IR function terminator references undeclared basic block target '%s'.".formatted(target.id()));
        }

        var operands = Objects.requireNonNull(terminator.operands(), "IR terminator operands must not be null.");

        for (var operand : operands) {
            validateValueGraph(
                operand,
                declaredParameters,
                declaredInstructions,
                declaredLocals,
                valuesByIdentifier,
                visitedValues
            );
        }

        if (terminator instanceof IrReturnTerminator returnTerminator) validateReturn(returnType, returnTerminator);
    }

    private static void validateValueGraph(
        IrValue value,
        Set<IrParameter> declaredParameters,
        Set<IrInstruction> declaredInstructions,
        Set<IrLocal> declaredLocals,
        Map<IrValueId, IrValue> valuesByIdentifier,
        Set<IrValue> visitedValues
    ) {
        Objects.requireNonNull(value, "IR value graph must not contain null values.");

        if (!visitedValues.add(value)) return;

        var previous = valuesByIdentifier.putIfAbsent(value.id(), value);

        if (previous != null && previous != value)
            throw new IllegalArgumentException("IR function must not contain duplicate value identifier '%s'.".formatted(value.id()));

        if (value instanceof IrParameter parameter && !declaredParameters.contains(parameter))
            throw new IllegalArgumentException("IR function value graph references an undeclared parameter '%s'.".formatted(parameter.name()));

        if (value instanceof IrValueInstruction instruction) {
            if (!declaredInstructions.contains(instruction))
                throw new IllegalArgumentException("IR function value graph references an undeclared instruction '%s'.".formatted(value.id()));

            validateLocalReference(instruction, declaredLocals);
            validateOperands(instruction, declaredParameters, declaredInstructions, declaredLocals, valuesByIdentifier, visitedValues);
        }
    }

    private static void validateLocalReference(IrInstruction instruction, Set<IrLocal> declaredLocals) {
        if (instruction instanceof IrLocalInstruction localInstruction && !declaredLocals.contains(localInstruction.local()))
            throw new IllegalArgumentException("IR function references undeclared local '%s'.".formatted(localInstruction.local().id()));
    }

    private static void validateOperands(
        IrInstruction instruction,
        Set<IrParameter> declaredParameters,
        Set<IrInstruction> declaredInstructions,
        Set<IrLocal> declaredLocals,
        Map<IrValueId, IrValue> valuesByIdentifier,
        Set<IrValue> visitedValues
    ) {
        var operands = Objects.requireNonNull(instruction.operands(), "IR instruction operands must not be null.");

        for (var operand : operands) {
            validateValueGraph(
                operand,
                declaredParameters,
                declaredInstructions,
                declaredLocals,
                valuesByIdentifier,
                visitedValues
            );
        }
    }

    private static void validateReturn(IrType returnType, IrReturnTerminator terminator) {
        if (!returnType.isValue()) {
            if (terminator.returnsValue())
                throw new IllegalArgumentException("IR function returning '%s' must use a bare return.".formatted(returnType.displayName()));

            return;
        }

        var value = terminator.value().orElseThrow(() -> new IllegalArgumentException("IR function returning '%s' must return a value.".formatted(returnType.displayName())));

        if (!returnType.equals(value.type()))
            throw new IllegalArgumentException("IR return type '%s' does not match function return type '%s'.".formatted(value.type().displayName(), returnType.displayName()));
    }
}
