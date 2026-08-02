package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrFunctionReference;
import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

final class IrProgramLoweringContext {
    private final IdentityHashMap<FunctionSymbol, IrFunctionId> functionIds = new IdentityHashMap<>();
    private final IdentityHashMap<FunctionSymbol, IrFunctionReference> functionReferences = new IdentityHashMap<>();

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

    IrFunctionReference assignFunctionReference(FunctionSymbol function, List<IrType> parameterTypes, IrType returnType) {
        Objects.requireNonNull(function, "Lowered function symbol must not be null.");
        Objects.requireNonNull(parameterTypes, "Lowered function parameter types must not be null.");
        Objects.requireNonNull(returnType, "Lowered function return type must not be null.");

        if (functionReferences.containsKey(function))
            throw new IrLoweringException("Function '%s' already has a canonical IR reference.".formatted(function.name()));

        final IrFunctionReference reference;

        try {
            reference = new IrFunctionReference(functionId(function), function.name(), parameterTypes, returnType);
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException("Semantic function '%s' produced an invalid IR reference: %s".formatted(function.name(), exception.getMessage()));
        }

        functionReferences.put(function, reference);

        return reference;
    }

    IrFunctionReference functionReference(FunctionSymbol function) {
        Objects.requireNonNull(function, "Queried function symbol must not be null.");

        var reference = functionReferences.get(function);

        if (reference == null)
            throw new IrLoweringException("Function '%s' has no canonical IR reference.".formatted(function.name()));

        return reference;
    }
}
