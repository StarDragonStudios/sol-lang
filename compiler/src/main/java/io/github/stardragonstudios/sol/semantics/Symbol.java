package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourceSpan;

public interface Symbol {
    SymbolKind kind();

    String name();

    SourceSpan span();
}
