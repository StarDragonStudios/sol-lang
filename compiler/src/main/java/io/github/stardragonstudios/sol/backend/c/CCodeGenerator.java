package io.github.stardragonstudios.sol.backend.c;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult;

import java.util.Objects;

public final class CCodeGenerator {
    private CCodeGenerator() {}

    public static CTranslationUnit generate(SemanticProgramAnalysisResult program) {
        Objects.requireNonNull(program, "Semantic program must not be null.");

        validateProgramDiagnostics(program);
        validateModuleAnalyses(program);
        validateEntryPoint(program);

        return new CProgramGenerator(program).generate();
    }

    private static void validateProgramDiagnostics(SemanticProgramAnalysisResult program) {
        if (program.programDiagnostics().stream().anyMatch(CCodeGenerator::isError)) {
            throw new CCodeGenerationException(
                CCodeGenerationException.Reason.PROGRAM_SEMANTIC_ERRORS,
                "Cannot generate C source: program-level semantic errors are present."
            );
        }
    }

    private static void validateModuleAnalyses(SemanticProgramAnalysisResult program) {
        for (var moduleName : program.moduleNames()) {
            var analysis = program.analysisOf(moduleName).orElseThrow(
                () -> new IllegalStateException(
                    "Semantic program has no analysis for module '%s'."
                        .formatted(
                            moduleName.qualifiedName()
                        )
                )
            );

            if (analysis.diagnostics().stream().anyMatch(CCodeGenerator::isError)) {
                throw new CCodeGenerationException(
                    CCodeGenerationException.Reason.MODULE_SEMANTIC_ERRORS,
                    "Cannot generate C source: module '%s' contains semantic errors."
                        .formatted(moduleName.qualifiedName())
                );
            }

            if (!analysis.model().moduleScope().isFrozen()) {
                throw new CCodeGenerationException(
                    CCodeGenerationException.Reason.UNFROZEN_SEMANTIC_SCOPE,
                    "Cannot generate C source: module '%s' has an unfrozen semantic scope."
                        .formatted(moduleName.qualifiedName())
                );
            }
        }
    }

    private static void validateEntryPoint(SemanticProgramAnalysisResult program) {
        if (program.entryPoint().isEmpty()) {
            throw new CCodeGenerationException(
                CCodeGenerationException.Reason.MISSING_ENTRY_POINT,
                "Cannot generate C source: no executable entry point is resolved."
            );
        }
    }

    private static boolean isError(Diagnostic diagnostic) {
        return diagnostic.severity() == DiagnosticSeverity.ERROR;
    }
}
