package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.SemanticAnalysisResult;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult;
import io.github.stardragonstudios.sol.semantics.types.TypeSubstitution;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.syntax.CallExpression;
import io.github.stardragonstudios.sol.syntax.SyntaxExpressions;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

record IrMonomorphizationPlan(List<IrFunctionInstantiation> functions) {
    IrMonomorphizationPlan {
        Objects.requireNonNull(functions, "Monomorphization plan functions must not be null.");
        functions = List.copyOf(functions);
    }

    static IrMonomorphizationPlan create(SemanticProgramAnalysisResult program) {
        Objects.requireNonNull(program, "Semantic program must not be null.");

        var models = new IdentityHashMap<FunctionSymbol, SemanticModel>();
        var roots = new ArrayList<IrFunctionInstantiation>();

        for (var moduleName : program.moduleNames()) {
            var module = program.moduleOf(moduleName).orElseThrow(() -> missingModule(moduleName));
            var model = requireAnalysis(program, moduleName).model();

            for (var function : module.exportedFunctions()) {
                models.put(function, model);

                if (function.typeParameters().isEmpty()) roots.add(IrFunctionInstantiation.canonical(function));
            }
        }

        validateFiniteInstantiations(roots, models);

        var ordered = new ArrayList<IrFunctionInstantiation>(roots);
        var known = new LinkedHashSet<IrFunctionInstantiation>(roots);

        for (var index = 0; index < ordered.size(); index++) {
            var current = ordered.get(index);

            for (var called : calledInstantiations(current, models)) if (known.add(called)) ordered.add(called);
        }

        return new IrMonomorphizationPlan(ordered);
    }

    private static void validateFiniteInstantiations(
        List<IrFunctionInstantiation> roots,
        IdentityHashMap<FunctionSymbol, SemanticModel> models
    ) {
        var completed = new LinkedHashSet<IrFunctionInstantiation>();
        var active = new ArrayList<IrFunctionInstantiation>();

        for (var root : roots) validateFiniteInstantiations(root, models, completed, active);
    }

    private static void validateFiniteInstantiations(
        IrFunctionInstantiation current,
        IdentityHashMap<FunctionSymbol, SemanticModel> models,
        Set<IrFunctionInstantiation> completed,
        List<IrFunctionInstantiation> active
    ) {
        if (completed.contains(current)) return;

        for (var ancestor : active) {
            if (ancestor.equals(current)) return;

            if (ancestor.function() == current.function()) throw new IrLoweringException(
                "Generic function '%s' recursively requests expanding instantiation '%s'."
                    .formatted(current.function().name(), current.irName())
            );
        }

        active.add(current);

        for (var called : calledInstantiations(current, models))
            validateFiniteInstantiations(called, models, completed, active);

        active.removeLast();
        completed.add(current);
    }

    private static List<IrFunctionInstantiation> calledInstantiations(
        IrFunctionInstantiation caller,
        IdentityHashMap<FunctionSymbol, SemanticModel> models
    ) {
        var body = caller.function().declaration().body();

        if (body.isEmpty()) return List.of();

        var model = models.get(caller.function());

        if (model == null) throw new IrLoweringException(
            "Function '%s' has no owning semantic model during monomorphization."
                .formatted(caller.function().name())
        );

        var calls = new ArrayList<IrFunctionInstantiation>();
        var substitutions = caller.substitutions();

        for (var expression : SyntaxExpressions.in(body.orElseThrow())) {
            if (!(expression instanceof CallExpression call)) continue;

            var target = model.calledFunctionOf(call).orElseThrow(() -> new IrLoweringException(
                "Call in function '%s' has no semantic target during monomorphization."
                    .formatted(caller.function().name())
            ));
            var arguments = new ArrayList<TypeSymbol>();

            for (var argument : model.typeArgumentsOf(call))
                arguments.add(TypeSubstitution.substitute(argument, substitutions));

            calls.add(new IrFunctionInstantiation(target, arguments));
        }

        return calls;
    }

    private static SemanticAnalysisResult requireAnalysis(SemanticProgramAnalysisResult program, ModuleName name) {
        return program.analysisOf(name).orElseThrow(() -> new IrLoweringException(
            "Semantic program has no analysis for module '%s'.".formatted(name.qualifiedName())
        ));
    }

    private static IrLoweringException missingModule(ModuleName name) {
        return new IrLoweringException(
            "Semantic program has no canonical module '%s'.".formatted(name.qualifiedName())
        );
    }
}
