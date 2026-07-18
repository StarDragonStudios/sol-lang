package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnStatementTest {
    private static final SourceSpan SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(9, 1, 10)
        );

    private static final LiteralExpression EXPRESSION =
        new LiteralExpression(
            LiteralKind.INTEGER,
            "42",
            SPAN
        );

    @Test
    void createsReturnStatementWithExpression() {
        var statement = new ReturnStatement(
            Optional.of(EXPRESSION),
            SPAN
        );

        assertEquals(
            Optional.of(EXPRESSION),
            statement.expression()
        );
        assertEquals(SPAN, statement.span());
    }

    @Test
    void createsBareReturnStatement() {
        var statement = new ReturnStatement(
            Optional.empty(),
            SPAN
        );

        assertTrue(statement.expression().isEmpty());
    }

    @Test
    void rejectsNullExpressionOptional() {
        assertThrows(
            NullPointerException.class,
            () -> new ReturnStatement(
                null,
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new ReturnStatement(
                Optional.empty(),
                null
            )
        );
    }
}
