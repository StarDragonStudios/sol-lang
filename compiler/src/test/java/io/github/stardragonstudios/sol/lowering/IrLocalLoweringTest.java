package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.*;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrLocalLoweringTest {
    @Test
    void lowersDeclarationsReferencesAndAssignmentsInOrder() {
        var function =
            lowerFunction(
                """
                fn calculate(input: int) -> int
                    const base: int = input
                    let copy: int = base
                    @mut let result: int = copy + 1
                    result = result + 2
                    return result
                end
                """
            );

        var block =
            function.entryBlock()
                .orElseThrow();

        var instructions =
            block.instructions();

        assertEquals(
            10,
            instructions.size()
        );

        var initializeBase =
            assertInstanceOf(
                IrLocalInitializeInstruction.class,
                instructions.get(0)
            );

        var loadBase =
            assertInstanceOf(
                IrLocalLoadInstruction.class,
                instructions.get(1)
            );

        var initializeCopy =
            assertInstanceOf(
                IrLocalInitializeInstruction.class,
                instructions.get(2)
            );

        var loadCopy =
            assertInstanceOf(
                IrLocalLoadInstruction.class,
                instructions.get(3)
            );

        var initializeExpression =
            assertInstanceOf(
                IrBinaryInstruction.class,
                instructions.get(4)
            );

        var initializeResult =
            assertInstanceOf(
                IrLocalInitializeInstruction.class,
                instructions.get(5)
            );

        var assignmentLoad =
            assertInstanceOf(
                IrLocalLoadInstruction.class,
                instructions.get(6)
            );

        var assignmentExpression =
            assertInstanceOf(
                IrBinaryInstruction.class,
                instructions.get(7)
            );

        var storeResult =
            assertInstanceOf(
                IrLocalStoreInstruction.class,
                instructions.get(8)
            );

        var returnLoad =
            assertInstanceOf(
                IrLocalLoadInstruction.class,
                instructions.get(9)
            );

        assertEquals(
            IrLocalKind.CONSTANT,
            initializeBase.local()
                .kind()
        );

        assertEquals(
            IrLocalKind.IMMUTABLE,
            initializeCopy.local()
                .kind()
        );

        assertEquals(
            IrLocalKind.MUTABLE,
            initializeResult.local()
                .kind()
        );

        assertSame(
            function.parameters()
                .getFirst(),
            initializeBase.initializer()
        );

        assertSame(
            initializeBase.local(),
            loadBase.local()
        );

        assertSame(
            loadBase,
            initializeCopy.initializer()
        );

        assertSame(
            initializeCopy.local(),
            loadCopy.local()
        );

        assertSame(
            initializeExpression,
            initializeResult.initializer()
        );

        assertSame(
            initializeResult.local(),
            assignmentLoad.local()
        );

        assertSame(
            assignmentExpression,
            storeResult.value()
        );

        assertSame(
            initializeResult.local(),
            storeResult.local()
        );

        assertSame(
            initializeResult.local(),
            returnLoad.local()
        );

        var terminator =
            assertInstanceOf(
                IrReturnTerminator.class,
                block.terminator()
            );

        assertSame(
            returnLoad,
            terminator.value()
                .orElseThrow()
        );
    }

    @Test
    void keepsLocalAndValueIdentifiersIndependent() {
        var function =
            lowerFunction(
                """
                fn calculate(input: int) -> int
                    let value: int = input
                    return value
                end
                """
            );

        var initialization =
            assertInstanceOf(
                IrLocalInitializeInstruction.class,
                function.entryBlock()
                    .orElseThrow()
                    .instructions()
                    .get(0)
            );

        var load =
            assertInstanceOf(
                IrLocalLoadInstruction.class,
                function.entryBlock()
                    .orElseThrow()
                    .instructions()
                    .get(1)
            );

        assertEquals(
            0,
            initialization.local()
                .id()
                .index()
        );

        /*
         * %0 belongs to the parameter, so the load receives %1.
         * local0 belongs to an independent identifier space.
         */
        assertEquals(
            new IrValueId(1),
            load.id()
        );
    }

    @Test
    void usesCanonicalLocalIdentityAcrossEveryOperation() {
        var function =
            lowerFunction(
                """
                fn calculate() -> int
                    @mut let value: int = 1
                    value = value + 1
                    return value
                end
                """
            );

        var instructions =
            function.entryBlock()
                .orElseThrow()
                .instructions();

        var initialization =
            assertInstanceOf(
                IrLocalInitializeInstruction.class,
                instructions.get(0)
            );

        var assignmentLoad =
            assertInstanceOf(
                IrLocalLoadInstruction.class,
                instructions.get(1)
            );

        var store =
            assertInstanceOf(
                IrLocalStoreInstruction.class,
                instructions.get(3)
            );

        var returnLoad =
            assertInstanceOf(
                IrLocalLoadInstruction.class,
                instructions.get(4)
            );

        assertSame(
            initialization.local(),
            assignmentLoad.local()
        );

        assertSame(
            initialization.local(),
            store.local()
        );

        assertSame(
            initialization.local(),
            returnLoad.local()
        );
    }

    @Test
    void producesDeterministicLocalIr() {
        var source =
            """
            fn calculate(input: int) -> int
                @mut let value: int = input
                value = value + 1
                return value
            end
            """;

        var first =
            IrTextFormatter.format(
                lowerProgram(
                    source
                )
            );

        var second =
            IrTextFormatter.format(
                lowerProgram(
                    source
                )
            );

        assertEquals(
            first,
            second
        );
    }

    @Test
    void rejectsImmutableAssignmentsBeforeLowering() {
        var semanticProgram = analyze(
            """
            fn calculate() -> int
                let value: int = 1
                value = 2
                return value
            end
            """
        );

        assertThrows(
            IrLoweringException.class,
            () -> IrProgramLowerer.lower(semanticProgram)
        );
    }

    @Test
    void rejectsAssignmentTypeErrorsBeforeLowering() {
        var semanticProgram = analyze(
            """
            fn calculate() -> int
                @mut let value: int = 1
                value = true
                return value
            end
            """
        );

        assertThrows(
            IrLoweringException.class,
            () -> IrProgramLowerer.lower(semanticProgram)
        );
    }

    private static IrFunction lowerFunction(String source) {
        return lowerProgram(source).modules().getFirst().functions().getFirst();
    }

    private static IrProgram lowerProgram(String source) {
        return IrProgramLowerer.lower(analyze(source));
    }

    private static SemanticProgramAnalysisResult analyze(String source) {
        return SemanticAnalyzer.analyzeModules(
            List.of(new SourceModule(new ModuleName(List.of("locals")), Parser.parse(Lexer.scan(source))))
        );
    }
}
