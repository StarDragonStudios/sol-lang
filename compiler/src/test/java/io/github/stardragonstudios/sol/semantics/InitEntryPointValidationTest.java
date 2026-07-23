package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InitEntryPointValidationTest {
    @Test
    void resolvesAnnotatedFunctionWithCanonicalModuleAndFunctionSymbols() {
        var library = module(
            "library",
            """
            fn helper() -> int
                return 1
            end
            """
        );

        var application = module(
            "company.product.cli",
            """
            @init
            fn launch(argument: string, retries: int) -> int
                return 0
            end
            """
        );

        var program = SemanticAnalyzer.analyzeProgram(
            List.of(
                library,
                application
            )
        );

        var applicationModule =
            program.moduleOf(
                application.name()
            ).orElseThrow();

        var applicationAnalysis =
            program.analysisOf(
                application.name()
            ).orElseThrow();

        var declaration =
            functionAt(
                application,
                0
            );

        var function =
            applicationAnalysis
                .model()
                .symbolOf(
                    declaration
                )
                .orElseThrow();

        var entryPoint =
            program.entryPoint()
                .orElseThrow();

        assertSame(
            applicationModule,
            entryPoint.module()
        );

        assertSame(
            function,
            entryPoint.function()
        );

        assertSame(
            function,
            applicationAnalysis
                .model()
                .moduleScope()
                .lookupLocal(
                    "launch"
                )
                .orElseThrow()
        );

        assertEquals(
            "launch",
            entryPoint.function()
                .name()
        );

        assertTrue(
            applicationAnalysis
                .diagnostics()
                .isEmpty()
        );

        assertTrue(
            program.programDiagnostics()
                .isEmpty()
        );
    }

    @Test
    void acceptsZeroOneAndMultipleParameters() {
        var zero = module(
            "zero",
            """
            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var one = module(
            "one",
            """
            @init
            fn launch(argument: string) -> int
                return 0
            end
            """
        );

        var multiple = module(
            "multiple",
            """
            @init
            fn launch(argument: string, retries: int) -> int
                return 7
            end
            """
        );

        assertTrue(
            SemanticAnalyzer
                .analyzeProgram(
                    List.of(zero)
                )
                .entryPoint()
                .isPresent()
        );

        assertTrue(
            SemanticAnalyzer
                .analyzeProgram(
                    List.of(one)
                )
                .entryPoint()
                .isPresent()
        );

        assertTrue(
            SemanticAnalyzer
                .analyzeProgram(
                    List.of(multiple)
                )
                .entryPoint()
                .isPresent()
        );
    }

    @Test
    void functionNameDoesNotSelectTheEntryPoint() {
        var application = module(
            "application",
            """
            fn init() -> int
                return 0
            end

            fn main() -> int
                return 0
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertEquals(
            List.of("SOL-S023"),
            program.programDiagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );

        assertTrue(
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .isEmpty()
        );
    }

    @Test
    void annotationNameIsCaseSensitive() {
        var application = module(
            "application",
            """
            @Init
            fn first() -> int
                return 0
            end

            @INIT
            fn second() -> int
                return 0
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertEquals(
            List.of("SOL-S023"),
            program.programDiagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );
    }

    @Test
    void generalAndIsolatedAnalysisDoNotRequireAnEntryPoint() {
        var source =
            """
            fn helper() -> int
                return 1
            end
            """;

        var unit =
            Parser.parse(
                Lexer.scan(source)
            );

        var isolated =
            SemanticAnalyzer.analyze(
                unit
            );

        var library =
            new SourceModule(
                new ModuleName(
                    List.of("library")
                ),
                unit
            );

        var general =
            SemanticAnalyzer.analyzeModules(
                List.of(library)
            );

        assertTrue(
            isolated.diagnostics()
                .isEmpty()
        );

        assertTrue(
            general.programDiagnostics()
                .isEmpty()
        );

        assertTrue(
            general.entryPoint()
                .isEmpty()
        );
    }

    @Test
    void generalAnalysisExposesAValidPresentEntryPoint() {
        var application = module(
            "application",
            """
            @init
            fn start() -> int
                return 0
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeModules(
                List.of(application)
            );

        assertTrue(
            program.entryPoint()
                .isPresent()
        );

        assertTrue(
            program.programDiagnostics()
                .isEmpty()
        );
    }

    @Test
    void executableAnalysisReportsMissingEntryPointAsProgramDiagnostic() {
        var application = module(
            "application",
            """
            fn helper() -> int
                return 1
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        var diagnostic =
            assertSingleProgramDiagnostic(
                program,
                "SOL-S023"
            );

        assertEquals(
            DiagnosticSeverity.ERROR,
            diagnostic.severity()
        );

        assertEquals(
            "Executable program must declare exactly one function annotated with '@init'.",
            diagnostic.message()
        );

        assertEquals(
            syntheticProgramSpan(),
            diagnostic.span()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );

        assertTrue(
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .isEmpty()
        );
    }

    @Test
    void executableAnalysisOfEmptyModuleCollectionReportsMissingEntryPoint() {
        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of()
            );

        assertEquals(
            List.of("SOL-S023"),
            program.programDiagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            program.moduleNames()
                .isEmpty()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );
    }

    @Test
    void reportsEveryAdditionalEntryPointInDeclarationOrder() {
        var firstModule = module(
            "first",
            """
            @init
            fn firstStart() -> int
                return 0
            end
            """
        );

        var secondModule = module(
            "second",
            """
            @init
            fn secondStart() -> int
                return 0
            end

            @init
            fn thirdStart() -> int
                return 0
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(
                    firstModule,
                    secondModule
                )
            );

        var firstAnalysis =
            program.analysisOf(
                firstModule.name()
            ).orElseThrow();

        var secondAnalysis =
            program.analysisOf(
                secondModule.name()
            ).orElseThrow();

        assertTrue(
            firstAnalysis.diagnostics()
                .isEmpty()
        );

        assertEquals(
            List.of(
                "SOL-S024",
                "SOL-S024"
            ),
            secondAnalysis.diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertEquals(
            List.of(
                "Function 'secondStart' is an additional '@init' entry point; only one is allowed.",
                "Function 'thirdStart' is an additional '@init' entry point; only one is allowed."
            ),
            secondAnalysis.diagnostics()
                .stream()
                .map(Diagnostic::message)
                .toList()
        );

        assertEquals(
            functionAt(
                secondModule,
                0
            )
                .annotations()
                .getFirst()
                .span(),
            secondAnalysis
                .diagnostics()
                .get(0)
                .span()
        );

        assertEquals(
            functionAt(
                secondModule,
                1
            )
                .annotations()
                .getFirst()
                .span(),
            secondAnalysis
                .diagnostics()
                .get(1)
                .span()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );

        assertTrue(
            program.programDiagnostics()
                .isEmpty()
        );
    }

    @Test
    void repeatedInitAnnotationsOnOneFunctionCountAsOneCandidate() {
        var application = module(
            "application",
            """
            @init
            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertTrue(
            program.entryPoint()
                .isPresent()
        );

        assertTrue(
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .isEmpty()
        );
    }

    @Test
    void bodylessEntryPointProducesSolS025() {
        var application = module(
            "application",
            """
            @init
            @fn nativeStart() -> int
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        var analysis =
            program.analysisOf(
                application.name()
            ).orElseThrow();

        var diagnostic =
            assertSingleModuleDiagnostic(
                analysis,
                "SOL-S025"
            );

        assertEquals(
            "Entry point 'nativeStart' must have a function body.",
            diagnostic.message()
        );

        assertEquals(
            functionAt(
                application,
                0
            ).span(),
            diagnostic.span()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );
    }

    @Test
    void nonIntEntryPointProducesSolS026() {
        var application = module(
            "application",
            """
            @init
            fn start() -> void
                return
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        var analysis =
            program.analysisOf(
                application.name()
            ).orElseThrow();

        var diagnostic =
            assertSingleModuleDiagnostic(
                analysis,
                "SOL-S026"
            );

        assertEquals(
            "Entry point 'start' must return 'int', but returns 'void'.",
            diagnostic.message()
        );

        assertEquals(
            functionAt(
                application,
                0
            )
                .returnType()
                .span(),
            diagnostic.span()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );
    }

    @Test
    void bodylessNonIntEntryPointReportsIndependentViolations() {
        var application = module(
            "application",
            """
            @init
            @fn start() -> void
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertEquals(
            List.of(
                "SOL-S025",
                "SOL-S026"
            ),
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );
    }

    @Test
    void unknownReturnTypeSuppressesSolS026() {
        var application = module(
            "application",
            """
            @init
            fn start() -> unknown
                return
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertEquals(
            List.of("SOL-S003"),
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );
    }

    @Test
    void invalidParameterTypesRetainOrdinaryDiagnostics() {
        var application = module(
            "application",
            """
            @init
            fn start(value: void) -> int
                return 0
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertEquals(
            List.of("SOL-S012"),
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            program.entryPoint()
                .isPresent()
        );
    }

    @Test
    void invalidBodyDoesNotSuppressStructurallyValidEntryPoint() {
        var application = module(
            "application",
            """
            @init
            fn start() -> int
                return missing()
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertEquals(
            List.of("SOL-S002"),
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            program.entryPoint()
                .isPresent()
        );
    }

    @Test
    void annotatedDuplicateDeclarationCannotBecomeEntryPoint() {
        var application = module(
            "application",
            """
            fn start() -> int
                return 0
            end

            @init
            fn start() -> int
                return 0
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertEquals(
            List.of("SOL-S001"),
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertTrue(
            program.entryPoint()
                .isEmpty()
        );

        assertTrue(
            program.programDiagnostics()
                .isEmpty()
        );
    }

    @Test
    void laterUnannotatedDuplicateDoesNotReplaceValidEntryPoint() {
        var application = module(
            "application",
            """
            @init
            fn start() -> int
                return 0
            end

            fn start() -> int
                return 1
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(application)
            );

        assertEquals(
            List.of("SOL-S001"),
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .stream()
                .map(Diagnostic::code)
                .toList()
        );

        assertSame(
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .model()
                .symbolOf(
                    functionAt(
                        application,
                        0
                    )
                )
                .orElseThrow(),
            program.entryPoint()
                .orElseThrow()
                .function()
        );
    }

    @Test
    void importedEntryPointDeclarationIsCountedOnlyOnce() {
        var bootstrap = module(
            "bootstrap",
            """
            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var application = module(
            "application",
            """
            inject bootstrap only launch

            fn helper() -> int
                return launch()
            end
            """
        );

        var program =
            SemanticAnalyzer.analyzeProgram(
                List.of(
                    bootstrap,
                    application
                )
            );

        assertTrue(
            program.entryPoint()
                .isPresent()
        );

        assertTrue(
            program.analysisOf(
                application.name()
            )
                .orElseThrow()
                .diagnostics()
                .isEmpty()
        );
    }

    @Test
    void rejectsNullProgramInputs() {
        assertThrows(
            NullPointerException.class,
            () ->
                SemanticAnalyzer
                    .analyzeProgram(
                        null
                    )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                SemanticAnalyzer
                    .analyzeProgram(
                        java.util.Arrays.asList(
                            (SourceModule) null
                        )
                    )
        );
    }

    private static Diagnostic
    assertSingleProgramDiagnostic(
        SemanticProgramAnalysisResult result,
        String expectedCode
    ) {
        assertEquals(
            1,
            result.programDiagnostics()
                .size()
        );

        var diagnostic =
            result.programDiagnostics()
                .getFirst();

        assertEquals(
            expectedCode,
            diagnostic.code()
        );

        return diagnostic;
    }

    private static Diagnostic
    assertSingleModuleDiagnostic(
        SemanticAnalysisResult result,
        String expectedCode
    ) {
        assertEquals(
            1,
            result.diagnostics()
                .size()
        );

        var diagnostic =
            result.diagnostics()
                .getFirst();

        assertEquals(
            expectedCode,
            diagnostic.code()
        );

        return diagnostic;
    }

    private static SourceSpan
    syntheticProgramSpan() {
        return new SourceSpan(
            new SourcePosition(
                0,
                1,
                1
            ),
            new SourcePosition(
                0,
                1,
                1
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

    private static FunctionDeclaration functionAt(
        SourceModule module,
        int index
    ) {
        return assertInstanceOf(
            FunctionDeclaration.class,
            module.unit()
                .declarations()
                .get(index)
        );
    }
}
