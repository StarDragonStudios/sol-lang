package io.github.stardragonstudios.sol.toolchain;

import java.util.List;
import java.util.Objects;

public record NativeLinkCommand(List<String> arguments) {
    public NativeLinkCommand {
        Objects.requireNonNull(arguments, "Native link-command arguments must not be null.");

        if (arguments.isEmpty()) throw new IllegalArgumentException("Native link command must contain at least one argument.");

        for (var argument : arguments) {
            Objects.requireNonNull(argument, "Native link-command arguments must not contain null values.");

            if (argument.isBlank()) throw new IllegalArgumentException("Native link-command arguments must not be blank.");
        }

        arguments = List.copyOf(arguments);
    }
}
