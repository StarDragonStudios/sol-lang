package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.*;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrProgramLowererTest {
    @Test
    void lowersLibraryModulesAndFunctionsDeterministically() {
        var firstName = new ModuleName(List.of("first"));
        var secondName = new ModuleName(List.of("second"));

        var semanticProgram = SemanticAnalyzer.analyzeModules(
            List.of(
                sourceModule(
                    firstName,
                    """
                    @fn external(value: int) -> int

                    fn calculate() -> int
                        return 1 + 2
                    end
                    """
                ),
                sourceModule(
                    secondName,
                    """
                    fn identity(value: int) -> int
                        return value
                    end
                    """
                )
            )
        );

        var lowered = IrProgramLowerer.lower(semanticProgram);

        assertFalse(lowered.hasEntryPoint());

        assertEquals(
            List.of(
                "first",
                "second"
            ),
            lowered.modules()
                .stream()
                .map(IrModule::name)
                .map(IrModuleName::qualifiedName)
                .toList()
        );

        assertEquals(
            List.of(
                "external",
                "calculate"
            ),
            lowered.modules()
                .getFirst()
                .functions()
                .stream()
                .map(IrFunction::name)
                .toList()
        );

        assertEquals(
            new IrFunctionId(0),
            lowered.modules().getFirst().functions().getFirst().id()
        );

        assertEquals(
            new IrFunctionId(1),
            lowered.modules().getFirst().functions().get(1).id()
        );

        assertEquals(
            new IrFunctionId(2),
            lowered.modules().get(1).functions().getFirst().id()
        );

        var calculate =
            lowered.modules().getFirst().functions().get(1);

        assertEquals(
            1,
            calculate.entryBlock().orElseThrow().instructions().size()
        );

        assertInstanceOf(
            IrBinaryInstruction.class,
            calculate.entryBlock().orElseThrow().instructions().getFirst()
        );
    }

    @Test
    void retainsCanonicalExecutableEntryPoint() {
        var name = new ModuleName(List.of("application"));

        var semanticProgram = SemanticAnalyzer.analyzeProgram(
            List.of(
                sourceModule(
                    name,
                    """
                    fn helper() -> int
                        return 1
                    end

                    @init
                    fn start() -> int
                        return helper
                    end
                    """
                )
            )
        );

        /*
         * Calls are outside issue #85, so use a parameter-free
         * expression supported by the current subset.
         */
        semanticProgram = SemanticAnalyzer.analyzeProgram(
            List.of(
                sourceModule(
                    name,
                    """
                    fn helper() -> int
                        return 1
                    end

                    @init
                    fn start() -> int
                        return 0
                    end
                    """
                )
            )
        );

        var lowered = IrProgramLowerer.lower(semanticProgram);

        assertTrue(lowered.hasEntryPoint());

        var module = lowered.modules().getFirst();
        var function = module.function("start").orElseThrow();

        assertSame(module, lowered.entryModule().orElseThrow());
        assertSame(function, lowered.entryFunction().orElseThrow());
        assertTrue(function.hasBody());

        assertInstanceOf(
            IrReturnTerminator.class,
            function.entryBlock().orElseThrow().terminator()
        );
    }

    @Test
    void producesEqualOutputForRepeatedLowering() {
        var name = new ModuleName(List.of("deterministic"));

        var semanticProgram = SemanticAnalyzer.analyzeModules(
            List.of(
                sourceModule(
                    name,
                    """
                    fn calculate(left: int, right: int) -> int
                        return left + right * 2
                    end
                    """
                )
            )
        );

        assertEquals(IrProgramLowerer.lower(semanticProgram), IrProgramLowerer.lower(semanticProgram));
    }

    @Test
    void rejectsModuleSemanticErrorsBeforeLowering() {
        var semanticProgram = SemanticAnalyzer.analyzeModules(
            List.of(
                sourceModule(
                    new ModuleName(
                        List.of("invalid")
                    ),
                    """
                    fn calculate() -> int
                        return missing
                    end
                    """
                )
            )
        );

        var exception = assertThrows(
            IrLoweringException.class,
            () -> IrProgramLowerer.lower(semanticProgram)
        );

        assertTrue(exception.getMessage().contains("SOL-S002"));
    }

    @Test
    void rejectsProgramSemanticErrorsBeforeLowering() {
        var semanticProgram = SemanticAnalyzer.analyzeProgram(
            List.of(
                sourceModule(
                    new ModuleName(
                        List.of("application")
                    ),
                    """
                    fn start() -> int
                        return 0
                    end
                    """
                )
            )
        );

        var exception = assertThrows(
            IrLoweringException.class,
            () -> IrProgramLowerer.lower(semanticProgram)
        );

        assertTrue(exception.getMessage().contains("SOL-S023"));
    }

    @Test
    void rejectsNullPrograms() {
        assertThrows(
            NullPointerException.class,
            () -> IrProgramLowerer.lower(null)
        );
    }

    @Test
    void lowersStringLiteralsWithDecodedEscapes() {
        var name = new ModuleName(List.of("strings"));

        var semanticProgram = SemanticAnalyzer.analyzeModules(
            List.of(
                sourceModule(
                    name,
                    """
                    fn message() -> string
                        return "Sol ñ\\n"
                    end
                    """
                )
            )
        );

        var lowered = IrProgramLowerer.lower(semanticProgram);
        var function = lowered.modules().getFirst().function("message").orElseThrow();
        var terminator = assertInstanceOf(IrReturnTerminator.class, function.entryBlock().orElseThrow().terminator());
        var string = assertInstanceOf(IrStringConstant.class, terminator.value().orElseThrow());

        assertEquals("Sol ñ\n", string.value());
    }

    private static SourceModule sourceModule(ModuleName name, String source) {
        return new SourceModule(name, Parser.parse(Lexer.scan(source)));
    }
}
