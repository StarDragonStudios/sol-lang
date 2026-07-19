package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.syntax.AssignmentStatement;
import io.github.stardragonstudios.sol.syntax.Block;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import io.github.stardragonstudios.sol.syntax.NameExpression;
import io.github.stardragonstudios.sol.syntax.Parameter;
import io.github.stardragonstudios.sol.syntax.VariableDeclarationStatement;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SemanticModel {
    private final Scope moduleScope;
    private final IdentityHashMap<FunctionDeclaration, Scope> functionScopes;
    private final IdentityHashMap<Block, Scope> blockScopes;
    private final IdentityHashMap<FunctionDeclaration, FunctionSymbol> functionSymbols;
    private final IdentityHashMap<Parameter, ParameterSymbol> parameterSymbols;
    private final IdentityHashMap<VariableDeclarationStatement, LocalVariableSymbol> localVariableSymbols;
    private final IdentityHashMap<NameExpression, Symbol> resolvedNames;
    private final IdentityHashMap<AssignmentStatement, Symbol> assignmentTargets;

    SemanticModel(
        Scope moduleScope,
        Map<FunctionDeclaration, Scope> functionScopes,
        Map<Block, Scope> blockScopes,
        Map<FunctionDeclaration, FunctionSymbol> functionSymbols,
        Map<Parameter, ParameterSymbol> parameterSymbols,
        Map<VariableDeclarationStatement, LocalVariableSymbol> localVariableSymbols,
        Map<NameExpression, Symbol> resolvedNames,
        Map<AssignmentStatement, Symbol> assignmentTargets
    ) {
        this.moduleScope = Objects.requireNonNull(moduleScope, "Module scope must not be null.");
        this.functionScopes = copyIdentityMap(functionScopes, "Function scope associations");
        this.blockScopes = copyIdentityMap(blockScopes, "Block scope associations");
        this.functionSymbols = copyIdentityMap(functionSymbols, "Function symbol associations");
        this.parameterSymbols = copyIdentityMap(parameterSymbols, "Parameter symbol associations");
        this.localVariableSymbols = copyIdentityMap(localVariableSymbols, "Local variable symbol associations");
        this.resolvedNames = copyIdentityMap(resolvedNames, "Resolved name associations");
        this.assignmentTargets = copyIdentityMap(assignmentTargets, "Assignment target associations");
    }

    public Scope moduleScope() {
        return moduleScope;
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

    public Optional<Symbol> assignmentTargetOf(AssignmentStatement statement) {
        Objects.requireNonNull(statement, "Assignment statement must not be null.");

        return Optional.ofNullable(assignmentTargets.get(statement));
    }

    private static <K, V>
    IdentityHashMap<K, V> copyIdentityMap(Map<K, V> source, String description) {
        Objects.requireNonNull(source, description + " must not be null.");

        var copy = new IdentityHashMap<K, V>();

        source.forEach((key, value) -> {
            Objects.requireNonNull(key, description + " must not contain null keys.");
            Objects.requireNonNull(value, description + " must not contain null values.");

            copy.put(key, value);
        });

        return copy;
    }
}
