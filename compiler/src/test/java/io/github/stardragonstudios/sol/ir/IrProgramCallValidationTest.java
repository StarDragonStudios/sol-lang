package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrProgramCallValidationTest {
    @Test
    void acceptsCallsToBodylessFunctionsAcrossModules() {
        var targetFunction =
            IrFunction.declaration(
                new IrFunctionId(0),
                "external",
                List.of(),
                PrimitiveIrType.INT
            );

        var reference =
            new IrFunctionReference(
                targetFunction.id(),
                targetFunction.name(),
                List.of(),
                targetFunction.returnType()
            );

        var caller =
            intCaller(
                new IrFunctionId(1),
                "caller",
                reference
            );

        var targetModule =
            new IrModule(
                new IrModuleName(
                    List.of(
                        "external"
                    )
                ),
                List.of(
                    targetFunction
                )
            );

        var callerModule =
            new IrModule(
                new IrModuleName(
                    List.of(
                        "application"
                    )
                ),
                List.of(
                    caller
                )
            );

        assertDoesNotThrow(
            () ->
                IrProgram.library(
                    List.of(
                        targetModule,
                        callerModule
                    )
                )
        );
    }

    @Test
    void rejectsCallsToUndeclaredFunctions() {
        var missingReference =
            new IrFunctionReference(
                new IrFunctionId(99),
                "missing",
                List.of(),
                PrimitiveIrType.INT
            );

        var caller =
            intCaller(
                new IrFunctionId(0),
                "caller",
                missingReference
            );

        var module =
            new IrModule(
                new IrModuleName(
                    List.of(
                        "application"
                    )
                ),
                List.of(
                    caller
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrProgram.library(
                    List.of(
                        module
                    )
                )
        );
    }

    @Test
    void rejectsMismatchedCallTargetName() {
        var target =
            IrFunction.declaration(
                new IrFunctionId(0),
                "canonical",
                List.of(),
                PrimitiveIrType.INT
            );

        var incorrectReference =
            new IrFunctionReference(
                target.id(),
                "different",
                List.of(),
                PrimitiveIrType.INT
            );

        assertInvalidTarget(
            target,
            incorrectReference
        );
    }

    @Test
    void rejectsMismatchedCallTargetParameterTypes() {
        var parameter =
            new IrParameter(
                new IrValueId(0),
                "value",
                PrimitiveIrType.INT
            );

        var target =
            IrFunction.declaration(
                new IrFunctionId(0),
                "canonical",
                List.of(
                    parameter
                ),
                PrimitiveIrType.INT
            );

        /*
         * The reference is internally valid, but does not describe
         * the canonical function stored in the program.
         */
        var incorrectReference =
            new IrFunctionReference(
                target.id(),
                target.name(),
                List.of(),
                PrimitiveIrType.INT
            );

        var call =
            new IrValueCallInstruction(
                new IrValueId(0),
                incorrectReference,
                List.of()
            );

        var caller =
            IrFunction.definition(
                new IrFunctionId(1),
                "caller",
                List.of(),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(0),
                        List.of(
                            call
                        ),
                        IrReturnTerminator.returning(
                            call
                        )
                    )
                )
            );

        assertInvalidProgram(
            target,
            caller
        );
    }

    @Test
    void rejectsMismatchedCallTargetReturnType() {
        var target =
            IrFunction.declaration(
                new IrFunctionId(0),
                "canonical",
                List.of(),
                PrimitiveIrType.INT
            );

        var incorrectReference =
            new IrFunctionReference(
                target.id(),
                target.name(),
                List.of(),
                PrimitiveIrType.FLOAT
            );

        var call =
            new IrValueCallInstruction(
                new IrValueId(0),
                incorrectReference,
                List.of()
            );

        var caller =
            IrFunction.definition(
                new IrFunctionId(1),
                "caller",
                List.of(),
                PrimitiveIrType.FLOAT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(0),
                        List.of(
                            call
                        ),
                        IrReturnTerminator.returning(
                            call
                        )
                    )
                )
            );

        assertInvalidProgram(
            target,
            caller
        );
    }

    @Test
    void requiresOneCanonicalReferenceInstancePerFunction() {
        var target =
            IrFunction.declaration(
                new IrFunctionId(0),
                "target",
                List.of(),
                PrimitiveIrType.INT
            );

        var firstReference =
            new IrFunctionReference(
                target.id(),
                target.name(),
                List.of(),
                target.returnType()
            );

        var equivalentReference =
            new IrFunctionReference(
                target.id(),
                target.name(),
                List.of(),
                target.returnType()
            );

        var firstCaller =
            intCaller(
                new IrFunctionId(1),
                "first_caller",
                firstReference
            );

        var secondCaller =
            intCaller(
                new IrFunctionId(2),
                "second_caller",
                equivalentReference
            );

        var module =
            new IrModule(
                new IrModuleName(
                    List.of(
                        "application"
                    )
                ),
                List.of(
                    target,
                    firstCaller,
                    secondCaller
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrProgram.library(
                    List.of(
                        module
                    )
                )
        );
    }

    @Test
    void acceptsSharedCanonicalReferenceAcrossMultipleCallers() {
        var target =
            IrFunction.declaration(
                new IrFunctionId(0),
                "target",
                List.of(),
                PrimitiveIrType.INT
            );

        var reference =
            new IrFunctionReference(
                target.id(),
                target.name(),
                List.of(),
                target.returnType()
            );

        var firstCaller =
            intCaller(
                new IrFunctionId(1),
                "first_caller",
                reference
            );

        var secondCaller =
            intCaller(
                new IrFunctionId(2),
                "second_caller",
                reference
            );

        var module =
            new IrModule(
                new IrModuleName(
                    List.of(
                        "application"
                    )
                ),
                List.of(
                    target,
                    firstCaller,
                    secondCaller
                )
            );

        assertDoesNotThrow(
            () ->
                IrProgram.library(
                    List.of(
                        module
                    )
                )
        );
    }

    private static void assertInvalidTarget(
        IrFunction target,
        IrFunctionReference reference
    ) {
        var caller =
            intCaller(
                new IrFunctionId(1),
                "caller",
                reference
            );

        assertInvalidProgram(
            target,
            caller
        );
    }

    private static void assertInvalidProgram(
        IrFunction target,
        IrFunction caller
    ) {
        var module =
            new IrModule(
                new IrModuleName(
                    List.of(
                        "application"
                    )
                ),
                List.of(
                    target,
                    caller
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                IrProgram.library(
                    List.of(
                        module
                    )
                )
        );
    }

    private static IrFunction intCaller(
        IrFunctionId id,
        String name,
        IrFunctionReference target
    ) {
        var call =
            new IrValueCallInstruction(
                new IrValueId(0),
                target,
                List.of()
            );

        return IrFunction.definition(
            id,
            name,
            List.of(),
            PrimitiveIrType.INT,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(0),
                    List.of(
                        call
                    ),
                    IrReturnTerminator.returning(
                        call
                    )
                )
            )
        );
    }
}