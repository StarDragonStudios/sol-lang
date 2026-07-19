package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InjectionDeclaration(
    InjectionKind kind,
    ModulePath modulePath,
    List<String> selectedNames,
    Optional<String> alias,
    SourceSpan span
) implements Declaration {
    public InjectionDeclaration {
        Objects.requireNonNull(
            kind,
            "Injection kind must not be null."
        );

        Objects.requireNonNull(
            modulePath,
            "Injected module path must not be null."
        );

        Objects.requireNonNull(
            selectedNames,
            "Selected injection names must not be null."
        );

        Objects.requireNonNull(
            alias,
            "Injection alias must not be null."
        );

        Objects.requireNonNull(
            span,
            "Injection declaration source span must not be null."
        );

        selectedNames = List.copyOf(selectedNames);

        for (var selectedName : selectedNames) {
            if (selectedName.isBlank()) {
                throw new IllegalArgumentException(
                    "Selected injection names must not be blank."
                );
            }
        }

        alias.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException(
                    "Injection alias must not be blank."
                );
            }
        });

        if (
            kind == InjectionKind.DIRECT
                && alias.isPresent()
        ) {
            throw new IllegalArgumentException(
                "Direct injections must not define a namespace alias."
            );
        }

        if (
            kind == InjectionKind.NAMESPACE
                && !selectedNames.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Namespace injections must not select individual names."
            );
        }
    }
}
