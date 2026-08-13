package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.Map;
import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.*;

final class LlvmVectorLowerer {
    private static final String VECTOR_MODULE = "std.collections.vector";
    private static final Map<String, String> FAILURES = Map.of(
        "_vector_fail_allocation", "Sol runtime error: vector allocation failed.",
        "_vector_fail_bounds", "Sol runtime error: vector index out of bounds.",
        "_vector_fail_capacity", "Sol runtime error: invalid or overflowing vector capacity.",
        "_vector_fail_empty_pop", "Sol runtime error: cannot pop an empty vector."
    );

    private LlvmVectorLowerer() {}

    static void lower(IrModule module, LlvmProgramLoweringContext context) {
        Objects.requireNonNull(module, "Lowered vector IR module must not be null.");
        Objects.requireNonNull(context, "LLVM vector lowering context must not be null.");

        if (!module.name().qualifiedName().equals(VECTOR_MODULE))
            throw new LlvmBackendException("LLVM vector lowerer cannot lower module '%s'.".formatted(module.name().qualifiedName()));

        for (var function : module.functions()) {
            var message = FAILURES.get(function.name());

            if (message != null) lowerFailure(function, message, context);
        }
    }

    private static void lowerFailure(IrFunction function, String message, LlvmProgramLoweringContext context) {
        if (function.hasBody() || !function.parameters().isEmpty() || function.returnType() != PrimitiveIrType.VOID)
            throw new LlvmBackendException("Vector failure helper '%s' has an invalid signature.".formatted(function.name()));

        var llvmContext = context.module().contextHandle();
        var builder = LLVMCreateBuilderInContext(llvmContext);
        requireValue(builder, "vector failure builder");

        try {
            var handle = context.function(function.id()).value();
            var entry = LLVMAppendBasicBlockInContext(llvmContext, handle, "vector.failure");
            var pointer = LLVMPointerTypeInContext(llvmContext, 0);
            var i32 = LLVMInt32TypeInContext(llvmContext);
            var puts = external(context, "puts", i32, pointer);
            var exit = external(context, "exit", LLVMVoidTypeInContext(llvmContext), i32);

            requireValue(entry, "vector failure block");
            LLVMPositionBuilderAtEnd(builder, entry);

            var diagnostic = LLVMBuildGlobalStringPtr(builder, message, "sol.vector.error." + function.name());
            call(builder, puts, diagnostic);
            call(builder, exit, LLVMConstInt(i32, 70, 0));
            requireValue(LLVMBuildUnreachable(builder), "vector failure terminator");
        } finally {
            LLVMDisposeBuilder(builder);
            builder.setNull();
        }
    }

    private static HostFunction external(LlvmProgramLoweringContext context, String name, LLVMTypeRef returnType, LLVMTypeRef... parameterTypes) {
        final LLVMTypeRef functionType;

        try (var nativeTypes = new PointerPointer<LLVMTypeRef>(parameterTypes)) {
            functionType = LLVMFunctionType(returnType, nativeTypes, parameterTypes.length, 0);
        }

        var function = LLVMGetNamedFunction(context.module().moduleHandle(), name);
        if (Pointer.isNull(function)) function = LLVMAddFunction(context.module().moduleHandle(), name, functionType);

        requireValue(functionType, "vector host function type '%s'".formatted(name));
        requireValue(function, "vector host function '%s'".formatted(name));
        return new HostFunction(functionType, function);
    }

    private static void call(LLVMBuilderRef builder, HostFunction function, LLVMValueRef... arguments) {
        final LLVMValueRef result;

        try (var nativeArguments = new PointerPointer<LLVMValueRef>(arguments)) {
            result = LLVMBuildCall2(builder, function.type(), function.value(), nativeArguments, arguments.length, "");
        }

        requireValue(result, "vector host call");
    }

    private static void requireValue(Pointer value, String description) {
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM failed to create %s.".formatted(description));
    }

    private record HostFunction(LLVMTypeRef type, LLVMValueRef value) {}
}
