package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;

public record TypeReference(
    String name,
    List<TypeReference> arguments,
    SourceSpan span
) implements SyntaxNode {
    public TypeReference {
        Objects.requireNonNull(
            name,
            "Type reference name must not be null."
        );

        Objects.requireNonNull(
            arguments,
            "Type reference arguments must not be null."
        );

        Objects.requireNonNull(
            span,
            "Type reference source span must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Type reference name must not be blank."
            );
        }

        arguments = List.copyOf(arguments);
        arguments.forEach(argument -> Objects.requireNonNull(argument, "Type reference arguments must not contain null values."));
    }

    public TypeReference(String name, SourceSpan span) {
        this(name, List.of(), span);
    }
}
