package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.ir.IrEntryPoint;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.ModuleSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticAnalysisResult;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.syntax.TypeReference;

import java.util.*;

public final class IrProgramLowerer {
    private IrProgramLowerer() {}

    public static IrProgram lower(SemanticProgramAnalysisResult program) {
        Objects.requireNonNull(program, "Lowered semantic program must not be null.");

        validateNoSemanticErrors(program);

        var programContext = new IrProgramLoweringContext();

        /*
         * Identifiers and typed references are assigned before any function
         * body is lowered. Calls may therefore target functions declared
         * later or functions belonging to another module.
         */
        assignFunctionIdentifiers(program, programContext);
        assignFunctionReferences(program, programContext);

        var loweredModules = new ArrayList<IrModule>();
        var modulesBySymbol = new IdentityHashMap<ModuleSymbol, IrModule>();
        var functionsBySymbol = new IdentityHashMap<FunctionSymbol, IrFunction>();

        for (var moduleName : program.moduleNames()) {
            var module = requireModule(program, moduleName);
            var analysis = requireAnalysis(program, moduleName);
            var lowered = IrModuleLowerer.lower(module, analysis, programContext, functionsBySymbol);

            if (modulesBySymbol.put(module, lowered) != null )
                throw new IrLoweringException("Module '%s' has already been lowered.".formatted(moduleName.qualifiedName()));

            loweredModules.add(lowered);
        }

        var semanticEntryPoint = program.entryPoint();

        if (semanticEntryPoint.isEmpty()) return createLibraryProgram(loweredModules);

        var entryPoint = semanticEntryPoint.orElseThrow();
        var entryModule = modulesBySymbol.get(entryPoint.module());

        if (entryModule == null)
            throw new IrLoweringException(
                "Semantic entry point module '%s' was not lowered.".formatted(entryPoint.module().name().qualifiedName())
            );

        var entryFunction = functionsBySymbol.get(entryPoint.function());

        if (entryFunction == null)
            throw new IrLoweringException(
                "Semantic entry point function '%s' was not lowered.".formatted(entryPoint.function().name())
            );

        try {
            return IrProgram.executable(loweredModules, new IrEntryPoint(entryModule, entryFunction));
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException("Semantic entry point produced invalid IR: %s".formatted(exception.getMessage()));
        }
    }

    private static void assignFunctionIdentifiers(SemanticProgramAnalysisResult program, IrProgramLoweringContext context) {
        for (var moduleName : program.moduleNames()) {
            var module = requireModule(program, moduleName);

            for (var function : module.exportedFunctions()) context.assignFunctionId(function);
        }
    }

    private static void assignFunctionReferences(SemanticProgramAnalysisResult program, IrProgramLoweringContext context) {
        for (var moduleName : program.moduleNames()) {
            var module = requireModule(program, moduleName);
            var model = requireAnalysis(program, moduleName).model();

            for (var function : module.exportedFunctions()) {
                var parameterTypes = new ArrayList<IrType>();

                for (var parameter : function.declaration().parameters()) {
                    var semanticType = requireType(model, parameter.type(), "parameter '%s' of function '%s'".formatted(parameter.name(), function.name()));

                    parameterTypes.add(IrTypeLowerer.lower(semanticType));
                }

                var returnType = IrTypeLowerer.lower(requireType(model, function.declaration().returnType(), "return type of function '%s'".formatted(function.name())));

                context.assignFunctionReference(function, parameterTypes, returnType);
            }
        }
    }

    private static TypeSymbol requireType(SemanticModel model, TypeReference reference, String description) {
        return model.typeOf(reference).orElseThrow(() -> new IrLoweringException("The %s has no resolved semantic type.".formatted(description)));
    }

    private static IrProgram createLibraryProgram(ArrayList<IrModule> modules) {
        try {
            return IrProgram.library(modules);
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException("Semantic library program produced invalid IR: %s".formatted(exception.getMessage()));
        }
    }

    private static ModuleSymbol requireModule(SemanticProgramAnalysisResult program, ModuleName name) {
        return program.moduleOf(name).orElseThrow(() -> new IrLoweringException("Semantic program has no canonical module '%s'.".formatted(name.qualifiedName())));
    }

    private static SemanticAnalysisResult requireAnalysis(SemanticProgramAnalysisResult program, ModuleName name) {
        return program.analysisOf(name).orElseThrow(() -> new IrLoweringException("Semantic program has no analysis for module '%s'.".formatted(name.qualifiedName())));
    }

    private static void validateNoSemanticErrors(SemanticProgramAnalysisResult program) {
        firstError(program.programDiagnostics()).ifPresent(diagnostic -> {throw semanticError(diagnostic);});

        for (var moduleName : program.moduleNames()) {
            var analysis = requireAnalysis(program, moduleName);

            firstError(analysis.diagnostics()).ifPresent(diagnostic -> {throw semanticError(diagnostic);});
        }
    }

    private static Optional<Diagnostic> firstError(List<Diagnostic> diagnostics) {
        return diagnostics.stream().filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR).findFirst();
    }

    private static IrLoweringException semanticError(Diagnostic diagnostic) {
        return new IrLoweringException("Cannot lower a semantic program containing error '%s': %s".formatted(diagnostic.code(), diagnostic.message()));
    }
}
