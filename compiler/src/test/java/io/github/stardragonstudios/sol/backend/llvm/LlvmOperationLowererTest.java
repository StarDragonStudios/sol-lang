package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBinaryInstruction;
import io.github.stardragonstudios.sol.ir.IrBinaryOperator;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrUnaryInstruction;
import io.github.stardragonstudios.sol.ir.IrUnaryOperator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmOperationLowererTest {
    @Test
    void lowersUnaryOperations() {
        var program = program(
            unaryFunction(0, "logical_not", IrUnaryOperator.LOGICAL_NOT, PrimitiveIrType.BOOLEAN),
            unaryFunction(1, "negate_integer", IrUnaryOperator.NEGATE, PrimitiveIrType.INT),
            unaryFunction(2, "negate_float", IrUnaryOperator.NEGATE, PrimitiveIrType.FLOAT),
            unaryFunction(3, "positive_integer", IrUnaryOperator.POSITIVE, PrimitiveIrType.INT)
        );

        try (var module = LlvmBackend.generate(program, "sol.unary-operations")) {
            var text = normalizeNewlines(module.text());

            assertTrue(text.contains("%value1 = xor i1 %value, true"));


            assertTrue(
                text.contains(
                    "%value1 = sub i64 0, %value"
                )
            );

            assertTrue(
                text.contains(
                    "%value1 = fneg double %value"
                )
            );

            /*
             * Unary positive reuses its operand and therefore emits
             * no extra LLVM instruction.
             */
            assertTrue(
                text.contains(
                    """
                    define i64 @sol.function3.positive_integer(i64 %value) {
                    block0:
                      ret i64 %value
                    }
                    """
                )
            );

            module.verify();
        }
    }

    @Test
    void lowersIntegerArithmeticOperations() {
        var program =
            program(
                binaryFunction(
                    0,
                    "add",
                    IrBinaryOperator.ADD,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    1,
                    "subtract",
                    IrBinaryOperator.SUBTRACT,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    2,
                    "multiply",
                    IrBinaryOperator.MULTIPLY,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    3,
                    "divide",
                    IrBinaryOperator.DIVIDE,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    4,
                    "remainder",
                    IrBinaryOperator.REMAINDER,
                    PrimitiveIrType.INT
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    program,
                    "sol.integer-arithmetic"
                )
        ) {
            var text =
                normalizeNewlines(
                    module.text()
                );

            assertTrue(
                text.contains(
                    "%value2 = add i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "%value2 = sub i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "%value2 = mul i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "%value2 = sdiv i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "%value2 = srem i64 %left, %right"
                )
            );

            module.verify();
        }
    }

    @Test
    void lowersFloatingPointArithmeticOperations() {
        var program =
            program(
                binaryFunction(
                    0,
                    "add",
                    IrBinaryOperator.ADD,
                    PrimitiveIrType.FLOAT
                ),
                binaryFunction(
                    1,
                    "subtract",
                    IrBinaryOperator.SUBTRACT,
                    PrimitiveIrType.FLOAT
                ),
                binaryFunction(
                    2,
                    "multiply",
                    IrBinaryOperator.MULTIPLY,
                    PrimitiveIrType.FLOAT
                ),
                binaryFunction(
                    3,
                    "divide",
                    IrBinaryOperator.DIVIDE,
                    PrimitiveIrType.FLOAT
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    program,
                    "sol.floating-arithmetic"
                )
        ) {
            var text =
                normalizeNewlines(
                    module.text()
                );

            assertTrue(
                text.contains(
                    "%value2 = fadd double %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "%value2 = fsub double %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "%value2 = fmul double %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "%value2 = fdiv double %left, %right"
                )
            );

            module.verify();
        }
    }

    @Test
    void lowersIntegerAndFloatingComparisons() {
        var program =
            program(
                binaryFunction(
                    0,
                    "integer_less",
                    IrBinaryOperator.LESS_THAN,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    1,
                    "integer_less_equal",
                    IrBinaryOperator.LESS_THAN_OR_EQUAL,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    2,
                    "integer_greater",
                    IrBinaryOperator.GREATER_THAN,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    3,
                    "integer_greater_equal",
                    IrBinaryOperator.GREATER_THAN_OR_EQUAL,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    4,
                    "integer_equal",
                    IrBinaryOperator.EQUAL,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    5,
                    "integer_not_equal",
                    IrBinaryOperator.NOT_EQUAL,
                    PrimitiveIrType.INT
                ),
                binaryFunction(
                    6,
                    "float_less",
                    IrBinaryOperator.LESS_THAN,
                    PrimitiveIrType.FLOAT
                ),
                binaryFunction(
                    7,
                    "float_less_equal",
                    IrBinaryOperator.LESS_THAN_OR_EQUAL,
                    PrimitiveIrType.FLOAT
                ),
                binaryFunction(
                    8,
                    "float_greater",
                    IrBinaryOperator.GREATER_THAN,
                    PrimitiveIrType.FLOAT
                ),
                binaryFunction(
                    9,
                    "float_greater_equal",
                    IrBinaryOperator.GREATER_THAN_OR_EQUAL,
                    PrimitiveIrType.FLOAT
                ),
                binaryFunction(
                    10,
                    "float_equal",
                    IrBinaryOperator.EQUAL,
                    PrimitiveIrType.FLOAT
                ),
                binaryFunction(
                    11,
                    "float_not_equal",
                    IrBinaryOperator.NOT_EQUAL,
                    PrimitiveIrType.FLOAT
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    program,
                    "sol.comparisons"
                )
        ) {
            var text =
                normalizeNewlines(
                    module.text()
                );

            assertTrue(
                text.contains(
                    "icmp slt i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "icmp sle i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "icmp sgt i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "icmp sge i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "icmp eq i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "icmp ne i64 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "fcmp olt double %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "fcmp ole double %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "fcmp ogt double %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "fcmp oge double %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "fcmp oeq double %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "fcmp une double %left, %right"
                )
            );

            module.verify();
        }
    }

    @Test
    void lowersBooleanOperationsAndCharacterEquality() {
        var program =
            program(
                binaryFunction(
                    0,
                    "logical_and",
                    IrBinaryOperator.LOGICAL_AND,
                    PrimitiveIrType.BOOLEAN
                ),
                binaryFunction(
                    1,
                    "logical_or",
                    IrBinaryOperator.LOGICAL_OR,
                    PrimitiveIrType.BOOLEAN
                ),
                binaryFunction(
                    2,
                    "character_equal",
                    IrBinaryOperator.EQUAL,
                    PrimitiveIrType.CHAR
                ),
                binaryFunction(
                    3,
                    "character_not_equal",
                    IrBinaryOperator.NOT_EQUAL,
                    PrimitiveIrType.CHAR
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    program,
                    "sol.logical-operations"
                )
        ) {
            var text =
                normalizeNewlines(
                    module.text()
                );

            assertTrue(
                text.contains(
                    "%value2 = and i1 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "%value2 = or i1 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "icmp eq i32 %left, %right"
                )
            );

            assertTrue(
                text.contains(
                    "icmp ne i32 %left, %right"
                )
            );

            module.verify();
        }
    }

    private static IrFunction unaryFunction(
        int functionIndex,
        String name,
        IrUnaryOperator operator,
        PrimitiveIrType operandType
    ) {
        var operand =
            new IrParameter(
                new IrValueId(
                    0
                ),
                "value",
                operandType
            );

        var instruction =
            new IrUnaryInstruction(
                new IrValueId(
                    1
                ),
                operator,
                operand
            );

        return IrFunction.definition(
            new IrFunctionId(
                functionIndex
            ),
            name,
            List.of(
                operand
            ),
            instruction.type(),
            List.of(
                new IrBasicBlock(
                    new IrBlockId(
                        0
                    ),
                    List.of(
                        instruction
                    ),
                    IrReturnTerminator.returning(
                        instruction
                    )
                )
            )
        );
    }

    private static IrFunction binaryFunction(
        int functionIndex,
        String name,
        IrBinaryOperator operator,
        PrimitiveIrType operandType
    ) {
        var left =
            new IrParameter(
                new IrValueId(
                    0
                ),
                "left",
                operandType
            );

        var right =
            new IrParameter(
                new IrValueId(
                    1
                ),
                "right",
                operandType
            );

        var instruction =
            new IrBinaryInstruction(
                new IrValueId(
                    2
                ),
                operator,
                left,
                right
            );

        return IrFunction.definition(
            new IrFunctionId(
                functionIndex
            ),
            name,
            List.of(
                left,
                right
            ),
            instruction.type(),
            List.of(
                new IrBasicBlock(
                    new IrBlockId(
                        0
                    ),
                    List.of(
                        instruction
                    ),
                    IrReturnTerminator.returning(
                        instruction
                    )
                )
            )
        );
    }

    private static IrProgram program(
        IrFunction... functions
    ) {
        return IrProgram.library(
            List.of(
                new IrModule(
                    new IrModuleName(
                        List.of(
                            "operations"
                        )
                    ),
                    List.of(
                        functions
                    )
                )
            )
        );
    }

    private static String normalizeNewlines(
        String text
    ) {
        return text.replace(
            "\r\n",
            "\n"
        );
    }
}
