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
    public static final ModuleName COLLECTIONS_VECTOR = new ModuleName(List.of("std", "collections", "vector"));
    public static final ModuleName FILE = new ModuleName(List.of("std", "file"));
    public static final ModuleName MEMORY = new ModuleName(List.of("std", "memory"));
    public static final ModuleName STRING = new ModuleName(List.of("std", "string"));

    private static final Map<ModuleName, String> SOURCES = Map.of(
        COLLECTIONS_VECTOR,
        """
        inject namespace std.memory as memory

        struct Vector<T>
            data: pointer<T>
            length: int
            capacity: int
        end

        @fn _vector_fail_allocation() -> void
        @fn _vector_fail_bounds() -> void
        @fn _vector_fail_capacity() -> void
        @fn _vector_fail_empty_pop() -> void

        fn create_vector<T>() -> pointer<Vector<T>>
            let vector: pointer<Vector<T>> = memory::allocate<Vector<T>>(1)

            if vector == null then
                _vector_fail_allocation()
            end

            vector->data = null
            vector->length = 0
            vector->capacity = 0
            return vector
        end

        fn destroy_vector<T>(vector: pointer<Vector<T>>) -> void
            if vector == null then
                return
            end

            memory::free<T>(vector->data)
            vector->data = null
            vector->length = 0
            vector->capacity = 0
            memory::free<Vector<T>>(vector)
            return
        end

        fn vector_length<T>(vector: pointer<Vector<T>>) -> int
            return vector->length
        end

        fn vector_capacity<T>(vector: pointer<Vector<T>>) -> int
            return vector->capacity
        end

        fn vector_get<T>(vector: pointer<Vector<T>>, index: int) -> T
            if index < 0 || index >= vector->length then
                _vector_fail_bounds()
            end

            return memory::load_at<T>(vector->data, index)
        end

        fn vector_set<T>(vector: pointer<Vector<T>>, index: int, value: T) -> void
            if index < 0 || index >= vector->length then
                _vector_fail_bounds()
            end

            memory::store_at<T>(vector->data, index, value)
            return
        end

        fn vector_reserve<T>(vector: pointer<Vector<T>>, requested: int) -> void
            if requested < 0 then
                _vector_fail_capacity()
            end

            if requested <= vector->capacity then
                return
            end

            let resized: pointer<T> = memory::reallocate<T>(vector->data, requested)

            if resized == null then
                _vector_fail_allocation()
            end

            vector->data = resized
            vector->capacity = requested
            return
        end

        fn vector_push<T>(vector: pointer<Vector<T>>, value: T) -> void
            if vector->length == vector->capacity then
                @mut let next_capacity: int = 8

                if vector->capacity > 0 then
                    if vector->capacity > 4611686018427387903 then
                        _vector_fail_capacity()
                    end

                    next_capacity = vector->capacity * 2
                end

                vector_reserve<T>(vector, next_capacity)
            end

            memory::store_at<T>(vector->data, vector->length, value)
            vector->length = vector->length + 1
            return
        end

        fn vector_pop<T>(vector: pointer<Vector<T>>) -> T
            if vector->length == 0 then
                _vector_fail_empty_pop()
            end

            let index: int = vector->length - 1
            let value: T = memory::load_at<T>(vector->data, index)
            vector->length = index
            return value
        end

        fn vector_clear<T>(vector: pointer<Vector<T>>) -> void
            vector->length = 0
            return
        end
        """,

        CONSOLE,
        """
        @fn print(value: string) -> void
        @fn print_line(value: string) -> void
        @fn read_line() -> string
        """,

        FILE,
        """
        @fn exists(path: string) -> boolean
        @fn read_text(path: string) -> string
        @fn write_text(path: string, content: string) -> boolean
        @fn append_text(path: string, content: string) -> boolean
        """,

        MEMORY,
        """
        @fn allocate<T>(count: int) -> pointer<T>
        @fn reallocate<T>(value: pointer<T>, count: int) -> pointer<T>
        @fn free<T>(value: pointer<T>) -> void
        @fn load<T>(value: pointer<T>) -> T
        @fn store<T>(target: pointer<T>, value: T) -> void
        @fn load_at<T>(value: pointer<T>, index: int) -> T
        @fn store_at<T>(target: pointer<T>, index: int, value: T) -> void
        """,

        STRING,
        """
        @fn length(value: string) -> int
        @fn slice(value: string, start: int, end_index: int) -> string
        @fn substring(value: string, start: int, count: int) -> string
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
