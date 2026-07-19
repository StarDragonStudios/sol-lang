package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhileStatementTest {
    private static final SourceSpan CONDITION_SPAN =
        new SourceSpan(
            new SourcePosition(6, 1, 7),
            new SourcePosition(13, 1, 14)
        );

    private static final SourceSpan BODY_SPAN =
        new SourceSpan(
            new SourcePosition(17, 2, 1),
            new SourcePosition(28, 3, 1)
        );

    private static final SourceSpan WHILE_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(31, 3, 4)
        );

    private static final NameExpression CONDITION =
        new NameExpression(
            "running",
            CONDITION_SPAN
        );

    private static final Block BODY =
        new Block(
            List.of(),
            BODY_SPAN
        );

    @Test
    void createsWhileStatementWithExpectedValues() {
        var statement = new WhileStatement(
            CONDITION,
            BODY,
            WHILE_SPAN
        );

        assertEquals(
            CONDITION,
            statement.condition()
        );

        assertEquals(
            BODY,
            statement.body()
        );

        assertEquals(
            WHILE_SPAN,
            statement.span()
        );
    }

    @Test
    void rejectsNullCondition() {
        assertThrows(
            NullPointerException.class,
            () -> new WhileStatement(
                null,
                BODY,
                WHILE_SPAN
            )
        );
    }

    @Test
    void rejectsNullBody() {
        assertThrows(
            NullPointerException.class,
            () -> new WhileStatement(
                CONDITION,
                null,
                WHILE_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new WhileStatement(
                CONDITION,
                BODY,
                null
            )
        );
    }
}
