package io.github.stardragonstudios.sol.syntax;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InjectionDeclarationTest {
    private static final SourceSpan MODULE_SPAN =
        new SourceSpan(
            new SourcePosition(7, 1, 8),
            new SourcePosition(15, 1, 16)
        );

    private static final SourceSpan DECLARATION_SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(42, 1, 43)
        );

    private static final ModulePath MODULE_PATH =
        new ModulePath(
            List.of("std", "file"),
            MODULE_SPAN
        );

    @Test
    void definesEveryInitialInjectionKind() {
        assertEquals(
            2,
            InjectionKind.values().length
        );

        assertEquals(
            InjectionKind.DIRECT,
            InjectionKind.valueOf("DIRECT")
        );

        assertEquals(
            InjectionKind.NAMESPACE,
            InjectionKind.valueOf("NAMESPACE")
        );
    }

    @Test
    void createsDirectInjection() {
        var declaration = new InjectionDeclaration(
            InjectionKind.DIRECT,
            MODULE_PATH,
            List.of("read_text", "write_text"),
            Optional.empty(),
            DECLARATION_SPAN
        );

        assertEquals(
            InjectionKind.DIRECT,
            declaration.kind()
        );

        assertEquals(
            MODULE_PATH,
            declaration.modulePath()
        );

        assertEquals(
            List.of("read_text", "write_text"),
            declaration.selectedNames()
        );

        assertEquals(
            Optional.empty(),
            declaration.alias()
        );

        assertEquals(
            DECLARATION_SPAN,
            declaration.span()
        );
    }

    @Test
    void createsNamespaceInjectionWithAlias() {
        var declaration = new InjectionDeclaration(
            InjectionKind.NAMESPACE,
            MODULE_PATH,
            List.of(),
            Optional.of("file"),
            DECLARATION_SPAN
        );

        assertEquals(
            InjectionKind.NAMESPACE,
            declaration.kind()
        );

        assertEquals(
            Optional.of("file"),
            declaration.alias()
        );
    }

    @Test
    void defensivelyCopiesSelectedNames() {
        var names = new ArrayList<>(
            List.of("read_text", "write_text")
        );

        var declaration = new InjectionDeclaration(
            InjectionKind.DIRECT,
            MODULE_PATH,
            names,
            Optional.empty(),
            DECLARATION_SPAN
        );

        names.clear();

        assertEquals(
            List.of("read_text", "write_text"),
            declaration.selectedNames()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> declaration.selectedNames().clear()
        );
    }

    @Test
    void rejectsAliasOnDirectInjection() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new InjectionDeclaration(
                InjectionKind.DIRECT,
                MODULE_PATH,
                List.of(),
                Optional.of("file"),
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsSelectedNamesOnNamespaceInjection() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new InjectionDeclaration(
                InjectionKind.NAMESPACE,
                MODULE_PATH,
                List.of("read_text"),
                Optional.empty(),
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsBlankSelectedNames() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new InjectionDeclaration(
                InjectionKind.DIRECT,
                MODULE_PATH,
                List.of(" "),
                Optional.empty(),
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsBlankAlias() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new InjectionDeclaration(
                InjectionKind.NAMESPACE,
                MODULE_PATH,
                List.of(),
                Optional.of(" "),
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullKind() {
        assertThrows(
            NullPointerException.class,
            () -> new InjectionDeclaration(
                null,
                MODULE_PATH,
                List.of(),
                Optional.empty(),
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullModulePath() {
        assertThrows(
            NullPointerException.class,
            () -> new InjectionDeclaration(
                InjectionKind.DIRECT,
                null,
                List.of(),
                Optional.empty(),
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullSelectedNames() {
        assertThrows(
            NullPointerException.class,
            () -> new InjectionDeclaration(
                InjectionKind.DIRECT,
                MODULE_PATH,
                null,
                Optional.empty(),
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullAliasOptional() {
        assertThrows(
            NullPointerException.class,
            () -> new InjectionDeclaration(
                InjectionKind.DIRECT,
                MODULE_PATH,
                List.of(),
                null,
                DECLARATION_SPAN
            )
        );
    }

    @Test
    void rejectsNullSpan() {
        assertThrows(
            NullPointerException.class,
            () -> new InjectionDeclaration(
                InjectionKind.DIRECT,
                MODULE_PATH,
                List.of(),
                Optional.empty(),
                null
            )
        );
    }
}
