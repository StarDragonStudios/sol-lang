package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record FunctionDeclaration(List<Annotation> annotations, String name, List<Parameter> parameters, TypeReference returnType, Optional<Block> body, SourceSpan span) implements Declaration {
    public FunctionDeclaration {
        Objects.requireNonNull(
            annotations,
            "Function annotations must not be null."
        );

        Objects.requireNonNull(
            name,
            "Function name must not be null."
        );

        Objects.requireNonNull(
            parameters,
            "Function parameters must not be null."
        );

        Objects.requireNonNull(
            returnType,
            "Function return type must not be null."
        );

        Objects.requireNonNull(
            body,
            "Function body must not be null."
        );

        Objects.requireNonNull(
            span,
            "Function source span must not be null."
        );

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Function name must not be blank."
            );
        }

        annotations = List.copyOf(annotations);
        parameters = List.copyOf(parameters);
    }
}
