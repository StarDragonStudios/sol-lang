package io.github.stardragonstudios.sol.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineParserTest {
    @Test
    void parsesSingleSourceFile() {
        var commandLine = CommandLineParser.parse(new String[] {"program.sol"});

        assertEquals(Path.of("program.sol"), commandLine.sourceFile());
        assertEquals(Optional.empty(), commandLine.output());
        assertFalse(commandLine.keepIntermediates());
    }

    @Test
    void parsesShortOutputOption() {
        var commandLine = CommandLineParser.parse(new String[] {"program.sol", "-o", "build/program"});

        assertEquals(Optional.of(Path.of("build/program")), commandLine.output());
    }

    @Test
    void parsesLongOutputOption() {
        var commandLine = CommandLineParser.parse(new String[] {"--output", "build/program", "program.sol"});

        assertEquals(Path.of("program.sol"), commandLine.sourceFile());
        assertEquals(Optional.of(Path.of("build/program")), commandLine.output());
    }

    @Test
    void parsesInlineLongOutputOption() {
        var commandLine = CommandLineParser.parse(new String[] {"program.sol", "--output=build/program"});

        assertEquals(Optional.of(Path.of("build/program")), commandLine.output());
    }

    @Test
    void parsesIntermediateRetentionOption() {
        var commandLine = CommandLineParser.parse(new String[] {"--keep-intermediates", "program.sol"});

        assertTrue(commandLine.keepIntermediates());
    }

    @Test
    void acceptsOptionsAfterSourceFile() {
        var commandLine = CommandLineParser.parse(new String[] {"program.sol", "--keep-intermediates", "-o", "program"});

        assertEquals(Path.of("program.sol"), commandLine.sourceFile());
        assertEquals(Optional.of(Path.of("program")), commandLine.output());
        assertTrue(commandLine.keepIntermediates());
    }

    @Test
    void supportsEndOfOptionsMarker() {
        var commandLine = CommandLineParser.parse(new String[] {"--", "-program.sol"});

        assertEquals(Path.of("-program.sol"), commandLine.sourceFile());
    }

    @Test
    void rejectsMissingSourceFile() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> CommandLineParser.parse(new String[] {})
        );

        assertTrue(exception.getMessage().contains("requires one Sol source file"));
    }

    @Test
    void rejectsMultipleSourceFiles() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> CommandLineParser.parse(new String[] {"first.sol", "second.sol"})
        );

        assertTrue(exception.getMessage().contains("exactly one source file"));
    }

    @Test
    void rejectsUnknownOptions() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> CommandLineParser.parse(new String[] {"--unknown", "program.sol"})
        );

        assertTrue(exception.getMessage().contains("--unknown"));
    }

    @Test
    void rejectsOutputWithoutPath() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> CommandLineParser.parse(new String[] {"program.sol", "-o"})
        );

        assertTrue(exception.getMessage().contains("requires an output path"));
    }

    @Test
    void rejectsDuplicateOutputOptions() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> CommandLineParser.parse(new String[] {"program.sol", "-o", "first", "--output", "second"})
        );

        assertTrue(exception.getMessage().contains("only be specified once"));
    }

    @Test
    void rejectsBlankInlineOutputPath() {
        var exception = assertThrows(
            CommandLineParsingException.class,
            () -> CommandLineParser.parse(new String[]{"program.sol", "--output="})
        );

        assertTrue(exception.getMessage().contains("must not be blank"));
    }


    @Test
    void rejectsNullArgumentArray() {
        assertThrows(
            NullPointerException.class,
            () -> CommandLineParser.parse(null)
        );
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(
            NullPointerException.class,
            () -> CommandLineParser.parse(new String[] {"program.sol", null})
        );
    }
}
