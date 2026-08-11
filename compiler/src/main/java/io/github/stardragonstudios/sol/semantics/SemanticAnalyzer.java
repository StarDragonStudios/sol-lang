package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.StructType;
import io.github.stardragonstudios.sol.semantics.types.TypeParameterType;
import io.github.stardragonstudios.sol.semantics.types.TypeSubstitution;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.source.SourcePosition;
import io.github.stardragonstudios.sol.source.SourceSpan;
import io.github.stardragonstudios.sol.syntax.*;
import java.util.*;

public final class SemanticAnalyzer {
    private static final String DUPLICATE_DECLARATION_CODE = "SOL-S001";
    private static final String UNRESOLVED_NAME_CODE = "SOL-S002";
    private static final String UNKNOWN_TYPE_CODE = "SOL-S003";
    private static final String NON_BOOLEAN_CONDITION_CODE = "SOL-S006";
    private static final String INVALID_VARIABLE_TYPE_CODE = "SOL-S007";
    private static final String INCOMPATIBLE_INITIALIZER_CODE = "SOL-S008";
    private static final String INVALID_ASSIGNMENT_TARGET_CODE = "SOL-S009";
    private static final String IMMUTABLE_ASSIGNMENT_CODE = "SOL-S010";
    private static final String INCOMPATIBLE_ASSIGNMENT_CODE = "SOL-S011";
    private static final String INVALID_PARAMETER_TYPE_CODE = "SOL-S012";
    private static final String NOT_CALLABLE_CODE = "SOL-S013";
    private static final String INCORRECT_ARGUMENT_COUNT_CODE = "SOL-S014";
    private static final String INCOMPATIBLE_ARGUMENT_CODE = "SOL-S015";
    private static final String MISSING_RETURN_VALUE_CODE = "SOL-S016";
    private static final String UNEXPECTED_RETURN_VALUE_CODE = "SOL-S017";
    private static final String INCOMPATIBLE_RETURN_CODE = "SOL-S018";
    private static final String UNRESOLVED_MODULE_CODE = "SOL-S019";
    private static final String UNKNOWN_INJECTED_SYMBOL_CODE = "SOL-S020";
    private static final String NON_NAMESPACE_QUALIFIER_CODE = "SOL-S021";
    private static final String UNKNOWN_NAMESPACE_MEMBER_CODE = "SOL-S022";
    private static final String MISSING_ENTRY_POINT_CODE = "SOL-S023";
    private static final String MULTIPLE_ENTRY_POINTS_CODE = "SOL-S024";
    private static final String BODYLESS_ENTRY_POINT_CODE = "SOL-S025";
    private static final String INVALID_ENTRY_POINT_RETURN_CODE = "SOL-S026";
    private static final String DUPLICATE_STRUCT_FIELD_CODE = "SOL-S027";
    private static final String INVALID_STRUCT_FIELD_TYPE_CODE = "SOL-S028";
    private static final String CYCLIC_STRUCT_LAYOUT_CODE = "SOL-S029";
    private static final String NOT_A_STRUCT_TYPE_CODE = "SOL-S030";
    private static final String UNKNOWN_STRUCT_FIELD_CODE = "SOL-S031";
    private static final String DUPLICATE_STRUCT_INITIALIZER_CODE = "SOL-S032";
    private static final String MISSING_STRUCT_INITIALIZER_CODE = "SOL-S033";
    private static final String INCOMPATIBLE_STRUCT_INITIALIZER_CODE = "SOL-S034";
    private static final String FIELD_ACCESS_ON_NON_STRUCT_CODE = "SOL-S035";
    private static final String UNKNOWN_FIELD_CODE = "SOL-S036";
    private static final String INVALID_FIELD_ASSIGNMENT_TARGET_CODE = "SOL-S037";
    private static final String RESERVED_STRUCT_NAME_CODE = "SOL-S038";
    private static final String DUPLICATE_TYPE_PARAMETER_CODE = "SOL-S039";
    private static final String GENERIC_ARITY_CODE = "SOL-S040";
    private static final String INVALID_TYPE_ARGUMENT_CODE = "SOL-S041";
    private static final String RECURSIVE_GENERIC_INSTANTIATION_CODE = "SOL-S042";

    private static final ModuleName ISOLATED_MODULE_NAME = new ModuleName(List.of("<isolated>"));
    private static final SourceSpan PROGRAM_DIAGNOSTIC_SPAN = new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(0, 1, 1));

    private SemanticAnalyzer() {}

    public static SemanticAnalysisResult analyze(CompilationUnit unit) {
        Objects.requireNonNull(unit, "Compilation unit must not be null.");

        var sourceModule = new SourceModule(ISOLATED_MODULE_NAME, unit);

        return analyzeModules(List.of(sourceModule)).analysisOf(ISOLATED_MODULE_NAME).orElseThrow();
    }

    public static SemanticProgramAnalysisResult analyzeModules(List<SourceModule> sourceModules) {
        Objects.requireNonNull(sourceModules, "Source modules must not be null.");

        return new ProgramBinder(sourceModules, false).bind();
    }

    public static SemanticProgramAnalysisResult analyzeProgram(List<SourceModule> sourceModules) {
        Objects.requireNonNull(sourceModules, "Source modules must not be null.");

        return new ProgramBinder(sourceModules, true).bind();
    }

    private static final class ProgramBinder {
        private final LinkedHashMap<ModuleName, ModuleSymbol> modules = new LinkedHashMap<>();
        private final LinkedHashMap<ModuleName, Binder> binders = new LinkedHashMap<>();
        private final IdentityHashMap<TypeReference, TypeSymbol> programResolvedTypes = new IdentityHashMap<>();

        private final boolean requireEntryPoint;

        private ProgramBinder(List<SourceModule> sourceModules, boolean requireEntryPoint) {
            this.requireEntryPoint = requireEntryPoint;

            var modulesCopy = new ArrayList<SourceModule>();

            for (var sourceModule : sourceModules) modulesCopy.add(Objects.requireNonNull(sourceModule, "Source modules must not contain null values."));

            for (var sourceModule : modulesCopy) {
                var module = new ModuleSymbol(sourceModule.name(), sourceModule.unit());

                if (modules.putIfAbsent(sourceModule.name(), module) != null)
                    throw new IllegalArgumentException("Duplicate source module '%s'.".formatted(sourceModule.name().qualifiedName()));
            }

            for (var sourceModule : modulesCopy) {
                var module = modules.get(sourceModule.name());

                binders.put(sourceModule.name(), new Binder(sourceModule, module, modules, programResolvedTypes));
            }
        }

        private SemanticProgramAnalysisResult bind() {
            binders.values().forEach(Binder::predeclareStructs);
            binders.values().forEach(Binder::predeclareFunctions);
            binders.values().forEach(Binder::resolveInjections);
            binders.values().forEach(Binder::bindStructDeclarations);
            validateStructLayouts();
            binders.values().forEach(Binder::bindFunctionSignatures);

            var programDiagnostics = new ArrayList<Diagnostic>();
            var entryPoint = resolveEntryPoint(programDiagnostics);

            binders.values().forEach(Binder::bindFunctionBodies);
            validateGenericInstantiations();

            var analyses = new LinkedHashMap<ModuleName, SemanticAnalysisResult>();

            binders.forEach((name, binder) -> analyses.put(name, binder.finish()));

            return new SemanticProgramAnalysisResult(modules, analyses, entryPoint, programDiagnostics);
        }

        private void validateGenericInstantiations() {
            var owners = new IdentityHashMap<FunctionSymbol, Binder>();
            var roots = new ArrayList<GenericFunctionInstantiation>();

            for (var binder : binders.values()) {
                for (var function : binder.module.exportedFunctions()) {
                    owners.put(function, binder);

                    if (function.typeParameters().isEmpty())
                        roots.add(new GenericFunctionInstantiation(function, List.of()));
                }
            }

            var completed = new HashSet<GenericFunctionInstantiation>();
            var active = new ArrayList<GenericFunctionInstantiation>();
            var reported = Collections.newSetFromMap(new IdentityHashMap<CallExpression, Boolean>());

            for (var root : roots) validateGenericInstantiation(root, owners, completed, active, reported);
        }

        private void validateGenericInstantiation(
            GenericFunctionInstantiation current,
            IdentityHashMap<FunctionSymbol, Binder> owners,
            Set<GenericFunctionInstantiation> completed,
            List<GenericFunctionInstantiation> active,
            Set<CallExpression> reported
        ) {
            if (completed.contains(current)) return;

            var binder = owners.get(current.function());

            if (binder == null) throw new IllegalStateException(
                "Function '%s' has no semantic binder during generic-instantiation validation."
                    .formatted(current.function().name())
            );

            var body = current.function().declaration().body();

            if (body.isEmpty()) {
                completed.add(current);
                return;
            }

            active.add(current);

            for (var expression : SyntaxExpressions.in(body.orElseThrow())) {
                if (!(expression instanceof CallExpression call)) continue;

                var target = binder.calledFunctions.get(call);

                if (target == null) continue;

                var openArguments = binder.calledFunctionTypeArguments.getOrDefault(call, List.of());

                if (openArguments.size() != target.typeParameters().size()) continue;

                var concreteArguments = openArguments.stream()
                    .map(argument -> TypeSubstitution.substitute(argument, current.substitutions()))
                    .toList();

                if (concreteArguments.stream().anyMatch(ProgramBinder::containsUnresolvedType)) continue;

                var called = new GenericFunctionInstantiation(target, concreteArguments);
                var recursive = false;

                for (var ancestor : active) {
                    if (ancestor.function() != called.function()) continue;

                    recursive = true;

                    if (!ancestor.equals(called) && reported.add(call)) binder.diagnostics.add(new Diagnostic(
                        RECURSIVE_GENERIC_INSTANTIATION_CODE,
                        DiagnosticSeverity.ERROR,
                        "Generic function '%s' recursively requests a different specialization '%s'."
                            .formatted(target.name(), called.displayName()),
                        call.span()
                    ));

                    break;
                }

                if (!recursive) validateGenericInstantiation(called, owners, completed, active, reported);
            }

            active.removeLast();
            completed.add(current);
        }

        private static boolean containsUnresolvedType(TypeSymbol type) {
            if (type == BuiltInTypes.ERROR || type instanceof TypeParameterType) return true;
            if (type instanceof StructType struct)
                return struct.arguments().stream().anyMatch(ProgramBinder::containsUnresolvedType);

            return false;
        }

        private void validateStructLayouts() {
            var owners = new IdentityHashMap<StructSymbol, Binder>();

            for (var binder : binders.values())
                for (var struct : binder.module.exportedStructs()) owners.put(struct, binder);

            var checked = new HashSet<StructType>();
            var invalid = new HashSet<StructType>();
            var reportedFields = Collections.newSetFromMap(new IdentityHashMap<StructFieldSymbol, Boolean>());

            for (var binder : binders.values())
                for (var struct : binder.module.exportedStructs()) validateStructLayout(
                    struct.type(),
                    owners,
                    checked,
                    invalid,
                    new ArrayList<>(),
                    reportedFields
                );
        }

        private boolean validateStructLayout(
            StructType instance,
            IdentityHashMap<StructSymbol, Binder> owners,
            Set<StructType> checked,
            Set<StructType> invalid,
            List<StructType> visiting,
            Set<StructFieldSymbol> reportedFields
        ) {
            if (checked.contains(instance)) return !invalid.contains(instance);

            var struct = instance.symbol();
            var substitutions = new IdentityHashMap<TypeParameterSymbol, TypeSymbol>();

            for (var index = 0; index < struct.typeParameters().size(); index++)
                substitutions.put(struct.typeParameters().get(index), instance.arguments().get(index));

            visiting.add(instance);

            for (var field : struct.fields()) {
                var openFieldType = programResolvedTypes.getOrDefault(field.type(), BuiltInTypes.ERROR);
                var fieldType = TypeSubstitution.substitute(openFieldType, substitutions);

                if (!(fieldType instanceof StructType nestedType)) continue;

                var recursive = visiting.stream().anyMatch(active -> active.symbol() == nestedType.symbol());

                if (recursive) {
                    var owner = owners.get(struct);

                    if (owner == null) throw new IllegalStateException("Struct '%s' has no semantic binder.".formatted(struct.name()));

                    if (reportedFields.add(field)) owner.diagnostics.add(new Diagnostic(
                        CYCLIC_STRUCT_LAYOUT_CODE,
                        DiagnosticSeverity.ERROR,
                        "Struct type '%s' has a recursive value layout through field '%s'."
                            .formatted(instance.name(), field.name()),
                        field.type().span()
                    ));

                    invalid.add(instance);
                    visiting.removeLast();
                    checked.add(instance);

                    return false;
                }

                if (!validateStructLayout(nestedType, owners, checked, invalid, visiting, reportedFields)) {
                    invalid.add(instance);
                    visiting.removeLast();
                    checked.add(instance);

                    return false;
                }
            }

            visiting.removeLast();
            checked.add(instance);

            return true;
        }

        private Optional<ProgramEntryPoint> resolveEntryPoint(List<Diagnostic> programDiagnostics) {
            var candidates = collectEntryPointCandidates();

            if (candidates.isEmpty()) {
                if (requireEntryPoint)
                    programDiagnostics.add(new Diagnostic(MISSING_ENTRY_POINT_CODE, DiagnosticSeverity.ERROR,
                        "Executable program must declare exactly one function annotated with '@init'.", PROGRAM_DIAGNOSTIC_SPAN));

                return Optional.empty();
            }

            var validCandidates = new IdentityHashMap<FunctionDeclaration, Boolean>();

            for (var candidate : candidates) validCandidates.put(candidate.declaration(), candidate.binder().validateEntryPointCandidate(candidate.declaration()));

            if (candidates.size() > 1) {
                for (var index = 1; index < candidates.size(); index++) {
                    var additional = candidates.get(index);

                    additional.binder().reportAdditionalEntryPoint(additional);
                }

                return Optional.empty();
            }

            var candidate = candidates.getFirst();

            if (!validCandidates.getOrDefault(candidate.declaration(), false)) return Optional.empty();

            return Optional.of(new ProgramEntryPoint(candidate.module(), candidate.function()));
        }

        private List<EntryPointCandidate> collectEntryPointCandidates() {
            var candidates = new ArrayList<EntryPointCandidate>();

            for (var binder : binders.values()) candidates.addAll(binder.entryPointCandidates());

            return List.copyOf(candidates);
        }
    }

    private record GenericFunctionInstantiation(FunctionSymbol function, List<TypeSymbol> arguments) {
        private GenericFunctionInstantiation {
            Objects.requireNonNull(function, "Generic function instantiation must not be null.");
            Objects.requireNonNull(arguments, "Generic function arguments must not be null.");
            arguments = List.copyOf(arguments);
        }

        private Map<TypeParameterSymbol, TypeSymbol> substitutions() {
            var substitutions = new IdentityHashMap<TypeParameterSymbol, TypeSymbol>();

            for (var index = 0; index < arguments.size(); index++)
                substitutions.put(function.typeParameters().get(index), arguments.get(index));

            return substitutions;
        }

        private String displayName() {
            if (arguments.isEmpty()) return function.name();

            var text = new StringJoiner(", ", function.name() + "<", ">");

            arguments.forEach(argument -> text.add(argument.name()));

            return text.toString();
        }
    }

    private record EntryPointCandidate(Binder binder, ModuleSymbol module, FunctionDeclaration declaration, FunctionSymbol function, Annotation annotation) {
        private EntryPointCandidate {
            Objects.requireNonNull(binder, "Entry point candidate binder must not be null.");
            Objects.requireNonNull(module, "Entry point candidate module must not be null.");
            Objects.requireNonNull(declaration, "Entry point candidate declaration must not be null.");
            Objects.requireNonNull(function, "Entry point candidate function must not be null.");
            Objects.requireNonNull(annotation, "Entry point candidate annotation must not be null.");
        }
    }

    private static final class Binder {
        private final CompilationUnit unit;
        private final Scope moduleScope;
        private final List<Scope> scopes = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();

        private final IdentityHashMap<FunctionDeclaration, Scope> functionScopes = new IdentityHashMap<>();
        private final IdentityHashMap<Block, Scope> blockScopes = new IdentityHashMap<>();
        private final IdentityHashMap<FunctionDeclaration, FunctionSymbol> functionSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<StructDeclaration, StructSymbol> structSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<StructFieldDeclaration, StructFieldSymbol> structFieldSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<TypeParameter, TypeParameterSymbol> typeParameterSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<Parameter, ParameterSymbol> parameterSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<VariableDeclarationStatement, LocalVariableSymbol> localVariableSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<NameExpression, Symbol> resolvedNames = new IdentityHashMap<>();
        private final IdentityHashMap<AssignmentStatement, Symbol> assignmentTargets = new IdentityHashMap<>();
        private final IdentityHashMap<FieldAssignmentStatement, Symbol> fieldAssignmentTargets = new IdentityHashMap<>();
        private final IdentityHashMap<StructConstructionExpression, StructSymbol> constructedStructs = new IdentityHashMap<>();
        private final IdentityHashMap<StructFieldInitializer, StructFieldSymbol> initializedStructFields = new IdentityHashMap<>();
        private final IdentityHashMap<FieldAccessExpression, StructFieldSymbol> accessedStructFields = new IdentityHashMap<>();
        private final IdentityHashMap<FunctionDeclaration, Boolean> duplicateFunctions = new IdentityHashMap<>();
        private final IdentityHashMap<StructDeclaration, Boolean> duplicateStructs = new IdentityHashMap<>();
        private final IdentityHashMap<TypeReference, TypeSymbol> resolvedTypes = new IdentityHashMap<>();
        private final IdentityHashMap<Expression, TypeSymbol> expressionTypes = new IdentityHashMap<>();
        private final IdentityHashMap<CallExpression, FunctionSymbol> calledFunctions = new IdentityHashMap<>();
        private final IdentityHashMap<CallExpression, List<TypeSymbol>> calledFunctionTypeArguments = new IdentityHashMap<>();
        private final IdentityHashMap<QualifiedNameExpression, FunctionSymbol> qualifiedNameSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<InjectionDeclaration, ModuleSymbol> injectedModules = new IdentityHashMap<>();
        private final IdentityHashMap<InjectionDeclaration, List<FunctionSymbol>> directlyInjectedFunctions = new IdentityHashMap<>();
        private final IdentityHashMap<InjectionDeclaration, NamespaceSymbol> injectedNamespaces = new IdentityHashMap<>();
        private final ModuleSymbol module;
        private final Map<ModuleName, ModuleSymbol> modules;
        private final IdentityHashMap<TypeReference, TypeSymbol> programResolvedTypes;
        private final IdentityHashMap<StructSymbol, Map<String, TypeParameterType>> structTypeEnvironments = new IdentityHashMap<>();
        private final IdentityHashMap<FunctionSymbol, Map<String, TypeParameterType>> functionTypeEnvironments = new IdentityHashMap<>();

        private Map<String, TypeParameterType> activeTypeEnvironment = Map.of();

        private Binder(SourceModule sourceModule, ModuleSymbol module, Map<ModuleName, ModuleSymbol> modules, IdentityHashMap<TypeReference, TypeSymbol> programResolvedTypes) {
            Objects.requireNonNull(sourceModule, "Source module must not be null.");

            this.module = Objects.requireNonNull(module, "Semantic module must not be null.");
            this.modules = Map.copyOf(Objects.requireNonNull(modules, "Semantic module registry must not be null."));
            this.programResolvedTypes = Objects.requireNonNull(programResolvedTypes, "Program type associations must not be null.");

            unit = sourceModule.unit();
            moduleScope = module.scope();

            scopes.add(moduleScope);
        }

        private SemanticAnalysisResult finish() {
            scopes.forEach(Scope::freeze);

            var model = new SemanticModel(
                moduleScope,
                functionScopes,
                blockScopes,
                functionSymbols,
                structSymbols,
                structFieldSymbols,
                typeParameterSymbols,
                parameterSymbols,
                localVariableSymbols,
                resolvedNames,
                assignmentTargets,
                fieldAssignmentTargets,
                constructedStructs,
                initializedStructFields,
                accessedStructFields,
                calledFunctions,
                calledFunctionTypeArguments,
                qualifiedNameSymbols,
                injectedModules,
                directlyInjectedFunctions,
                injectedNamespaces,
                resolvedTypes,
                expressionTypes
            );

            diagnostics.sort(Comparator
                .comparingInt((Diagnostic diagnostic) -> diagnostic.span().start().offset())
                .thenComparingInt(diagnostic -> diagnostic.span().end().offset())
            );

            return new SemanticAnalysisResult(model, diagnostics);
        }

        private void predeclareStructs() {
            for (var declaration : unit.declarations()) {
                if (!(declaration instanceof StructDeclaration struct)) continue;

                var symbol = new StructSymbol(struct, module.name());

                structSymbols.put(struct, symbol);
                structTypeEnvironments.put(symbol, createTypeEnvironment(symbol.typeParameters()));

                for (var parameter : symbol.typeParameters()) typeParameterSymbols.put(parameter.declaration(), parameter);

                for (var field : symbol.fields()) structFieldSymbols.put(field.declaration(), field);

                if (BuiltInTypes.lookup(symbol.name()).isPresent()) {
                    duplicateStructs.put(struct, true);
                    continue;
                }

                if (!module.declareExport(symbol)) duplicateStructs.put(struct, true);
            }
        }

        private void predeclareFunctions() {
            for (var declaration : unit.declarations())
                if (declaration instanceof FunctionDeclaration function) {
                    var symbol = new FunctionSymbol(function);

                    functionSymbols.put(function, symbol);
                    functionTypeEnvironments.put(symbol, createTypeEnvironment(symbol.typeParameters()));

                    for (var parameter : symbol.typeParameters()) typeParameterSymbols.put(parameter.declaration(), parameter);

                    if (!module.declareExport(symbol)) duplicateFunctions.put(function, true);
                }
        }

        private Map<String, TypeParameterType> createTypeEnvironment(List<TypeParameterSymbol> parameters) {
            var environment = new LinkedHashMap<String, TypeParameterType>();

            for (var parameter : parameters) environment.putIfAbsent(parameter.name(), parameter.type());

            return Map.copyOf(environment);
        }

        private void bindStructDeclarations() {
            for (var declaration : unit.declarations()) {
                if (!(declaration instanceof StructDeclaration struct)) continue;

                var symbol = structSymbols.get(struct);

                if (BuiltInTypes.lookup(symbol.name()).isPresent()) diagnostics.add(new Diagnostic(
                    RESERVED_STRUCT_NAME_CODE,
                    DiagnosticSeverity.ERROR,
                    "Struct name '%s' is reserved by a built-in type.".formatted(symbol.name()),
                    struct.span()
                ));
                else if (duplicateStructs.containsKey(struct)) reportDuplicate(symbol);

                validateTypeParameters(symbol.typeParameters(), "struct '%s'".formatted(symbol.name()));

                var fieldNames = new HashSet<String>();
                var typeEnvironment = structTypeEnvironments.get(symbol);

                for (var field : symbol.fields()) {
                    if (!fieldNames.add(field.name())) diagnostics.add(new Diagnostic(
                        DUPLICATE_STRUCT_FIELD_CODE,
                        DiagnosticSeverity.ERROR,
                        "Struct '%s' declares field '%s' more than once.".formatted(symbol.name(), field.name()),
                        field.span()
                    ));

                    var fieldType = resolveTypeReference(field.type(), typeEnvironment);

                    if (fieldType != BuiltInTypes.ERROR && !fieldType.isValue()) diagnostics.add(new Diagnostic(
                        INVALID_STRUCT_FIELD_TYPE_CODE,
                        DiagnosticSeverity.ERROR,
                        "Field '%s' of struct '%s' cannot have non-value type '%s'.".formatted(field.name(), symbol.name(), fieldType.name()),
                        field.type().span()
                    ));
                }
            }

        }

        private void bindFunctionSignatures() {
            for (var declaration : unit.declarations()) if (declaration instanceof FunctionDeclaration function) bindFunctionSignature(function);
        }

        private List<EntryPointCandidate> entryPointCandidates() {
            var candidates = new ArrayList<EntryPointCandidate>();

            for (var declaration : unit.declarations()) {
                if (!(declaration instanceof FunctionDeclaration function)) continue;

                var annotation = function.annotations().stream().filter(candidate -> candidate.name().equals("init")).findFirst();

                annotation.ifPresent(initAnnotation -> candidates.add(new EntryPointCandidate(this, module, function, functionSymbols.get(function), initAnnotation)));
            }

            return List.copyOf(candidates);
        }

        private boolean validateEntryPointCandidate(FunctionDeclaration function) {
            var valid = !duplicateFunctions.containsKey(function);

            if (!function.typeParameters().isEmpty()) {
                diagnostics.add(new Diagnostic(
                    GENERIC_ARITY_CODE,
                    DiagnosticSeverity.ERROR,
                    "Entry point '%s' cannot declare type parameters.".formatted(function.name()),
                    function.span()
                ));

                valid = false;
            }

            if (function.body().isEmpty()) {
                diagnostics.add(new Diagnostic(
                    BODYLESS_ENTRY_POINT_CODE,
                    DiagnosticSeverity.ERROR, "Entry point '%s' must have a function body.".formatted(function.name()), function.span()
                ));

                valid = false;
            }

            var returnType = resolvedTypes.getOrDefault(function.returnType(), BuiltInTypes.ERROR);

            if (returnType == BuiltInTypes.ERROR) {
                valid = false;
            } else if (returnType != BuiltInTypes.INT) {
                diagnostics.add(new Diagnostic(
                    INVALID_ENTRY_POINT_RETURN_CODE,
                    DiagnosticSeverity.ERROR,
                    "Entry point '%s' must return 'int', but returns '%s'.".formatted(function.name(), returnType.name()), function.returnType().span()
                ));

                valid = false;
            }

            return valid;
        }

        private void reportAdditionalEntryPoint(EntryPointCandidate candidate) {
            diagnostics.add(new Diagnostic(
                MULTIPLE_ENTRY_POINTS_CODE,
                DiagnosticSeverity.ERROR,
                "Function '%s' is an additional '@init' entry point; only one is allowed.".formatted(candidate.declaration().name()), candidate.annotation().span()
            ));
        }

        private void validateParameterType(FunctionDeclaration function, Parameter parameter, TypeSymbol type) {
            if (type == BuiltInTypes.ERROR || type.isValue()) return;

            diagnostics.add(new Diagnostic(
                INVALID_PARAMETER_TYPE_CODE,
                DiagnosticSeverity.ERROR,
                "Parameter '%s' of function '%s' cannot have non-value type '%s'.".formatted(parameter.name(), function.name(), type.name()), parameter.type().span()
            ));
        }

        private void bindFunctionSignature(FunctionDeclaration function) {
            var functionSymbol = functionSymbols.get(function);

            if (duplicateFunctions.containsKey(function)) reportDuplicate(functionSymbol);

            validateTypeParameters(functionSymbol.typeParameters(), "function '%s'".formatted(functionSymbol.name()));

            var typeEnvironment = functionTypeEnvironments.get(functionSymbol);

            var functionScope = createChildScope(ScopeKind.FUNCTION, moduleScope);

            functionScopes.put(function, functionScope);

            for (var parameter : function.parameters()) {
                var parameterType = resolveTypeReference(parameter.type(), typeEnvironment);

                validateParameterType(function, parameter, parameterType);

                var parameterSymbol = new ParameterSymbol(parameter);

                parameterSymbols.put(parameter, parameterSymbol);

                declareOrReport(functionScope, parameterSymbol);
            }

            resolveTypeReference(function.returnType(), typeEnvironment);
        }

        private void bindFunctionBodies() {
            for (var declaration : unit.declarations()) if (declaration instanceof FunctionDeclaration function) bindFunctionBody(function);
        }

        private void bindFunctionBody(FunctionDeclaration function) {
            if (function.body().isEmpty()) return;

            var body = function.body().orElseThrow();
            var functionScope = functionScopes.get(function);
            var previousTypeEnvironment = activeTypeEnvironment;

            activeTypeEnvironment = functionTypeEnvironments.get(functionSymbols.get(function));

            try {
                blockScopes.put(body, functionScope);
                bindBlock(body, functionScope, function);
            } finally {
                activeTypeEnvironment = previousTypeEnvironment;
            }
        }

        private void validateTypeParameters(List<TypeParameterSymbol> parameters, String ownerDescription) {
            var names = new HashSet<String>();

            for (var parameter : parameters) if (!names.add(parameter.name())) diagnostics.add(new Diagnostic(
                DUPLICATE_TYPE_PARAMETER_CODE,
                DiagnosticSeverity.ERROR,
                "Type parameter '%s' is declared more than once on %s.".formatted(parameter.name(), ownerDescription),
                parameter.span()
            ));
        }

        private void bindBlock(Block block, Scope scope, FunctionDeclaration function) {
            for (var statement : block.statements()) bindStatement(statement, scope, function);
        }

        private void bindStatement(Statement statement, Scope scope, FunctionDeclaration function) {
            if (statement instanceof ReturnStatement returnStatement) {
                bindReturn(returnStatement, scope, function);

                return;
            }

            if (statement instanceof VariableDeclarationStatement variableDeclaration) {
                bindVariableDeclaration(variableDeclaration, scope);

                return;
            }

            if (statement instanceof AssignmentStatement assignment) {
                bindAssignment(assignment, scope);

                return;
            }

            if (statement instanceof FieldAssignmentStatement fieldAssignment) {
                bindFieldAssignment(fieldAssignment, scope);

                return;
            }

            if (statement instanceof CallStatement callStatement) {
                bindExpression(callStatement.call(), scope);

                return;
            }

            if (statement instanceof ConditionalStatement conditional) {
                bindConditional(conditional, scope, function);

                return;
            }

            if (statement instanceof WhileStatement whileStatement) {
                bindWhile(whileStatement, scope, function);

                return;
            }

            throw new IllegalStateException("Unsupported statement type: " + statement.getClass().getName());
        }

        private void bindVariableDeclaration(VariableDeclarationStatement declaration, Scope scope) {
            var declaredType = resolveTypeReference(declaration.type());

            /*
             * The initializer is bound before the
             * variable enters its scope. A variable
             * is therefore not visible inside its
             * own initializer.
             */
            var initializerType = bindExpression(declaration.initializer(), scope);

            validateVariableType(declaration, declaredType);

            validateVariableInitializer(declaration, declaredType, initializerType);

            var symbol = new LocalVariableSymbol(declaration);

            localVariableSymbols.put(declaration, symbol);

            declareOrReport(scope, symbol);
        }

        private void bindAssignment(AssignmentStatement assignment, Scope scope) {
            bindExpression(assignment.target(), scope);

            var targetSymbol = resolvedNames.get(assignment.target());

            if (targetSymbol != null) assignmentTargets.put(assignment, targetSymbol);

            var valueType = bindExpression(assignment.value(), scope);

            validateAssignment(assignment, targetSymbol, valueType);
        }

        private void bindFieldAssignment(FieldAssignmentStatement assignment, Scope scope) {
            var targetType = bindExpression(assignment.target(), scope);
            var rootSymbol = rootSymbolOf(assignment.target());

            if (rootSymbol != null) fieldAssignmentTargets.put(assignment, rootSymbol);

            var valueType = bindExpression(assignment.value(), scope);

            validateFieldAssignment(assignment, rootSymbol, targetType, valueType);
        }

        private void validateVariableType(VariableDeclarationStatement declaration, TypeSymbol declaredType) {
            if (declaredType == BuiltInTypes.ERROR || declaredType.isValue()) return;

            diagnostics.add(new Diagnostic(
                INVALID_VARIABLE_TYPE_CODE,
                DiagnosticSeverity.ERROR,
                "Variable '%s' cannot have non-value type '%s'.".formatted(declaration.name(), declaredType.name()), declaration.type().span()
            ));
        }

        private void validateVariableInitializer(VariableDeclarationStatement declaration, TypeSymbol declaredType, TypeSymbol initializerType) {
            if (declaredType == BuiltInTypes.ERROR || initializerType == BuiltInTypes.ERROR || !declaredType.isValue() || sameType(declaredType, initializerType)) return;

            diagnostics.add(new Diagnostic(
                INCOMPATIBLE_INITIALIZER_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot initialize variable '%s' of type '%s' with value of type '%s'.".formatted(declaration.name(), declaredType.name(), initializerType.name()),
                declaration.initializer().span()
            ));
        }

        private void bindConditional(ConditionalStatement conditional, Scope scope, FunctionDeclaration function) {
            var conditionType = bindExpression(conditional.condition(), scope);

            validateCondition(conditional.condition(), conditionType);

            bindNestedBlock(conditional.thenBlock(), scope, function);

            conditional.elseBlock().ifPresent(block -> bindNestedBlock(block, scope, function));
        }

        private void bindWhile(WhileStatement whileStatement, Scope scope, FunctionDeclaration function) {
            var conditionType = bindExpression(whileStatement.condition(), scope);

            validateCondition(whileStatement.condition(), conditionType);

            bindNestedBlock(whileStatement.body(), scope, function);
        }

        private void bindNestedBlock(Block block, Scope parent, FunctionDeclaration function) {
            var blockScope = createChildScope(ScopeKind.BLOCK, parent);

            blockScopes.put(block, blockScope);

            bindBlock(block, blockScope, function);
        }

        private TypeSymbol bindExpression(Expression expression, Scope scope) {
            TypeSymbol type;

            switch (expression) {
                case LiteralExpression literal -> type = BuiltInTypes.typeOf(literal.kind());

                case NameExpression name -> type = bindNameExpression(name, scope);

                case ParenthesizedExpression parenthesized -> type = bindExpression(parenthesized.expression(), scope);

                case UnaryExpression unary -> {
                    var operandType = bindExpression(unary.operand(), scope);

                    type = OperatorTypeChecker.checkUnary(unary, operandType, diagnostics);
                }

                case BinaryExpression binary -> {
                    var leftType = bindExpression(binary.left(), scope);
                    var rightType = bindExpression(binary.right(), scope);

                    type = OperatorTypeChecker.checkBinary(binary, leftType, rightType, diagnostics);
                }

                case CallExpression call -> type = bindCallExpression(call, scope);

                case QualifiedNameExpression qualified -> type = bindQualifiedNameExpression(qualified, scope);

                case StructConstructionExpression construction -> type = bindStructConstruction(construction, scope);

                case FieldAccessExpression fieldAccess -> type = bindFieldAccess(fieldAccess, scope);

                case null, default -> {
                    assert expression != null;

                    throw new IllegalStateException("Unsupported expression type: " + expression.getClass().getName());
                }
            }

            expressionTypes.put(expression, type);

            return type;
        }

        private TypeSymbol bindStructConstruction(StructConstructionExpression expression, Scope scope) {
            var type = resolveTypeReference(expression.type());
            var initializerTypes = new IdentityHashMap<StructFieldInitializer, TypeSymbol>();

            for (var initializer : expression.fields()) initializerTypes.put(initializer, bindExpression(initializer.value(), scope));

            if (type == BuiltInTypes.ERROR) return BuiltInTypes.ERROR;

            if (!(type instanceof StructType structType)) {
                diagnostics.add(new Diagnostic(
                    NOT_A_STRUCT_TYPE_CODE,
                    DiagnosticSeverity.ERROR,
                    "Type '%s' is not a struct and cannot be constructed with field initializers.".formatted(type.name()),
                    expression.type().span()
                ));

                return BuiltInTypes.ERROR;
            }

            var struct = structType.symbol();

            constructedStructs.put(expression, struct);

            var initializedNames = new HashSet<String>();

            for (var initializer : expression.fields()) {
                var field = struct.field(initializer.name());

                if (field.isEmpty()) {
                    diagnostics.add(new Diagnostic(
                        UNKNOWN_STRUCT_FIELD_CODE,
                        DiagnosticSeverity.ERROR,
                        "Struct '%s' has no field named '%s'.".formatted(struct.name(), initializer.name()),
                        initializer.span()
                    ));

                    continue;
                }

                var resolvedField = field.orElseThrow();

                initializedStructFields.put(initializer, resolvedField);

                if (!initializedNames.add(initializer.name())) {
                    diagnostics.add(new Diagnostic(
                        DUPLICATE_STRUCT_INITIALIZER_CODE,
                        DiagnosticSeverity.ERROR,
                        "Field '%s' of struct '%s' is initialized more than once.".formatted(initializer.name(), struct.name()),
                        initializer.span()
                    ));

                    continue;
                }

                var expectedType = fieldTypeOf(structType, resolvedField);
                var actualType = initializerTypes.get(initializer);

                if (expectedType != BuiltInTypes.ERROR && actualType != BuiltInTypes.ERROR && !sameType(expectedType, actualType)) diagnostics.add(new Diagnostic(
                    INCOMPATIBLE_STRUCT_INITIALIZER_CODE,
                    DiagnosticSeverity.ERROR,
                    "Field '%s' of struct '%s' expects type '%s', but found '%s'.".formatted(
                        resolvedField.name(), struct.name(), expectedType.name(), actualType.name()
                    ),
                    initializer.value().span()
                ));
            }

            for (var field : struct.fields()) if (!initializedNames.contains(field.name())) diagnostics.add(new Diagnostic(
                MISSING_STRUCT_INITIALIZER_CODE,
                DiagnosticSeverity.ERROR,
                "Construction of struct '%s' is missing field '%s'.".formatted(struct.name(), field.name()),
                expression.span()
            ));

            return structType;
        }

        private TypeSymbol bindFieldAccess(FieldAccessExpression expression, Scope scope) {
            var targetType = bindExpression(expression.target(), scope);

            if (targetType == BuiltInTypes.ERROR) return BuiltInTypes.ERROR;

            if (!(targetType instanceof StructType structType)) {
                diagnostics.add(new Diagnostic(
                    FIELD_ACCESS_ON_NON_STRUCT_CODE,
                    DiagnosticSeverity.ERROR,
                    "Cannot access field '%s' on value of non-struct type '%s'.".formatted(expression.fieldName(), targetType.name()),
                    expression.fieldSpan()
                ));

                return BuiltInTypes.ERROR;
            }

            var field = structType.symbol().field(expression.fieldName());

            if (field.isEmpty()) {
                diagnostics.add(new Diagnostic(
                    UNKNOWN_FIELD_CODE,
                    DiagnosticSeverity.ERROR,
                    "Struct '%s' has no field named '%s'.".formatted(structType.name(), expression.fieldName()),
                    expression.fieldSpan()
                ));

                return BuiltInTypes.ERROR;
            }

            var resolvedField = field.orElseThrow();

            accessedStructFields.put(expression, resolvedField);

            return fieldTypeOf(structType, resolvedField);
        }

        private TypeSymbol fieldTypeOf(StructType instance, StructFieldSymbol field) {
            if (instance.symbol() != field.owner()) throw new IllegalArgumentException(
                "Field '%s' does not belong to struct type '%s'.".formatted(field.name(), instance.name())
            );

            var substitutions = new IdentityHashMap<TypeParameterSymbol, TypeSymbol>();
            var parameters = instance.symbol().typeParameters();

            for (var index = 0; index < parameters.size(); index++) substitutions.put(parameters.get(index), instance.arguments().get(index));

            return TypeSubstitution.substitute(resolvedTypeOf(field.type()), substitutions);
        }

        private Symbol rootSymbolOf(FieldAccessExpression expression) {
            Expression target = expression.target();

            while (target instanceof FieldAccessExpression fieldAccess) target = fieldAccess.target();

            if (target instanceof NameExpression name) return resolvedNames.get(name);

            return null;
        }

        private TypeSymbol bindQualifiedNameExpression(QualifiedNameExpression expression, Scope scope) {
            bindExpression(expression.qualifier(), scope);

            expressionTypes.put(expression.member(), BuiltInTypes.ERROR);

            var qualifierSymbol = resolvedNames.get(expression.qualifier());

            if (qualifierSymbol == null)
                /*
                 * bindName already emitted
                 * SOL-S002.
                 */
                return BuiltInTypes.ERROR;

            if (!(qualifierSymbol instanceof NamespaceSymbol namespace)) {
                diagnostics.add(new Diagnostic(
                    NON_NAMESPACE_QUALIFIER_CODE,
                    DiagnosticSeverity.ERROR,
                    "Name '%s' does not refer to an injected namespace.".formatted(expression.qualifier().name()), expression.qualifier().span()
                ));

                return BuiltInTypes.ERROR;
            }

            var function = namespace.targetModule().exportedFunction(expression.member().name());

            if (function.isEmpty()) {
                diagnostics.add(new Diagnostic(
                    UNKNOWN_NAMESPACE_MEMBER_CODE,
                    DiagnosticSeverity.ERROR,
                    "Module '%s' does not declare function '%s'.".formatted(namespace.targetModule().name().qualifiedName(), expression.member().name()),
                    expression.member().span()
                ));

                return BuiltInTypes.ERROR;
            }

            var resolvedFunction = function.orElseThrow();

            qualifiedNameSymbols.put(expression, resolvedFunction);

            return BuiltInTypes.ERROR;
        }

        private TypeSymbol bindNameExpression(NameExpression expression, Scope scope) {
            var symbol = bindName(expression, scope);

            return symbol.map(this::typeOfValueSymbol).orElse(BuiltInTypes.ERROR);
        }

        private TypeSymbol typeOfValueSymbol(Symbol symbol) {
            if (symbol instanceof ParameterSymbol parameter) return resolvedTypes.getOrDefault(parameter.type(), BuiltInTypes.ERROR);

            if (symbol instanceof LocalVariableSymbol localVariable) return resolvedTypes.getOrDefault(localVariable.type(), BuiltInTypes.ERROR);

            return BuiltInTypes.ERROR;
        }

        private TypeSymbol bindCallExpression(CallExpression call, Scope scope) {
            var calleeType = bindExpression(call.callee(), scope);
            var argumentTypes = new ArrayList<TypeSymbol>(call.arguments().size());
            var typeArguments = new ArrayList<TypeSymbol>(call.typeArguments().size());

            for (var argument : call.arguments()) argumentTypes.add(bindExpression(argument, scope));
            for (var typeArgument : call.typeArguments()) typeArguments.add(resolveTypeReference(typeArgument));

            var resolvedFunction = resolvedFunctionOf(call.callee());

            if (resolvedFunction.isEmpty()) {
                if (calleeType != BuiltInTypes.ERROR) diagnostics.add(new Diagnostic(
                    NOT_CALLABLE_CODE,
                    DiagnosticSeverity.ERROR,
                    "Expression of type '%s' is not callable.".formatted(calleeType.name()),
                    call.callee().span())
                );

                return BuiltInTypes.ERROR;
            }

            var function = resolvedFunction.orElseThrow();

            calledFunctions.put(call, function);
            calledFunctionTypeArguments.put(call, List.copyOf(typeArguments));

            validateArgumentCount(call, function);

            if (!validateCallTypeArguments(call, function, typeArguments)) return BuiltInTypes.ERROR;

            var substitutions = functionSubstitutions(function, typeArguments);

            validateArgumentTypes(call, function, argumentTypes, substitutions);

            return TypeSubstitution.substitute(resolvedTypeOf(function.declaration().returnType()), substitutions);
        }

        private boolean validateCallTypeArguments(CallExpression call, FunctionSymbol function, List<TypeSymbol> arguments) {
            var expected = function.typeParameters().size();

            if (arguments.size() != expected) {
                diagnostics.add(new Diagnostic(
                    GENERIC_ARITY_CODE,
                    DiagnosticSeverity.ERROR,
                    "Function '%s' expects %d type arguments, but received %d.".formatted(function.name(), expected, arguments.size()),
                    call.span()
                ));

                return false;
            }

            var valid = true;

            for (var index = 0; index < arguments.size(); index++) {
                var argument = arguments.get(index);

                if (argument == BuiltInTypes.VOID) {
                    diagnostics.add(new Diagnostic(
                        INVALID_TYPE_ARGUMENT_CODE,
                        DiagnosticSeverity.ERROR,
                        "Type argument %d of function '%s' cannot be 'void'.".formatted(index + 1, function.name()),
                        call.typeArguments().get(index).span()
                    ));

                    valid = false;
                } else if (argument == BuiltInTypes.ERROR) valid = false;
            }

            return valid;
        }

        private IdentityHashMap<TypeParameterSymbol, TypeSymbol> functionSubstitutions(
            FunctionSymbol function,
            List<TypeSymbol> arguments
        ) {
            var substitutions = new IdentityHashMap<TypeParameterSymbol, TypeSymbol>();

            for (var index = 0; index < arguments.size(); index++) substitutions.put(function.typeParameters().get(index), arguments.get(index));

            return substitutions;
        }

        private void bindReturn(ReturnStatement statement, Scope scope, FunctionDeclaration function) {
            var returnType = resolvedTypes.getOrDefault(function.returnType(), BuiltInTypes.ERROR);

            if (statement.expression().isEmpty()) {
                if (returnType.isValue()) diagnostics.add(new Diagnostic(
                    MISSING_RETURN_VALUE_CODE,
                    DiagnosticSeverity.ERROR,
                    "Function '%s' must return a value of type '%s'.".formatted(function.name(), returnType.name()), statement.span())
                );

                return;
            }

            var expression = statement.expression().orElseThrow();

            var expressionType = bindExpression(expression, scope);

            if (returnType == BuiltInTypes.VOID) {
                diagnostics.add(new Diagnostic(
                    UNEXPECTED_RETURN_VALUE_CODE,
                    DiagnosticSeverity.ERROR,
                    "Function '%s' returns 'void' and cannot return a value.".formatted(function.name()),
                    expression.span()
                ));

                return;
            }

            if (returnType == BuiltInTypes.ERROR || expressionType == BuiltInTypes.ERROR || sameType(returnType, expressionType)) return;

            diagnostics.add(new Diagnostic(
                INCOMPATIBLE_RETURN_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot return value of type '%s' from function '%s' returning '%s'.".formatted(expressionType.name(), function.name(), returnType.name()), expression.span()
            ));
        }

        private Optional<FunctionSymbol> resolvedFunctionOf(Expression expression) {
            if (expression instanceof NameExpression name) {
                var symbol = resolvedNames.get(name);

                if (symbol instanceof FunctionSymbol function) return Optional.of(function);

                return Optional.empty();
            }

            if (expression instanceof ParenthesizedExpression parenthesized) return resolvedFunctionOf(parenthesized.expression());
            if (expression instanceof QualifiedNameExpression qualified) return Optional.ofNullable(qualifiedNameSymbols.get(qualified));

            return Optional.empty();
        }

        private void validateCondition(Expression condition, TypeSymbol type) {
            if (type == BuiltInTypes.BOOLEAN || type == BuiltInTypes.ERROR) return;

            diagnostics.add(new Diagnostic(
                NON_BOOLEAN_CONDITION_CODE,
                DiagnosticSeverity.ERROR,
                "Condition must have type 'boolean', but found '%s'.".formatted(type.name()),
                condition.span()
            ));
        }

        private Optional<Symbol> bindName(NameExpression expression, Scope scope) {
            var symbol = scope.lookup(expression.name());

            if (symbol.isPresent()) {
                resolvedNames.put(expression, symbol.orElseThrow());

                return symbol;
            }

            diagnostics.add(new Diagnostic(
                UNRESOLVED_NAME_CODE,
                DiagnosticSeverity.ERROR,
                "Unresolved name '%s'.".formatted(expression.name()),
                expression.span()
            ));

            return Optional.empty();
        }

        private TypeSymbol resolveTypeReference(TypeReference reference) {
            return resolveTypeReference(reference, activeTypeEnvironment);
        }

        private TypeSymbol resolveTypeReference(TypeReference reference, Map<String, TypeParameterType> typeEnvironment) {
            var arguments = new ArrayList<TypeSymbol>(reference.arguments().size());

            for (var argument : reference.arguments()) arguments.add(resolveTypeReference(argument, typeEnvironment));

            var parameter = typeEnvironment.get(reference.name());

            if (parameter != null) {
                if (!arguments.isEmpty()) return reportGenericArity(reference, "type parameter '%s'".formatted(reference.name()), 0, arguments.size());

                return recordResolvedType(reference, parameter);
            }

            var primitive = BuiltInTypes.lookup(reference.name());

            if (primitive.isPresent()) {
                var type = primitive.orElseThrow();

                if (!arguments.isEmpty()) return reportGenericArity(reference, "built-in type '%s'".formatted(reference.name()), 0, arguments.size());

                return recordResolvedType(reference, type);
            }

            var declared = moduleScope.lookupLocal(reference.name()).filter(StructSymbol.class::isInstance).map(StructSymbol.class::cast);

            if (declared.isPresent()) {
                var struct = declared.orElseThrow();
                var expected = struct.typeParameters().size();

                if (arguments.size() != expected) return reportGenericArity(reference, "struct '%s'".formatted(struct.name()), expected, arguments.size());

                var validArguments = true;

                for (var index = 0; index < arguments.size(); index++) {
                    var argument = arguments.get(index);

                    if (argument == BuiltInTypes.VOID) {
                        diagnostics.add(new Diagnostic(
                            INVALID_TYPE_ARGUMENT_CODE,
                            DiagnosticSeverity.ERROR,
                            "Type argument %d of struct '%s' cannot be 'void'.".formatted(index + 1, struct.name()),
                            reference.arguments().get(index).span()
                        ));

                        validArguments = false;
                    } else if (argument == BuiltInTypes.ERROR) validArguments = false;
                }

                if (!validArguments) return recordResolvedType(reference, BuiltInTypes.ERROR);

                var type = arguments.equals(struct.type().arguments()) ? struct.type() : new StructType(struct, arguments);

                return recordResolvedType(reference, type);
            }

            diagnostics.add(new Diagnostic(
                UNKNOWN_TYPE_CODE,
                DiagnosticSeverity.ERROR,
                "Unknown type '%s'.".formatted(reference.name()), reference.span()
            ));

            return recordResolvedType(reference, BuiltInTypes.ERROR);
        }

        private TypeSymbol reportGenericArity(TypeReference reference, String target, int expected, int actual) {
            diagnostics.add(new Diagnostic(
                GENERIC_ARITY_CODE,
                DiagnosticSeverity.ERROR,
                "%s expects %d type arguments, but received %d.".formatted(target, expected, actual),
                reference.span()
            ));

            return recordResolvedType(reference, BuiltInTypes.ERROR);
        }

        private TypeSymbol recordResolvedType(TypeReference reference, TypeSymbol type) {
            resolvedTypes.put(reference, type);
            programResolvedTypes.put(reference, type);

            return type;
        }

        private TypeSymbol resolvedTypeOf(TypeReference reference) {
            return programResolvedTypes.getOrDefault(reference, BuiltInTypes.ERROR);
        }

        private void declareOrReport(Scope scope, Symbol symbol) {
            if (!scope.declare(symbol)) reportDuplicate(symbol);
        }

        private void reportDuplicate(Symbol symbol) {
            diagnostics.add(new Diagnostic(
                DUPLICATE_DECLARATION_CODE,
                DiagnosticSeverity.ERROR,
                "Duplicate declaration of '%s'.".formatted(symbol.name()),
                symbol.span()
            ));
        }

        private Scope createChildScope(ScopeKind kind, Scope parent) {
            var scope = new Scope(kind, parent);

            scopes.add(scope);

            return scope;
        }

        private void validateAssignment(AssignmentStatement assignment, Symbol targetSymbol, TypeSymbol valueType) {
            switch (targetSymbol) {
                case null -> {
                    /*
                     * bindName already emitted
                     * SOL-S002.
                     */
                    return;
                }

                case LocalVariableSymbol localVariable -> {
                    validateLocalAssignment(assignment, localVariable, valueType);

                    return;
                }

                case ParameterSymbol parameter -> {
                    reportImmutableParameter(assignment, parameter);
                    validateAssignmentType(assignment, parameter.name(), resolvedTypeOf(parameter), valueType);

                    return;
                }

                default -> {}
            }

            diagnostics.add(new Diagnostic(
                INVALID_ASSIGNMENT_TARGET_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot assign to '%s' because it is not a variable.".formatted(targetSymbol.name()),
                assignment.target().span()
            ));
        }

        private void validateFieldAssignment(
            FieldAssignmentStatement assignment,
            Symbol rootSymbol,
            TypeSymbol targetType,
            TypeSymbol valueType
        ) {
            switch (rootSymbol) {
                case LocalVariableSymbol local -> {
                    if (!local.isMutable()) diagnostics.add(new Diagnostic(
                        IMMUTABLE_ASSIGNMENT_CODE,
                        DiagnosticSeverity.ERROR,
                        "Cannot mutate a field of immutable variable '%s'.".formatted(local.name()),
                        assignment.target().span()
                    ));
                }

                case ParameterSymbol parameter -> diagnostics.add(new Diagnostic(
                    IMMUTABLE_ASSIGNMENT_CODE,
                    DiagnosticSeverity.ERROR,
                    "Cannot mutate a field of immutable parameter '%s'.".formatted(parameter.name()),
                    assignment.target().span()
                ));

                case null, default -> diagnostics.add(new Diagnostic(
                    INVALID_FIELD_ASSIGNMENT_TARGET_CODE,
                    DiagnosticSeverity.ERROR,
                    "Field assignment target must be rooted in a local variable or parameter.",
                    assignment.target().span()
                ));
            }

            if (targetType == BuiltInTypes.ERROR || valueType == BuiltInTypes.ERROR || sameType(targetType, valueType)) return;

            diagnostics.add(new Diagnostic(
                INCOMPATIBLE_ASSIGNMENT_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot assign value of type '%s' to field '%s' of type '%s'.".formatted(
                    valueType.name(), assignment.target().fieldName(), targetType.name()
                ),
                assignment.value().span()
            ));
        }

        private void validateLocalAssignment(AssignmentStatement assignment, LocalVariableSymbol variable, TypeSymbol valueType) {
            if (!variable.isMutable()) diagnostics.add(new Diagnostic(
                IMMUTABLE_ASSIGNMENT_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot assign to immutable variable '%s'.".formatted(variable.name()),
                assignment.target().span())
            );

            validateAssignmentType(assignment, variable.name(), resolvedTypeOf(variable), valueType);
        }

        private void reportImmutableParameter(AssignmentStatement assignment, ParameterSymbol parameter) {
            diagnostics.add(new Diagnostic(
                IMMUTABLE_ASSIGNMENT_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot assign to immutable parameter '%s'.".formatted(parameter.name()),
                assignment.target().span()
            ));
        }

        private void validateAssignmentType(AssignmentStatement assignment, String targetName, TypeSymbol targetType, TypeSymbol valueType) {
            if (targetType == BuiltInTypes.ERROR || valueType == BuiltInTypes.ERROR || sameType(targetType, valueType)) return;

            diagnostics.add(new Diagnostic(
                INCOMPATIBLE_ASSIGNMENT_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot assign value of type '%s' to '%s' of type '%s'.".formatted(valueType.name(), targetName, targetType.name()),
                assignment.value().span()
            ));
        }

        private void validateArgumentCount(CallExpression call, FunctionSymbol function) {
            var expected = function.declaration().parameters().size();
            var actual = call.arguments().size();

            if (expected == actual) return;

            var argumentWord = expected == 1 ? "argument" : "arguments";

            diagnostics.add(new Diagnostic(
                INCORRECT_ARGUMENT_COUNT_CODE,
                DiagnosticSeverity.ERROR,
                "Function '%s' expects %d %s, but received %d.".formatted(function.name(), expected, argumentWord, actual),
                call.span()
            ));
        }

        private void validateArgumentTypes(
            CallExpression call,
            FunctionSymbol function,
            List<TypeSymbol> argumentTypes,
            Map<TypeParameterSymbol, TypeSymbol> substitutions
        ) {
            var parameters = function.declaration().parameters();
            var comparableCount = Math.min(parameters.size(), argumentTypes.size());

            for (var index = 0; index < comparableCount; index++) {
                var parameter = parameters.get(index);
                var expectedType = TypeSubstitution.substitute(resolvedTypeOf(parameter.type()), substitutions);
                var actualType = argumentTypes.get(index);

                if (expectedType == BuiltInTypes.ERROR || actualType == BuiltInTypes.ERROR || !expectedType.isValue() || sameType(expectedType, actualType)) continue;

                diagnostics.add(new Diagnostic(
                    INCOMPATIBLE_ARGUMENT_CODE,
                    DiagnosticSeverity.ERROR,
                    "Argument %d of function '%s' expects type '%s', but found '%s'.".formatted(index + 1, function.name(), expectedType.name(), actualType.name()),
                    call.arguments().get(index).span()
                ));
            }
        }

        private TypeSymbol resolvedTypeOf(LocalVariableSymbol variable) {
            return resolvedTypeOf(variable.type());
        }

        private TypeSymbol resolvedTypeOf(ParameterSymbol parameter) {
            return resolvedTypeOf(parameter.type());
        }

        private boolean sameType(TypeSymbol left, TypeSymbol right) {
            return left == right || left.equals(right);
        }

        private void resolveInjections() {
            for (var declaration : unit.declarations()) if (declaration instanceof InjectionDeclaration injection) resolveInjection(injection);
        }

        private void resolveInjection(InjectionDeclaration injection) {
            var targetName = new ModuleName(injection.modulePath().segments());
            var targetModule = modules.get(targetName);

            if (targetModule == null) {
                diagnostics.add(new Diagnostic(
                    UNRESOLVED_MODULE_CODE,
                    DiagnosticSeverity.ERROR,
                    "Cannot resolve module '%s'.".formatted(targetName.qualifiedName()),
                    injection.modulePath().span()
                ));

                return;
            }

            injectedModules.put(injection, targetModule);

            switch (injection.kind()) {
                case DIRECT -> resolveDirectInjection(injection, targetModule);
                case NAMESPACE -> resolveNamespaceInjection(injection, targetModule);
            }
        }

        private void resolveDirectInjection(InjectionDeclaration injection, ModuleSymbol targetModule) {
            var injected = new ArrayList<FunctionSymbol>();

            if (injection.selectedNames().isEmpty()) {
                for (var function : targetModule.exportedFunctions()) declareInjectedFunction(injection, function, injected);
                for (var struct : targetModule.exportedStructs()) declareInjectedStruct(injection, struct);
            } else {
                for (var selectedName : injection.selectedNames()) {
                    var function = targetModule.exportedFunction(selectedName);
                    var struct = targetModule.exportedStruct(selectedName);

                    if (function.isEmpty() && struct.isEmpty()) {
                        diagnostics.add(new Diagnostic(
                            UNKNOWN_INJECTED_SYMBOL_CODE,
                            DiagnosticSeverity.ERROR,
                            "Module '%s' does not declare function '%s'.".formatted(targetModule.name().qualifiedName(), selectedName),
                            injection.span()
                        ));

                        continue;
                    }

                    function.ifPresent(value -> declareInjectedFunction(injection, value, injected));
                    struct.ifPresent(value -> declareInjectedStruct(injection, value));
                }
            }

            directlyInjectedFunctions.put(injection, List.copyOf(injected));
        }

        private void declareInjectedFunction(InjectionDeclaration injection, FunctionSymbol function, List<FunctionSymbol> injected) {
            if (!moduleScope.declare(function)) {
                reportInjectedDuplicate(function.name(), injection);

                return;
            }

            injected.add(function);
        }

        private void declareInjectedStruct(InjectionDeclaration injection, StructSymbol struct) {
            if (!moduleScope.declare(struct)) reportInjectedDuplicate(struct.name(), injection);
        }

        private void resolveNamespaceInjection(InjectionDeclaration injection, ModuleSymbol targetModule) {
            var namespaceName = injection.alias().orElseGet(() -> targetModule.name().simpleName());
            var namespace = new NamespaceSymbol(namespaceName, targetModule, injection);

            if (!moduleScope.declare(namespace)) {
                reportInjectedDuplicate(namespaceName, injection);

                return;
            }

            injectedNamespaces.put(injection, namespace);
        }

        private void reportInjectedDuplicate(String name, InjectionDeclaration injection) {
            diagnostics.add(new Diagnostic(
                DUPLICATE_DECLARATION_CODE,
                DiagnosticSeverity.ERROR,
                "Duplicate declaration of '%s'.".formatted(name),
                injection.span()
            ));
        }
    }
}
