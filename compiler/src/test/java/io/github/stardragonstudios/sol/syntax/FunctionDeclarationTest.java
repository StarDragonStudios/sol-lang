package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionDeclarationTest {
    private static final SourceSpan SPAN = new SourceSpan(
        new SourcePosition(0, 1, 1),
        new SourcePosition(10, 2, 4)
    );

    private static final TypeReference RETURN_TYPE =
        new TypeReference("void", SPAN);

    private static final Block BODY =
        new Block(List.of(), SPAN);

    @Test
    void createsFunctionDeclarationWithExpectedValues() {
        var function = new FunctionDeclaration(
            "initialize",
            RETURN_TYPE,
            BODY,
            SPAN
        );

        assertEquals("initialize", function.name());
        assertEquals(RETURN_TYPE, function.returnType());
        assertEquals(BODY, function.body());
        assertEquals(SPAN, function.span());
    }

    @Test
    void rejectsNullName() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                null,
                RETURN_TYPE,
                BODY,
                SPAN
            )
        );
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionDeclaration(
                " ",
                RETURN_TYPE,
                BODY,
                SPAN
            )
        );
    }

    @Test
    void rejectsNullReturnType() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                "initialize",
                null,
                BODY,
                SPAN
            )
        );
    }

    @Test
    void rejectsNullBody() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                "initialize",
                RETURN_TYPE,
                null,
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                "initialize",
                RETURN_TYPE,
                BODY,
                null
            )
        );
    }
}
