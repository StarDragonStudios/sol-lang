package io.github.stardragonstudios.sol.lexer;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TokenTest {
    @Test
    void storesTokenData() {
        var span = new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(2, 1, 3)
        );

        var token = new Token(TokenKind.FN, "fn", span);

        assertEquals(TokenKind.FN, token.kind());
        assertEquals("fn", token.lexeme());
        assertEquals(span, token.span());
    }

    @Test
    void supportsCoreSolTokenKinds() {
        assertEquals("FN", TokenKind.FN.name());
        assertEquals("AT", TokenKind.AT.name());
        assertEquals("ARROW", TokenKind.ARROW.name());
        assertEquals("NEWLINE", TokenKind.NEWLINE.name());
        assertEquals("EOF", TokenKind.EOF.name());
    }
}
