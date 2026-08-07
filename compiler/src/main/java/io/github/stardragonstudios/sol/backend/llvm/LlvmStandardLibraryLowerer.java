package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrProgram;

import java.util.Objects;

final class LlvmStandardLibraryLowerer {
    private static final String CONSOLE_MODULE = "std.console";

    private LlvmStandardLibraryLowerer() {}

    static void lower(IrProgram program, LlvmProgramLoweringContext context) {
        Objects.requireNonNull(program, "Lowered standard-library IR program must not be null.");
        Objects.requireNonNull(context, "LLVM standard-library lowering context must not be null.");

        for (var module : program.modules()) if (module.name().qualifiedName().equals(CONSOLE_MODULE)) LlvmConsoleLowerer.lower(module, context);
    }
}
