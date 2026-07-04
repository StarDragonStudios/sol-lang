package io.github.stardragonstudios.sol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

final class SolCompilerTest {
    @Test
    void startsWithoutError() {
        assertDoesNotThrow(() -> SolCompiler.main(new String[0]));
    }
}
