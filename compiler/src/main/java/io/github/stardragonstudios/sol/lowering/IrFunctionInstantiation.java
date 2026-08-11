package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.TypeParameterSymbol;
import io.github.stardragonstudios.sol.semantics.types.StructType;
import io.github.stardragonstudios.sol.semantics.types.PointerType;
import io.github.stardragonstudios.sol.semantics.types.TypeParameterType;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

record IrFunctionInstantiation(FunctionSymbol function, List<TypeSymbol> arguments) {
    IrFunctionInstantiation {
        Objects.requireNonNull(function, "Instantiated function must not be null.");
        Objects.requireNonNull(arguments, "Function instantiation arguments must not be null.");

        arguments = List.copyOf(arguments);
        arguments.forEach(argument -> Objects.requireNonNull(argument, "Function instantiation arguments must not contain null values."));

        if (arguments.size() != function.typeParameters().size()) throw new IrLoweringException(
            "Function '%s' expects %d IR type arguments, but received %d."
                .formatted(function.name(), function.typeParameters().size(), arguments.size())
        );

        for (var argument : arguments) if (containsTypeParameter(argument)) throw new IrLoweringException(
            "Function '%s' cannot be monomorphized with unresolved type argument '%s'."
                .formatted(function.name(), argument.name())
        );
    }

    static IrFunctionInstantiation canonical(FunctionSymbol function) {
        return new IrFunctionInstantiation(function, List.of());
    }

    Map<TypeParameterSymbol, TypeSymbol> substitutions() {
        var substitutions = new IdentityHashMap<TypeParameterSymbol, TypeSymbol>();

        for (var index = 0; index < arguments.size(); index++)
            substitutions.put(function.typeParameters().get(index), arguments.get(index));

        return substitutions;
    }

    String irName() {
        if (arguments.isEmpty()) return function.name();

        var name = new StringJoiner("$", function.name() + "$", "");

        arguments.forEach(argument -> name.add(encode(argument)));

        return name.toString();
    }

    private static boolean containsTypeParameter(TypeSymbol type) {
        if (type instanceof TypeParameterType) return true;
        if (type instanceof PointerType pointer) return containsTypeParameter(pointer.elementType());
        if (type instanceof StructType struct) return struct.arguments().stream().anyMatch(IrFunctionInstantiation::containsTypeParameter);

        return false;
    }

    private static String encode(TypeSymbol type) {
        if (type instanceof PointerType pointer) return "p_" + encode(pointer.elementType()) + "_e";
        if (!(type instanceof StructType struct)) return escape(type.name());

        var text = new StringBuilder("s");

        struct.symbol().moduleName().ifPresent(module -> text.append(escape(module.qualifiedName())).append('_'));
        text.append(escape(struct.symbol().name()));

        for (var argument : struct.arguments()) text.append("_").append(encode(argument));

        return text.append("_e").toString();
    }

    private static String escape(String text) {
        var result = new StringBuilder();

        text.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) result.appendCodePoint(codePoint);
            else result.append('_').append(Integer.toHexString(codePoint)).append('_');
        });

        return result.toString();
    }
}
