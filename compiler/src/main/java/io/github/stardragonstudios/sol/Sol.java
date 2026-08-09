package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.backend.llvm.LlvmBackendException;
import io.github.stardragonstudios.sol.cli.CommandLineParsingException;
import io.github.stardragonstudios.sol.cli.CompilerExitCode;
import io.github.stardragonstudios.sol.cli.CompilerPipelineException;
import io.github.stardragonstudios.sol.cli.DiagnosticFormatter;
import io.github.stardragonstudios.sol.cli.FrontendCompilationException;
import io.github.stardragonstudios.sol.cli.ProgramExecutionException;
import io.github.stardragonstudios.sol.cli.ProgramRunResult;
import io.github.stardragonstudios.sol.cli.RunCommandLine;
import io.github.stardragonstudios.sol.cli.RunCommandLineParser;
import io.github.stardragonstudios.sol.cli.SolProgramRunner;
import io.github.stardragonstudios.sol.lowering.IrLoweringException;
import io.github.stardragonstudios.sol.toolchain.NativeToolchainException;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Objects;

public final class Sol {
    private Sol() {}

    public static void main(String[] args) {
        if (SolVersion.isVersionRequest(args)) {
            SolVersion.print(System.out);
            return;
        }

        System.exit(run(args, System.err, new SolProgramRunner()::run));
    }

    static int run(String[] arguments, PrintStream errorOutput, RunAction runAction) {
        Objects.requireNonNull(arguments, "Sol command-line arguments must not be null.");
        Objects.requireNonNull(errorOutput, "Sol error output must not be null.");
        Objects.requireNonNull(runAction, "Sol run action must not be null.");

        final RunCommandLine commandLine;

        try {
            commandLine = parseRunCommand(arguments);
        } catch (CommandLineParsingException exception) {
            printFailure(errorOutput, "command-line error", exception);

            return CompilerExitCode.COMMAND_LINE_ERROR.value();
        }

        try {
            var result = runAction.run(commandLine);

            for (var diagnostic : result.diagnostics()) errorOutput.println(DiagnosticFormatter.format(diagnostic));

            /*
             * A successfully compiled and launched Sol program owns the
             * command's exit status. Do not translate it into compiler
             * exit-code categories.
             */
            return result.exitCode();
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
        } catch (ProgramExecutionException exception) {
            printFailure(errorOutput, "execution error", exception);

            return CompilerExitCode.PROGRAM_EXECUTION_ERROR.value();
        }
    }

    private static RunCommandLine parseRunCommand(String[] arguments) {
        if (arguments.length == 0) throw new CommandLineParsingException("Sol requires a command.");

        var command = arguments[0];

        if (!command.equals("run")) throw new CommandLineParsingException("Unknown Sol command '%s'.".formatted(command));

        return RunCommandLineParser.parse(Arrays.copyOfRange(arguments, 1, arguments.length));
    }

    private static void printFailure(PrintStream output, String category, RuntimeException exception) {
        output.println("%s: %s".formatted(category, exception.getMessage()));
    }
}

@FunctionalInterface
interface RunAction {
    ProgramRunResult run(RunCommandLine commandLine);
}
