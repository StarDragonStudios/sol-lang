package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.cli.CompilerExitCode;
import io.github.stardragonstudios.sol.cli.ProgramExecutionException;
import io.github.stardragonstudios.sol.cli.ProgramRunResult;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolTest {
    @Test
    void dispatchesRunCommandAndReturnsProgramExitCode() {
        var errorBytes = new ByteArrayOutputStream();
        var receivedSource = new AtomicReference<Path>();

        var exitCode = Sol.run(
            new String[] {"run", "program.sol"},
            new PrintStream(errorBytes),
            commandLine -> {
                receivedSource.set(commandLine.sourceFile());

                return new ProgramRunResult(23, List.of());
            }
        );

        assertEquals(23, exitCode);
        assertEquals(Path.of("program.sol"), receivedSource.get());
        assertEquals("", errorBytes.toString());
    }

    @Test
    void rejectsMissingCommand() {
        var errorBytes = new ByteArrayOutputStream();
        var exitCode = Sol.run(
            new String[] {}, new PrintStream(errorBytes),
            _ -> new ProgramRunResult(0, List.of())
        );

        assertEquals(CompilerExitCode.COMMAND_LINE_ERROR.value(), exitCode);
        assertTrue(errorBytes.toString().contains("Sol requires a command"));
    }

    @Test
    void rejectsUnknownCommand() {
        var errorBytes = new ByteArrayOutputStream();

        var exitCode = Sol.run(
            new String[] {"explode", "program.sol"},
            new PrintStream(errorBytes),
        _ -> new ProgramRunResult(0, List.of())
        );

        assertEquals(CompilerExitCode.COMMAND_LINE_ERROR.value(), exitCode);
        assertTrue(errorBytes.toString().contains("Unknown Sol command 'explode'"));
    }

    @Test
    void reportsProgramExecutionFailure() {
        var errorBytes = new ByteArrayOutputStream();

        var exitCode = Sol.run(
            new String[] {"run", "program.sol"},
            new PrintStream(errorBytes),
            _ -> {
                throw new ProgramExecutionException("Could not start program.");
            }
        );

        assertEquals(CompilerExitCode.PROGRAM_EXECUTION_ERROR.value(), exitCode);
        assertTrue(errorBytes.toString().contains("execution error: Could not start program."));
    }

    @Test
    void programExitCodeIsNotInterpretedAsCompilerFailure() {
        var errorBytes = new ByteArrayOutputStream();

        /*
         * 4 is also FRONTEND_ERROR for compiler failures, but after a
         * successful compile and launch the exit status belongs entirely
         * to the Sol program.
         */
        var exitCode = Sol.run(
            new String[] {"run", "program.sol"},
            new PrintStream(errorBytes),
            _ -> new ProgramRunResult(CompilerExitCode.FRONTEND_ERROR.value(), List.of())
        );

        assertEquals(4, exitCode);
        assertEquals("", errorBytes.toString());
    }
}
