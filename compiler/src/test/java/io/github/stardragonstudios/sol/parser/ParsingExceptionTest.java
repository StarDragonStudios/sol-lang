package io.github.stardragonstudios.sol.parser;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParsingExceptionTest {
    @Test
    void exposesItsDiagnostic() {
        var diagnostic = new Diagnostic(
            "SOL-P001",
            DiagnosticSeverity.ERROR,
            "Unexpected token.",
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(1, 1, 2)
            )
        );

        var exception = new ParsingException(diagnostic);

        assertSame(
            diagnostic,
            exception.diagnostic()
        );
        assertEquals(
            diagnostic.message(),
            exception.getMessage()
        );
    }

    @Test
    void rejectsNullDiagnostic() {
        assertThrows(
            NullPointerException.class,
            () -> new ParsingException(null)
        );
    }
}
