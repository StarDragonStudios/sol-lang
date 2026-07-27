package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.semantics.SemanticModel;

import java.util.Objects;

final class IrFunctionLowerer {
    private IrFunctionLowerer() {}

    static IrFunction lower(IrFunctionSignature signature, SemanticModel model) {
        Objects.requireNonNull(signature, "Lowered function signature must not be null.");
        Objects.requireNonNull(model, "Semantic model must not be null.");

        if (signature.symbol().declaration().body().isEmpty()) return signature.declaration();

        return IrFunctionBodyLowerer.lower(signature, model);
    }
}
