package io.github.stardragonstudios.sol.lowering;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrParameter;
import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.semantics.FunctionSymbol;

import java.util.List;
import java.util.Objects;

record IrFunctionSignature(
    FunctionSymbol symbol,
    IrFunctionId id,
    List<IrParameter> parameters,
    IrType returnType,
    IrFunctionLoweringContext context
) {
    IrFunctionSignature {
        Objects.requireNonNull(symbol, "Lowered function symbol must not be null.");
        Objects.requireNonNull(id, "Lowered function identifier must not be null.");
        Objects.requireNonNull(parameters, "Lowered function parameters must not be null.");
        Objects.requireNonNull(returnType, "Lowered function return type must not be null.");
        Objects.requireNonNull(context, "Function lowering context must not be null.");

        parameters = List.copyOf(parameters);

        if (context.function() != symbol)
            throw new IrLoweringException("Function signature and lowering context must reference the same canonical function symbol.");
    }

    String name() {
        return context.functionName();
    }

    IrFunction declaration() {
        return IrFunction.declaration(id, name(), parameters, returnType);
    }

    IrFunction definition(List<IrBasicBlock> blocks) {
        Objects.requireNonNull(blocks, "Lowered function blocks must not be null.");

        return IrFunction.definition(id, name(), parameters, returnType, blocks);
    }
}
