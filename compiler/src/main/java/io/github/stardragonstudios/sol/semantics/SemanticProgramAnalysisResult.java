package io.github.stardragonstudios.sol.semantics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SemanticProgramAnalysisResult {
    private final LinkedHashMap<ModuleName, ModuleSymbol> modules;
    private final LinkedHashMap<ModuleName, SemanticAnalysisResult> analyses;

    SemanticProgramAnalysisResult(Map<ModuleName, ModuleSymbol> modules, Map<ModuleName, SemanticAnalysisResult> analyses) {
        Objects.requireNonNull(modules, "Program modules must not be null.");
        Objects.requireNonNull(analyses, "Program module analyses must not be null.");

        this.modules = copyMap(modules, "Program modules");
        this.analyses = copyMap(analyses, "Program module analyses");

        if (!this.modules.keySet().equals(this.analyses.keySet())) {
            throw new IllegalArgumentException("Every program module must have exactly one semantic analysis.");
        }
    }

    public List<ModuleName> moduleNames() {
        return List.copyOf(modules.keySet());
    }

    public Optional<ModuleSymbol> moduleOf(ModuleName name) {
        Objects.requireNonNull(name, "Module query name must not be null.");

        return Optional.ofNullable(modules.get(name));
    }

    public Optional<SemanticAnalysisResult> analysisOf(ModuleName name) {
        Objects.requireNonNull(name, "Module analysis query name must not be null.");

        return Optional.ofNullable(analyses.get(name));
    }

    private static <K, V> LinkedHashMap<K, V> copyMap(Map<K, V> source, String description) {
        var copy = new LinkedHashMap<K, V>();

        source.forEach((key, value) -> {
            Objects.requireNonNull(key, description + " must not contain null keys.");
            Objects.requireNonNull(value, description + " must not contain null values.");

            copy.put(key, value);
        });

        return copy;
    }
}
