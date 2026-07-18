package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CallExpressionTest {
    private static final SourceSpan CALLEE_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(3, 1, 4)
        );

    private static final SourceSpan ARGUMENT_SPAN =
        new SourceSpan(
            new SourcePosition(4, 1, 5),
            new SourcePosition(6, 1, 7)
        );

    private static final SourceSpan CALL_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(7, 1, 8)
        );

    private static final NameExpression CALLEE =
        new NameExpression(
            "add",
            CALLEE_SPAN
        );

    private static final LiteralExpression ARGUMENT =
        new LiteralExpression(
            LiteralKind.INTEGER,
            "42",
            ARGUMENT_SPAN
        );

    @Test
    void createsCallExpressionWithExpectedValues() {
        var call = new CallExpression(
            CALLEE,
            List.of(ARGUMENT),
            CALL_SPAN
        );

        assertEquals(CALLEE, call.callee());
        assertEquals(
            List.of(ARGUMENT),
            call.arguments()
        );
        assertEquals(CALL_SPAN, call.span());
    }

    @Test
    void createsCallWithoutArguments() {
        var call = new CallExpression(
            CALLEE,
            List.of(),
            CALL_SPAN
        );

        assertEquals(List.of(), call.arguments());
    }

    @Test
    void defensivelyCopiesArguments() {
        var arguments = new ArrayList<Expression>();
        arguments.add(ARGUMENT);

        var call = new CallExpression(
            CALLEE,
            arguments,
            CALL_SPAN
        );

        arguments.clear();

        assertEquals(1, call.arguments().size());

        assertThrows(
            UnsupportedOperationException.class,
            () -> call.arguments().clear()
        );
    }

    @Test
    void rejectsNullArgumentElements() {
        assertThrows(
            NullPointerException.class,
            () -> new CallExpression(
                CALLEE,
                java.util.Arrays.asList(
                    ARGUMENT,
                    null
                ),
                CALL_SPAN
            )
        );
    }

    @Test
    void rejectsNullCallee() {
        assertThrows(
            NullPointerException.class,
            () -> new CallExpression(
                null,
                List.of(),
                CALL_SPAN
            )
        );
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(
            NullPointerException.class,
            () -> new CallExpression(
                CALLEE,
                null,
                CALL_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new CallExpression(
                CALLEE,
                List.of(),
                null
            )
        );
    }
}
