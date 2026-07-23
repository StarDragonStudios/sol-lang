package io.github.stardragonstudios.sol.backend.c;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CTranslationUnitTest {
    @Test
    void preservesCanonicalSourceExactly() {
        var source = "typedef int generated;\n";
        var unit = new CTranslationUnit(source);

        assertEquals(source, unit.source());
    }

    @Test
    void supportsEmptySource() {
        assertEquals("", new CTranslationUnit("").source());
    }

    @Test
    void definesValueEqualityThroughSource() {
        assertEquals(
            new CTranslationUnit("typedef int generated;\n"),
            new CTranslationUnit("typedef int generated;\n")
        );

        assertNotEquals(
            new CTranslationUnit("typedef int first;\n"),
            new CTranslationUnit("typedef int second;\n")
        );
    }

    @Test
    void rejectsNullSource() {
        assertThrows(
            NullPointerException.class,
            () -> new CTranslationUnit(null)
        );
    }

    @Test
    void rejectsCarriageReturnLineEndings() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new CTranslationUnit("typedef int generated;\r\n")
        );
    }

    @Test
    void rejectsMissingFinalNewline() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new CTranslationUnit("typedef int generated;")
        );
    }

    @Test
    void rejectsRepeatedFinalNewlines() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new CTranslationUnit("typedef int generated;\n\n")
        );
    }
}
