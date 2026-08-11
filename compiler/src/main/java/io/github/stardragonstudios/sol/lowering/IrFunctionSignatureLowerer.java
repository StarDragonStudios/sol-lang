package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ParameterSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.syntax.Parameter;
import io.github.stardragonstudios.sol.syntax.TypeReference;

import java.util.ArrayList;
import java.util.Objects;

final class IrFunctionSignatureLowerer {
    private IrFunctionSignatureLowerer() {}

    static IrFunctionSignature lower(FunctionSymbol function, SemanticModel model, IrProgramLoweringContext programContext) {
        Objects.requireNonNull(function, "Lowered function symbol must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");
        Objects.requireNonNull(programContext, "Program lowering context must not be null.");

        validateCanonicalFunction(function, model);

        var functionContext = new IrFunctionLoweringContext(function, programContext);
        var loweredParameters = new ArrayList<IrParameter>();

        for (var parameterDeclaration : function.declaration().parameters()) {
            var parameterSymbol = requireParameterSymbol(parameterDeclaration, model, function);

            var parameterType = requireType(
                parameterSymbol.type(),
                model,
                "parameter '%s' of function '%s'".formatted(parameterSymbol.name(), function.name())
            );

            loweredParameters.add(functionContext.declareParameter(parameterSymbol, functionContext.lowerType(parameterType)));
        }

        var returnType = requireType(
            function.declaration().returnType(),
            model,
            "return type of function '%s'".formatted(function.name())
        );

        return new IrFunctionSignature(
            function,
            programContext.functionId(function),
            loweredParameters,
            functionContext.lowerType(returnType),
            functionContext
        );
    }

    private static void validateCanonicalFunction(FunctionSymbol function, SemanticModel model) {
        var canonical = model.symbolOf(function.declaration()).orElseThrow(
            () -> new IrLoweringException("Function '%s' has no semantic symbol.".formatted(function.name()))
        );

        if (canonical != function)
            throw new IrLoweringException(
                "Function '%s' is not the canonical semantic symbol for its declaration.".formatted(function.name())
            );
    }

    private static ParameterSymbol requireParameterSymbol(Parameter declaration, SemanticModel model, FunctionSymbol function) {
        return model.symbolOf(declaration).orElseThrow(
            () -> new IrLoweringException(
                "Parameter '%s' of function '%s' has no semantic symbol.".formatted(declaration.name(), function.name())
            )
        );
    }

    private static TypeSymbol requireType(TypeReference reference, SemanticModel model, String description) {
        return model.typeOf(reference).orElseThrow(
            () -> new IrLoweringException(
                "The %s has no resolved semantic type.".formatted(description)
            )
        );
    }
}
