package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompilationUnitTest {
    private static final SourceSpan SPAN = new SourceSpan(
        new SourcePosition(0, 1, 1),
        new SourcePosition(4, 1, 5)
    );

    @Test
    void createsCompilationUnitWithExpectedValues() {
        var declaration = new TestDeclaration(SPAN);

        var unit = new CompilationUnit(
            List.of(declaration),
            SPAN
        );

        assertEquals(
            List.of(declaration),
            unit.declarations()
        );
        assertEquals(SPAN, unit.span());
    }

    @Test
    void defensivelyCopiesDeclarations() {
        var declarations = new ArrayList<Declaration>();
        declarations.add(new TestDeclaration(SPAN));

        var unit = new CompilationUnit(
            declarations,
            SPAN
        );

        declarations.clear();

        assertEquals(1, unit.declarations().size());

        assertThrows(
            UnsupportedOperationException.class,
            () -> unit.declarations().clear()
        );
    }

    @Test
    void rejectsNullDeclarations() {
        assertThrows(
            NullPointerException.class,
            () -> new CompilationUnit(null, SPAN)
        );
    }

    @Test
    void rejectsNullDeclarationElements() {
        assertThrows(
            NullPointerException.class,
            () -> new CompilationUnit(
                java.util.Arrays.asList(
                    new TestDeclaration(SPAN),
                    null
                ),
                SPAN
            )
        );
    }

    @Test
    void rejectsNullSourceSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new CompilationUnit(
                List.of(),
                null
            )
        );
    }

    private record TestDeclaration(
        SourceSpan span
    ) implements Declaration {
    }
}
