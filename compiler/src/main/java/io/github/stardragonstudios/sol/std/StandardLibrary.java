package io.github.stardragonstudios.sol.std;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.lexer.LexicalException;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.parser.ParsingException;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StandardLibrary {
    public static final ModuleName CONSOLE = new ModuleName(List.of("std", "console"));
    public static final ModuleName FILE = new ModuleName(List.of("std", "file"));

    private static final Map<ModuleName, String> SOURCES = Map.of(
        CONSOLE,
        """
        @fn print(value: string) -> void
        @fn print_line(value: string) -> void
        """,

        FILE,
        """
        @fn exists(path: string) -> boolean
        @fn write_text(path: string, content: string) -> boolean
        @fn append_text(path: string, content: string) -> boolean
        """
    );

    private StandardLibrary() {}

    public static boolean contains(ModuleName moduleName) {
        Objects.requireNonNull(moduleName, "Standard-library module name must not be null.");

        return SOURCES.containsKey(moduleName);
    }

    public static Optional<SourceModule> sourceModule(ModuleName moduleName) {
        Objects.requireNonNull(moduleName, "Standard-library module name must not be null.");

        var source = SOURCES.get(moduleName);

        if (source == null) return Optional.empty();

        try {
            return Optional.of(new SourceModule(moduleName, Parser.parse(Lexer.scan(source))));
        } catch (LexicalException | ParsingException exception) {
            throw new IllegalStateException("Bundled standard-library module '%s' is invalid.".formatted(moduleName.qualifiedName()), exception);
        }
    }

    public static Path sourcePath(ModuleName moduleName) {
        Objects.requireNonNull(moduleName, "Standard-library module name must not be null.");

        if (!contains(moduleName)) throw new IllegalArgumentException("Unknown standard-library module '%s'.".formatted(moduleName.qualifiedName()));

        var segments = moduleName.segments();
        var path = Path.of(".sol-stdlib");

        for (var index = 0; index < segments.size() - 1; index++) path = path.resolve(segments.get(index));

        return path.resolve(segments.getLast() + ".sol").toAbsolutePath().normalize();
    }
}
