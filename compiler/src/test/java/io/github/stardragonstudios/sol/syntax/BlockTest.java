package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockTest {
    private static final SourceSpan SPAN = new SourceSpan(
        new SourcePosition(0, 1, 1),
        new SourcePosition(3, 1, 4)
    );

    @Test
    void createsEmptyBlock() {
        var block = new Block(
            List.of(),
            SPAN
        );

        assertEquals(List.of(), block.statements());
        assertEquals(SPAN, block.span());
    }

    @Test
    void defensivelyCopiesStatements() {
        var statements = new ArrayList<Statement>();
        statements.add(new TestStatement(SPAN));

        var block = new Block(
            statements,
            SPAN
        );

        statements.clear();

        assertEquals(1, block.statements().size());

        assertThrows(
            UnsupportedOperationException.class,
            () -> block.statements().clear()
        );
    }

    @Test
    void rejectsNullStatements() {
        assertThrows(
            NullPointerException.class,
            () -> new Block(null, SPAN)
        );
    }

    @Test
    void rejectsNullStatementElements() {
        assertThrows(
            NullPointerException.class,
            () -> new Block(
                java.util.Arrays.asList(
                    new TestStatement(SPAN),
                    null
                ),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new Block(List.of(), null)
        );
    }

    private record TestStatement(
        SourceSpan span
    ) implements Statement {
    }
}
