package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionDeclarationTest {
    private static final SourceSpan SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(10, 2, 4)
        );

    private static final TypeReference RETURN_TYPE =
        new TypeReference(
            "void",
            SPAN
        );

    private static final Block BODY =
        new Block(
            List.of(),
            SPAN
        );

    private static final Parameter PARAMETER =
        new Parameter(
            "value",
            RETURN_TYPE,
            SPAN
        );

    private static final Annotation ANNOTATION =
        new Annotation(
            "init",
            SPAN
        );

    @Test
    void createsFunctionDefinitionWithExpectedValues() {
        var function = new FunctionDeclaration(
            List.of(ANNOTATION),
            "initialize",
            List.of(PARAMETER),
            RETURN_TYPE,
            Optional.of(BODY),
            SPAN
        );

        assertEquals(
            List.of(ANNOTATION),
            function.annotations()
        );

        assertEquals("initialize", function.name());

        assertEquals(
            List.of(PARAMETER),
            function.parameters()
        );

        assertEquals(
            RETURN_TYPE,
            function.returnType()
        );

        assertEquals(
            Optional.of(BODY),
            function.body()
        );

        assertEquals(SPAN, function.span());
    }

    @Test
    void createsBodylessFunctionDeclaration() {
        var function = new FunctionDeclaration(
            List.of(),
            "print_line",
            List.of(PARAMETER),
            RETURN_TYPE,
            Optional.empty(),
            SPAN
        );

        assertEquals(
            Optional.empty(),
            function.body()
        );
    }

    @Test
    void defensivelyCopiesAnnotations() {
        var annotations =
            new ArrayList<Annotation>();

        annotations.add(ANNOTATION);

        var function = new FunctionDeclaration(
            annotations,
            "initialize",
            List.of(),
            RETURN_TYPE,
            Optional.of(BODY),
            SPAN
        );

        annotations.clear();

        assertEquals(
            List.of(ANNOTATION),
            function.annotations()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> function.annotations().clear()
        );
    }

    @Test
    void defensivelyCopiesParameters() {
        var parameters =
            new ArrayList<Parameter>();

        parameters.add(PARAMETER);

        var function = new FunctionDeclaration(
            List.of(),
            "initialize",
            parameters,
            RETURN_TYPE,
            Optional.of(BODY),
            SPAN
        );

        parameters.clear();

        assertEquals(
            List.of(PARAMETER),
            function.parameters()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> function.parameters().clear()
        );
    }

    @Test
    void rejectsNullAnnotations() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                null,
                "initialize",
                List.of(),
                RETURN_TYPE,
                Optional.of(BODY),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullAnnotationElements() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                java.util.Arrays.asList(
                    ANNOTATION,
                    null
                ),
                "initialize",
                List.of(),
                RETURN_TYPE,
                Optional.of(BODY),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullName() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                List.of(),
                null,
                List.of(),
                RETURN_TYPE,
                Optional.of(BODY),
                SPAN
            )
        );
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionDeclaration(
                List.of(),
                " ",
                List.of(),
                RETURN_TYPE,
                Optional.of(BODY),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullParameters() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                List.of(),
                "initialize",
                null,
                RETURN_TYPE,
                Optional.of(BODY),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullParameterElements() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                List.of(),
                "initialize",
                java.util.Arrays.asList(
                    PARAMETER,
                    null
                ),
                RETURN_TYPE,
                Optional.of(BODY),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullReturnType() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                List.of(),
                "initialize",
                List.of(),
                null,
                Optional.of(BODY),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullBodyOptional() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionDeclaration(
                List.of(),
                "initialize",
                List.of(),
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
                List.of(),
                "initialize",
                List.of(),
                RETURN_TYPE,
                Optional.of(BODY),
                null
            )
        );
    }
}
