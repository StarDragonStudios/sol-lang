package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolModelTest {
    private static final SourceSpan SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(10, 1, 11)
        );

    private static final TypeReference INTEGER_TYPE =
        new TypeReference(
            "int",
            SPAN
        );

    private static final Parameter PARAMETER =
        new Parameter(
            "value",
            INTEGER_TYPE,
            SPAN
        );

    private static final NameExpression INITIALIZER =
        new NameExpression(
            "initial",
            SPAN
        );

    private static final VariableDeclarationStatement
        LOCAL_DECLARATION =
        new VariableDeclarationStatement(
            VariableDeclarationKind.MUTABLE_LET,
            "counter",
            INTEGER_TYPE,
            INITIALIZER,
            SPAN
        );

    private static final FunctionDeclaration
        FUNCTION_DEFINITION =
        new FunctionDeclaration(
            List.of(),
            "calculate",
            List.of(PARAMETER),
            INTEGER_TYPE,
            Optional.of(
                new Block(
                    List.of(),
                    SPAN
                )
            ),
            SPAN
        );

    private static final FunctionDeclaration
        BODYLESS_FUNCTION =
        new FunctionDeclaration(
            List.of(),
            "external_calculate",
            List.of(PARAMETER),
            INTEGER_TYPE,
            Optional.empty(),
            SPAN
        );

    private static final ModulePath MODULE_PATH =
        new ModulePath(
            List.of("std", "console"),
            SPAN
        );

    private static final InjectionDeclaration
        DIRECT_INJECTION =
        new InjectionDeclaration(
            InjectionKind.DIRECT,
            MODULE_PATH,
            List.of("print_line"),
            Optional.empty(),
            SPAN
        );

    private static final InjectionDeclaration
        NAMESPACE_INJECTION =
        new InjectionDeclaration(
            InjectionKind.NAMESPACE,
            MODULE_PATH,
            List.of(),
            Optional.of("io"),
            SPAN
        );

    @Test
    void definesEveryInitialSymbolKind() {
        assertEquals(
            List.of(
                SymbolKind.FUNCTION,
                SymbolKind.PARAMETER,
                SymbolKind.LOCAL_VARIABLE,
                SymbolKind.IMPORTED_NAME,
                SymbolKind.MODULE_NAMESPACE
            ),
            List.of(SymbolKind.values())
        );
    }

    @Test
    void createsFunctionSymbol() {
        var symbol = new FunctionSymbol(
            FUNCTION_DEFINITION
        );

        assertEquals(
            SymbolKind.FUNCTION,
            symbol.kind()
        );

        assertEquals(
            "calculate",
            symbol.name()
        );

        assertSame(
            FUNCTION_DEFINITION,
            symbol.declaration()
        );

        assertEquals(
            SPAN,
            symbol.span()
        );

        assertTrue(
            symbol.declaration()
                .body()
                .isPresent()
        );
    }

    @Test
    void preservesBodylessFunctionDeclaration() {
        var symbol = new FunctionSymbol(
            BODYLESS_FUNCTION
        );

        assertTrue(
            symbol.declaration()
                .body()
                .isEmpty()
        );
    }

    @Test
    void createsParameterSymbol() {
        var symbol = new ParameterSymbol(
            PARAMETER
        );

        assertEquals(
            SymbolKind.PARAMETER,
            symbol.kind()
        );

        assertEquals("value", symbol.name());
        assertEquals(INTEGER_TYPE, symbol.type());
        assertEquals(SPAN, symbol.span());
    }

    @Test
    void createsLocalVariableSymbol() {
        var symbol = new LocalVariableSymbol(
            LOCAL_DECLARATION
        );

        assertEquals(
            SymbolKind.LOCAL_VARIABLE,
            symbol.kind()
        );

        assertEquals(
            "counter",
            symbol.name()
        );

        assertEquals(
            INTEGER_TYPE,
            symbol.type()
        );

        assertEquals(
            VariableDeclarationKind.MUTABLE_LET,
            symbol.declarationKind()
        );

        assertEquals(SPAN, symbol.span());
    }

    @Test
    void createsImportedNameSymbol() {
        var symbol = new ImportedNameSymbol(
            "print_line",
            DIRECT_INJECTION
        );

        assertEquals(
            SymbolKind.IMPORTED_NAME,
            symbol.kind()
        );

        assertEquals(
            "print_line",
            symbol.name()
        );

        assertEquals(
            MODULE_PATH,
            symbol.modulePath()
        );

        assertEquals(SPAN, symbol.span());
    }

    @Test
    void createsModuleNamespaceSymbol() {
        var symbol =
            new ModuleNamespaceSymbol(
                "io",
                NAMESPACE_INJECTION
            );

        assertEquals(
            SymbolKind.MODULE_NAMESPACE,
            symbol.kind()
        );

        assertEquals("io", symbol.name());

        assertEquals(
            MODULE_PATH,
            symbol.modulePath()
        );

        assertEquals(
            Optional.of("io"),
            symbol.explicitAlias()
        );

        assertEquals(SPAN, symbol.span());
    }

    @Test
    void rejectsNullSyntaxDeclarations() {
        assertThrows(
            NullPointerException.class,
            () -> new FunctionSymbol(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> new ParameterSymbol(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> new LocalVariableSymbol(null)
        );
    }

    @Test
    void rejectsInvalidImportedNameSymbols() {
        assertThrows(
            NullPointerException.class,
            () -> new ImportedNameSymbol(
                null,
                DIRECT_INJECTION
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ImportedNameSymbol(
                " ",
                DIRECT_INJECTION
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new ImportedNameSymbol(
                "print_line",
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ImportedNameSymbol(
                "print_line",
                NAMESPACE_INJECTION
            )
        );
    }

    @Test
    void rejectsInvalidModuleNamespaceSymbols() {
        assertThrows(
            NullPointerException.class,
            () -> new ModuleNamespaceSymbol(
                null,
                NAMESPACE_INJECTION
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleNamespaceSymbol(
                " ",
                NAMESPACE_INJECTION
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new ModuleNamespaceSymbol(
                "io",
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ModuleNamespaceSymbol(
                "io",
                DIRECT_INJECTION
            )
        );
    }
}
