package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.util.List;
import java.util.Objects;

public record CompilationUnit(List<Declaration> declarations, SourceSpan span) implements SyntaxNode {
    public CompilationUnit {
        Objects.requireNonNull(declarations, "Compilation unit declarations must not be null.");
        Objects.requireNonNull(span, "Compilation unit source span must not be null.");

        declarations = List.copyOf(declarations);
    }
}
