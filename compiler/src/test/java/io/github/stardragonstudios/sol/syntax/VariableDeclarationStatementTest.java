package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VariableDeclarationStatementTest {
    private static final SourceSpan TYPE_SPAN =
        new SourceSpan(
            new SourcePosition(14, 1, 15),
            new SourcePosition(17, 1, 18)
        );

    private static final SourceSpan INITIALIZER_SPAN =
        new SourceSpan(
            new SourcePosition(20, 1, 21),
            new SourcePosition(22, 1, 23)
        );

    private static final SourceSpan DECLARATION_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            INITIALIZER_SPAN.end()
        );

    private static final TypeReference TYPE =
        new TypeReference(
            "int",
            TYPE_SPAN
        );

    private static final LiteralExpression INITIALIZER =
        new LiteralExpression(
            LiteralKind.INTEGER,
            "42",
            INITIALIZER_SPAN
        );

    @Test
    void definesEveryInitialDeclarationKind() {
        assertEquals(
            3,
            VariableDeclarationKind.values().length
        );

        assertEquals(
            VariableDeclarationKind.CONST,
            VariableDeclarationKind.valueOf("CONST")
        );

        assertEquals(
            VariableDeclarationKind.LET,
            VariableDeclarationKind.valueOf("LET")
        );

        assertEquals(
            VariableDeclarationKind.MUTABLE_LET,
            VariableDeclarationKind.valueOf("MUTABLE_LET")
        );
    }

    @Test
    void createsVariableDeclarationWithExpectedValues() {
        var declaration =
            new VariableDeclarationStatement(
                VariableDeclarationKind.CONST,
                "answer",
                TYPE,
                INITIALIZER,
                DECLARATION_SPAN
            );

        assertEquals(
            VariableDeclarationKind.CONST,
            declaration.kind()
        );

        assertEquals("answer", declaration.name());
        assertEquals(TYPE, declaration.type());
        assertEquals(
            INITIALIZER,
            declaration.initializer()
        );
        assertEquals(
            DECLARATION_SPAN,
            declaration.span()
        );
    }

    @Test
    void rejectsNullKind() {
        assertThrows(
            NullPointerException.class,
            () -> new VariableDeclarationStatement(
                null,
                "answer",
                TYPE,
                INITIALIZER,
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullName() {
        assertThrows(
            NullPointerException.class,
            () -> new VariableDeclarationStatement(
                VariableDeclarationKind.LET,
                null,
                TYPE,
                INITIALIZER,
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new VariableDeclarationStatement(
                VariableDeclarationKind.LET,
                "   ",
                TYPE,
                INITIALIZER,
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullType() {
        assertThrows(
            NullPointerException.class,
            () -> new VariableDeclarationStatement(
                VariableDeclarationKind.LET,
                "answer",
                null,
                INITIALIZER,
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullInitializer() {
        assertThrows(
            NullPointerException.class,
            () -> new VariableDeclarationStatement(
                VariableDeclarationKind.LET,
                "answer",
                TYPE,
                null,
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new VariableDeclarationStatement(
                VariableDeclarationKind.LET,
                "answer",
                TYPE,
                INITIALIZER,
                null
            )
        );
    }
}
