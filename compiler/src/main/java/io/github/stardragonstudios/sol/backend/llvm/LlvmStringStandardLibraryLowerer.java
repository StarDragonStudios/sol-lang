package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.*;

final class LlvmStringStandardLibraryLowerer {
    private static final String STRING_MODULE = "std.string";
    private static final String LENGTH = "length";
    private static final String SLICE = "slice";
    private static final String SUBSTRING = "substring";

    private LlvmStringStandardLibraryLowerer() {}

    static void lower(IrModule module, LlvmProgramLoweringContext context) {
        Objects.requireNonNull(module, "Lowered string IR module must not be null.");
        Objects.requireNonNull(context, "LLVM string standard-library context must not be null.");

        if (!module.name().qualifiedName().equals(STRING_MODULE)) throw new LlvmBackendException(
            "LLVM string standard-library lowerer cannot lower module '%s'.".formatted(module.name().qualifiedName())
        );

        for (var function : module.functions()) {
            switch (function.name()) {
                case LENGTH -> lowerLength(function, context);
                case SLICE -> lowerSlice(function, context);
                case SUBSTRING -> lowerSubstring(function, context);

                default -> {
                    /* Future std.string operations are lowered independently. */
                }
            }
        }
    }

    private static void lowerLength(IrFunction function, LlvmProgramLoweringContext context) {
        validate(function, 1);

        if (function.returnType() != PrimitiveIrType.INT)
            throw invalidSignature(function, "must return 'int'");

        withBuilder(function, context, builder -> {
            var entry = appendBlock(function, context, "string.length.entry");

            LLVMPositionBuilderAtEnd(builder, entry);

            var value = parameter(function, context, 0);
            var length = context.stringRuntime().scalarLength(builder, value, "scalar_length");

            requireValue(LLVMBuildRet(builder, length), "return of standard string function 'length'");
        });
    }

    private static void lowerSlice(IrFunction function, LlvmProgramLoweringContext context) {
        validate(function, 3);

        if (function.returnType() != PrimitiveIrType.STRING)
            throw invalidSignature(function, "must return 'string'");

        withBuilder(function, context, builder -> {
            var entry = appendBlock(function, context, "string.slice.entry");

            LLVMPositionBuilderAtEnd(builder, entry);

            var result = context.stringRuntime().slice(
                builder,
                parameter(function, context, 0),
                parameter(function, context, 1),
                parameter(function, context, 2),
                "slice"
            );

            requireValue(LLVMBuildRet(builder, result), "return of standard string function 'slice'");
        });
    }

    private static void lowerSubstring(IrFunction function, LlvmProgramLoweringContext context) {
        validate(function, 3);

        if (function.returnType() != PrimitiveIrType.STRING)
            throw invalidSignature(function, "must return 'string'");

        withBuilder(function, context, builder -> {
            var entry = appendBlock(function, context, "string.substring.entry");

            LLVMPositionBuilderAtEnd(builder, entry);

            var result = context.stringRuntime().substring(
                builder,
                parameter(function, context, 0),
                parameter(function, context, 1),
                parameter(function, context, 2),
                "substring"
            );

            requireValue(LLVMBuildRet(builder, result), "return of standard string function 'substring'");
        });
    }

    private static void validate(IrFunction function, int parameterCount) {
        if (function.hasBody()) throw invalidSignature(function, "must be a bodyless standard-library declaration");
        if (function.parameters().size() != parameterCount)
            throw invalidSignature(function, "must declare exactly %d parameter(s)".formatted(parameterCount));
        if (function.parameters().getFirst().type() != PrimitiveIrType.STRING)
            throw invalidSignature(function, "must accept 'string' as its first parameter");

        for (var index = 1; index < function.parameters().size(); index++)
            if (function.parameters().get(index).type() != PrimitiveIrType.INT)
                throw invalidSignature(function, "must accept 'int' for index parameter %d".formatted(index));
    }

    private static org.bytedeco.llvm.LLVM.LLVMValueRef parameter(
        IrFunction function,
        LlvmProgramLoweringContext context,
        int index
    ) {
        var value = LLVMGetParam(context.function(function.id()).value(), index);

        requireValue(value, "parameter %d of standard string function '%s'".formatted(index, function.name()));

        return value;
    }

    private static org.bytedeco.llvm.LLVM.LLVMBasicBlockRef appendBlock(
        IrFunction function,
        LlvmProgramLoweringContext context,
        String name
    ) {
        var block = LLVMAppendBasicBlockInContext(
            context.module().contextHandle(),
            context.function(function.id()).value(),
            name
        );

        requireValue(block, "standard string block '%s'".formatted(name));

        return block;
    }

    private static void withBuilder(IrFunction function, LlvmProgramLoweringContext context, BuilderAction action) {
        var builder = LLVMCreateBuilderInContext(context.module().contextHandle());

        requireValue(builder, "builder for standard string function '%s'".formatted(function.name()));

        try {
            action.lower(builder);
        } finally {
            LLVMDisposeBuilder(builder);
            builder.setNull();
        }
    }

    private static LlvmBackendException invalidSignature(IrFunction function, String reason) {
        return new LlvmBackendException("Standard string function '%s' %s.".formatted(function.name(), reason));
    }

    private static void requireValue(Pointer value, String description) {
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM failed to create %s.".formatted(description));
    }

    @FunctionalInterface
    private interface BuilderAction {
        void lower(LLVMBuilderRef builder);
    }
}
