package io.github.stardragonstudios.sol;

import io.github.stardragonstudios.sol.backend.llvm.LlvmBackendException;
import io.github.stardragonstudios.sol.cli.CompilerExitCode;
import io.github.stardragonstudios.sol.cli.CompilerPipelineException;
import io.github.stardragonstudios.sol.cli.CompilerPipelineResult;
import io.github.stardragonstudios.sol.cli.FrontendCompilationException;
import io.github.stardragonstudios.sol.cli.SourceDiagnostic;
import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lowering.IrLoweringException;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.toolchain.NativeToolchainException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SolCompilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void successfulCompilationReturnsZero() {
        var errorBytes = new ByteArrayOutputStream();
        var source = temporaryDirectory.resolve("program.sol");
        var executable = temporaryDirectory.resolve("program");

        var exitCode = SolCompiler.run(
            new String[] {source.toString()},
            new PrintStream(errorBytes),
            _ -> new CompilerPipelineResult(source, executable, List.of())
        );

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode);
        assertEquals("", errorBytes.toString());
    }

    @Test
    void commandLineErrorsReturnDedicatedExitCode() {
        var errorBytes = new ByteArrayOutputStream();
        var invoked = new AtomicBoolean();

        var exitCode = SolCompiler.run(
            new String[] {},
            new PrintStream(errorBytes),
            _ -> {invoked.set(true);

                throw new AssertionError("Compilation must not run.");
            }
        );

        assertEquals(CompilerExitCode.COMMAND_LINE_ERROR.value(), exitCode);
        assertFalse(invoked.get());
        assertTrue(errorBytes.toString().contains("command-line error:"));
    }

    @Test
    void inputErrorsReturnDedicatedExitCode() {
        assertFailure(
            new CompilerPipelineException("Cannot read source."),
            CompilerExitCode.INPUT_ERROR,
            "input error: Cannot read source."
        );
    }

    @Test
    void frontendErrorsPreserveDiagnosticLocation() {
        var source = temporaryDirectory.resolve("program.sol");

        var diagnostic = new SourceDiagnostic(
            source,
            new Diagnostic(
                "SOL-S002",
                DiagnosticSeverity.ERROR,
                "Unresolved name 'missing'.",
                new SourceSpan(new SourcePosition(12, 2, 5), new SourcePosition(19, 2, 12))
            )
        );

        var errorBytes = new ByteArrayOutputStream();

        var exitCode = SolCompiler.run(
            new String[] {source.toString()},
            new PrintStream(errorBytes),
            _ -> {
                throw new FrontendCompilationException(List.of(diagnostic));
            }
        );

        assertEquals(CompilerExitCode.FRONTEND_ERROR.value(), exitCode);

        assertEquals("%s:2:5: error [SOL-S002]: Unresolved name 'missing'.%n".formatted(source.toAbsolutePath().normalize()), errorBytes.toString());
    }

    @Test
    void successfulWarningsArePrintedWithoutFailingCompilation() {
        var source = temporaryDirectory.resolve("program.sol");
        var executable = temporaryDirectory.resolve("program");

        var warning = new SourceDiagnostic(
            source,
            new Diagnostic(
                "SOL-W001",
                DiagnosticSeverity.WARNING,
                "Example warning.",
                new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(0, 1, 1))
            )
        );

        var errorBytes = new ByteArrayOutputStream();

        var exitCode = SolCompiler.run(
            new String[] {source.toString()},
            new PrintStream(errorBytes),
            _ -> new CompilerPipelineResult(source, executable, List.of(warning))
        );

        assertEquals(CompilerExitCode.SUCCESS.value(), exitCode);

        assertTrue(errorBytes.toString().contains("warning [SOL-W001]: Example warning."));
    }

    @Test
    void loweringErrorsReturnDedicatedExitCode() {
        assertFailure(new IrLoweringException("Cannot lower program."), CompilerExitCode.LOWERING_ERROR, "lowering error: Cannot lower program.");
    }

    @Test
    void backendErrorsReturnDedicatedExitCode() {
        assertFailure(new LlvmBackendException("Cannot emit LLVM."), CompilerExitCode.BACKEND_ERROR, "backend error: Cannot emit LLVM.");
    }

    @Test
    void toolchainErrorsReturnDedicatedExitCode() {
        assertFailure(new NativeToolchainException("Linker failed."), CompilerExitCode.TOOLCHAIN_ERROR, "toolchain error: Linker failed.");
    }

    private void assertFailure(RuntimeException failure, CompilerExitCode expectedExitCode, String expectedOutput) {
        var errorBytes = new ByteArrayOutputStream();
        var source = temporaryDirectory.resolve("program.sol");
        var exitCode = SolCompiler.run(new String[] {source.toString()}, new PrintStream(errorBytes), _ -> {throw failure;});

        assertEquals(expectedExitCode.value(), exitCode);
        assertEquals(expectedOutput + System.lineSeparator(), errorBytes.toString());
    }
}
