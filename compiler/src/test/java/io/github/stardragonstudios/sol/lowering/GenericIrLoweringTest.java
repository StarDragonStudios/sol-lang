package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrCallInstruction;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrStructConstructInstruction;
import io.github.stardragonstudios.sol.ir.IrStructFieldExtractInstruction;
import io.github.stardragonstudios.sol.ir.IrTextFormatter;
import io.github.stardragonstudios.sol.lexer.Lexer;
import io.github.stardragonstudios.sol.parser.Parser;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalyzer;
import io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult;
import io.github.stardragonstudios.sol.semantics.SourceModule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenericIrLoweringTest {
    @Test
    void monomorphizesFunctionsForEachConcreteTypeArgument() {
        var lowered = lower(module(
            "application",
            """
            fn identity<T>(value: T) -> T
                return value
            end

            @init
            fn launch() -> int
                let number: int = identity<int>(42)
                let enabled: boolean = identity<boolean>(true)
                if enabled then
                    return number
                else
                    return 0
                end
            end
            """
        ));
        var module = lowered.modules().getFirst();

        assertEquals(
            List.of("launch", "identity$int", "identity$boolean"),
            module.functions().stream().map(IrFunction::name).toList()
        );

        var targets = module.function("launch").orElseThrow().blocks().stream()
            .flatMap(block -> block.instructions().stream())
            .filter(IrCallInstruction.class::isInstance)
            .map(IrCallInstruction.class::cast)
            .map(call -> call.target().name())
            .toList();

        assertEquals(List.of("identity$int", "identity$boolean"), targets);
    }

    @Test
    void monomorphizesGenericStructLayoutsAndFunctionBodies() {
        var lowered = lower(module(
            "application",
            """
            struct Box<T>
                value: T
            end

            fn wrap<T>(value: T) -> Box<T>
                return Box<T> { value: value }
            end

            @init
            fn launch() -> int
                let number: Box<int> = wrap<int>(42)
                let flag: Box<boolean> = wrap<boolean>(true)
                if flag.value then
                    return number.value
                else
                    return 0
                end
            end
            """
        ));
        var module = lowered.modules().getFirst();

        assertEquals(
            List.of("application::Box<int>", "application::Box<boolean>"),
            module.structs().stream().map(type -> type.displayName()).toList()
        );
        assertEquals("int", module.structs().get(0).fields().getFirst().type().displayName());
        assertEquals("boolean", module.structs().get(1).fields().getFirst().type().displayName());
        assertEquals(
            List.of("launch", "wrap$int", "wrap$boolean"),
            module.functions().stream().map(IrFunction::name).toList()
        );

        for (var name : List.of("wrap$int", "wrap$boolean")) assertTrue(
            module.function(name).orElseThrow().blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .anyMatch(IrStructConstructInstruction.class::isInstance)
        );

        assertTrue(module.function("launch").orElseThrow().blocks().stream()
            .flatMap(block -> block.instructions().stream())
            .anyMatch(IrStructFieldExtractInstruction.class::isInstance));
    }

    @Test
    void sharesCrossModuleConcreteGenericInstances() {
        var lowered = lower(
            module(
                "core",
                """
                struct Result<T>
                    value: T
                end

                fn result<T>(value: T) -> Result<T>
                    return Result<T> { value: value }
                end

                fn identity<T>(value: T) -> T
                    return value
                end
                """
            ),
            module(
                "application",
                """
                inject core only Result, result, identity

                @init
                fn launch() -> int
                    let answer: Result<int> = result<int>(42)
                    let copied: Result<int> = identity<Result<int>>(answer)
                    return copied.value
                end
                """
            )
        );
        var core = lowered.modules().getFirst();
        var application = lowered.modules().get(1);

        assertEquals(List.of("core::Result<int>"), core.structs().stream().map(type -> type.displayName()).toList());
        assertEquals(
            List.of("result$int", "identity$score_Result_int_e"),
            core.functions().stream().map(IrFunction::name).toList()
        );
        assertEquals(List.of("launch"), application.functions().stream().map(IrFunction::name).toList());

        var calls = callsOf(application.functions().getFirst());
        var target = calls.getFirst().target();

        assertEquals("result$int", target.name());
        assertEquals(core.structs().getFirst(), target.returnType());
        assertEquals("identity$score_Result_int_e", calls.get(1).target().name());
        assertEquals(core.structs().getFirst(), calls.get(1).target().returnType());
    }

    @Test
    void monomorphizesNamespaceQualifiedGenericCalls() {
        var lowered = lower(
            module(
                "core",
                """
                fn identity<T>(value: T) -> T
                    return value
                end
                """
            ),
            module(
                "application",
                """
                inject namespace core as utilities

                @init
                fn launch() -> int
                    return utilities::identity<int>(42)
                end
                """
            )
        );

        assertEquals(List.of("identity$int"), lowered.modules().getFirst().functions().stream().map(IrFunction::name).toList());
        assertEquals(
            "identity$int",
            callsOf(lowered.modules().get(1).function("launch").orElseThrow()).getFirst().target().name()
        );
    }

    @Test
    void sharesOneCanonicalSpecializationAndAllowsSameTypeRecursion() {
        var lowered = lower(module(
            "application",
            """
            fn repeat<T>(value: T, again: boolean) -> T
                if again then
                    return repeat<T>(value, false)
                end

                return value
            end

            @init
            fn launch() -> int
                return repeat<int>(42, true)
            end
            """
        ));
        var module = lowered.modules().getFirst();
        var launch = module.function("launch").orElseThrow();
        var repeat = module.function("repeat$int").orElseThrow();
        var launchTarget = callsOf(launch).getFirst().target();
        var recursiveTarget = callsOf(repeat).getFirst().target();

        assertEquals(List.of("launch", "repeat$int"), module.functions().stream().map(IrFunction::name).toList());
        assertEquals(repeat.id(), launchTarget.id());
        assertSame(launchTarget, recursiveTarget);
    }

    @Test
    void omitsUnusedOpenGenericsAndProducesDeterministicIr() {
        var analyzed = analyze(module(
            "application",
            """
            struct Box<T>
                value: T
            end

            fn unused<T>(value: T) -> Box<T>
                return Box<T> { value: value }
            end

            @init
            fn launch() -> int
                return 0
            end
            """
        ));

        assertNoDiagnostics(analyzed);

        var first = IrProgramLowerer.lower(analyzed);
        var second = IrProgramLowerer.lower(analyzed);

        assertEquals(List.of(), first.modules().getFirst().structs());
        assertEquals(List.of("launch"), first.modules().getFirst().functions().stream().map(IrFunction::name).toList());
        assertEquals(first, second);
        assertEquals(IrTextFormatter.format(first), IrTextFormatter.format(second));
    }

    @Test
    void rejectsExpandingRecursiveInstantiations() {
        var analyzed = analyze(module(
            "application",
            """
            struct Box<T>
                value: T
            end

            fn expand<T>(value: T) -> void
                expand<Box<T>>(Box<T> { value: value })
                return
            end

            @init
            fn launch() -> int
                expand<int>(1)
                return 0
            end
            """
        ));

        assertEquals(
            List.of("SOL-S042"),
            analyzed.analysisOf(new ModuleName(List.of("application"))).orElseThrow().diagnostics().stream()
                .map(diagnostic -> diagnostic.code())
                .toList()
        );
        assertThrows(IrLoweringException.class, () -> IrProgramLowerer.lower(analyzed));
    }

    private static io.github.stardragonstudios.sol.ir.IrProgram lower(SourceModule... modules) {
        var analyzed = analyze(modules);

        assertNoDiagnostics(analyzed);

        return IrProgramLowerer.lower(analyzed);
    }

    private static SemanticProgramAnalysisResult analyze(SourceModule... modules) {
        return SemanticAnalyzer.analyzeProgram(List.of(modules));
    }

    private static List<IrCallInstruction> callsOf(IrFunction function) {
        return function.blocks().stream()
            .flatMap(block -> block.instructions().stream())
            .filter(IrCallInstruction.class::isInstance)
            .map(IrCallInstruction.class::cast)
            .toList();
    }

    private static SourceModule module(String name, String source) {
        return new SourceModule(new ModuleName(List.of(name)), Parser.parse(Lexer.scan(source)));
    }

    private static void assertNoDiagnostics(SemanticProgramAnalysisResult analyzed) {
        assertTrue(analyzed.programDiagnostics().isEmpty(), analyzed.programDiagnostics().toString());

        for (var moduleName : analyzed.moduleNames()) {
            var diagnostics = analyzed.analysisOf(moduleName).orElseThrow().diagnostics();

            assertTrue(diagnostics.isEmpty(), diagnostics.toString());
        }
    }
}
