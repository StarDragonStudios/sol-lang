package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.Expression;
import io.github.stardragonstudios.sol.syntax.ReturnStatement;
import io.github.stardragonstudios.sol.syntax.Statement;

import java.util.List;
import java.util.Objects;

final class IrFunctionBodyLowerer {
    private IrFunctionBodyLowerer() {}

    static IrFunction lower(IrFunctionSignature signature, SemanticModel model) {
        Objects.requireNonNull(signature, "Lowered function signature must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");

        var declaration = signature.symbol().declaration();
        var body = declaration.body().orElseThrow(
            () -> new IrLoweringException(
                "Function '%s' has no body to lower.".formatted(signature.name())
            )
        );

        var statements = body.statements();

        validateSupportedBody(signature, statements);

        var returnStatement = (ReturnStatement) statements.getFirst();
        var terminator = returnStatement.expression().map(
            (Expression expression) -> IrReturnTerminator.returning(
                IrExpressionLowerer.lower(
                    expression,
                    model,
                    signature.context()
                )
            )
        ).orElseGet(IrReturnTerminator::bare);

        var block = signature.context().finishBlock(terminator);

        try {
            return signature.definition(List.of(block));
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException(
                "Semantically validated function '%s' produced invalid IR: %s".formatted(signature.name(), exception.getMessage())
            );
        }
    }

    private static void validateSupportedBody(IrFunctionSignature signature, List<Statement> statements) {
        if (statements.isEmpty()) throw new IrLoweringException(
            "Function '%s' must contain an explicit return statement in the current IR lowering subset.".formatted(signature.name())
        );

        for (var statement : statements) if (!(statement instanceof ReturnStatement)) throw unsupportedStatement(signature, statement);

        if (statements.size() != 1) throw new IrLoweringException(
            "Function '%s' must contain exactly one top-level return statement in the current IR lowering subset.".formatted(signature.name())
        );
    }

    private static IrLoweringException unsupportedStatement(IrFunctionSignature signature, Statement statement) {
        return new IrLoweringException(
            "Unsupported statement syntax '%s' in function '%s' during IR lowering.".formatted(statement.getClass().getSimpleName(), signature.name())
        );
    }
}
