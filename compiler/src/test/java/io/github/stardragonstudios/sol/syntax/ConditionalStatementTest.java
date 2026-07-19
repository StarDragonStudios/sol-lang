package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionalStatementTest {
    private static final SourceSpan CONDITION_SPAN =
        new SourceSpan(
            new SourcePosition(3, 1, 4),
            new SourcePosition(10, 1, 11)
        );

    private static final SourceSpan THEN_BLOCK_SPAN =
        new SourceSpan(
            new SourcePosition(16, 2, 1),
            new SourcePosition(25, 3, 1)
        );

    private static final SourceSpan ELSE_BLOCK_SPAN =
        new SourceSpan(
            new SourcePosition(30, 4, 1),
            new SourcePosition(39, 5, 1)
        );

    private static final SourceSpan CONDITIONAL_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(42, 5, 4)
        );

    private static final NameExpression CONDITION =
        new NameExpression(
            "enabled",
            CONDITION_SPAN
        );

    private static final Block THEN_BLOCK =
        new Block(
            List.of(),
            THEN_BLOCK_SPAN
        );

    private static final Block ELSE_BLOCK =
        new Block(
            List.of(),
            ELSE_BLOCK_SPAN
        );

    @Test
    void createsConditionalWithoutElseBlock() {
        var conditional = new ConditionalStatement(
            CONDITION,
            THEN_BLOCK,
            Optional.empty(),
            CONDITIONAL_SPAN
        );

        assertEquals(
            CONDITION,
            conditional.condition()
        );

        assertEquals(
            THEN_BLOCK,
            conditional.thenBlock()
        );

        assertEquals(
            Optional.empty(),
            conditional.elseBlock()
        );

        assertEquals(
            CONDITIONAL_SPAN,
            conditional.span()
        );
    }

    @Test
    void createsConditionalWithElseBlock() {
        var conditional = new ConditionalStatement(
            CONDITION,
            THEN_BLOCK,
            Optional.of(ELSE_BLOCK),
            CONDITIONAL_SPAN
        );

        assertEquals(
            Optional.of(ELSE_BLOCK),
            conditional.elseBlock()
        );
    }

    @Test
    void rejectsNullCondition() {
        assertThrows(
            NullPointerException.class,
            () -> new ConditionalStatement(
                null,
                THEN_BLOCK,
                Optional.empty(),
                CONDITIONAL_SPAN
            )
        );
    }

    @Test
    void rejectsNullThenBlock() {
        assertThrows(
            NullPointerException.class,
            () -> new ConditionalStatement(
                CONDITION,
                null,
                Optional.empty(),
                CONDITIONAL_SPAN
            )
        );
    }

    @Test
    void rejectsNullElseBlockOptional() {
        assertThrows(
            NullPointerException.class,
            () -> new ConditionalStatement(
                CONDITION,
                THEN_BLOCK,
                null,
                CONDITIONAL_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new ConditionalStatement(
                CONDITION,
                THEN_BLOCK,
                Optional.empty(),
                null
            )
        );
    }
}
