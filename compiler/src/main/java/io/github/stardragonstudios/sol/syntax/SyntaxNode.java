package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourceSpan;

public interface SyntaxNode {
    SourceSpan span();
}
