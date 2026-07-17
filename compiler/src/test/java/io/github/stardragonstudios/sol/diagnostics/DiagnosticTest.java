package io.github.stardragonstudios.sol.diagnostics;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticTest {
    private static final SourceSpan SPAN = new SourceSpan(
        new SourcePosition(4, 2, 3),
        new SourcePosition(5, 2, 4)
    );

    @Test
    void createsDiagnosticWithExpectedValues() {
        var diagnostic = new Diagnostic(
            "SOL-L001",
            DiagnosticSeverity.ERROR,
            "Unexpected character '&'.",
            SPAN
        );

        assertEquals("SOL-L001", diagnostic.code());
        assertEquals(
            DiagnosticSeverity.ERROR,
            diagnostic.severity()
        );
        assertEquals(
            "Unexpected character '&'.",
            diagnostic.message()
        );
        assertEquals(SPAN, diagnostic.span());
    }

    @Test
    void supportsWarningDiagnostics() {
        var diagnostic = new Diagnostic(
            "SOL-L002",
            DiagnosticSeverity.WARNING,
            "Example warning.",
            SPAN
        );

        assertEquals(
            DiagnosticSeverity.WARNING,
            diagnostic.severity()
        );
    }

    @Test
    void rejectsNullCode() {
        assertThrows(
            NullPointerException.class,
            () -> new Diagnostic(
                null,
                DiagnosticSeverity.ERROR,
                "Message.",
                SPAN
            )
        );
    }

    @Test
    void rejectsBlankCode() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Diagnostic(
                "   ",
                DiagnosticSeverity.ERROR,
                "Message.",
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSeverity() {
        assertThrows(
            NullPointerException.class,
            () -> new Diagnostic(
                "SOL-L001",
                null,
                "Message.",
                SPAN
            )
        );
    }

    @Test
    void rejectsNullMessage() {
        assertThrows(
            NullPointerException.class,
            () -> new Diagnostic(
                "SOL-L001",
                DiagnosticSeverity.ERROR,
                null,
                SPAN
            )
        );
    }

    @Test
    void rejectsBlankMessage() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Diagnostic(
                "SOL-L001",
                DiagnosticSeverity.ERROR,
                "\t\n",
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSourceSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new Diagnostic(
                "SOL-L001",
                DiagnosticSeverity.ERROR,
                "Message.",
                null
            )
        );
    }
}
