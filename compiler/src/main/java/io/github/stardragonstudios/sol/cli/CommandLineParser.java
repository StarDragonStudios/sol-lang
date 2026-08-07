package io.github.stardragonstudios.sol.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class CommandLineParser {
    private CommandLineParser() {}

    public static CompilerCommandLine parse(String[] arguments) {
        Objects.requireNonNull(arguments, "Compiler command-line arguments must not be null.");

        Path sourceFile = null;
        Path output = null;

        var keepIntermediates = false;
        var positionalOnly = false;

        for (var index = 0; index < arguments.length; index++) {
            var argument = Objects.requireNonNull(arguments[index], "Compiler command-line arguments must not contain null values.");

            if (!positionalOnly && argument.equals("--")) {
                positionalOnly = true;
                continue;
            }

            if (!positionalOnly) {
                if (argument.equals("--keep-intermediates")) {
                    keepIntermediates = true;
                    continue;
                }

                if (argument.equals("-o") || argument.equals("--output")) {
                    if (output != null) throw duplicateOutput();

                    if (index + 1 >= arguments.length) throw new CommandLineParsingException("Option '%s' requires an output path.".formatted(argument));

                    index++;

                    output = parsePath(arguments[index], "Compiler output path");

                    continue;
                }

                if (argument.startsWith("--output=")) {
                    if (output != null) throw duplicateOutput();

                    output = parsePath(argument.substring("--output=".length()), "Compiler output path");

                    continue;
                }

                if (argument.startsWith("-")) throw new CommandLineParsingException("Unknown compiler option '%s'.".formatted(argument));
            }

            if (sourceFile != null)
                throw new CommandLineParsingException("Compiler expects exactly one source file, but received both '%s' and '%s'.".formatted(sourceFile, argument));

            sourceFile = parsePath(argument, "Compiler source file");
        }

        if (sourceFile == null) throw new CommandLineParsingException("Compiler requires one Sol source file.");

        return new CompilerCommandLine(sourceFile, Optional.ofNullable(output), keepIntermediates);
    }

    private static Path parsePath(String value, String description) {
        Objects.requireNonNull(value, "%s must not be null.".formatted(description));

        if (value.isBlank()) throw new CommandLineParsingException("%s must not be blank.".formatted(description));

        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new CommandLineParsingException("%s '%s' is invalid.".formatted(description, value), exception);
        }
    }

    private static CommandLineParsingException duplicateOutput() {
        return new CommandLineParsingException("Compiler output path may only be specified once.");
    }
}
