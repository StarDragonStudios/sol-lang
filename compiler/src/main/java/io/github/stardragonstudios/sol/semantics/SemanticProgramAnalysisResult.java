package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SemanticProgramAnalysisResult {
    private final LinkedHashMap<ModuleName, ModuleSymbol> modules;
    private final LinkedHashMap<ModuleName, SemanticAnalysisResult> analyses;
    private final Optional<ProgramEntryPoint> entryPoint;
    private final List<Diagnostic> programDiagnostics;

    SemanticProgramAnalysisResult(
        Map<ModuleName, ModuleSymbol> modules,
        Map<ModuleName, SemanticAnalysisResult> analyses
    ) {
        this(
            modules,
            analyses,
            Optional.empty(),
            List.of()
        );
    }

    SemanticProgramAnalysisResult(
        Map<ModuleName, ModuleSymbol> modules,
        Map<ModuleName, SemanticAnalysisResult> analyses,
        Optional<ProgramEntryPoint> entryPoint,
        List<Diagnostic> programDiagnostics
    ) {
        Objects.requireNonNull(
            modules,
            "Program modules must not be null."
        );

        Objects.requireNonNull(
            analyses,
            "Program module analyses must not be null."
        );

        this.entryPoint = Objects.requireNonNull(
            entryPoint,
            "Program entry point must not be null."
        );

        this.modules = copyMap(
            modules,
            "Program modules"
        );

        this.analyses = copyMap(
            analyses,
            "Program module analyses"
        );

        this.programDiagnostics = copyAndSortDiagnostics(
            programDiagnostics
        );

        if (
            !this.modules.keySet()
                .equals(this.analyses.keySet())
        ) {
            throw new IllegalArgumentException(
                "Every program module must have exactly one semantic analysis."
            );
        }

        this.entryPoint.ifPresent(
            resolvedEntryPoint -> {
                var canonicalModule = this.modules.get(
                    resolvedEntryPoint.module()
                        .name()
                );

                if (
                    canonicalModule
                        != resolvedEntryPoint.module()
                ) {
                    throw new IllegalArgumentException(
                        "Program entry point must reference a canonical program module."
                    );
                }

                var canonicalFunction =
                    canonicalModule.exportedFunction(
                        resolvedEntryPoint.function()
                            .name()
                    );

                if (
                    canonicalFunction.isEmpty()
                        || canonicalFunction.orElseThrow()
                            != resolvedEntryPoint.function()
                ) {
                    throw new IllegalArgumentException(
                        "Program entry point must reference a canonical module function."
                    );
                }
            }
        );
    }

    public List<ModuleName> moduleNames() {
        return List.copyOf(
            modules.keySet()
        );
    }

    public Optional<ModuleSymbol> moduleOf(
        ModuleName name
    ) {
        Objects.requireNonNull(
            name,
            "Module query name must not be null."
        );

        return Optional.ofNullable(
            modules.get(name)
        );
    }

    public Optional<SemanticAnalysisResult> analysisOf(
        ModuleName name
    ) {
        Objects.requireNonNull(
            name,
            "Module analysis query name must not be null."
        );

        return Optional.ofNullable(
            analyses.get(name)
        );
    }

    public Optional<ProgramEntryPoint> entryPoint() {
        return entryPoint;
    }

    public List<Diagnostic> programDiagnostics() {
        return programDiagnostics;
    }

    private static List<Diagnostic> copyAndSortDiagnostics(
        List<Diagnostic> diagnostics
    ) {
        Objects.requireNonNull(
            diagnostics,
            "Program diagnostics must not be null."
        );

        var copy = new ArrayList<Diagnostic>(
            diagnostics.size()
        );

        for (var diagnostic : diagnostics) {
            copy.add(
                Objects.requireNonNull(
                    diagnostic,
                    "Program diagnostics must not contain null values."
                )
            );
        }

        copy.sort(
            Comparator
                .comparingInt(
                    (Diagnostic diagnostic) ->
                        diagnostic.span()
                            .start()
                            .offset()
                )
                .thenComparingInt(
                    diagnostic ->
                        diagnostic.span()
                            .end()
                            .offset()
                )
                .thenComparing(
                    Diagnostic::code
                )
        );

        return List.copyOf(copy);
    }

    private static <K, V>
    LinkedHashMap<K, V> copyMap(
        Map<K, V> source,
        String description
    ) {
        var copy =
            new LinkedHashMap<K, V>();

        source.forEach(
            (key, value) -> {
                Objects.requireNonNull(
                    key,
                    description
                        + " must not contain null keys."
                );

                Objects.requireNonNull(
                    value,
                    description
                        + " must not contain null values."
                );

                copy.put(
                    key,
                    value
                );
            }
        );

        return copy;
    }
}
