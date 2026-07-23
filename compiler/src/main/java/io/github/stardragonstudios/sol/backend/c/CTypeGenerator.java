package io.github.stardragonstudios.sol.backend.c;

import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;

import java.util.Objects;

final class CTypeGenerator {
    private CTypeGenerator() {}

    static String generate(TypeSymbol type, ModuleName moduleName, FunctionSymbol function) {
        Objects.requireNonNull(type, "Generated semantic type must not be null.");
        Objects.requireNonNull(moduleName, "Generated type module must not be null.");
        Objects.requireNonNull(function, "Generated type function must not be null.");

        if (type == BuiltInTypes.INT) return "sol_int";
        if (type == BuiltInTypes.FLOAT) return "sol_float";
        if (type == BuiltInTypes.BOOLEAN) return "sol_boolean";
        if (type == BuiltInTypes.CHAR) return "sol_char";
        if (type == BuiltInTypes.VOID) return "void";

        throw unsupportedType(type, moduleName, function);
    }

    static void requireSupportedValueType(TypeSymbol type, ModuleName moduleName, FunctionSymbol function) {
        Objects.requireNonNull(type, "Generated expression type must not be null.");

        if (
            type == BuiltInTypes.INT
                || type == BuiltInTypes.FLOAT
                || type == BuiltInTypes.BOOLEAN
                || type == BuiltInTypes.CHAR
        ) return;

        throw unsupportedType(type, moduleName, function);
    }

    private static CCodeGenerationException unsupportedType(TypeSymbol type, ModuleName moduleName, FunctionSymbol function) {
        return new CCodeGenerationException(
            CCodeGenerationException.Reason.UNSUPPORTED_TYPE,
            "Cannot generate C function '%s' in module '%s': unsupported semantic type '%s'."
                .formatted(function.name(), moduleName.qualifiedName(), type.name())
        );
    }
}
