package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrFunctionReference;
import io.github.stardragonstudios.sol.ir.IrStructType;
import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.StructSymbol;
import io.github.stardragonstudios.sol.semantics.types.StructType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

final class IrProgramLoweringContext {
    private final LinkedHashMap<IrFunctionInstantiation, IrFunctionId> functionIds = new LinkedHashMap<>();
    private final LinkedHashMap<IrFunctionInstantiation, IrFunctionReference> functionReferences = new LinkedHashMap<>();
    private final LinkedHashMap<StructType, IrStructType> structTypes = new LinkedHashMap<>();

    private int nextFunctionIndex;

    IrFunctionId assignFunctionId(FunctionSymbol function) {
        return assignFunctionInstantiationId(IrFunctionInstantiation.canonical(function));
    }

    IrFunctionId assignFunctionInstantiationId(IrFunctionInstantiation instantiation) {
        Objects.requireNonNull(instantiation, "Lowered function instantiation must not be null.");

        if (functionIds.containsKey(instantiation))
            throw new IrLoweringException("Function instantiation '%s' already has an IR identifier.".formatted(instantiation.irName()));

        var identifier = new IrFunctionId(nextFunctionIndex);

        nextFunctionIndex++;
        functionIds.put(instantiation, identifier);

        return identifier;
    }

    IrFunctionId functionId(FunctionSymbol function) {
        return functionInstantiationId(IrFunctionInstantiation.canonical(function));
    }

    IrFunctionId functionInstantiationId(IrFunctionInstantiation instantiation) {
        Objects.requireNonNull(instantiation, "Queried function instantiation must not be null.");

        var identifier = functionIds.get(instantiation);

        if (identifier == null)
            throw new IrLoweringException("Function instantiation '%s' has no assigned IR identifier.".formatted(instantiation.irName()));

        return identifier;
    }

    IrFunctionReference assignFunctionReference(FunctionSymbol function, List<IrType> parameterTypes, IrType returnType) {
        return assignFunctionInstantiationReference(IrFunctionInstantiation.canonical(function), parameterTypes, returnType);
    }

    IrFunctionReference assignFunctionInstantiationReference(
        IrFunctionInstantiation instantiation,
        List<IrType> parameterTypes,
        IrType returnType
    ) {
        Objects.requireNonNull(instantiation, "Lowered function instantiation must not be null.");
        Objects.requireNonNull(parameterTypes, "Lowered function parameter types must not be null.");
        Objects.requireNonNull(returnType, "Lowered function return type must not be null.");

        if (functionReferences.containsKey(instantiation))
            throw new IrLoweringException("Function instantiation '%s' already has a canonical IR reference.".formatted(instantiation.irName()));

        final IrFunctionReference reference;

        try {
            reference = new IrFunctionReference(
                functionInstantiationId(instantiation),
                instantiation.irName(),
                parameterTypes,
                returnType
            );
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException(
                "Semantic function instantiation '%s' produced an invalid IR reference: %s"
                    .formatted(instantiation.irName(), exception.getMessage())
            );
        }

        functionReferences.put(instantiation, reference);

        return reference;
    }

    IrFunctionReference functionReference(FunctionSymbol function) {
        return functionInstantiationReference(IrFunctionInstantiation.canonical(function));
    }

    IrFunctionReference functionInstantiationReference(IrFunctionInstantiation instantiation) {
        Objects.requireNonNull(instantiation, "Queried function instantiation must not be null.");

        var reference = functionReferences.get(instantiation);

        if (reference == null)
            throw new IrLoweringException("Function instantiation '%s' has no canonical IR reference.".formatted(instantiation.irName()));

        return reference;
    }

    void assignStructType(StructSymbol struct, IrStructType type) {
        assignStructType(Objects.requireNonNull(struct, "Lowered struct symbol must not be null.").type(), type);
    }

    void assignStructType(StructType struct, IrStructType type) {
        Objects.requireNonNull(struct, "Lowered struct type must not be null.");
        Objects.requireNonNull(type, "Assigned IR struct type must not be null.");

        if (structTypes.putIfAbsent(struct, type) != null)
            throw new IrLoweringException("Struct type '%s' already has a canonical IR type.".formatted(struct.name()));
    }

    IrStructType structType(StructSymbol struct) {
        return structType(Objects.requireNonNull(struct, "Queried struct symbol must not be null.").type());
    }

    IrStructType structType(StructType struct) {
        Objects.requireNonNull(struct, "Queried struct type must not be null.");

        var type = structTypes.get(struct);

        if (type == null) throw new IrLoweringException("Struct type '%s' has no canonical IR type.".formatted(struct.name()));

        return type;
    }
}
