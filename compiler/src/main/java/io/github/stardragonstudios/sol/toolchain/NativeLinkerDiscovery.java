package io.github.stardragonstudios.sol.toolchain;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class NativeLinkerDiscovery {
    public static final String LINKER_ENVIRONMENT_VARIABLE = "SOL_LINKER";
    private static final String PATH_ENVIRONMENT_VARIABLE = "PATH";

    private NativeLinkerDiscovery() {}

    public static NativeLinkerDriver discover() {
        return discover(System.getenv(), File.pathSeparator, candidateNames(), Files::isRegularFile, Files::isExecutable);
    }

    static NativeLinkerDriver discover(Map<String, String> environment, String pathSeparator, List<String> candidateNames, Predicate<Path> regularFile, Predicate<Path> executable) {
        Objects.requireNonNull(environment, "Native linker-discovery environment must not be null.");
        Objects.requireNonNull(pathSeparator, "Native linker path separator must not be null.");
        Objects.requireNonNull(candidateNames, "Native linker candidate names must not be null.");
        Objects.requireNonNull(regularFile, "Native linker regular-file predicate must not be null.");
        Objects.requireNonNull(executable, "Native linker executable predicate must not be null.");

        if (pathSeparator.isEmpty()) throw new IllegalArgumentException("Native linker path separator must not be empty.");

        validateCandidateNames(candidateNames);

        if (environment.containsKey(LINKER_ENVIRONMENT_VARIABLE)) {
            var configured = environment.get(LINKER_ENVIRONMENT_VARIABLE);

            return discoverConfigured(configured, environment.get(PATH_ENVIRONMENT_VARIABLE), pathSeparator, regularFile, executable);
        }

        var discovered = findOnPath(candidateNames, environment.get(PATH_ENVIRONMENT_VARIABLE), pathSeparator, regularFile, executable);

        if (discovered.isPresent()) return new NativeLinkerDriver(discovered.orElseThrow());

        throw new NativeToolchainException("No native linker driver was found. Install 'clang' or configure an executable linker through the 'SOL_LINKER' environment variable.");
    }

    private static NativeLinkerDriver discoverConfigured(String configured, String path, String pathSeparator, Predicate<Path> regularFile, Predicate<Path> executable) {
        Objects.requireNonNull(configured, "Configured native linker from 'SOL_LINKER' must not be null.");

        if (configured.isBlank()) throw new NativeToolchainException("Configured native linker from 'SOL_LINKER' must not be blank.");

        var configuredPath = parsePath(configured, "Configured native linker path from 'SOL_LINKER' is invalid.");

        if (configuredPath.isAbsolute() || configuredPath.getParent() != null) {
            var normalized = configuredPath.toAbsolutePath().normalize();

            if (!isAvailable(normalized, regularFile, executable))
                throw new NativeToolchainException("Configured native linker '%s' from 'SOL_LINKER' is not a regular executable file.".formatted(normalized));

            return new NativeLinkerDriver(normalized);
        }

        var discovered = findOnPath(List.of(configured), path, pathSeparator, regularFile, executable);

        if (discovered.isEmpty())
            throw new NativeToolchainException("Configured native linker command '%s' from 'SOL_LINKER' was not found as an executable file in 'PATH'.".formatted(configured));

        return new NativeLinkerDriver(discovered.orElseThrow());
    }

    private static Optional<Path> findOnPath(List<String> candidateNames, String path, String pathSeparator, Predicate<Path> regularFile, Predicate<Path> executable) {
        if (path == null || path.isBlank()) return Optional.empty();


        var directories = pathDirectories(path, pathSeparator);

        /*
         * Candidate priority is evaluated before PATH order.
         * Every PATH directory is searched for clang before
         * falling back to cc.
         */
        for (var candidateName : candidateNames) {
            for (var directory : directories) {
                var candidate = directory.resolve(candidateName).toAbsolutePath().normalize();

                if (isAvailable(candidate, regularFile, executable)) return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    private static List<Path> pathDirectories(String path, String pathSeparator) {
        var directories = new ArrayList<Path>();
        var entries = path.split(Pattern.quote(pathSeparator), -1);

        for (var entry : entries) {
            if (entry.isBlank()) continue;

            try {
                directories.add(Path.of(entry));
            } catch (InvalidPathException ignored) {
                /*
                 * One malformed PATH entry must not prevent
                 * discovery in later valid entries.
                 */
            }
        }

        return List.copyOf(directories);
    }

    private static boolean isAvailable(Path candidate, Predicate<Path> regularFile, Predicate<Path> executable) {
        return regularFile.test(candidate) && executable.test(candidate);
    }

    private static void validateCandidateNames(List<String> candidateNames) {
        if (candidateNames.isEmpty()) throw new IllegalArgumentException("Native linker discovery requires at least one candidate name.");

        for (var index = 0; index < candidateNames.size(); index++) {
            var candidateName = getCandidateName(candidateNames, index);
            var candidatePath = parsePath(candidateName, "Native linker candidate name at index %d is invalid.".formatted(index));

            if (candidatePath.isAbsolute() || candidatePath.getParent() != null)
                throw new IllegalArgumentException("Native linker candidate '%s' must be a simple file name.".formatted(candidateName));
        }
    }

    private static String getCandidateName(List<String> candidateNames, int index) {
        var candidateName = Objects.requireNonNull(
            candidateNames.get(index),
            "Native linker candidate name at index %d must not be null.".formatted(index)
        );

        if (candidateName.isBlank()) throw new IllegalArgumentException("Native linker candidate name at index %d must not be blank.".formatted(index));

        return candidateName;
    }

    private static Path parsePath(String value, String message) {
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new NativeToolchainException(message, exception);
        }
    }

    private static List<String> candidateNames() {
        var operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (operatingSystem.contains("win")) return List.of("clang.exe", "clang", "cc.exe", "cc");

        return List.of("clang", "cc");
    }
}
