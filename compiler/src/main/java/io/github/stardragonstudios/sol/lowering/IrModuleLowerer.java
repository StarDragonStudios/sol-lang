package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrStructType;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ModuleSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticAnalysisResult;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class IrModuleLowerer {
    private IrModuleLowerer() {}

    static IrModule lower(
        ModuleSymbol module,
        SemanticAnalysisResult analysis,
        IrProgramLoweringContext programContext,
        IdentityHashMap<FunctionSymbol, IrFunction> loweredFunctions
    ) {
        Objects.requireNonNull(module, "Lowered semantic module must not be null.");
        Objects.requireNonNull(analysis, "Semantic module analysis must not be null.");
        Objects.requireNonNull(programContext, "Program lowering context must not be null.");
        Objects.requireNonNull(loweredFunctions, "Lowered function registry must not be null.");

        validateCanonicalAnalysis(module, analysis);

        var structs = module.exportedStructs().stream().map(programContext::structType).toList();
        var instantiations = module.exportedFunctions().stream().map(IrFunctionInstantiation::canonical).toList();
        var loweredByInstantiation = new LinkedHashMap<IrFunctionInstantiation, IrFunction>();
        var lowered = lower(module, analysis, programContext, instantiations, structs, loweredByInstantiation);

        loweredByInstantiation.forEach((instantiation, function) -> {
            if (loweredFunctions.put(instantiation.function(), function) != null)
                throw new IrLoweringException("Function '%s' has already been lowered.".formatted(instantiation.function().name()));
        });

        return lowered;
    }

    static IrModule lower(
        ModuleSymbol module,
        SemanticAnalysisResult analysis,
        IrProgramLoweringContext programContext,
        List<IrFunctionInstantiation> instantiations,
        List<IrStructType> structs,
        Map<IrFunctionInstantiation, IrFunction> loweredFunctions
    ) {
        Objects.requireNonNull(module, "Lowered semantic module must not be null.");
        Objects.requireNonNull(analysis, "Semantic module analysis must not be null.");
        Objects.requireNonNull(programContext, "Program lowering context must not be null.");
        Objects.requireNonNull(instantiations, "Module function instantiations must not be null.");
        Objects.requireNonNull(structs, "Module IR struct types must not be null.");
        Objects.requireNonNull(loweredFunctions, "Lowered function registry must not be null.");

        validateCanonicalAnalysis(module, analysis);

        var functions = new ArrayList<IrFunction>();

        for (var instantiation : instantiations) {
            var signature = IrFunctionSignatureLowerer.lowerInstantiation(instantiation, analysis.model(), programContext);
            var lowered = IrFunctionLowerer.lower(signature, analysis.model());

            if (loweredFunctions.put(instantiation, lowered) != null)
                throw new IrLoweringException("Function instantiation '%s' has already been lowered.".formatted(instantiation.irName()));

            functions.add(lowered);
        }

        try {
            return new IrModule(new IrModuleName(module.name().segments()), structs, functions);
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException(
                "Semantic module '%s' produced invalid IR: %s"
                    .formatted(module.name().qualifiedName(), exception.getMessage())
            );
        }
    }

    private static void validateCanonicalAnalysis(ModuleSymbol module, SemanticAnalysisResult analysis) {
        if (analysis.model().moduleScope() != module.scope())
            throw new IrLoweringException("Semantic analysis does not belong to module '%s'.".formatted(module.name().qualifiedName()));
    }
}
