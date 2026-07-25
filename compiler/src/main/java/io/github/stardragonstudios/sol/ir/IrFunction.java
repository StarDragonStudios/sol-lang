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

            if (blocks.isEmpty())
                throw new IllegalArgumentException("Defined IR function must contain at least one basic block.");

            for (var block : blocks)
                Objects.requireNonNull(block, "IR function body must not contain null basic blocks.");

            body = Optional.of(List.copyOf(blocks));
        }

        validateBody(parameters, returnType, body);
    }

    public static IrFunction declaration(IrFunctionId id, String name, List<IrParameter> parameters, IrType returnType) {
        return new IrFunction(id, name, parameters, returnType, Optional.empty());
    }

    public static IrFunction definition(IrFunctionId id, String name, List<IrParameter> parameters, IrType returnType, List<IrBasicBlock> blocks) {
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
        var instances = Collections.newSetFromMap(new IdentityHashMap<IrParameter, Boolean>());

        for (var parameter : parameters) {
            Objects.requireNonNull(parameter, "IR function parameters must not contain null values.");

            if (!instances.add(parameter))
                throw new IllegalArgumentException("IR function must not contain the same parameter instance more than once.");

            if (!identifiers.add(parameter.id()))
                throw new IllegalArgumentException(
                    "IR function must not contain duplicate parameter identifier '%s'."
                        .formatted(parameter.id())
                );

            if (!names.add(parameter.name()))
                throw new IllegalArgumentException(
                    "IR function must not contain duplicate parameter name '%s'."
                        .formatted(parameter.name())
                );
        }
    }

    private static void validateBody(List<IrParameter> parameters, IrType returnType, Optional<List<IrBasicBlock>> body) {
        if (body.isEmpty()) return;

        var blockIdentifiers = new HashSet<IrBlockId>();

        Set<IrParameter> declaredParameters = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IrInstruction> declaredInstructions = Collections.newSetFromMap(new IdentityHashMap<>());

        declaredParameters.addAll(parameters);

        for (var block : body.orElseThrow()) {
            if (!blockIdentifiers.add(block.id()))
                throw new IllegalArgumentException(
                    "IR function must not contain duplicate basic block identifier '%s'."
                        .formatted(block.id())
                );

            for (var instruction : block.instructions())
                if (!declaredInstructions.add(instruction))
                    throw new IllegalArgumentException("IR function must not contain the same instruction instance more than once.");
        }

        var valuesByIdentifier = new HashMap<IrValueId, IrValue>();

        Set<IrValue> visitedValues = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var parameter : parameters)
            validateValueGraph(parameter, declaredParameters, declaredInstructions, valuesByIdentifier, visitedValues);

        for (var block : body.orElseThrow()) {
            for (var instruction : block.instructions())
                validateInstruction(instruction, declaredParameters, declaredInstructions, valuesByIdentifier, visitedValues);

            if (block.terminator() instanceof IrReturnTerminator returnTerminator) {
                validateReturn(returnType, returnTerminator);

                returnTerminator.value().ifPresent(value -> validateValueGraph(
                    value,
                    declaredParameters,
                    declaredInstructions,
                    valuesByIdentifier,
                    visitedValues
                ));
            }
        }
    }

    private static void validateInstruction(
        IrInstruction instruction,
        Set<IrParameter> declaredParameters,
        Set<IrInstruction> declaredInstructions,
        Map<IrValueId, IrValue> valuesByIdentifier,
        Set<IrValue> visitedValues
    ) {
        Objects.requireNonNull(instruction, "IR instruction must not be null.");

        if (!declaredInstructions.contains(instruction))
            throw new IllegalArgumentException("IR function references an undeclared instruction instance.");

        validateValueGraph(
            instruction,
            declaredParameters,
            declaredInstructions,
            valuesByIdentifier,
            visitedValues
        );
    }

    private static void validateValueGraph(
        IrValue value,
        Set<IrParameter> declaredParameters,
        Set<IrInstruction> declaredInstructions,
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

        if (value instanceof IrInstruction instruction) {
            if (!declaredInstructions.contains(instruction))
                throw new IllegalArgumentException("IR function value graph references an undeclared instruction '%s'.".formatted(value.id()));

            validateOperands(
                instruction,
                declaredParameters,
                declaredInstructions,
                valuesByIdentifier,
                visitedValues
            );
        }
    }

    private static void validateOperands(
        IrInstruction instruction,
        Set<IrParameter> declaredParameters,
        Set<IrInstruction> declaredInstructions,
        Map<IrValueId, IrValue> valuesByIdentifier,
        Set<IrValue> visitedValues
    ) {
        var operands = Objects.requireNonNull(instruction.operands(), "IR instruction operands must not be null.");

        for (var operand : operands)
            validateValueGraph(operand, declaredParameters, declaredInstructions, valuesByIdentifier, visitedValues);
    }

    private static void validateReturn(IrType returnType, IrReturnTerminator terminator) {
        if (!returnType.isValue()) {
            if (terminator.returnsValue())
                throw new IllegalArgumentException(
                    "IR function returning '%s' must use a bare return."
                        .formatted(returnType.displayName())
                );

            return;
        }

        var value = terminator.value().orElseThrow(
            () -> new IllegalArgumentException(
                "IR function returning '%s' must return a value."
                    .formatted(returnType.displayName())
            )
        );

        if (!returnType.equals(value.type()))
            throw new IllegalArgumentException(
                "IR return type '%s' does not match function return type '%s'."
                    .formatted(value.type().displayName(), returnType.displayName())
            );
    }
}
