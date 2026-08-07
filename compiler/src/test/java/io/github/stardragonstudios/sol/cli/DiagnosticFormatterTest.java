package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosticFormatterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void formatsSourceLocationSeverityCodeAndMessage() {
        var source = temporaryDirectory.resolve("program.sol");

        var diagnostic = new Diagnostic(
            "SOL-X001",
            DiagnosticSeverity.ERROR,
            "Something went wrong.",
            new SourceSpan(new SourcePosition(15, 3, 7), new SourcePosition(20, 3, 12))
        );

        assertEquals(
            "%s:3:7: error [SOL-X001]: Something went wrong.".formatted(source.toAbsolutePath().normalize()),
            DiagnosticFormatter.format(new SourceDiagnostic(source, diagnostic))
        );
    }
}
