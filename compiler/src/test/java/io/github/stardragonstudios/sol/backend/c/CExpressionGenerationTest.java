package io.github.stardragonstudios.sol.backend.c;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CExpressionGenerationTest {
    @Test
    void generatesPrimitiveLiterals() {
        var source = generate(
            """
            fn integer_value() -> int
                return 42
            end

            fn float_value() -> float
                return 3.5
            end

            fn true_value() -> boolean
                return true
            end

            fn false_value() -> boolean
                return false
            end

            fn character_value() -> char
                return '\\n'
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertTrue(
            source.contains(
                "return ((sol_int)42);"
            )
        );

        assertTrue(
            source.contains(
                "return ((sol_float)3.5);"
            )
        );

        assertTrue(
            source.contains(
                "return ((sol_boolean)1);"
            )
        );

        assertTrue(
            source.contains(
                "return ((sol_boolean)0);"
            )
        );

        assertTrue(
            source.contains(
                "return ((sol_char)'\\n');"
            )
        );
    }

    @Test
    void generatesParameterReferencesWithAllocatedNames() {
        var source = generate(
            """
            fn identity(original_name: int) -> int
                return original_name
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertTrue(
            source.contains(
                "static sol_int sol_function_0(sol_int sol_parameter_0)"
            )
        );

        assertTrue(
            source.contains(
                "return sol_parameter_0;"
            )
        );

        assertFalse(
            source.contains(
                "original_name"
            )
        );
    }

    @Test
    void generatesUnaryExpressionsWithExplicitParentheses() {
        var source = generate(
            """
            fn negate(value: int) -> int
                return -value
            end

            fn positive(value: float) -> float
                return +value
            end

            fn logical_not(value: boolean) -> boolean
                return !value
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertTrue(
            source.contains(
                "return (-sol_parameter_0);"
            )
        );

        assertTrue(
            source.contains(
                "return (+sol_parameter_0);"
            )
        );

        assertTrue(
            source.contains(
                "return (!sol_parameter_0);"
            )
        );
    }

    @Test
    void preservesParenthesizedExpressions() {
        var source = generate(
            """
            fn grouped(value: int) -> int
                return +(value)
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertTrue(
            source.contains(
                "return (+(sol_parameter_0));"
            )
        );
    }

    @Test
    void generatesArithmeticExpressionsStructurally() {
        var source = generate(
            """
            fn calculate(left: int, right: int) -> int
                return left + right * 2
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertTrue(
            source.contains(
                "return (sol_parameter_0 + (sol_parameter_1 * ((sol_int)2)));"
            )
        );
    }

    @Test
    void generatesAllBinaryOperators() {
        var source = generate(
            """
            fn multiply(left: int, right: int) -> int
                return left * right
            end

            fn divide(left: int, right: int) -> int
                return left / right
            end

            fn remainder(left: int, right: int) -> int
                return left % right
            end

            fn add(left: int, right: int) -> int
                return left + right
            end

            fn subtract(left: int, right: int) -> int
                return left - right
            end

            fn less(left: int, right: int) -> boolean
                return left < right
            end

            fn less_equal(left: int, right: int) -> boolean
                return left <= right
            end

            fn greater(left: int, right: int) -> boolean
                return left > right
            end

            fn greater_equal(left: int, right: int) -> boolean
                return left >= right
            end

            fn equal(left: int, right: int) -> boolean
                return left == right
            end

            fn not_equal(left: int, right: int) -> boolean
                return left != right
            end

            fn logical_and(left: boolean, right: boolean) -> boolean
                return left && right
            end

            fn logical_or(left: boolean, right: boolean) -> boolean
                return left || right
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertTrue(source.contains("(sol_parameter_0 * sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 / sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 % sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 + sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 - sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 < sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 <= sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 > sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 >= sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 == sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 != sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 && sol_parameter_1)"));
        assertTrue(source.contains("(sol_parameter_0 || sol_parameter_1)"));
    }

    @Test
    void nestedExpressionsDoNotDependOnCPrecedence() {
        var source = generate(
            """
            fn nested(value: int) -> int
                return -(value + 2 * 3)
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertTrue(
            source.contains(
                "return (-((sol_parameter_0 + (((sol_int)2) * ((sol_int)3)))));"
            )
        );
    }

    private static String generate(
        String source
    ) {
        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(
                    new SourceModule(
                        new ModuleName(
                            List.of(
                                "application"
                            )
                        ),
                        Parser.parse(
                            Lexer.scan(source)
                        )
                    )
                )
            );

        assertTrue(
            program.programDiagnostics()
                .isEmpty()
        );

        assertTrue(
            program.analysisOf(
                    new ModuleName(
                        List.of(
                            "application"
                        )
                    )
                )
                .orElseThrow()
                .diagnostics()
                .isEmpty()
        );

        return CCodeGenerator.generate(
            program
        ).source();
    }
}
