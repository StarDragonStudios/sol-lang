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

    private static final ModuleName ISOLATED_MODULE_NAME = new ModuleName(List.of("<isolated>"));

    private SemanticAnalyzer() {}

    public static SemanticAnalysisResult analyze(CompilationUnit unit) {
        Objects.requireNonNull(unit, "Compilation unit must not be null.");

        var sourceModule = new SourceModule(ISOLATED_MODULE_NAME, unit);

        return analyzeModules(List.of(sourceModule)).analysisOf(ISOLATED_MODULE_NAME).orElseThrow();
    }

    public static SemanticProgramAnalysisResult analyzeModules(List<SourceModule> sourceModules) {
        Objects.requireNonNull(sourceModules, "Source modules must not be null.");

        return new ProgramBinder(sourceModules).bind();
    }

    private static final class ProgramBinder {
        private final LinkedHashMap<ModuleName, ModuleSymbol> modules = new LinkedHashMap<>();
        private final LinkedHashMap<ModuleName, Binder> binders = new LinkedHashMap<>();
        private final IdentityHashMap<TypeReference, TypeSymbol> programResolvedTypes = new IdentityHashMap<>();

        private ProgramBinder(List<SourceModule> sourceModules) {
            var modulesCopy = new ArrayList<SourceModule>();

            for (var sourceModule : sourceModules) modulesCopy.add(Objects.requireNonNull(sourceModule, "Source modules must not contain null values."));

            for (var sourceModule : modulesCopy) {
                var module = new ModuleSymbol(sourceModule.name(), sourceModule.unit());

                if (modules.putIfAbsent(sourceModule.name(), module) != null)
                    throw new IllegalArgumentException("Duplicate source module '%s'.".formatted(sourceModule.name().qualifiedName()));
            }

            for (var sourceModule : modulesCopy) {
                var module = modules.get(sourceModule.name());

                binders.put(
                    sourceModule.name(),
                    new Binder(sourceModule, module, modules, programResolvedTypes)
                );
            }
        }

        private SemanticProgramAnalysisResult bind() {
            binders.values().forEach(Binder::predeclareFunctions);
            binders.values().forEach(Binder::resolveInjections);
            binders.values().forEach(Binder::bindFunctionSignatures);
            binders.values().forEach(Binder::bindFunctionBodies);

            var analyses = new LinkedHashMap<ModuleName, SemanticAnalysisResult>();

            binders.forEach((name, binder) -> analyses.put(name, binder.finish()));

            return new SemanticProgramAnalysisResult(modules, analyses);
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
        private final IdentityHashMap<Parameter, ParameterSymbol> parameterSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<VariableDeclarationStatement, LocalVariableSymbol> localVariableSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<NameExpression, Symbol> resolvedNames = new IdentityHashMap<>();
        private final IdentityHashMap<AssignmentStatement, Symbol> assignmentTargets = new IdentityHashMap<>();
        private final IdentityHashMap<FunctionDeclaration, Boolean> duplicateFunctions = new IdentityHashMap<>();
        private final IdentityHashMap<TypeReference, TypeSymbol> resolvedTypes = new IdentityHashMap<>();
        private final IdentityHashMap<Expression, TypeSymbol> expressionTypes = new IdentityHashMap<>();
        private final IdentityHashMap<CallExpression, FunctionSymbol> calledFunctions = new IdentityHashMap<>();
        private final IdentityHashMap<QualifiedNameExpression, FunctionSymbol> qualifiedNameSymbols = new IdentityHashMap<>();
        private final IdentityHashMap<InjectionDeclaration, ModuleSymbol> injectedModules = new IdentityHashMap<>();
        private final IdentityHashMap<InjectionDeclaration, List<FunctionSymbol>> directlyInjectedFunctions = new IdentityHashMap<>();
        private final IdentityHashMap<InjectionDeclaration, NamespaceSymbol> injectedNamespaces = new IdentityHashMap<>();
        private final ModuleSymbol module;
        private final Map<ModuleName, ModuleSymbol> modules;
        private final IdentityHashMap<TypeReference, TypeSymbol> programResolvedTypes;

        private Binder(
            SourceModule sourceModule,
            ModuleSymbol module,
            Map<ModuleName, ModuleSymbol> modules,
            IdentityHashMap<TypeReference, TypeSymbol> programResolvedTypes
        ) {
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
                parameterSymbols,
                localVariableSymbols,
                resolvedNames,
                assignmentTargets,
                calledFunctions,
                qualifiedNameSymbols,
                injectedModules,
                directlyInjectedFunctions,
                injectedNamespaces,
                resolvedTypes,
                expressionTypes
            );

            diagnostics.sort(
                Comparator
                    .comparingInt((Diagnostic diagnostic) -> diagnostic.span().start().offset())
                    .thenComparingInt(diagnostic -> diagnostic.span().end().offset())
            );

            return new SemanticAnalysisResult(model, diagnostics);
        }

        private void predeclareFunctions() {
            for (var declaration : unit.declarations()) {
                if (declaration instanceof FunctionDeclaration function) {
                    var symbol = new FunctionSymbol(function);
                    functionSymbols.put(function, symbol);

                    if (!module.declareExport(symbol)) duplicateFunctions.put(function, true);
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

        private void validateParameterType(FunctionDeclaration function, Parameter parameter, TypeSymbol type) {
            if (type == BuiltInTypes.ERROR || type.isValue()) return;

            diagnostics.add(new Diagnostic(
                INVALID_PARAMETER_TYPE_CODE,
                DiagnosticSeverity.ERROR,
                "Parameter '%s' of function '%s' cannot have non-value type '%s'."
                    .formatted(parameter.name(), function.name(), type.name()),
                parameter.type().span()
            ));
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
                var parameterType = resolveTypeReference(parameter.type());
                validateParameterType(function, parameter, parameterType);

                var parameterSymbol = new ParameterSymbol(parameter);
                parameterSymbols.put(parameter, parameterSymbol);
                declareOrReport(functionScope, parameterSymbol);
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
                bindBlock(body, functionScope, function);
            });
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

            if (statement instanceof ConditionalStatement conditional) {
                bindConditional(conditional, scope, function);
                return;
            }

            if (statement instanceof WhileStatement whileStatement) {
                bindWhile(whileStatement, scope, function);
                return;
            }

            throw new IllegalStateException(
                "Unsupported statement type: " + statement.getClass().getName()
            );
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
                case null, default -> {
                    assert expression != null;
                    throw new IllegalStateException("Unsupported expression type: " + expression.getClass().getName());
                }
            }

            expressionTypes.put(expression, type);

            return type;
        }

        private TypeSymbol bindQualifiedNameExpression(QualifiedNameExpression expression, Scope scope) {
            bindExpression(expression.qualifier(), scope);

            expressionTypes.put(expression.member(), BuiltInTypes.ERROR);

            var qualifierSymbol = resolvedNames.get(expression.qualifier());

            if (qualifierSymbol == null) {
                /*
                 * bindName already emitted SOL-S002.
                 */
                return BuiltInTypes.ERROR;
            }

            if (!(qualifierSymbol instanceof NamespaceSymbol namespace)) {
                diagnostics.add(new Diagnostic(
                    NON_NAMESPACE_QUALIFIER_CODE,
                    DiagnosticSeverity.ERROR,
                    "Name '%s' does not refer to an injected namespace."
                        .formatted(
                            expression.qualifier().name()
                        ),
                    expression.qualifier().span()
                ));

                return BuiltInTypes.ERROR;
            }

            var function = namespace.targetModule().exportedFunction(expression.member().name());

            if (function.isEmpty()) {
                diagnostics.add(new Diagnostic(
                    UNKNOWN_NAMESPACE_MEMBER_CODE,
                    DiagnosticSeverity.ERROR,
                    "Module '%s' does not declare function '%s'."
                        .formatted(
                            namespace.targetModule()
                                .name()
                                .qualifiedName(),
                            expression.member().name()
                        ),
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
            var calleeType = bindExpression(call.callee(), scope);
            var argumentTypes = new ArrayList<TypeSymbol>(call.arguments().size());

            for (var argument : call.arguments()) argumentTypes.add(bindExpression(argument, scope));

            var resolvedFunction = resolvedFunctionOf(call.callee());

            if (resolvedFunction.isEmpty()) {
                if (calleeType != BuiltInTypes.ERROR) {
                    diagnostics.add(new Diagnostic(
                        NOT_CALLABLE_CODE,
                        DiagnosticSeverity.ERROR,
                        "Expression of type '%s' is not callable."
                            .formatted(calleeType.name()),
                        call.callee().span()
                    ));
                }

                return BuiltInTypes.ERROR;
            }

            var function = resolvedFunction.orElseThrow();
            calledFunctions.put(call, function);

            validateArgumentCount(call, function);
            validateArgumentTypes(call, function, argumentTypes);

            return resolvedTypeOf(function.declaration().returnType());
        }

        private void bindReturn(ReturnStatement statement, Scope scope, FunctionDeclaration function) {
            var returnType = resolvedTypes.getOrDefault(function.returnType(), BuiltInTypes.ERROR);

            if (statement.expression().isEmpty()) {
                if (returnType.isValue()) {
                    diagnostics.add(new Diagnostic(
                        MISSING_RETURN_VALUE_CODE,
                        DiagnosticSeverity.ERROR,
                        "Function '%s' must return a value of type '%s'."
                            .formatted(function.name(), returnType.name()),
                        statement.span()
                    ));
                }

                return;
            }

            var expression = statement.expression().orElseThrow();
            var expressionType = bindExpression(expression, scope);

            if (returnType == BuiltInTypes.VOID) {
                diagnostics.add(new Diagnostic(
                    UNEXPECTED_RETURN_VALUE_CODE,
                    DiagnosticSeverity.ERROR,
                    "Function '%s' returns 'void' and cannot return a value."
                        .formatted(function.name()),
                    expression.span()
                ));

                return;
            }

            if (
                returnType == BuiltInTypes.ERROR
                    || expressionType == BuiltInTypes.ERROR
                    || returnType == expressionType
            ) return;


            diagnostics.add(new Diagnostic(
                INCOMPATIBLE_RETURN_CODE,
                DiagnosticSeverity.ERROR,
                "Cannot return value of type '%s' from function '%s' returning '%s'."
                    .formatted(
                        expressionType.name(),
                        function.name(),
                        returnType.name()
                    ),
                expression.span()
            ));
        }


        private Optional<FunctionSymbol> resolvedFunctionOf(Expression expression) {
            if (expression instanceof NameExpression name) {
                var symbol = resolvedNames.get(name);

                if (symbol instanceof FunctionSymbol function) return Optional.of(function);

                return Optional.empty();
            }

            if (expression instanceof ParenthesizedExpression parenthesized)
                return resolvedFunctionOf(parenthesized.expression());

            if (expression instanceof QualifiedNameExpression qualified)
                return Optional.ofNullable(qualifiedNameSymbols.get(qualified));

            return Optional.empty();
        }

        private void validateCondition(Expression condition, TypeSymbol type) {
            if (type == BuiltInTypes.BOOLEAN || type == BuiltInTypes.ERROR) return;

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
                programResolvedTypes.put(reference, type);

                return type;
            }

            resolvedTypes.put(reference, BuiltInTypes.ERROR);
            programResolvedTypes.put(reference, BuiltInTypes.ERROR);

            diagnostics.add(new Diagnostic(
                UNKNOWN_TYPE_CODE,
                DiagnosticSeverity.ERROR,
                "Unknown type '%s'.".formatted(reference.name()),
                reference.span()
            ));

            return BuiltInTypes.ERROR;
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

        private void validateArgumentCount(CallExpression call, FunctionSymbol function) {
            var expected = function.declaration().parameters().size();
            var actual = call.arguments().size();

            if (expected == actual) return;

            var argumentWord = expected == 1 ? "argument" : "arguments";

            diagnostics.add(new Diagnostic(
                INCORRECT_ARGUMENT_COUNT_CODE,
                DiagnosticSeverity.ERROR,
                "Function '%s' expects %d %s, but received %d."
                    .formatted(
                        function.name(),
                        expected,
                        argumentWord,
                        actual
                    ),
                call.span()
            ));
        }

        private void validateArgumentTypes(CallExpression call, FunctionSymbol function, List<TypeSymbol> argumentTypes) {
            var parameters = function.declaration().parameters();
            var comparableCount = Math.min(parameters.size(), argumentTypes.size());

            for (var index = 0; index < comparableCount; index++) {
                var parameter = parameters.get(index);
                var expectedType = resolvedTypeOf(parameter.type());
                var actualType = argumentTypes.get(index);

                if (
                    expectedType == BuiltInTypes.ERROR
                        || actualType == BuiltInTypes.ERROR
                        || !expectedType.isValue()
                        || expectedType == actualType
                ) continue;

                diagnostics.add(new Diagnostic(
                    INCOMPATIBLE_ARGUMENT_CODE,
                    DiagnosticSeverity.ERROR,
                    "Argument %d of function '%s' expects type '%s', but found '%s'."
                        .formatted(
                            index + 1,
                            function.name(),
                            expectedType.name(),
                            actualType.name()
                        ),
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

        private void resolveInjections() {
            for (var declaration : unit.declarations())
                if (declaration instanceof InjectionDeclaration injection)
                    resolveInjection(injection);
        }

        private void resolveInjection(InjectionDeclaration injection) {
            var targetName = new ModuleName(injection.modulePath().segments());

            var targetModule = modules.get(targetName);

            if (targetModule == null) {
                diagnostics.add(new Diagnostic(
                    UNRESOLVED_MODULE_CODE,
                    DiagnosticSeverity.ERROR,
                    "Cannot resolve module '%s'."
                        .formatted(
                            targetName.qualifiedName()
                        ),
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
                for (var function : targetModule.exportedFunctions())
                    declareInjectedFunction(injection, function, injected);
            } else {
                for (var selectedName : injection.selectedNames()) {
                    var function = targetModule.exportedFunction(selectedName);

                    if (function.isEmpty()) {
                        diagnostics.add(new Diagnostic(
                            UNKNOWN_INJECTED_SYMBOL_CODE,
                            DiagnosticSeverity.ERROR,
                            "Module '%s' does not declare function '%s'."
                                .formatted(
                                    targetModule.name()
                                        .qualifiedName(),
                                    selectedName
                                ),
                            injection.span()
                        ));

                        continue;
                    }

                    declareInjectedFunction(injection, function.orElseThrow(), injected);
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
                "Duplicate declaration of '%s'."
                    .formatted(name),
                injection.span()
            ));
        }
    }
}
