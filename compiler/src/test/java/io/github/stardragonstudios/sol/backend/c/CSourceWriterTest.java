package io.github.stardragonstudios.sol.backend.c;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CSourceWriterTest {
    @Test
    void returnsEmptySourceWhenNothingWasWritten() {
        assertEquals("", new CSourceWriter().source());
    }

    @Test
    void writesLinesWithCanonicalFinalNewline() {
        var writer = new CSourceWriter();
        writer.line("typedef int sol_int;");

        assertEquals("typedef int sol_int;\n", writer.source());
    }

    @Test
    void indentsWithFourSpaces() {
        var writer = new CSourceWriter();
        writer.indent();
        writer.line("return 0;");

        assertEquals("    return 0;\n", writer.source());
        assertFalse(writer.source().contains("\t"));
    }

    @Test
    void writesNestedBracedBlocks() {
        var writer = new CSourceWriter();

        writer.openBlock("int main(void)");
        writer.openBlock("if (1)");
        writer.line("return 0;");
        writer.closeBlock();
        writer.closeBlock();

        assertEquals(
            """
            int main(void) {
                if (1) {
                    return 0;
                }
            }
            """,
            writer.source()
        );
    }

    @Test
    void avoidsLeadingAndRepeatedBlankLines() {
        var writer = new CSourceWriter();

        writer.blankLine();
        writer.blankLine();
        writer.line("first");
        writer.blankLine();
        writer.blankLine();
        writer.line("second");

        assertEquals(
            """
            first

            second
            """,
            writer.source()
        );
    }

    @Test
    void removesTrailingBlankLinesFromCompletedSource() {
        var writer = new CSourceWriter();
        writer.line("content");
        writer.blankLine();
        writer.blankLine();

        assertEquals("content\n", writer.source());
    }

    @Test
    void completedSourceIsNotChangedByLaterWrites() {
        var writer = new CSourceWriter();
        writer.line("first");

        var firstSource = writer.source();
        writer.line("second");

        assertEquals("first\n", firstSource);

        assertEquals(
            """
            first
            second
            """,
            writer.source()
        );
    }

    @Test
    void rejectsIndentationBelowZero() {
        var writer = new CSourceWriter();

        assertThrows(IllegalStateException.class, writer::dedent);
        assertThrows(IllegalStateException.class, writer::closeBlock);
    }

    @Test
    void rejectsInvalidLines() {
        var writer = new CSourceWriter();

        assertThrows(
            NullPointerException.class,
            () -> writer.line(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> writer.line("first\nsecond")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> writer.line("first\rsecond")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> writer.line("\treturn 0;")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> writer.line("return 0; ")
        );
    }

    @Test
    void rejectsBlankBlockHeaders() {
        var writer = new CSourceWriter();

        assertThrows(
            NullPointerException.class,
            () -> writer.openBlock(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> writer.openBlock("   ")
        );
    }
}
