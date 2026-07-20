package io.github.stardragonstudios.sol.semantics;

import io.github.stardragonstudios.sol.diagnostics.Diagnostic;
import io.github.stardragonstudios.sol.diagnostics.DiagnosticSeverity;
import io.github.stardragonstudios.sol.semantics.types.BuiltInTypes;
import io.github.stardragonstudios.sol.semantics.types.TypeSymbol;
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

    private SemanticAnalyzer() {}

    public static SemanticAnalysisResult analyze(CompilationUnit unit) {
        Objects.requireNonNull(unit, "Compilation unit must not be null.");

        return new Binder(unit).bind();
    }

    private static final class Binder {
        private final CompilationUnit unit;
        private final Scope moduleScope;
        private final List<Scope> scopes = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final IdentityHashMap<FunctionDeclaration, Scope> functionScopes = new IdentityHashMap<>();
        private final IdentityHashMap<Block, Scope> blockScopes = new IdentityHashMap<>();
        private final IdentityHashMap<FunctionDeclaration, FunctionSymbol> functionSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<Parameter, ParameterSymbol> parameterSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<VariableDeclarationStatement, LocalVariableSymbol> localVariableSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<NameExpression, Symbol> resolvedNames = new IdentityHashMap<>();
        private final IdentityHashMap<AssignmentStatement, Symbol> assignmentTargets = new IdentityHashMap<>();
        private final IdentityHashMap<FunctionDeclaration, Boolean> duplicateFunctions = new IdentityHashMap<>();
        private final IdentityHashMap<TypeReference, TypeSymbol> resolvedTypes = new IdentityHashMap<>();
        private final IdentityHashMap<Expression, TypeSymbol> expressionTypes = new IdentityHashMap<>();

        private Binder(CompilationUnit unit) {
            this.unit = unit;

            moduleScope = new Scope(
                ScopeKind.MODULE
            );

            scopes.add(moduleScope);
        }

        private SemanticAnalysisResult bind() {
            predeclareFunctions();
            bindFunctionSignatures();
            bindFunctionBodies();

            scopes.forEach(Scope::freeze);

            var model = new SemanticModel(
                moduleScope,
                functionScopes,
                blockScopes,
                functionSymbols,
                parameterSymbols,
                localVariableSymbols,
                resolvedNames,
                assignmentTargets,
                resolvedTypes,
                expressionTypes
            );

            diagnostics.sort(
                Comparator
                    .comparingInt(
                        (Diagnostic diagnostic) ->
                            diagnostic.span()
                                .start()
                                .offset()
                    )
                    .thenComparingInt(
                        diagnostic ->
                            diagnostic.span()
                                .end()
                                .offset()
                    )
            );

            return new SemanticAnalysisResult(
                model,
                diagnostics
            );
        }

        private void predeclareFunctions() {
            for (var declaration : unit.declarations()) {
                if (declaration instanceof FunctionDeclaration function) {
                    var symbol = new FunctionSymbol(function);
                    functionSymbols.put(function, symbol);

                    if (!moduleScope.declare(symbol)) duplicateFunctions.put(function, true);
                }
            }
        }

        private void bindFunctionSignatures() {
            for (var declaration : unit.declarations()) {
                if (declaration instanceof FunctionDeclaration function) {
                    bindFunctionSignature(function);
                }
            }
        }

        private void bindFunctionSignature(FunctionDeclaration function) {
            var functionSymbol = functionSymbols.get(function);

            if (duplicateFunctions.containsKey(function)) {
                reportDuplicate(functionSymbol);
            }

            var functionScope = createChildScope(
                ScopeKind.FUNCTION,
                moduleScope
            );

            functionScopes.put(function, functionScope);

            for (var parameter : function.parameters()) {
                resolveTypeReference(parameter.type());

                var parameterSymbol =
                    new ParameterSymbol(parameter);

                parameterSymbols.put(
                    parameter,
                    parameterSymbol
                );

                declareOrReport(
                    functionScope,
                    parameterSymbol
                );
            }

            resolveTypeReference(function.returnType());
        }

        private void bindFunctionBodies() {
            for (var declaration : unit.declarations()) {
                if (declaration instanceof FunctionDeclaration function) {
                    bindFunctionBody(function);
                }
            }
        }

        private void bindFunctionBody(FunctionDeclaration function) {
            function.body().ifPresent(body -> {
                var functionScope = functionScopes.get(function);

                blockScopes.put(body, functionScope);
                bindBlock(body, functionScope);
            });
        }

        private void bindBlock(Block block, Scope scope) {
            for (var statement : block.statements()) bindStatement(statement, scope);
        }

        private void bindStatement(Statement statement, Scope scope) {
            if (statement instanceof ReturnStatement returnStatement) {
                returnStatement.expression().ifPresent(expression -> bindExpression(expression, scope));

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

            if (statement instanceof ConditionalStatement conditional) {
                bindConditional(conditional, scope);

                return;
            }

            if (statement instanceof WhileStatement whileStatement) {
                bindWhile(whileStatement, scope);

                return;
            }

            throw new IllegalStateException("Unsupported statement type: " + statement.getClass().getName());
        }

        private void bindVariableDeclaration(VariableDeclarationStatement declaration, Scope scope) {
            var declaredType = resolveTypeReference(declaration.type());

            /*
             * The initializer is bound before the variable
             * enters its scope. A variable is therefore not
             * visible inside its own initializer.
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

        private void validateVariableType(VariableDeclarationStatement declaration, TypeSymbol declaredType) {
            if (declaredType == BuiltInTypes.ERROR || declaredType.isValue()) return;

            diagnostics.add(new Diagnostic(
                INVALID_VARIABLE_TYPE_CODE,
                DiagnosticSeverity.ERROR,
                "Variable '%s' cannot have non-value type '%s'."
                    .formatted(
                        declaration.name(),
                        declaredType.name()
                    ),
                declaration.type().span()
            ));
        }

        private void validateVariableInitializer(VariableDeclarationStatement declaration, TypeSymbol declaredType, TypeSymbol initializerType) {
            if (
                declaredType == BuiltInTypes.ERROR
                    || initializerType == BuiltInTypes.ERROR
                    || !declaredType.isValue()
                    || declaredType == initializerType
            ) return;

            diagnostics.add(new Diagnostic(
                INCOMPATIBLE_INITIALIZER_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot initialize variable '%s' of type '%s' with value of type '%s'."
                    .formatted(
                        declaration.name(),
                        declaredType.name(),
                        initializerType.name()
                    ),
                declaration.initializer().span()
            ));
        }

        private void bindConditional(ConditionalStatement conditional, Scope scope) {
            var conditionType = bindExpression(conditional.condition(), scope);
            validateCondition(conditional.condition(), conditionType);
            bindNestedBlock(conditional.thenBlock(), scope);
            conditional.elseBlock().ifPresent(block -> bindNestedBlock(block, scope));
        }

        private void bindWhile(WhileStatement whileStatement, Scope scope) {
            var conditionType = bindExpression(whileStatement.condition(), scope);
            validateCondition(whileStatement.condition(), conditionType);
            bindNestedBlock(whileStatement.body(), scope);
        }

        private void bindNestedBlock(Block block, Scope parent) {
            var blockScope = createChildScope(ScopeKind.BLOCK, parent);
            blockScopes.put(block, blockScope);
            bindBlock(block, blockScope);
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
                case null, default -> {
                    assert expression != null;
                    throw new IllegalStateException("Unsupported expression type: " + expression.getClass().getName());
                }
            }

            expressionTypes.put(expression, type);

            return type;
        }

        private TypeSymbol bindNameExpression(NameExpression expression, Scope scope) {
            var symbol = bindName(expression, scope);

            return symbol
                .map(this::typeOfValueSymbol)
                .orElse(BuiltInTypes.ERROR);
        }

        private TypeSymbol typeOfValueSymbol(Symbol symbol) {
            if (symbol instanceof ParameterSymbol parameter) {
                return resolvedTypes.getOrDefault(
                    parameter.type(),
                    BuiltInTypes.ERROR
                );
            }

            if (symbol instanceof LocalVariableSymbol localVariable) {
                return resolvedTypes.getOrDefault(
                    localVariable.type(),
                    BuiltInTypes.ERROR
                );
            }

            return BuiltInTypes.ERROR;
        }

        private TypeSymbol bindCallExpression(CallExpression call, Scope scope) {
            bindExpression(call.callee(), scope);

            for (var argument : call.arguments()) {
                bindExpression(argument, scope);
            }

            return resolvedFunctionOf(call.callee())
                .map(function -> resolvedTypes.getOrDefault(
                    function.declaration().returnType(),
                    BuiltInTypes.ERROR
                ))
                .orElse(BuiltInTypes.ERROR);
        }

        private Optional<FunctionSymbol> resolvedFunctionOf(Expression expression) {
            if (expression instanceof NameExpression name) {
                var symbol = resolvedNames.get(name);

                if (symbol instanceof FunctionSymbol function) {
                    return Optional.of(function);
                }

                return Optional.empty();
            }

            if (expression instanceof ParenthesizedExpression parenthesized) {
                return resolvedFunctionOf(
                    parenthesized.expression()
                );
            }

            return Optional.empty();
        }

        private void validateCondition(Expression condition, TypeSymbol type) {
            if (
                type == BuiltInTypes.BOOLEAN
                    || type == BuiltInTypes.ERROR
            ) {
                return;
            }

            diagnostics.add(new Diagnostic(
                NON_BOOLEAN_CONDITION_CODE,
                DiagnosticSeverity.ERROR,
                "Condition must have type 'boolean', but found '%s'."
                    .formatted(type.name()),
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
            var primitive = BuiltInTypes.lookup(reference.name());

            if (primitive.isPresent()) {
                var type = primitive.orElseThrow();

                resolvedTypes.put(reference, type);

                return type;
            }

            resolvedTypes.put(reference, BuiltInTypes.ERROR);

            diagnostics.add(new Diagnostic(
                UNKNOWN_TYPE_CODE,
                DiagnosticSeverity.ERROR,
                "Unknown type '%s'.".formatted(reference.name()),
                reference.span()
            ));

            return BuiltInTypes.ERROR;
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
                     * bindName already emitted SOL-S002.
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
                "Cannot assign to '%s' because it is not a variable."
                    .formatted(targetSymbol.name()),
                assignment.target().span()
            ));
        }

        private void validateLocalAssignment(AssignmentStatement assignment, LocalVariableSymbol variable, TypeSymbol valueType) {
            if (!variable.isMutable()) {
                diagnostics.add(new Diagnostic(
                    IMMUTABLE_ASSIGNMENT_CODE,
                    DiagnosticSeverity.ERROR,
                    "Cannot assign to immutable variable '%s'."
                        .formatted(variable.name()),
                    assignment.target().span()
                ));
            }

            validateAssignmentType(assignment, variable.name(), resolvedTypeOf(variable), valueType);
        }

        private void reportImmutableParameter(AssignmentStatement assignment, ParameterSymbol parameter) {
            diagnostics.add(new Diagnostic(
                IMMUTABLE_ASSIGNMENT_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot assign to immutable parameter '%s'."
                    .formatted(parameter.name()),
                assignment.target().span()
            ));
        }

        private void validateAssignmentType(AssignmentStatement assignment, String targetName, TypeSymbol targetType, TypeSymbol valueType) {
            if (targetType == BuiltInTypes.ERROR || valueType == BuiltInTypes.ERROR || targetType == valueType) return;

            diagnostics.add(new Diagnostic(
                INCOMPATIBLE_ASSIGNMENT_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot assign value of type '%s' to '%s' of type '%s'."
                    .formatted(
                        valueType.name(),
                        targetName,
                        targetType.name()
                    ),
                assignment.value().span()
            ));
        }

        private TypeSymbol resolvedTypeOf(LocalVariableSymbol variable) {
            return resolvedTypes.getOrDefault(variable.type(), BuiltInTypes.ERROR);
        }

        private TypeSymbol resolvedTypeOf(ParameterSymbol parameter) {
            return resolvedTypes.getOrDefault(parameter.type(), BuiltInTypes.ERROR);
        }
    }
}
