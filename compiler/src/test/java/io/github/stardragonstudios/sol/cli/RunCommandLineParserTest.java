package io.github.stardragonstudios.sol.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunCommandLineParserTest {
    @Test
    void parsesSingleSourceFile() {
        var commandLine = RunCommandLineParser.parse(new String[] {"program.sol"});

        assertEquals(Path.of("program.sol"), commandLine.sourceFile());
    }

    @Test
    void supportsEndOfOptionsMarker() {
        var commandLine = RunCommandLineParser.parse(new String[] {"--", "-program.sol"});

        assertEquals(Path.of("-program.sol"), commandLine.sourceFile());
    }

    @Test
    void rejectsMissingSourceFile() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> RunCommandLineParser.parse(new String[] {})
        );

        assertTrue(exception.getMessage().contains("requires one Sol source file"));
    }

    @Test
    void rejectsMultipleSourceFiles() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> RunCommandLineParser.parse(new String[] {"first.sol", "second.sol"})
        );

        assertTrue(exception.getMessage().contains("exactly one source file"));
    }

    @Test
    void rejectsCompilerOutputOption() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> RunCommandLineParser.parse(new String[] {"program.sol", "-o", "program"})
        );

        assertTrue(exception.getMessage().contains("-o"));
    }

    @Test
    void rejectsIntermediateRetentionOption() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> RunCommandLineParser.parse(new String[] {"--keep-intermediates", "program.sol"})
        );

        assertTrue(exception.getMessage().contains("--keep-intermediates"));
    }

    @Test
    void rejectsBlankSourceFile() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> RunCommandLineParser.parse(new String[] {""})
        );

        assertTrue(exception.getMessage().contains("must not be blank"));
    }

    @Test
    void rejectsNullArgumentArray() {
        assertThrows(
            NullPointerException.class,
            () -> RunCommandLineParser.parse(null)
        );
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(
            NullPointerException.class,
            () -> RunCommandLineParser.parse(new String[] {null})
        );
    }
}
