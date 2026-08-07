package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.lowering.IrProgramLowerer;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult;
import io.github.stardragonstudios.sol.toolchain.NativeExecutableCompiler;
import io.github.stardragonstudios.sol.toolchain.TemporaryObjectPolicy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CompilerPipeline {
    private final NativeCompilationStage nativeCompilation;

    public CompilerPipeline() {
        this((program, moduleName, output, objectPolicy) -> NativeExecutableCompiler
            .host()
            .compile(program, moduleName, output, objectPolicy)
            .executable()
        );
    }

    CompilerPipeline(NativeCompilationStage nativeCompilation) {
        this.nativeCompilation = Objects.requireNonNull(nativeCompilation, "Native compilation stage must not be null.");
    }

    public CompilerPipelineResult compile(CompilerCommandLine commandLine) {
        Objects.requireNonNull(commandLine, "Compiler command line must not be null.");

        var sourceFile = commandLine.sourceFile().toAbsolutePath().normalize();
        var moduleName = CompilerOutputPath.moduleName(sourceFile);
        var discoveredProgram = SourceProgramDiscovery.discover(sourceFile);
        var semanticProgram = SemanticAnalyzer.analyzeProgram(discoveredProgram.modules());
        var diagnostics = collectDiagnostics(sourceFile, discoveredProgram, semanticProgram);

        if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.diagnostic().severity() == DiagnosticSeverity.ERROR))
            throw new FrontendCompilationException(diagnostics);

        var irProgram = IrProgramLowerer.lower(semanticProgram);
        var output = commandLine.output().map(path -> path.toAbsolutePath().normalize()).orElseGet(() -> CompilerOutputPath.defaultFor(sourceFile));
        var objectPolicy = commandLine.keepIntermediates() ? TemporaryObjectPolicy.KEEP : TemporaryObjectPolicy.DELETE;
        var executable = nativeCompilation.compile(irProgram, moduleName, output, objectPolicy);

        return new CompilerPipelineResult(sourceFile, executable, diagnostics);
    }

    private static List<SourceDiagnostic> collectDiagnostics(Path entrySourceFile, DiscoveredSourceProgram discoveredProgram, SemanticProgramAnalysisResult program) {
        var diagnostics = new ArrayList<SourceDiagnostic>();

        /*
         * Program-wide diagnostics have no owning source module.
         * The CLI anchors them to the explicitly supplied entry file.
         */
        addDiagnostics(diagnostics, entrySourceFile, program.programDiagnostics());

        for (var moduleName : program.moduleNames()) {
            var analysis = program.analysisOf(moduleName).orElseThrow(
                () -> new IllegalStateException("Semantic program has no analysis for module '%s'.".formatted(moduleName))
            );

            addDiagnostics(diagnostics, discoveredProgram.sourceFileOf(moduleName), analysis.diagnostics());
        }

        return List.copyOf(diagnostics);
    }

    private static void addDiagnostics(List<SourceDiagnostic> destination, Path sourceFile, List<Diagnostic> diagnostics) {
        for (var diagnostic : diagnostics) destination.add(new SourceDiagnostic(sourceFile, diagnostic));
    }
}

@FunctionalInterface
interface NativeCompilationStage {
    Path compile(IrProgram program, String moduleName, Path output, TemporaryObjectPolicy objectPolicy);
}
