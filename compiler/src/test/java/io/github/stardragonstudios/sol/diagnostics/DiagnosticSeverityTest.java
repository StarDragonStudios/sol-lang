package io.github.stardragonstudios.sol.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosticSeverityTest {
    @Test
    void definesErrorAndWarningSeverities() {
        assertEquals(
            2,
            DiagnosticSeverity.values().length
        );

        assertEquals(
            DiagnosticSeverity.ERROR,
            DiagnosticSeverity.valueOf("ERROR")
        );

        assertEquals(
            DiagnosticSeverity.WARNING,
            DiagnosticSeverity.valueOf("WARNING")
        );
    }
}
