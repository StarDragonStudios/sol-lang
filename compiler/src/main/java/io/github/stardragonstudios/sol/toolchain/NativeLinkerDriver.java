package io.github.stardragonstudios.sol.toolchain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record NativeLinkerDriver(Path executable) {
    public NativeLinkerDriver {
        executable = normalize(executable, "Native linker executable path must not be null.");
    }

    public NativeLinkCommand linkCommand(List<Path> objectFiles, Path output) {
        Objects.requireNonNull(objectFiles, "Native object-file paths must not be null.");

        if (objectFiles.isEmpty()) throw new IllegalArgumentException("Native link command must contain at least one object file.");

        var normalizedOutput = normalize(output, "Native executable output path must not be null.");
        var arguments = new ArrayList<String>(objectFiles.size() + 3);

        arguments.add(executable.toString());

        for (var index = 0; index < objectFiles.size(); index++) {
            var objectFile = normalize(objectFiles.get(index), "Native object-file path at index %d must not be null.".formatted(index));

            arguments.add(objectFile.toString());
        }

        arguments.add("-o");
        arguments.add(normalizedOutput.toString());

        return new NativeLinkCommand(arguments);
    }

    private static Path normalize(Path path, String nullMessage) {
        return Objects.requireNonNull(path, nullMessage).toAbsolutePath().normalize();
    }
}
