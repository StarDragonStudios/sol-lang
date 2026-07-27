package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.ir.IrEntryPoint;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.ModuleSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticAnalysisResult;
import io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult;

import java.util.*;

public final class IrProgramLowerer {
    private IrProgramLowerer() {}

    public static IrProgram lower(SemanticProgramAnalysisResult program) {
        Objects.requireNonNull(program, "Lowered semantic program must not be null.");

        validateNoSemanticErrors(program);

        var programContext = new IrProgramLoweringContext();

        assignFunctionIdentifiers(program, programContext);

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

    private static IrProgram createLibraryProgram(ArrayList<IrModule> modules) {
        try {
            return IrProgram.library(modules);
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException("Semantic library program produced invalid IR: %s".formatted(exception.getMessage()));
        }
    }

    private static ModuleSymbol requireModule(SemanticProgramAnalysisResult program, ModuleName name) {
        return program.moduleOf(name).orElseThrow(
            () -> new IrLoweringException(
                "Semantic program has no canonical module '%s'.".formatted(name.qualifiedName())
            )
        );
    }

    private static SemanticAnalysisResult requireAnalysis(SemanticProgramAnalysisResult program, ModuleName name) {
        return program.analysisOf(name).orElseThrow(
            () -> new IrLoweringException(
                "Semantic program has no analysis for module '%s'.".formatted(name.qualifiedName())
            )
        );
    }

    private static void validateNoSemanticErrors(SemanticProgramAnalysisResult program) {
        firstError(program.programDiagnostics()).ifPresent(diagnostic -> {
            throw semanticError(diagnostic);
        });

        for (var moduleName : program.moduleNames()) {
            var analysis = requireAnalysis(program, moduleName);

            firstError(analysis.diagnostics()).ifPresent(diagnostic -> {
                throw semanticError(diagnostic);
            });
        }
    }

    private static Optional<Diagnostic> firstError(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
            .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
            .findFirst();
    }

    private static IrLoweringException semanticError(Diagnostic diagnostic) {
        return new IrLoweringException(
            "Cannot lower a semantic program containing error '%s': %s".formatted(diagnostic.code(), diagnostic.message())
        );
    }
}
