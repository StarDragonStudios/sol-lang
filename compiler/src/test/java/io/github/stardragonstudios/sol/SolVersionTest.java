package io.github.stardragonstudios.sol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SolVersionTest {
    @Test
    void recognizesVersionRequest() {
        assertTrue(SolVersion.isVersionRequest(new String[] {"--version"}));
    }

    @Test
    void rejectsVersionRequestWithAdditionalArguments() {
        assertFalse(SolVersion.isVersionRequest(new String[] {"--version", "program.sol"}));
    }

    @Test
    void rejectsUnrelatedArguments() {
        assertFalse(SolVersion.isVersionRequest(new String[] {"run", "program.sol"}));
    }

    @Test
    void reportsDevelopmentVersionOutsidePackagedJar() {
        assertEquals("development", SolVersion.current());
    }

    @Test
    void printsFormattedVersion() {
        var bytes = new ByteArrayOutputStream();

        SolVersion.print(new PrintStream(bytes));

        assertEquals("Sol development" + System.lineSeparator(), bytes.toString());
    }
}
