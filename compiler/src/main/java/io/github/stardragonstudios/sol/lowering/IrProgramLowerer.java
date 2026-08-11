package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.ir.IrEntryPoint;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.ir.IrStructField;
import io.github.stardragonstudios.sol.ir.IrStructType;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;
import io.github.stardragonstudios.sol.semantics.ModuleName;
import io.github.stardragonstudios.sol.semantics.ModuleSymbol;
import io.github.stardragonstudios.sol.semantics.SemanticAnalysisResult;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.semantics.SemanticProgramAnalysisResult;
import io.github.stardragonstudios.sol.semantics.StructSymbol;
import io.github.stardragonstudios.sol.semantics.TypeParameterSymbol;
import io.github.stardragonstudios.sol.semantics.types.StructType;
import io.github.stardragonstudios.sol.semantics.types.TypeSubstitution;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.syntax.Expression;
import io.github.stardragonstudios.sol.syntax.SyntaxExpressions;
import io.github.stardragonstudios.sol.syntax.TypeReference;

import java.util.*;

public final class IrProgramLowerer {
    private IrProgramLowerer() {}

    public static IrProgram lower(SemanticProgramAnalysisResult program) {
        Objects.requireNonNull(program, "Lowered semantic program must not be null.");

        validateNoSemanticErrors(program);

        var plan = IrMonomorphizationPlan.create(program);
        var owners = collectOwners(program);
        var programContext = new IrProgramLoweringContext();
        var structInstances = discoverStructInstances(program, plan, owners);

        assignStructTypes(program, owners, structInstances, programContext);

        /*
         * Identifiers and typed references are assigned before any function
         * body is lowered. Calls may therefore target functions declared
         * later or functions belonging to another module.
         */
        assignFunctionIdentifiers(plan, programContext);
        assignFunctionReferences(program, plan, owners, programContext);

        var loweredModules = new ArrayList<IrModule>();
        var modulesBySymbol = new IdentityHashMap<ModuleSymbol, IrModule>();
        var functionsByInstantiation = new LinkedHashMap<IrFunctionInstantiation, IrFunction>();

        for (var moduleName : program.moduleNames()) {
            var module = requireModule(program, moduleName);
            var analysis = requireAnalysis(program, moduleName);
            var moduleFunctions = plan.functions().stream()
                .filter(instantiation -> owners.functions().get(instantiation.function()) == module)
                .toList();
            var moduleStructs = structInstances.stream()
                .filter(instance -> owners.structs().get(instance.symbol()) == module)
                .map(programContext::structType)
                .toList();
            var lowered = IrModuleLowerer.lower(
                module,
                analysis,
                programContext,
                moduleFunctions,
                moduleStructs,
                functionsByInstantiation
            );

            if (modulesBySymbol.put(module, lowered) != null)
                throw new IrLoweringException("Module '%s' has already been lowered.".formatted(moduleName.qualifiedName()));

            loweredModules.add(lowered);
        }

        var semanticEntryPoint = program.entryPoint();

        if (semanticEntryPoint.isEmpty()) return createLibraryProgram(loweredModules);

        var entryPoint = semanticEntryPoint.orElseThrow();
        var entryModule = modulesBySymbol.get(entryPoint.module());

        if (entryModule == null)
            throw new IrLoweringException(
                "Semantic entry point module '%s' was not lowered.".formatted(entryPoint.module().name().qualifiedName())
            );

        var entryFunction = functionsByInstantiation.get(IrFunctionInstantiation.canonical(entryPoint.function()));

        if (entryFunction == null)
            throw new IrLoweringException(
                "Semantic entry point function '%s' was not lowered.".formatted(entryPoint.function().name())
            );

        try {
            return IrProgram.executable(loweredModules, new IrEntryPoint(entryModule, entryFunction));
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException("Semantic entry point produced invalid IR: %s".formatted(exception.getMessage()));
        }
    }

    private static void assignFunctionIdentifiers(IrMonomorphizationPlan plan, IrProgramLoweringContext context) {
        for (var instantiation : plan.functions()) context.assignFunctionInstantiationId(instantiation);
    }

    private static void assignStructTypes(
        SemanticProgramAnalysisResult program,
        SemanticOwners owners,
        List<StructType> instances,
        IrProgramLoweringContext context
    ) {
        var lowered = new HashSet<StructType>();
        var visiting = new HashSet<StructType>();

        for (var instance : instances) lowerStructType(program, instance, owners, context, lowered, visiting);
    }

    private static IrStructType lowerStructType(
        SemanticProgramAnalysisResult program,
        StructType instance,
        SemanticOwners owners,
        IrProgramLoweringContext context,
        Set<StructType> lowered,
        Set<StructType> visiting
    ) {
        if (lowered.contains(instance)) return context.structType(instance);
        if (!visiting.add(instance)) throw new IrLoweringException(
            "Struct type '%s' has a recursive value layout during IR lowering.".formatted(instance.name())
        );

        var struct = instance.symbol();
        var module = owners.structs().get(struct);

        if (module == null) throw new IrLoweringException("Struct '%s' has no owning semantic module.".formatted(struct.name()));

        var model = requireAnalysis(program, module.name()).model();
        var substitutions = structSubstitutions(instance);
        var fields = new ArrayList<IrStructField>();

        for (var field : struct.fields()) {
            var openType = requireType(model, field.type(), "field '%s' of struct '%s'".formatted(field.name(), struct.name()));
            var semanticType = TypeSubstitution.substitute(openType, substitutions);
            IrType fieldType;

            if (semanticType instanceof StructType nested) {
                fieldType = lowerStructType(program, nested, owners, context, lowered, visiting);
            } else {
                fieldType = IrTypeLowerer.lower(semanticType);
            }

            fields.add(new IrStructField(field.name(), fieldType, field.index()));
        }

        visiting.remove(instance);

        var qualifiedName = qualifiedTypeName(instance, owners);
        var type = new IrStructType(qualifiedName, fields);

        context.assignStructType(instance, type);
        lowered.add(instance);

        return type;
    }

    private static void assignFunctionReferences(
        SemanticProgramAnalysisResult program,
        IrMonomorphizationPlan plan,
        SemanticOwners owners,
        IrProgramLoweringContext context
    ) {
        for (var instantiation : plan.functions()) {
            var function = instantiation.function();
            var module = owners.functions().get(function);

            if (module == null) throw new IrLoweringException(
                "Function '%s' has no owning semantic module.".formatted(function.name())
            );

            var model = requireAnalysis(program, module.name()).model();
            var parameterTypes = new ArrayList<IrType>();

            for (var parameter : function.declaration().parameters()) {
                var openType = requireType(
                    model,
                    parameter.type(),
                    "parameter '%s' of function '%s'".formatted(parameter.name(), function.name())
                );
                var semanticType = TypeSubstitution.substitute(openType, instantiation.substitutions());

                parameterTypes.add(IrTypeLowerer.lower(semanticType, context));
            }

            var openReturnType = requireType(
                model,
                function.declaration().returnType(),
                "return type of function '%s'".formatted(function.name())
            );
            var returnType = IrTypeLowerer.lower(
                TypeSubstitution.substitute(openReturnType, instantiation.substitutions()),
                context
            );

            context.assignFunctionInstantiationReference(instantiation, parameterTypes, returnType);
        }
    }

    private static SemanticOwners collectOwners(SemanticProgramAnalysisResult program) {
        var functions = new IdentityHashMap<FunctionSymbol, ModuleSymbol>();
        var structs = new IdentityHashMap<StructSymbol, ModuleSymbol>();

        for (var moduleName : program.moduleNames()) {
            var module = requireModule(program, moduleName);

            for (var function : module.exportedFunctions()) functions.put(function, module);
            for (var struct : module.exportedStructs()) structs.put(struct, module);
        }

        return new SemanticOwners(functions, structs);
    }

    private static List<StructType> discoverStructInstances(
        SemanticProgramAnalysisResult program,
        IrMonomorphizationPlan plan,
        SemanticOwners owners
    ) {
        var ordered = new ArrayList<StructType>();
        var known = new LinkedHashSet<StructType>();

        for (var moduleName : program.moduleNames()) {
            var module = requireModule(program, moduleName);

            for (var struct : module.exportedStructs())
                if (struct.typeParameters().isEmpty()) addStructType(struct.type(), known, ordered);
        }

        for (var instantiation : plan.functions()) {
            var function = instantiation.function();
            var module = owners.functions().get(function);

            if (module == null) throw new IrLoweringException(
                "Function '%s' has no owning semantic module.".formatted(function.name())
            );

            var model = requireAnalysis(program, module.name()).model();
            var substitutions = instantiation.substitutions();

            for (var parameter : function.declaration().parameters())
                addStructType(TypeSubstitution.substitute(requireType(
                    model,
                    parameter.type(),
                    "parameter '%s' of function '%s'".formatted(parameter.name(), function.name())
                ), substitutions), known, ordered);

            addStructType(TypeSubstitution.substitute(requireType(
                model,
                function.declaration().returnType(),
                "return type of function '%s'".formatted(function.name())
            ), substitutions), known, ordered);

            function.declaration().body().ifPresent(body -> {
                for (Expression expression : SyntaxExpressions.in(body)) model.typeOf(expression)
                    .map(type -> TypeSubstitution.substitute(type, substitutions))
                    .ifPresent(type -> addStructType(type, known, ordered));
            });
        }

        for (var index = 0; index < ordered.size(); index++) {
            var instance = ordered.get(index);
            var module = owners.structs().get(instance.symbol());

            if (module == null) throw new IrLoweringException(
                "Struct '%s' has no owning semantic module.".formatted(instance.symbol().name())
            );

            var model = requireAnalysis(program, module.name()).model();
            var substitutions = structSubstitutions(instance);

            for (var field : instance.symbol().fields()) addStructType(
                TypeSubstitution.substitute(requireType(
                    model,
                    field.type(),
                    "field '%s' of struct '%s'".formatted(field.name(), instance.symbol().name())
                ), substitutions),
                known,
                ordered
            );
        }

        return List.copyOf(ordered);
    }

    private static void addStructType(TypeSymbol type, Set<StructType> known, List<StructType> ordered) {
        if (!(type instanceof StructType struct) || !known.add(struct)) return;

        ordered.add(struct);
    }

    private static IdentityHashMap<TypeParameterSymbol, TypeSymbol> structSubstitutions(StructType instance) {
        var substitutions = new IdentityHashMap<TypeParameterSymbol, TypeSymbol>();
        var parameters = instance.symbol().typeParameters();

        for (var index = 0; index < parameters.size(); index++)
            substitutions.put(parameters.get(index), instance.arguments().get(index));

        return substitutions;
    }

    private static String qualifiedTypeName(StructType type, SemanticOwners owners) {
        var module = owners.structs().get(type.symbol());

        if (module == null) throw new IrLoweringException(
            "Struct '%s' has no owning semantic module.".formatted(type.symbol().name())
        );

        if (type.arguments().isEmpty())
            return "%s::%s".formatted(module.name().qualifiedName(), type.symbol().name());

        var arguments = new StringJoiner(", ", "<", ">");

        for (var argument : type.arguments())
            arguments.add(argument instanceof StructType nested ? qualifiedTypeName(nested, owners) : argument.name());

        return "%s::%s%s".formatted(module.name().qualifiedName(), type.symbol().name(), arguments);
    }

    private record SemanticOwners(
        IdentityHashMap<FunctionSymbol, ModuleSymbol> functions,
        IdentityHashMap<StructSymbol, ModuleSymbol> structs
    ) {}

    private static TypeSymbol requireType(SemanticModel model, TypeReference reference, String description) {
        return model.typeOf(reference).orElseThrow(() -> new IrLoweringException("The %s has no resolved semantic type.".formatted(description)));
    }

    private static IrProgram createLibraryProgram(ArrayList<IrModule> modules) {
        try {
            return IrProgram.library(modules);
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException("Semantic library program produced invalid IR: %s".formatted(exception.getMessage()));
        }
    }

    private static ModuleSymbol requireModule(SemanticProgramAnalysisResult program, ModuleName name) {
        return program.moduleOf(name).orElseThrow(() -> new IrLoweringException("Semantic program has no canonical module '%s'.".formatted(name.qualifiedName())));
    }

    private static SemanticAnalysisResult requireAnalysis(SemanticProgramAnalysisResult program, ModuleName name) {
        return program.analysisOf(name).orElseThrow(() -> new IrLoweringException("Semantic program has no analysis for module '%s'.".formatted(name.qualifiedName())));
    }

    private static void validateNoSemanticErrors(SemanticProgramAnalysisResult program) {
        firstError(program.programDiagnostics()).ifPresent(diagnostic -> {throw semanticError(diagnostic);});

        for (var moduleName : program.moduleNames()) {
            var analysis = requireAnalysis(program, moduleName);

            firstError(analysis.diagnostics()).ifPresent(diagnostic -> {throw semanticError(diagnostic);});
        }
    }

    private static Optional<Diagnostic> firstError(List<Diagnostic> diagnostics) {
        return diagnostics.stream().filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR).findFirst();
    }

    private static IrLoweringException semanticError(Diagnostic diagnostic) {
        return new IrLoweringException("Cannot lower a semantic program containing error '%s': %s".formatted(diagnostic.code(), diagnostic.message()));
    }
}
