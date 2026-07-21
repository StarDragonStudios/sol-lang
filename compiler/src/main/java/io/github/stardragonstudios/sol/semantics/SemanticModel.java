package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
import io.github.stardragonstudios.sol.syntax.*;

import java.util.*;

public final class SemanticModel {
    private final Scope moduleScope;
    private final IdentityHashMap<FunctionDeclaration, Scope> functionScopes;
    private final IdentityHashMap<Block, Scope> blockScopes;
    private final IdentityHashMap<FunctionDeclaration, FunctionSymbol> functionSymbols;
    private final IdentityHashMap<Parameter, ParameterSymbol> parameterSymbols;
    private final IdentityHashMap<VariableDeclarationStatement, LocalVariableSymbol> localVariableSymbols;
    private final IdentityHashMap<NameExpression, Symbol> resolvedNames;
    private final IdentityHashMap<AssignmentStatement, Symbol> assignmentTargets;
    private final IdentityHashMap<TypeReference, TypeSymbol> resolvedTypes;
    private final IdentityHashMap<Expression, TypeSymbol> expressionTypes;
    private final IdentityHashMap<CallExpression, FunctionSymbol> calledFunctions;
    private final IdentityHashMap<QualifiedNameExpression, FunctionSymbol> qualifiedNameSymbols;
    private final IdentityHashMap<InjectionDeclaration, ModuleSymbol> injectedModules;
    private final IdentityHashMap<InjectionDeclaration, List<FunctionSymbol>> directlyInjectedFunctions;
    private final IdentityHashMap<InjectionDeclaration, NamespaceSymbol> injectedNamespaces;

    SemanticModel(
        Scope moduleScope,
        Map<FunctionDeclaration, Scope> functionScopes,
        Map<Block, Scope> blockScopes,
        Map<FunctionDeclaration, FunctionSymbol> functionSymbols,
        Map<Parameter, ParameterSymbol> parameterSymbols,
        Map<VariableDeclarationStatement, LocalVariableSymbol> localVariableSymbols,
        Map<NameExpression, Symbol> resolvedNames,
        Map<AssignmentStatement, Symbol> assignmentTargets,
        Map<CallExpression, FunctionSymbol> calledFunctions,Map<QualifiedNameExpression, FunctionSymbol> qualifiedNameSymbols,
        Map<InjectionDeclaration, ModuleSymbol> injectedModules,
        Map<InjectionDeclaration, List<FunctionSymbol>> directlyInjectedFunctions,
        Map<InjectionDeclaration, NamespaceSymbol> injectedNamespaces,
        Map<TypeReference, TypeSymbol> resolvedTypes,
        Map<Expression, TypeSymbol> expressionTypes
    ) {
        this.moduleScope = Objects.requireNonNull(moduleScope, "Module scope must not be null.");
        this.functionScopes = copyIdentityMap(functionScopes, "Function scope associations");
        this.blockScopes = copyIdentityMap(blockScopes, "Block scope associations");
        this.functionSymbols = copyIdentityMap(functionSymbols, "Function symbol associations");
        this.parameterSymbols = copyIdentityMap(parameterSymbols, "Parameter symbol associations");
        this.localVariableSymbols = copyIdentityMap(localVariableSymbols, "Local variable symbol associations");
        this.resolvedNames = copyIdentityMap(resolvedNames, "Resolved name associations");
        this.assignmentTargets = copyIdentityMap(assignmentTargets, "Assignment target associations");
        this.resolvedTypes = copyIdentityMap(resolvedTypes, "Resolved type associations");
        this.expressionTypes = copyIdentityMap(expressionTypes, "Expression type associations");
        this.calledFunctions = copyIdentityMap(calledFunctions, "Called function associations");
        this.qualifiedNameSymbols = copyIdentityMap(qualifiedNameSymbols, "Qualified name associations");
        this.injectedModules = copyIdentityMap(injectedModules, "Injected module associations");
        this.directlyInjectedFunctions = copyIdentityListMap(directlyInjectedFunctions, "Directly injected function associations");
        this.injectedNamespaces = copyIdentityMap(injectedNamespaces, "Injected namespace associations");
    }

    public Scope moduleScope() {
        return moduleScope;
    }

    public Optional<FunctionSymbol> calledFunctionOf(CallExpression call) {
        Objects.requireNonNull(call, "Call expression must not be null.");
        return Optional.ofNullable(calledFunctions.get(call));
    }

    public Optional<Scope> scopeOf(FunctionDeclaration declaration) {
        Objects.requireNonNull(declaration, "Function declaration must not be null.");

        return Optional.ofNullable(functionScopes.get(declaration));
    }

    public Optional<Scope> scopeOf(Block block) {
        Objects.requireNonNull(block, "Block must not be null.");

        return Optional.ofNullable(blockScopes.get(block));
    }

    public Optional<FunctionSymbol> symbolOf(FunctionDeclaration declaration) {
        Objects.requireNonNull(declaration, "Function declaration must not be null.");

        return Optional.ofNullable(functionSymbols.get(declaration));
    }

    public Optional<ParameterSymbol> symbolOf(Parameter parameter) {
        Objects.requireNonNull(parameter, "Parameter must not be null.");

        return Optional.ofNullable(parameterSymbols.get(parameter));
    }

    public Optional<LocalVariableSymbol> symbolOf(VariableDeclarationStatement declaration) {
        Objects.requireNonNull(declaration, "Variable declaration must not be null.");

        return Optional.ofNullable(localVariableSymbols.get(declaration));
    }

    public Optional<Symbol> symbolOf(NameExpression expression) {
        Objects.requireNonNull(expression, "Name expression must not be null.");

        return Optional.ofNullable(resolvedNames.get(expression));
    }

    public Optional<FunctionSymbol> symbolOf(QualifiedNameExpression expression) {
        Objects.requireNonNull(expression, "Qualified name expression must not be null.");

        return Optional.ofNullable(qualifiedNameSymbols.get(expression));
    }

    public Optional<ModuleSymbol> injectedModuleOf(InjectionDeclaration declaration) {
        Objects.requireNonNull(declaration, "Injection declaration must not be null.");

        return Optional.ofNullable(injectedModules.get(declaration));
    }

    public List<FunctionSymbol> directlyInjectedFunctionsOf(InjectionDeclaration declaration) {
        Objects.requireNonNull(declaration, "Injection declaration must not be null.");

        return directlyInjectedFunctions.getOrDefault(declaration, List.of());
    }

    public Optional<NamespaceSymbol> injectedNamespaceOf(InjectionDeclaration declaration) {
        Objects.requireNonNull(declaration, "Injection declaration must not be null.");

        return Optional.ofNullable(injectedNamespaces.get(declaration));
    }

    public Optional<Symbol> assignmentTargetOf(AssignmentStatement statement) {
        Objects.requireNonNull(statement, "Assignment statement must not be null.");

        return Optional.ofNullable(assignmentTargets.get(statement));
    }

    public Optional<TypeSymbol> typeOf(TypeReference reference) {
        Objects.requireNonNull(reference, "Type reference must not be null.");

        return Optional.ofNullable(resolvedTypes.get(reference));
    }

    public Optional<TypeSymbol> typeOf(Expression expression) {
        Objects.requireNonNull(expression, "Expression must not be null."
        );

        return Optional.ofNullable(expressionTypes.get(expression));
    }

    private static <K, V> IdentityHashMap<K, V> copyIdentityMap(Map<K, V> source, String description) {
        Objects.requireNonNull(source, description + " must not be null.");
        var copy = new IdentityHashMap<K, V>();

        source.forEach((key, value) -> {
            Objects.requireNonNull(key, description + " must not contain null keys.");
            Objects.requireNonNull(value, description + " must not contain null values.");

            copy.put(key, value);
        });

        return copy;
    }

    private static <K, V>
    IdentityHashMap<K, List<V>> copyIdentityListMap(Map<K, List<V>> source, String description) {
        Objects.requireNonNull(source, description + " must not be null.");

        var copy = new IdentityHashMap<K, List<V>>();

        source.forEach((key, values) -> {
            Objects.requireNonNull(key, description + " must not contain null keys.");
            Objects.requireNonNull(values, description + " must not contain null lists.");

            copy.put(key, List.copyOf(values));
        });

        return copy;
    }
}
