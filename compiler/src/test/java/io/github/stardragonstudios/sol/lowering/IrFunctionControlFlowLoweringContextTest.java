package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrBlockTarget;
import io.github.stardragonstudios.sol.ir.IrBranchTerminator;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrFunctionControlFlowLoweringContextTest {
    @Test
    void createsInitialBlockLazily() {
        var context =
            context();

        assertFalse(
            context.hasActiveBlock()
        );

        var entry =
            context.currentBlockTarget();

        assertTrue(
            context.hasActiveBlock()
        );

        assertEquals(
            new IrBlockId(0),
            entry.id()
        );

        assertSame(
            entry,
            context.currentBlockTarget()
        );
    }

    @Test
    void allocatesFutureTargetsDeterministically() {
        var context =
            context();

        var entry =
            context.currentBlockTarget();

        var thenTarget =
            context.newBlockTarget();

        var exitTarget =
            context.newBlockTarget();

        assertEquals(
            new IrBlockId(0),
            entry.id()
        );

        assertEquals(
            new IrBlockId(1),
            thenTarget.id()
        );

        assertEquals(
            new IrBlockId(2),
            exitTarget.id()
        );
    }

    @Test
    void buildsMultipleBlocksInTerminationOrder() {
        var context =
            context();

        var entry =
            context.currentBlockTarget();

        var loop =
            context.newBlockTarget();

        var exit =
            context.newBlockTarget();

        var entryBlock =
            context.finishBlock(
                new IrBranchTerminator(
                    loop
                )
            );

        assertFalse(
            context.hasActiveBlock()
        );

        context.beginBlock(
            loop
        );

        var loopBlock =
            context.finishBlock(
                new IrBranchTerminator(
                    entry
                )
            );

        context.beginBlock(
            exit
        );

        var exitBlock =
            context.finishBlock(
                IrReturnTerminator.bare()
            );

        assertEquals(
            List.of(
                entryBlock,
                loopBlock,
                exitBlock
            ),
            context.blocks()
        );

        assertSame(
            entry,
            entryBlock.target()
        );

        assertSame(
            loop,
            loopBlock.target()
        );

        assertSame(
            exit,
            exitBlock.target()
        );
    }

    @Test
    void rejectsBeginningBlockBeforeCurrentBlockTerminates() {
        var context =
            context();

        context.currentBlockTarget();

        var target =
            context.newBlockTarget();

        assertThrows(
            IrLoweringException.class,
            () ->
                context.beginBlock(
                    target
                )
        );
    }

    @Test
    void rejectsForeignAndEquivalentTargets() {
        var context =
            context();

        var canonical =
            context.currentBlockTarget();

        context.finishBlock(
            IrReturnTerminator.bare()
        );

        var equivalent =
            new IrBlockTarget(
                canonical.id()
            );

        assertThrows(
            IrLoweringException.class,
            () ->
                context.beginBlock(
                    equivalent
                )
        );
    }

    @Test
    void rejectsStartingSameTargetMoreThanOnce() {
        var context =
            context();

        context.currentBlockTarget();

        var target =
            context.newBlockTarget();

        context.finishBlock(
            new IrBranchTerminator(
                target
            )
        );

        context.beginBlock(
            target
        );

        context.finishBlock(
            IrReturnTerminator.bare()
        );

        assertThrows(
            IrLoweringException.class,
            () ->
                context.beginBlock(
                    target
                )
        );
    }

    @Test
    void requiresExplicitBlockAfterTermination() {
        var context =
            context();

        context.finishBlock(
            IrReturnTerminator.bare()
        );

        assertFalse(
            context.hasActiveBlock()
        );

        assertThrows(
            IrLoweringException.class,
            () ->
                context.finishBlock(
                    IrReturnTerminator.bare()
                )
        );
    }

    @Test
    void returnsImmutableBlockCollection() {
        var context =
            context();

        context.finishBlock(
            IrReturnTerminator.bare()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () ->
                context.blocks()
                    .clear()
        );
    }

    @Test
    void rejectsNullBlockTargets() {
        var context =
            context();

        context.finishBlock(
            IrReturnTerminator.bare()
        );

        assertThrows(
            NullPointerException.class,
            () ->
                context.beginBlock(
                    null
                )
        );
    }

    private static IrFunctionLoweringContext context() {
        var unit =
            Parser.parse(
                Lexer.scan(
                    """
                    fn perform() -> void
                        return
                    end
                    """
                )
            );

        var result =
            SemanticAnalyzer.analyze(
                unit
            );

        var declaration =
            (FunctionDeclaration)
                unit.declarations()
                    .getFirst();

        var function =
            result.model()
                .symbolOf(
                    declaration
                )
                .orElseThrow();

        return new IrFunctionLoweringContext(
            function
        );
    }
}
