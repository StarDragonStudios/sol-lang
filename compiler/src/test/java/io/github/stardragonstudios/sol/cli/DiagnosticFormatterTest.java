package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosticFormatterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersSingleLineSourceSpan()
        throws IOException {

        var source = temporaryDirectory.resolve("program.sol");

        var text =
            """
            @init
            fn launch() -> int
                return missing
            end
            """;

        Files.writeString(source, text);

        var start = text.indexOf("missing");

        var diagnostic = new Diagnostic(
            "SOL-S002",
            DiagnosticSeverity.ERROR,
            "Unresolved name 'missing'.",
            new SourceSpan(new SourcePosition(start, 3, 12), new SourcePosition(start + "missing".length(), 3, 19))
        );

        assertEquals(
            """
            %s:3:12: error [SOL-S002]: Unresolved name 'missing'.
              |
            3 |     return missing
              |            ^^^^^^^
            """.formatted(source.toAbsolutePath().normalize()).stripTrailing(),
            DiagnosticFormatter.format(new SourceDiagnostic(source, diagnostic))
        );
    }

    @Test
    void rendersAtLeastOneCaretForEmptySpan() {
        var source = temporaryDirectory.resolve("program.sol");
        var text = "return";

        SourcePosition sourcePosition = new SourcePosition(text.length(), 1, text.length() + 1);

        var diagnostic = new Diagnostic("SOL-X001", DiagnosticSeverity.ERROR, "Expected expression.", new SourceSpan(sourcePosition, sourcePosition));

        assertEquals(
            """
            %s:1:7: error [SOL-X001]: Expected expression.
              |
            1 | return
              |       ^
            """.formatted(source.toAbsolutePath().normalize()).stripTrailing(),
            DiagnosticFormatter.format(new SourceDiagnostic(source, diagnostic), text)
        );
    }

    @Test
    void preservesTabsInMarkerAlignment() {
        var source = temporaryDirectory.resolve("program.sol");
        var text = "\treturn missing";
        var start = text.indexOf("missing");

        var diagnostic = new Diagnostic(
            "SOL-S002",
            DiagnosticSeverity.WARNING,
            "Example warning.",
            new SourceSpan(new SourcePosition(start, 1, 9), new SourcePosition(start + "missing".length(), 1, 16))
        );

        assertEquals(
            """
            %s:1:9: warning [SOL-S002]: Example warning.
              |
            1 | \treturn missing
              | \t       ^^^^^^^
            """.formatted(source.toAbsolutePath().normalize()).stripTrailing(),
            DiagnosticFormatter.format(new SourceDiagnostic(source, diagnostic), text)
        );
    }

    @Test
    void fallsBackToHeaderWhenSourceFileCannotBeRead() {
        var source = temporaryDirectory.resolve("missing.sol");

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

    @Test
    void rendersMultilineSourceSpan() {
        var source = temporaryDirectory.resolve("program.sol");
        var text =
            """
            first
            second
            """;

        var diagnostic = new Diagnostic(
            "SOL-X001",
            DiagnosticSeverity.ERROR,
            "Multiline problem.",
            new SourceSpan(new SourcePosition(2, 1, 3), new SourcePosition(9, 2, 4))
        );

        assertEquals(
            """
            %s:1:3: error [SOL-X001]: Multiline problem.
              |
            1 | first
              |   ^^^
            2 | second
              | ^^^
            """.formatted(source.toAbsolutePath().normalize()).stripTrailing(),
            DiagnosticFormatter.format(
                new SourceDiagnostic(source, diagnostic),
                text
            )
        );
    }

    @Test
    void multilineSpanEndingAtNextLineStartDoesNotMarkFinalLine() {
        var source = temporaryDirectory.resolve("program.sol");

        var text =
            """
            first
            second
            """;

        var diagnostic = new Diagnostic(
            "SOL-X001",
            DiagnosticSeverity.ERROR,
            "Multiline problem.",
            new SourceSpan(
                new SourcePosition(2, 1, 3),
                new SourcePosition(6, 2, 1)
            )
        );

        assertEquals(
            """
            %s:1:3: error [SOL-X001]: Multiline problem.
              |
            1 | first
              |   ^^^
            """.formatted(source.toAbsolutePath().normalize()).stripTrailing(),
            DiagnosticFormatter.format(new SourceDiagnostic(source, diagnostic), text)
        );
    }

    @Test
    void rendersSourceUsingCrLfLineEndings() {
        var source = temporaryDirectory.resolve("program.sol");
        var text = "first\r\nsecond";
        var start = text.indexOf("second") + 2;

        var diagnostic = new Diagnostic(
            "SOL-X001",
            DiagnosticSeverity.ERROR,
            "CRLF problem.",
            new SourceSpan(new SourcePosition(start, 2, 3), new SourcePosition(start + 3, 2, 6))
        );

        assertEquals(
            """
            %s:2:3: error [SOL-X001]: CRLF problem.
              |
            2 | second
              |   ^^^
            """.formatted(source.toAbsolutePath().normalize()).stripTrailing(),
            DiagnosticFormatter.format(new SourceDiagnostic(source, diagnostic), text)
        );
    }

    @Test
    void staleSpanFallsBackToHeader() {
        var source = temporaryDirectory.resolve("program.sol");

        var diagnostic = new Diagnostic(
            "SOL-X001",
            DiagnosticSeverity.ERROR,
            "Stale diagnostic.",
            new SourceSpan(new SourcePosition(100, 1, 2), new SourcePosition(101, 1, 3))
        );

        assertEquals(
            "%s:1:2: error [SOL-X001]: Stale diagnostic.".formatted(source.toAbsolutePath().normalize()),
            DiagnosticFormatter.format(new SourceDiagnostic(source, diagnostic), "short")
        );
    }
}
