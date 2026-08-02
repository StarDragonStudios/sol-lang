package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.semantics.SemanticModel;
import io.github.stardragonstudios.sol.syntax.Block;

import java.util.Objects;

final class IrFunctionBodyLowerer {
    private IrFunctionBodyLowerer() {}

    static IrFunction lower(IrFunctionSignature signature, SemanticModel model) {
        Objects.requireNonNull(signature, "Lowered function signature must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");

        var body = getBody(signature);

        /*
         * Materialize block0 before recursively lowering the syntax body.
         * Nested blocks are always entered explicitly by IrBlockLowerer.
         */
        signature.context().currentBlockTarget();

        IrBlockLowerer.lower(body, model, signature.context());

        if (signature.context().hasActiveBlock())
            throw new IrLoweringException("Function '%s' can reach the end of its body without an explicit return.".formatted(signature.name()));

        try {
            return signature.definition(signature.context().blocks());
        } catch (IllegalArgumentException exception) {
            throw new IrLoweringException("Semantically validated function '%s' produced invalid IR: %s".formatted(signature.name(), exception.getMessage()));
        }
    }

    private static Block getBody(IrFunctionSignature signature) {
        return signature.symbol()
            .declaration()
            .body()
            .orElseThrow(() -> new IrLoweringException("Function '%s' has no body to lower.".formatted(signature.name())));
    }
}
