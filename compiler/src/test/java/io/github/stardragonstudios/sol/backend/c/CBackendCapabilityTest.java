package io.github.stardragonstudios.sol.backend.c;

import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CBackendCapabilityTest {
    @Test
    void rejectsStringReturnType() {
        var exception = generateFailure(
            """
            fn text() -> string
                return "hello"
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertEquals(
            CCodeGenerationException.Reason
                .UNSUPPORTED_TYPE,
            exception.reason()
        );

        assertEquals(
            "Cannot generate C function 'text' in module 'application': unsupported semantic type 'string'.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsStringParameterType() {
        var exception = generateFailure(
            """
            fn consume(value: string) -> int
                return 0
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        assertEquals(
            CCodeGenerationException.Reason
                .UNSUPPORTED_TYPE,
            exception.reason()
        );

        assertEquals(
            "Cannot generate C function 'consume' in module 'application': unsupported semantic type 'string'.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsFunctionCalls() {
        var exception = generateFailure(
            """
            fn helper() -> int
                return 1
            end

            @init
            fn launch() -> int
                return helper()
            end
            """
        );

        assertEquals(
            CCodeGenerationException.Reason
                .UNSUPPORTED_EXPRESSION,
            exception.reason()
        );

        assertEquals(
            "Cannot generate CallExpression in function 'launch' of module 'application'.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsQualifiedFunctionNames() {
        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(
                    module(
                        "library",
                        """
                        fn helper() -> int
                            return 1
                        end
                        """
                    ),
                    module(
                        "application",
                        """
                        inject namespace library as lib

                        @init
                        fn launch() -> int
                            return lib::helper()
                        end
                        """
                    )
                )
            );

        assertTrue(
            program.programDiagnostics()
                .isEmpty()
        );

        var exception =
            assertThrows(
                CCodeGenerationException.class,
                () ->
                    CCodeGenerator.generate(
                        program
                    )
            );

        assertEquals(
            CCodeGenerationException.Reason
                .UNSUPPORTED_EXPRESSION,
            exception.reason()
        );

        assertEquals(
            "Cannot generate CallExpression in function 'launch' of module 'application'.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsLocalVariableStatements() {
        var exception = generateFailure(
            """
            @init
            fn launch() -> int
                let value: int = 1
                return 0
            end
            """
        );

        assertEquals(
            CCodeGenerationException.Reason
                .UNSUPPORTED_STATEMENT,
            exception.reason()
        );

        assertEquals(
            "Cannot generate VariableDeclarationStatement in function 'launch' of module 'application'.",
            exception.getMessage()
        );
    }

    @Test
    void rejectsAssignmentStatements() {
        var exception = generateFailure(
            """
            @init
            fn launch() -> int
                @mut let value: int = 1
                value = 2
                return 0
            end
            """
        );

        assertEquals(
            CCodeGenerationException.Reason
                .UNSUPPORTED_STATEMENT,
            exception.reason()
        );

        assertEquals(
            "Cannot generate VariableDeclarationStatement in function 'launch' of module 'application'.",
            exception.getMessage()
        );
    }

    @Test
    void backendFailuresAreDeterministic() {
        var first = generateFailure(
            """
            @init
            fn launch() -> int
                let value: int = 1
                return 0
            end
            """
        );

        var second = generateFailure(
            """
            @init
            fn launch() -> int
                let value: int = 1
                return 0
            end
            """
        );

        assertEquals(
            first.reason(),
            second.reason()
        );

        assertEquals(
            first.getMessage(),
            second.getMessage()
        );

        assertFalse(
            first.getMessage()
                .contains("@")
        );
    }

    private static CCodeGenerationException
    generateFailure(
        String source
    ) {
        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(
                    module(
                        "application",
                        source
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

        return assertThrows(
            CCodeGenerationException.class,
            () ->
                CCodeGenerator.generate(
                    program
                )
        );
    }

    private static SourceModule module(
        String name,
        String source
    ) {
        return new SourceModule(
            new ModuleName(
                List.of(
                    name.split("\\.")
                )
            ),
            Parser.parse(
                Lexer.scan(source)
            )
        );
    }
}
