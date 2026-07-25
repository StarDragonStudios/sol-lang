package io.github.stardragonstudios.sol.ir;

import java.util.Objects;

public record IrEntryPoint(IrModule module, IrFunction function) {
    public IrEntryPoint {
        Objects.requireNonNull(module, "IR entry point module must not be null.");
        Objects.requireNonNull(function, "IR entry point function must not be null.");

        var declaredByModule = module.functions().stream().anyMatch(candidate -> candidate == function);

        if (!declaredByModule)
            throw new IllegalArgumentException("IR entry point function must be declared by its entry point module.");

        if (!function.hasBody())
            throw new IllegalArgumentException("IR entry point function must have a body.");

        if (function.returnType() != PrimitiveIrType.INT)
            throw new IllegalArgumentException("IR entry point function must return 'int', but returns '%s'.".formatted(function.returnType().displayName()));
    }
}
