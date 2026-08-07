package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DiscoveredSourceProgram {
    private final List<SourceModule> modules;
    private final Map<ModuleName, Path> sourceFiles;

    public DiscoveredSourceProgram(List<SourceModule> modules, Map<ModuleName, Path> sourceFiles) {
        Objects.requireNonNull(modules, "Discovered source modules must not be null.");
        Objects.requireNonNull(sourceFiles, "Discovered source-file map must not be null.");

        this.modules = List.copyOf(modules);
        var copiedSourceFiles = new LinkedHashMap<ModuleName, Path>();

        sourceFiles.forEach(
            (moduleName, sourceFile) -> copiedSourceFiles.put(
                Objects.requireNonNull(moduleName, "Discovered source-file map must not contain null module names."),
                Objects.requireNonNull(sourceFile, "Discovered source-file map must not contain null paths.").toAbsolutePath().normalize()
            )
        );

        this.sourceFiles = Map.copyOf(copiedSourceFiles);

        if (this.modules.size() != this.sourceFiles.size()) throw new IllegalArgumentException("Every discovered source module must have exactly one source file.");

        for (var module : this.modules)
            if (!this.sourceFiles.containsKey(module.name()))
                throw new IllegalArgumentException("Discovered module '%s' has no source file.".formatted(module.name()));
    }

    public List<SourceModule> modules() {
        return modules;
    }

    public Path sourceFileOf(ModuleName moduleName) {
        Objects.requireNonNull(moduleName, "Source-file module query must not be null.");

        var sourceFile = sourceFiles.get(moduleName);

        if (sourceFile == null) throw new IllegalArgumentException("No source file is registered for module '%s'.".formatted(moduleName));

        return sourceFile;
    }
}
