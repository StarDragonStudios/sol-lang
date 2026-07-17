package io.github.stardragonstudios.sol.lexer;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LexicalExceptionTest {
    @Test
    void exposesItsDiagnostic() {
        var diagnostic = new Diagnostic(
            "SOL-L001",
            DiagnosticSeverity.ERROR,
            "Unexpected character.",
            new SourceSpan(
                new SourcePosition(0, 1, 1),
                new SourcePosition(1, 1, 2)
            )
        );

        var exception = new LexicalException(diagnostic);

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
            () -> new LexicalException(null)
        );
    }
}
