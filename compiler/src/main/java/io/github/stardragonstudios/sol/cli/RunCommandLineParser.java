package io.github.stardragonstudios.sol.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

public final class RunCommandLineParser {
    private RunCommandLineParser() {}

    public static RunCommandLine parse(String[] arguments) {
        Objects.requireNonNull(arguments, "Run command-line arguments must not be null.");

        Path sourceFile = null;
        var positionalOnly = false;

        for (String s : arguments) {
            var argument = Objects.requireNonNull(s, "Run command-line arguments must not contain null values.");

            if (!positionalOnly && argument.equals("--")) {
                positionalOnly = true;
                continue;
            }

            if (!positionalOnly && argument.startsWith("-")) throw new CommandLineParsingException("Unknown run option '%s'.".formatted(argument));
            if (sourceFile != null) throw new CommandLineParsingException("Run expects exactly one source file, but received both '%s' and '%s'.".formatted(sourceFile, argument));

            sourceFile = parsePath(argument);
        }

        if (sourceFile == null) throw new CommandLineParsingException("Run requires one Sol source file.");

        return new RunCommandLine(sourceFile);
    }

    private static Path parsePath(String value) {
        if (value.isBlank()) throw new CommandLineParsingException("Run source file must not be blank.");

        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new CommandLineParsingException("Run source file '%s' is invalid.".formatted(value), exception);
        }
    }
}
