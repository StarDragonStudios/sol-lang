package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrFunctionTest {
    @Test
    void createsBodylessFunctionDeclarations() {
        var function = IrFunction.declaration(new IrFunctionId(0), "external", List.of(), PrimitiveIrType.VOID);

        assertEquals(new IrFunctionId(0), function.id());
        assertEquals("external", function.name());
        assertSame(PrimitiveIrType.VOID, function.returnType());
        assertFalse(function.hasBody());
        assertTrue(function.blocks().isEmpty());
        assertTrue(function.entryBlock().isEmpty());
    }

    @Test
    void createsTypedFunctionDefinitions() {
        var parameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.INT);
        var constant = new IrIntConstant(new IrValueId(1), 2);
        var result = new IrBinaryInstruction(new IrValueId(2), IrBinaryOperator.ADD, parameter, constant);
        var block = new IrBasicBlock(new IrBlockId(0), List.of(result), IrReturnTerminator.returning(result));
        var function = IrFunction.definition(new IrFunctionId(0), "add_two", List.of(parameter), PrimitiveIrType.INT, List.of(block));

        assertTrue(function.hasBody());
        assertEquals(List.of(parameter), function.parameters());
        assertEquals(List.of(block), function.blocks());
        assertSame(block, function.entryBlock().orElseThrow());
    }

    @Test
    void preservesFunctionCollectionsDefensively() {
        var parameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.INT);
        var block = new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(parameter));
        var parameters = new ArrayList<>(List.of(parameter));
        var blocks = new ArrayList<>(List.of(block));
        var function = IrFunction.definition(new IrFunctionId(0), "identity", parameters, PrimitiveIrType.INT, blocks);

        parameters.clear();
        blocks.clear();

        assertEquals(List.of(parameter), function.parameters());
        assertEquals(List.of(block), function.blocks());

        assertThrows(
            UnsupportedOperationException.class,
            () -> function.parameters().clear()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> function.blocks().clear()
        );
    }

    @Test
    void validatesFunctionReturnTypes() {
        var integer = new IrIntConstant(new IrValueId(0), 42);
        var floatingPoint = new IrFloatConstant(new IrValueId(1), 42.0);

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.definition(
                new IrFunctionId(0),
                "invalid_void",
                List.of(),
                PrimitiveIrType.VOID,
                List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(integer)))
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.definition(
                new IrFunctionId(0),
                "missing_value",
                List.of(),
                PrimitiveIrType.INT,
                List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.bare()))
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.definition(
                new IrFunctionId(0),
                "wrong_type",
                List.of(),
                PrimitiveIrType.INT,
                List.of(new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.returning(floatingPoint)))
            )
        );
    }

    @Test
    void rejectsDuplicateFunctionComponents() {
        var firstParameter = new IrParameter(new IrValueId(0), "first", PrimitiveIrType.INT);
        var duplicateIdentifier = new IrParameter(new IrValueId(0), "second", PrimitiveIrType.INT);
        var duplicateName = new IrParameter(new IrValueId(1), "first", PrimitiveIrType.INT);

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.declaration(
                new IrFunctionId(0),
                "duplicate_identifier",
                List.of(firstParameter, duplicateIdentifier),
                PrimitiveIrType.VOID
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.declaration(
                new IrFunctionId(0),
                "duplicate_name",
                List.of(firstParameter, duplicateName),
                PrimitiveIrType.VOID
            )
        );

        var firstBlock = new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.bare());
        var duplicateBlock = new IrBasicBlock(new IrBlockId(0), List.of(), IrReturnTerminator.bare());

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.definition(
                new IrFunctionId(0),
                "duplicate_blocks",
                List.of(),
                PrimitiveIrType.VOID,
                List.of(firstBlock, duplicateBlock)
            )
        );
    }

    @Test
    void rejectsDuplicateValueIdentifiers() {
        var parameter = new IrParameter(new IrValueId(0), "value", PrimitiveIrType.INT);
        var duplicate = new IrIntConstant(new IrValueId(0), 1);
        var instruction = new IrBinaryInstruction(new IrValueId(1), IrBinaryOperator.ADD, parameter, duplicate);

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.definition(
                new IrFunctionId(0),
                "duplicate_value",
                List.of(parameter),
                PrimitiveIrType.INT,
                List.of(new IrBasicBlock(new IrBlockId(0), List.of(instruction), IrReturnTerminator.returning(instruction)))
            )
        );
    }

    @Test
    void rejectsForeignParametersAndInstructions() {
        var declaredParameter = new IrParameter(new IrValueId(0), "declared", PrimitiveIrType.INT);
        var foreignParameter = new IrParameter(new IrValueId(1), "foreign", PrimitiveIrType.INT);
        var instruction = new IrUnaryInstruction(new IrValueId(2), IrUnaryOperator.POSITIVE, foreignParameter);

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.definition(
                new IrFunctionId(0),
                "foreign_parameter",
                List.of(declaredParameter),
                PrimitiveIrType.INT,
                List.of(new IrBasicBlock(new IrBlockId(0), List.of(instruction), IrReturnTerminator.returning(instruction)))
            )
        );

        var constant = new IrIntConstant(new IrValueId(3), 1);
        var hiddenInstruction = new IrUnaryInstruction(new IrValueId(4), IrUnaryOperator.NEGATE, constant);
        var visibleInstruction = new IrUnaryInstruction(new IrValueId(5), IrUnaryOperator.POSITIVE, hiddenInstruction);

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.definition(
                new IrFunctionId(0),
                "foreign_instruction",
                List.of(),
                PrimitiveIrType.INT,
                List.of(new IrBasicBlock(new IrBlockId(0), List.of(visibleInstruction), IrReturnTerminator.returning(visibleInstruction)))
            )
        );
    }

    @Test
    void rejectsInvalidFunctionConstruction() {
        assertThrows(
            NullPointerException.class,
            () -> new IrFunction(
                null,
                "function",
                List.of(),
                PrimitiveIrType.VOID,
                Optional.empty()
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> IrFunction.declaration(
                new IrFunctionId(0),
                null,
                List.of(),
                PrimitiveIrType.VOID
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.declaration(
                new IrFunctionId(0),
                " ",
                List.of(),
                PrimitiveIrType.VOID
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> IrFunction.declaration(
                new IrFunctionId(0),
                "function",
                null,
                PrimitiveIrType.VOID
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> IrFunction.declaration(
                new IrFunctionId(0),
                "function",
                List.of(),
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> IrFunction.definition(
                new IrFunctionId(0),
                "empty",
                List.of(),
                PrimitiveIrType.VOID,
                List.of()
            )
        );
    }

    @Test
    void permitsInstructionsWithoutResultValues() {
        var constant = new IrIntConstant(new IrValueId(0), 42);

        IrInstruction effect = new TestEffectInstruction(List.of(constant));

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "perform_effect",
            List.of(),
            PrimitiveIrType.VOID,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(0),
                    List.of(effect),
                    IrReturnTerminator.bare()
                )
            )
        );

        assertEquals(List.of(effect), function.blocks().getFirst().instructions());
    }

    private record TestEffectInstruction(List<IrValue> operands) implements IrInstruction {
        private TestEffectInstruction {
            operands = List.copyOf(operands);
        }

        @Override
        public IrValueId id() {
            return null;
        }

        @Override
        public IrType type() {
            return null;
        }
    }
}
