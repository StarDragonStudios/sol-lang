package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.lexer.LexicalException;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.parser.ParsingException;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SourceModule;
import io.github.stardragonstudios.sol.syntax.CompilationUnit;
import io.github.stardragonstudios.sol.syntax.InjectionDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class SourceProgramDiscovery {
    private SourceProgramDiscovery() {}

    public static DiscoveredSourceProgram discover(Path entrySourceFile) {
        var entryFile = Objects.requireNonNull(entrySourceFile, "Entry source file must not be null.").toAbsolutePath().normalize();

        if (!Files.isRegularFile(entryFile)) throw new CompilerPipelineException("Sol source file '%s' does not exist or is not a regular file.".formatted(entryFile));

        /*
         * Until a package/project manifest defines another module root,
         * the directory containing the entry source is the program's
         * filesystem module root.
         */
        var moduleRoot = entryFile.getParent();

        if (moduleRoot == null) throw new CompilerPipelineException("Cannot determine module root for source file '%s'.".formatted(entryFile));

        var entryName = new ModuleName(List.of(CompilerOutputPath.moduleName(entryFile)));
        var modules = new LinkedHashMap<ModuleName, SourceModule>();
        var sourceFiles = new LinkedHashMap<ModuleName, Path>();

        discoverModule(entryName, entryFile, moduleRoot, true, modules, sourceFiles);

        return new DiscoveredSourceProgram(new ArrayList<>(modules.values()), sourceFiles);
    }

    private static void discoverModule(
        ModuleName moduleName,
        Path sourceFile,
        Path moduleRoot,
        boolean required,
        LinkedHashMap<ModuleName, SourceModule> modules,
        LinkedHashMap<ModuleName, Path> sourceFiles
    ) {
        if (modules.containsKey(moduleName)) return;

        if (!Files.isRegularFile(sourceFile)) {
            if (required) throw new CompilerPipelineException("Sol source file '%s' does not exist or is not a regular file.".formatted(sourceFile));

            /*
             * Missing injected modules are intentionally left absent.
             * Semantic analysis will report SOL-S019 at the injection site.
             */
            return;
        }

        var unit = parseSource(sourceFile);
        var module = new SourceModule(moduleName, unit);

        /*
         * Register before recursing so cyclic injections terminate.
         */
        modules.put(moduleName, module);
        sourceFiles.put(moduleName, sourceFile);

        for (var declaration : unit.declarations()) {
            if (!(declaration instanceof InjectionDeclaration injection)) continue;

            var injectedName = new ModuleName(injection.modulePath().segments());

            if (modules.containsKey(injectedName)) continue;

            discoverModule(injectedName, sourcePathFor(moduleRoot, injectedName), moduleRoot, false, modules, sourceFiles);
        }
    }

    private static Path sourcePathFor(Path moduleRoot, ModuleName moduleName) {
        var path = moduleRoot;
        var segments = moduleName.segments();

        for (var index = 0; index < segments.size() - 1; index++) path = path.resolve(segments.get(index));

        return path.resolve(segments.getLast() + ".sol").toAbsolutePath().normalize();
    }

    private static CompilationUnit parseSource(Path sourceFile) {
        final String source;

        try {
            source = Files.readString(sourceFile);
        } catch (IOException exception) {
            throw new CompilerPipelineException("Failed to read Sol source file '%s'.".formatted(sourceFile), exception);
        }

        return getCompilationUnit(sourceFile, source);
    }

    static CompilationUnit getCompilationUnit(Path sourceFile, String source) {
        try {
            return Parser.parse(Lexer.scan(source));
        } catch (LexicalException exception) {
            throw new FrontendCompilationException(List.of(new SourceDiagnostic(sourceFile, exception.diagnostic())));
        } catch (ParsingException exception) {
            throw new FrontendCompilationException(List.of(new SourceDiagnostic(sourceFile, exception.diagnostic())));
        }
    }
}
