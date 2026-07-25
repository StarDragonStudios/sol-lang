package io.github.stardragonstudios.sol.ir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrTextFormatterTest {
    @Test
    void formatsEmptyLibraryPrograms() {
        var program = IrProgram.library(List.of());

        assertEquals(
            """
            program {
              entry none
            }
            """,
            IrTextFormatter.format(program)
        );
    }

    @Test
    void formatsModulesAndDeclarationsInOrder() {
        var first = IrFunction.declaration(
            new IrFunctionId(0),
            "first",
            List.of(),
            PrimitiveIrType.VOID
        );

        var parameter = new IrParameter(
            new IrValueId(0),
            "value",
            PrimitiveIrType.STRING
        );

        var second = IrFunction.declaration(
            new IrFunctionId(1),
            "write",
            List.of(parameter),
            PrimitiveIrType.VOID
        );

        var program = IrProgram.library(
            List.of(
                new IrModule(new IrModuleName(List.of("first")), List.of(first)),
                new IrModule(new IrModuleName(List.of("std", "console")), List.of(second)
                )
            )
        );

        assertEquals(
            """
            program {
              entry none

              module @first {
                declare @function0 first() -> void
              }

              module @std.console {
                declare @function1 write(%0 value: string) -> void
              }
            }
            """,
            IrTextFormatter.format(program)
        );
    }

    @Test
    void formatsExecutableFunctionsDeterministically() {
        var parameter = new IrParameter(
            new IrValueId(0),
            "argument",
            PrimitiveIrType.INT
        );

        var constant = new IrIntConstant(new IrValueId(1), 2);

        var addition = new IrBinaryInstruction(
            new IrValueId(2),
            IrBinaryOperator.ADD,
            parameter,
            constant
        );

        var negation = new IrUnaryInstruction(
            new IrValueId(3),
            IrUnaryOperator.NEGATE,
            addition
        );

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "launch",
            List.of(parameter),
            PrimitiveIrType.INT,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(0),
                    List.of(addition, negation),
                    IrReturnTerminator.returning(negation)
                )
            )
        );

        var module = new IrModule(
            new IrModuleName(List.of("application")),
            List.of(function)
        );

        var program = IrProgram.executable(
            List.of(module),
            new IrEntryPoint(module, function)
        );

        var expected =
            """
            program {
              entry @application::function0

              module @application {
                define @function0 launch(%0 argument: int) -> int {
                  block0:
                    %1: int = const 2
                    %2: int = add %0, %1
                    %3: int = negate %2
                    return %3
                }
              }
            }
            """;

        assertEquals(
            expected,
            IrTextFormatter.format(program)
        );

        assertEquals(
            expected,
            IrTextFormatter.format(program)
        );
    }

    @Test
    void emitsSharedConstantsOnlyOnce() {
        var constant = new IrIntConstant(
            new IrValueId(0),
            2
        );

        var first = new IrUnaryInstruction(
            new IrValueId(1),
            IrUnaryOperator.NEGATE,
            constant
        );

        var second = new IrBinaryInstruction(
            new IrValueId(2),
            IrBinaryOperator.ADD,
            first,
            constant
        );

        var function = IrFunction.definition(
            new IrFunctionId(0),
            "calculate",
            List.of(),
            PrimitiveIrType.INT,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(0),
                    List.of(first, second),
                    IrReturnTerminator.returning(second)
                )
            )
        );

        var program = IrProgram.library(
            List.of(
                new IrModule(
                    new IrModuleName(List.of("calculation")),
                    List.of(function)
                )
            )
        );

        var formatted = IrTextFormatter.format(program);

        assertEquals(1, formatted.split("%0: int = const 2", -1).length - 1);
    }

    @Test
    void formatsPrimitiveConstantValues() {
        var integer = functionReturning(
            new IrFunctionId(0),
            "integer",
            PrimitiveIrType.INT,
            new IrIntConstant(new IrValueId(0), -42)
        );

        var floatingPoint = functionReturning(
            new IrFunctionId(1),
            "floating_point",
            PrimitiveIrType.FLOAT,
            new IrFloatConstant(new IrValueId(0), 3.5)
        );

        var booleanValue = functionReturning(
            new IrFunctionId(2),
            "boolean_value",
            PrimitiveIrType.BOOLEAN,
            new IrBooleanConstant(new IrValueId(0), true)
        );

        var character = functionReturning(
            new IrFunctionId(3),
            "character",
            PrimitiveIrType.CHAR,
            new IrCharConstant(new IrValueId(0), 0x1F409)
        );

        var string = functionReturning(
            new IrFunctionId(4),
            "string_value",
            PrimitiveIrType.STRING,
            new IrStringConstant(new IrValueId(0), "Sol\n\"Dragon\" \uD83D\uDC09")
        );

        var program = IrProgram.library(
            List.of(
                new IrModule(
                    new IrModuleName(List.of("constants")),
                    List.of(integer, floatingPoint, booleanValue, character, string)
                )
            )
        );

        var formatted = IrTextFormatter.format(program);

        assertTrue(formatted.contains("%0: int = const -42"));
        assertTrue(formatted.contains("%0: float = const 3.5"));
        assertTrue(formatted.contains("%0: boolean = const true"));
        assertTrue(formatted.contains("%0: char = const U+1F409"));
        assertTrue(formatted.contains("%0: string = const \"Sol\\n\\\"Dragon\\\" \\u{1F409}\""));
    }

    @Test
    void rejectsNullPrograms() {
        assertThrows(
            NullPointerException.class,
            () -> IrTextFormatter.format(null)
        );
    }

    private static IrFunction functionReturning(IrFunctionId id, String name, IrType returnType, IrValue value) {
        return IrFunction.definition(
            id,
            name,
            List.of(),
            returnType,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(0),
                    List.of(),
                    IrReturnTerminator.returning(value)
                )
            )
        );
    }
}
