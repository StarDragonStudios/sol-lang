package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;

import java.util.IdentityHashMap;
import java.util.Objects;

final class IrProgramLoweringContext {
    private final IdentityHashMap<FunctionSymbol, IrFunctionId> functionIds = new IdentityHashMap<>();

    private int nextFunctionIndex;

    IrFunctionId assignFunctionId(FunctionSymbol function) {
        Objects.requireNonNull(function, "Lowered function symbol must not be null.");

        if (functionIds.containsKey(function))
            throw new IrLoweringException("Function '%s' already has an IR identifier.".formatted(function.name()));

        var identifier = new IrFunctionId(nextFunctionIndex);

        nextFunctionIndex++;
        functionIds.put(function, identifier);

        return identifier;
    }

    IrFunctionId functionId(FunctionSymbol function) {
        Objects.requireNonNull(function, "Queried function symbol must not be null.");

        var identifier = functionIds.get(function);

        if (identifier == null)
            throw new IrLoweringException("Function '%s' has no assigned IR identifier.".formatted(function.name()));

        return identifier;
    }
}
