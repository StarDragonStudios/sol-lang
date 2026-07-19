package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssignmentStatementTest {
    private static final SourceSpan TARGET_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(7, 1, 8)
        );

    private static final SourceSpan VALUE_SPAN =
        new SourceSpan(
            new SourcePosition(10, 1, 11),
            new SourcePosition(12, 1, 13)
        );

    private static final SourceSpan ASSIGNMENT_SPAN =
        new SourceSpan(
            TARGET_SPAN.start(),
            VALUE_SPAN.end()
        );

    private static final NameExpression TARGET =
        new NameExpression(
            "counter",
            TARGET_SPAN
        );

    private static final LiteralExpression VALUE =
        new LiteralExpression(
            LiteralKind.INTEGER,
            "42",
            VALUE_SPAN
        );

    @Test
    void createsAssignmentStatementWithExpectedValues() {
        var assignment = new AssignmentStatement(
            TARGET,
            VALUE,
            ASSIGNMENT_SPAN
        );

        assertEquals(TARGET, assignment.target());
        assertEquals(VALUE, assignment.value());
        assertEquals(
            ASSIGNMENT_SPAN,
            assignment.span()
        );
    }

    @Test
    void rejectsNullTarget() {
        assertThrows(
            NullPointerException.class,
            () -> new AssignmentStatement(
                null,
                VALUE,
                ASSIGNMENT_SPAN
            )
        );
    }

    @Test
    void rejectsNullValue() {
        assertThrows(
            NullPointerException.class,
            () -> new AssignmentStatement(
                TARGET,
                null,
                ASSIGNMENT_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new AssignmentStatement(
                TARGET,
                VALUE,
                null
            )
        );
    }
}
