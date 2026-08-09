package io.github.stardragonstudios.sol;

import java.io.PrintStream;
import java.util.Objects;

public final class SolVersion {
    private static final String DEVELOPMENT_VERSION = "development";

    private SolVersion() {}

    public static String current() {
        var version = SolVersion.class.getPackage().getImplementationVersion();

        if (version == null || version.isBlank()) return DEVELOPMENT_VERSION;

        return version;
    }

    public static boolean isVersionRequest(String[] arguments) {
        Objects.requireNonNull(arguments, "Command-line arguments must not be null.");

        return arguments.length == 1 && "--version".equals(arguments[0]);
    }

    public static void print(PrintStream output) {
        Objects.requireNonNull(output, "Version output must not be null.");

        output.println("Sol " + current());
    }
}
