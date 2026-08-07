package io.github.stardragonstudios.sol.cli;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class CompilerOutputPath {
    private CompilerOutputPath() {}

    public static Path defaultFor(Path sourceFile) {
        var normalized = Objects.requireNonNull(sourceFile, "Compiler source path must not be null.").toAbsolutePath().normalize();
        var name = getName(normalized);
        var executableName = name.substring(0, name.length() - ".sol".length());

        if (executableName.isBlank()) throw new CompilerPipelineException("Sol source file '%s' must have a name before '.sol'.".formatted(normalized));

        return normalized.resolveSibling(executableName);
    }

    private static String getName(Path normalized) {
        var fileName = normalized.getFileName();

        if (fileName == null) throw new IllegalArgumentException("Compiler source path must identify a file.");

        var name = fileName.toString();

        if (!name.toLowerCase(Locale.ROOT).endsWith(".sol")) throw new CompilerPipelineException("Sol source file '%s' must use the '.sol' extension.".formatted(normalized));

        return name;
    }

    public static String moduleName(Path sourceFile) {
        return defaultFor(sourceFile).getFileName().toString();
    }
}
