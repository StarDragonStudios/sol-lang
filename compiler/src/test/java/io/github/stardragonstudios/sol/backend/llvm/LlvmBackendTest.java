package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrBooleanConstant;
import io.github.stardragonstudios.sol.ir.IrCharConstant;
import io.github.stardragonstudios.sol.ir.IrFloatConstant;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmBackendTest {
    @Test
    void generatesFunctionDefinitionsAndReturnsParameters() {
        var parameter =
            new IrParameter(
                new IrValueId(
                    0
                ),
                "value",
                PrimitiveIrType.INT
            );

        var identity =
            IrFunction.definition(
                new IrFunctionId(
                    0
                ),
                "identity",
                List.of(
                    parameter
                ),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(),
                        IrReturnTerminator.returning(
                            parameter
                        )
                    )
                )
            );

        var noop =
            IrFunction.definition(
                new IrFunctionId(
                    1
                ),
                "noop",
                List.of(),
                PrimitiveIrType.VOID,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(),
                        IrReturnTerminator.bare()
                    )
                )
            );

        var declaration =
            IrFunction.declaration(
                new IrFunctionId(
                    2
                ),
                "external",
                List.of(),
                PrimitiveIrType.INT
            );

        var program =
            IrProgram.library(
                List.of(
                    new IrModule(
                        new IrModuleName(
                            List.of(
                                "application"
                            )
                        ),
                        List.of(
                            identity,
                            noop,
                            declaration
                        )
                    )
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    program,
                    "sol.definitions"
                )
        ) {
            assertEquals(
                """
                ; ModuleID = 'sol.definitions'
                source_filename = "sol.definitions"

                define i64 @sol.function0.identity(i64 %value) {
                block0:
                  ret i64 %value
                }

                define void @sol.function1.noop() {
                block0:
                  ret void
                }

                declare i64 @sol.function2.external()
                """,
                normalizeNewlines(
                    module.text()
                )
            );

            module.verify();
        }
    }

    @Test
    void generatesPrimitiveConstants() {
        var integer =
            constantFunction(
                new IrFunctionId(
                    0
                ),
                "integer",
                PrimitiveIrType.INT,
                new IrIntConstant(
                    new IrValueId(
                        0
                    ),
                    -42
                )
            );

        var floating =
            constantFunction(
                new IrFunctionId(
                    1
                ),
                "floating",
                PrimitiveIrType.FLOAT,
                new IrFloatConstant(
                    new IrValueId(
                        0
                    ),
                    1.5
                )
            );

        var booleanValue =
            constantFunction(
                new IrFunctionId(
                    2
                ),
                "boolean_value",
                PrimitiveIrType.BOOLEAN,
                new IrBooleanConstant(
                    new IrValueId(
                        0
                    ),
                    true
                )
            );

        var character =
            constantFunction(
                new IrFunctionId(
                    3
                ),
                "character",
                PrimitiveIrType.CHAR,
                new IrCharConstant(
                    new IrValueId(
                        0
                    ),
                    0x1F600
                )
            );

        var program =
            IrProgram.library(
                List.of(
                    new IrModule(
                        new IrModuleName(
                            List.of(
                                "constants"
                            )
                        ),
                        List.of(
                            integer,
                            floating,
                            booleanValue,
                            character
                        )
                    )
                )
            );

        try (
            var module =
                LlvmBackend.generate(
                    program,
                    "sol.constants"
                )
        ) {
            var text =
                normalizeNewlines(
                    module.text()
                );

            assertTrue(
                text.contains(
                    "ret i64 -42"
                )
            );

            assertTrue(
                text.contains(
                    "ret double 1.500000e+00"
                )
            );

            assertTrue(
                text.contains(
                    "ret i1 true"
                )
            );

            assertTrue(
                text.contains(
                    "ret i32 128512"
                )
            );

            module.verify();
        }
    }

    @Test
    void isolatesGeneratedModulesBetweenInvocations() {
        var program =
            IrProgram.library(
                List.of()
            );

        try (
            var first =
                LlvmBackend.generate(
                    program,
                    "sol.first-generation"
                );

            var second =
                LlvmBackend.generate(
                    program,
                    "sol.second-generation"
                )
        ) {
            assertTrue(
                first.text()
                    .contains(
                        "sol.first-generation"
                    )
            );

            assertTrue(
                second.text()
                    .contains(
                        "sol.second-generation"
                    )
            );

            assertTrue(
                first.contextHandle()
                    .address()
                    != second.contextHandle()
                    .address()
            );
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(
            NullPointerException.class,
            () ->
                LlvmBackend.generate(
                    null,
                    "sol.invalid"
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                LlvmBackend.generate(
                    IrProgram.library(
                        List.of()
                    ),
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                LlvmBackend.generate(
                    IrProgram.library(
                        List.of()
                    ),
                    "   "
                )
        );
    }

    private static IrFunction constantFunction(
        IrFunctionId identifier,
        String name,
        PrimitiveIrType returnType,
        io.github.stardragonstudios.sol.ir.IrValue value
    ) {
        return IrFunction.definition(
            identifier,
            name,
            List.of(),
            returnType,
            List.of(
                new IrBasicBlock(
                    new IrBlockId(
                        0
                    ),
                    List.of(),
                    IrReturnTerminator.returning(
                        value
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