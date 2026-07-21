package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.InjectionDeclaration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleModelTest {
    @Test
    void createsSingleAndMultiSegmentModuleNames() {
        var single = new ModuleName(List.of("application"));
        var multiple = new ModuleName(List.of("std", "console"));

        assertEquals("application", single.qualifiedName());
        assertEquals("application", single.simpleName());
        assertEquals("std.console", multiple.qualifiedName());
        assertEquals("console", multiple.simpleName());
        assertEquals("std.console", multiple.toString());
    }

    @Test
    void moduleNamesUseValueEqualityAndDefensiveCopying() {
        var segments = new ArrayList<>(List.of("std", "console"));
        var first = new ModuleName(segments);
        var second = new ModuleName(List.of("std", "console"));

        segments.set(1, "file");

        assertEquals(first, second);
        assertEquals(List.of("std", "console"), first.segments());
        assertThrows(UnsupportedOperationException.class, () -> first.segments().add("extra"));
    }

    @Test
    void rejectsInvalidModuleNames() {
        assertThrows(NullPointerException.class, () -> new ModuleName(null));
        assertThrows(IllegalArgumentException.class, () -> new ModuleName(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ModuleName(List.of("")));
        assertThrows(IllegalArgumentException.class, () -> new ModuleName(List.of("std", " ")));

        var segments = new ArrayList<String>();
        segments.add("std");
        segments.add(null);

        assertThrows(NullPointerException.class, () -> new ModuleName(segments));
    }

    @Test
    void createsSourceAndModuleSymbols() {
        var unit = Parser.parse(Lexer.scan(
            """
            @fn exported() -> void
            """
        ));

        var name = new ModuleName(List.of("sample"));
        var sourceModule = new SourceModule(name, unit);
        var module = new ModuleSymbol(sourceModule.name(), sourceModule.unit());
        var declaration = assertInstanceOf(FunctionDeclaration.class, unit.declarations().getFirst());
        var function = new FunctionSymbol(declaration);

        assertTrue(module.declareExport(function));
        assertFalse(module.declareExport(function));
        assertSame(function, module.exportedFunction("exported").orElseThrow());
        assertEquals(List.of(function), module.exportedFunctions());
        assertSame(
            function,
            module.scope()
                .lookupLocal("exported")
                .orElseThrow()
        );
    }

    @Test
    void createsNamespaceSymbols() {
        var targetUnit = Parser.parse(Lexer.scan(
            """
            @fn print_line(value: string) -> void
            """
        ));

        var receivingUnit = Parser.parse(Lexer.scan(
            """
            inject namespace std.console as io
            """
        ));

        var injection = assertInstanceOf(InjectionDeclaration.class, receivingUnit.declarations().getFirst());
        var targetModule = new ModuleSymbol(new ModuleName(List.of("std", "console")), targetUnit);
        var namespace = new NamespaceSymbol("io", targetModule, injection);

        assertEquals(SymbolKind.MODULE_NAMESPACE, namespace.kind());
        assertEquals("io", namespace.name());
        assertSame(targetModule, namespace.targetModule());
        assertEquals(injection.span(), namespace.span());
    }

    @Test
    void rejectsNullModuleComponents() {
        var unit = Parser.parse(Lexer.scan(
            """
            @fn external() -> void
            """
        ));

        var name = new ModuleName(List.of("sample"));

        assertThrows(NullPointerException.class, () -> new SourceModule(null, unit));
        assertThrows(NullPointerException.class, () -> new SourceModule(name, null));
        assertThrows(NullPointerException.class, () -> new ModuleSymbol(null, unit));
        assertThrows(NullPointerException.class, () -> new ModuleSymbol(name, null));
    }

    @Test
    void analyzesModulesInInputOrder() {
        var first = new SourceModule(
            new ModuleName(
                List.of("first")
            ),
            Parser.parse(Lexer.scan(
                """
                @fn one() -> void
                """
            ))
        );

        var second = new SourceModule(
            new ModuleName(
                List.of("second")
            ),
            Parser.parse(Lexer.scan(
                """
                @fn two() -> void
                """
            ))
        );

        var result = SemanticAnalyzer.analyzeModules(
            List.of(first, second)
        );

        assertEquals(
            List.of(
                first.name(),
                second.name()
            ),
            result.moduleNames()
        );

        assertTrue(
            result.analysisOf(first.name())
                .isPresent()
        );

        assertTrue(
            result.analysisOf(second.name())
                .isPresent()
        );

        assertTrue(
            result.analysisOf(
                new ModuleName(
                    List.of("missing")
                )
            ).isEmpty()
        );

        assertThrows(
            NullPointerException.class,
            () -> result.analysisOf(null)
        );

        assertThrows(
            NullPointerException.class,
            () -> result.moduleOf(null)
        );
    }

    @Test
    void rejectsDuplicateInputModuleNames() {
        var first = new SourceModule(
            new ModuleName(
                List.of("application")
            ),
            Parser.parse(Lexer.scan(
                """
                @fn first() -> void
                """
            ))
        );

        var duplicate = new SourceModule(
            new ModuleName(
                List.of("application")
            ),
            Parser.parse(Lexer.scan(
                """
                @fn second() -> void
                """
            ))
        );

        var exception = assertThrows(
            IllegalArgumentException.class,
            () -> SemanticAnalyzer.analyzeModules(
                List.of(first, duplicate)
            )
        );

        assertEquals(
            "Duplicate source module 'application'.",
            exception.getMessage()
        );
    }
}
