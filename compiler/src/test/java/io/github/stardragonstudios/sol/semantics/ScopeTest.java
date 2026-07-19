package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeTest {
    private static final SourceSpan SPAN =
        new SourceSpan(
            new SourcePosition(0, 1, 1),
            new SourcePosition(5, 1, 6)
        );

    private static final TypeReference TYPE =
        new TypeReference(
            "int",
            SPAN
        );

    private static FunctionSymbol functionSymbol(
        String name
    ) {
        return new FunctionSymbol(
            new FunctionDeclaration(
                List.of(),
                name,
                List.of(),
                TYPE,
                Optional.empty(),
                SPAN
            )
        );
    }

    private static ParameterSymbol parameterSymbol(
        String name
    ) {
        return new ParameterSymbol(
            new Parameter(
                name,
                TYPE,
                SPAN
            )
        );
    }

    @Test
    void definesEveryInitialScopeKind() {
        assertEquals(
            List.of(
                ScopeKind.MODULE,
                ScopeKind.FUNCTION,
                ScopeKind.BLOCK
            ),
            List.of(ScopeKind.values())
        );
    }

    @Test
    void createsRootScope() {
        var scope = new Scope(
            ScopeKind.MODULE
        );

        assertEquals(
            ScopeKind.MODULE,
            scope.kind()
        );

        assertTrue(scope.parent().isEmpty());

        assertTrue(
            scope.declaredSymbols().isEmpty()
        );
    }

    @Test
    void createsChildScope() {
        var parent = new Scope(
            ScopeKind.MODULE
        );

        var child = new Scope(
            ScopeKind.FUNCTION,
            parent
        );

        assertEquals(
            ScopeKind.FUNCTION,
            child.kind()
        );

        assertEquals(
            Optional.of(parent),
            child.parent()
        );
    }

    @Test
    void declaresSymbolsInInsertionOrder() {
        var scope = new Scope(
            ScopeKind.MODULE
        );

        var first =
            functionSymbol("first");

        var second =
            functionSymbol("second");

        assertTrue(scope.declare(first));
        assertTrue(scope.declare(second));

        assertEquals(
            List.of(first, second),
            scope.declaredSymbols()
        );
    }

    @Test
    void exposesImmutableSymbolSnapshot() {
        var scope = new Scope(
            ScopeKind.MODULE
        );

        var first =
            functionSymbol("first");

        var second =
            functionSymbol("second");

        scope.declare(first);

        var snapshot =
            scope.declaredSymbols();

        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.clear()
        );

        scope.declare(second);

        assertEquals(1, snapshot.size());

        assertEquals(
            2,
            scope.declaredSymbols().size()
        );
    }

    @Test
    void performsLocalLookup() {
        var scope = new Scope(
            ScopeKind.MODULE
        );

        var symbol =
            functionSymbol("calculate");

        scope.declare(symbol);

        assertEquals(
            Optional.of(symbol),
            scope.lookupLocal("calculate")
        );

        assertTrue(
            scope.lookupLocal("missing")
                .isEmpty()
        );
    }

    @Test
    void performsLexicalParentLookup() {
        var moduleScope = new Scope(
            ScopeKind.MODULE
        );

        var functionScope = new Scope(
            ScopeKind.FUNCTION,
            moduleScope
        );

        var blockScope = new Scope(
            ScopeKind.BLOCK,
            functionScope
        );

        var function =
            functionSymbol("calculate");

        var parameter =
            parameterSymbol("value");

        moduleScope.declare(function);
        functionScope.declare(parameter);

        assertEquals(
            Optional.of(function),
            blockScope.lookup("calculate")
        );

        assertEquals(
            Optional.of(parameter),
            blockScope.lookup("value")
        );

        assertTrue(
            blockScope.lookupLocal("value")
                .isEmpty()
        );
    }

    @Test
    void returnsEmptyForMissingSymbol() {
        var scope = new Scope(
            ScopeKind.MODULE
        );

        assertTrue(
            scope.lookup("missing").isEmpty()
        );
    }

    @Test
    void rejectsDuplicateDeclarationWithoutReplacingOriginal() {
        var scope = new Scope(
            ScopeKind.MODULE
        );

        var original =
            functionSymbol("calculate");

        var duplicate =
            parameterSymbol("calculate");

        assertTrue(scope.declare(original));
        assertFalse(scope.declare(duplicate));

        var resolved = scope
            .lookupLocal("calculate")
            .orElseThrow();

        assertSame(original, resolved);

        assertEquals(
            List.of(original),
            scope.declaredSymbols()
        );
    }

    @Test
    void allowsShadowingInChildScope() {
        var parent = new Scope(
            ScopeKind.MODULE
        );

        var child = new Scope(
            ScopeKind.FUNCTION,
            parent
        );

        var outer =
            functionSymbol("value");

        var inner =
            parameterSymbol("value");

        assertTrue(parent.declare(outer));
        assertTrue(child.declare(inner));

        assertSame(
            inner,
            child.lookup("value").orElseThrow()
        );

        assertSame(
            outer,
            parent.lookup("value").orElseThrow()
        );
    }

    @Test
    void rejectsInvalidConstructionAndDeclarations() {
        assertThrows(
            NullPointerException.class,
            () -> new Scope(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> new Scope(
                ScopeKind.BLOCK,
                null
            )
        );

        var scope = new Scope(
            ScopeKind.MODULE
        );

        assertThrows(
            NullPointerException.class,
            () -> scope.declare(null)
        );
    }

    @Test
    void rejectsInvalidLookupNames() {
        var scope = new Scope(
            ScopeKind.MODULE
        );

        assertThrows(
            NullPointerException.class,
            () -> scope.lookup(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> scope.lookupLocal(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> scope.lookup(" ")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> scope.lookupLocal(" ")
        );
    }
}
