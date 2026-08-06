package io.github.stardragonstudios.sol.toolchain;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record NativeExecutableLayout(Path executable, Path objectFile) {
    public NativeExecutableLayout {
        executable = normalize(executable, "Native executable path must not be null.");
        objectFile = normalize(objectFile, "Temporary native object path must not be null.");

        if (executable.getFileName() == null) throw new IllegalArgumentException("Native executable path must identify a file.");
        if (objectFile.getFileName() == null) throw new IllegalArgumentException("Temporary native object path must identify a file.");
        if (executable.equals(objectFile)) throw new IllegalArgumentException("Native executable and temporary object paths must be different.");
    }

    public static NativeExecutableLayout host(Path requestedOutput) {
        return forOperatingSystem(requestedOutput, System.getProperty("os.name", ""));
    }

    static NativeExecutableLayout forOperatingSystem(Path requestedOutput, String operatingSystem) {
        var normalizedOutput = normalize(requestedOutput, "Requested native executable path must not be null.");

        Objects.requireNonNull(operatingSystem, "Native executable operating-system name must not be null.");

        if (normalizedOutput.getFileName() == null) throw new IllegalArgumentException("Requested native executable path must identify a file.");

        var windows = isWindows(operatingSystem);
        var executable = windows ? withWindowsExtension(normalizedOutput) : normalizedOutput;
        var objectSuffix = windows ? ".sol-link.obj" : ".sol-link.o";
        var objectFile = executable.resolveSibling(executable.getFileName() + objectSuffix);

        return new NativeExecutableLayout(executable, objectFile);
    }

    private static boolean isWindows(String operatingSystem) {
        return operatingSystem.strip().toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static Path withWindowsExtension(Path output) {
        var fileName = output.getFileName().toString();

        if (fileName.toLowerCase(Locale.ROOT).endsWith(".exe")) return output;

        return output.resolveSibling(fileName + ".exe");
    }

    private static Path normalize(Path path, String nullMessage) {
        return Objects.requireNonNull(path, nullMessage).toAbsolutePath().normalize();
    }
}
