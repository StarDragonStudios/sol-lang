package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.backend.llvm.LlvmBackendException;
import io.github.stardragonstudios.sol.cli.CommandLineParser;
import io.github.stardragonstudios.sol.cli.CommandLineParsingException;
import io.github.stardragonstudios.sol.cli.CompilerCommandLine;
import io.github.stardragonstudios.sol.cli.CompilerExitCode;
import io.github.stardragonstudios.sol.cli.CompilerPipeline;
import io.github.stardragonstudios.sol.cli.CompilerPipelineException;
import io.github.stardragonstudios.sol.cli.CompilerPipelineResult;
import io.github.stardragonstudios.sol.cli.DiagnosticFormatter;
import io.github.stardragonstudios.sol.cli.FrontendCompilationException;
import io.github.stardragonstudios.sol.lowering.IrLoweringException;
import io.github.stardragonstudios.sol.toolchain.NativeToolchainException;

import java.io.PrintStream;
import java.util.Objects;

public final class SolCompiler {
    private SolCompiler() {}

    public static void main(String[] args) {
        System.exit(run(args, System.err, new CompilerPipeline()::compile));
    }

    static int run(String[] args, PrintStream errorOutput, CompilationAction compilation) {
        Objects.requireNonNull(errorOutput, "Compiler error output must not be null.");
        Objects.requireNonNull(compilation, "Compiler compilation action must not be null.");

        final CompilerCommandLine commandLine;

        try {
            commandLine = CommandLineParser.parse(args);
        } catch (CommandLineParsingException exception) {
            printFailure(errorOutput, "command-line error", exception);

            return CompilerExitCode.COMMAND_LINE_ERROR.value();
        }

        try {
            var result = compilation.compile(commandLine);

            for (var diagnostic : result.diagnostics()) errorOutput.println(DiagnosticFormatter.format(diagnostic));

            return CompilerExitCode.SUCCESS.value();
        } catch (CompilerPipelineException exception) {
            printFailure(errorOutput, "input error", exception);

            return CompilerExitCode.INPUT_ERROR.value();
        } catch (FrontendCompilationException exception) {
            for (var diagnostic : exception.diagnostics()) errorOutput.println(DiagnosticFormatter.format(diagnostic));

            return CompilerExitCode.FRONTEND_ERROR.value();
        } catch (IrLoweringException exception) {
            printFailure(errorOutput, "lowering error", exception);

            return CompilerExitCode.LOWERING_ERROR.value();
        } catch (LlvmBackendException exception) {
            printFailure(errorOutput, "backend error", exception);

            return CompilerExitCode.BACKEND_ERROR.value();
        } catch (NativeToolchainException exception) {
            printFailure(errorOutput, "toolchain error", exception);

            return CompilerExitCode.TOOLCHAIN_ERROR.value();
        }
    }

    private static void printFailure(PrintStream output, String category, RuntimeException exception) {
        output.println("%s: %s".formatted(category, exception.getMessage()));
    }
}

@FunctionalInterface
interface CompilationAction {
    CompilerPipelineResult compile(CompilerCommandLine commandLine);
}
