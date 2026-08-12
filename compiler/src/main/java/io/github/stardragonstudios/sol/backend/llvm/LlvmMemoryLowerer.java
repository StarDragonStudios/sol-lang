package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrPointerType;
import io.github.stardragonstudios.sol.ir.IrStructType;
import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMAddFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCall2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildICmp;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildMul;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildGEP2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStore;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRet;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRetVoid;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildSDiv;
import static org.bytedeco.llvm.global.LLVM.LLVMConstInt;
import static org.bytedeco.llvm.global.LLVM.LLVMConstNull;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBuilderInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBuilder;
import static org.bytedeco.llvm.global.LLVM.LLVMFunctionType;
import static org.bytedeco.llvm.global.LLVM.LLVMGetParam;
import static org.bytedeco.llvm.global.LLVM.LLVMGetNamedFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMIntEQ;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSGT;
import static org.bytedeco.llvm.global.LLVM.LLVMIntSLE;
import static org.bytedeco.llvm.global.LLVM.LLVMInt64TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPointerTypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;
import static org.bytedeco.llvm.global.LLVM.LLVMSizeOf;
import static org.bytedeco.llvm.global.LLVM.LLVMVoidTypeInContext;

final class LlvmMemoryLowerer {
    private static final String MEMORY_MODULE = "std.memory";
    private static final String ALLOCATE = "allocate";
    private static final String REALLOCATE = "reallocate";
    private static final String FREE = "free";
    private static final String LOAD = "load";
    private static final String STORE = "store";
    private static final String LOAD_AT = "load_at";
    private static final String STORE_AT = "store_at";

    private LlvmMemoryLowerer() {}

    static void lower(IrModule module, LlvmProgramLoweringContext context) {
        Objects.requireNonNull(module, "Lowered memory IR module must not be null.");
        Objects.requireNonNull(context, "LLVM memory lowering context must not be null.");

        if (!module.name().qualifiedName().equals(MEMORY_MODULE)) throw new LlvmBackendException(
            "LLVM memory lowerer cannot lower module '%s'.".formatted(module.name().qualifiedName())
        );

        var host = declareHostFunctions(context);

        for (var function : module.functions()) {
            switch (sourceName(function)) {
                case ALLOCATE -> lowerAllocate(function, host.malloc(), context);
                case REALLOCATE -> lowerReallocate(function, host.realloc(), host.free(), context);
                case FREE -> lowerFree(function, host.free(), context);
                case LOAD -> lowerLoad(function, false, context);
                case STORE -> lowerStore(function, false, context);
                case LOAD_AT -> lowerLoad(function, true, context);
                case STORE_AT -> lowerStore(function, true, context);

                default -> {
                    /* Future std.memory operations are lowered independently. */
                }
            }
        }
    }

    private static void lowerAllocate(IrFunction function, HostFunction malloc, LlvmProgramLoweringContext context) {
        var pointerType = validateAllocate(function);

        withBuilder(function, context, builder -> {
            var llvmContext = context.module().contextHandle();
            var handle = context.function(function.id()).value();
            var entry = appendBlock(llvmContext, handle, "memory.entry");
            var invalid = appendBlock(llvmContext, handle, "memory.invalid");

            LLVMPositionBuilderAtEnd(builder, entry);

            if (isZeroSized(pointerType.elementType())) {
                requireValue(LLVMBuildBr(builder, invalid), "zero-sized allocation branch");
            } else {
                var sizeCheck = appendBlock(llvmContext, handle, "memory.size_check");
                var allocate = appendBlock(llvmContext, handle, "memory.allocate");
                var count = requireParameter(handle, 0, function);
                var zero = LLVMConstInt(LLVMInt64TypeInContext(llvmContext), 0, 0);
                var positive = LLVMBuildICmp(builder, LLVMIntSGT, count, zero, "positive_count");

                requireValue(positive, "positive allocation count condition");
                requireValue(LLVMBuildCondBr(builder, positive, sizeCheck, invalid), "allocation count branch");

                LLVMPositionBuilderAtEnd(builder, sizeCheck);

                var elementSize = LLVMSizeOf(LlvmTypeLowerer.lower(pointerType.elementType(), llvmContext));
                var maximum = LLVMConstInt(LLVMInt64TypeInContext(llvmContext), Long.MAX_VALUE, 0);
                var maximumCount = LLVMBuildSDiv(builder, maximum, elementSize, "maximum_count");
                var fits = LLVMBuildICmp(builder, LLVMIntSLE, count, maximumCount, "allocation_fits");

                requireValue(elementSize, "allocation element size");
                requireValue(maximumCount, "maximum allocation count");
                requireValue(fits, "allocation overflow condition");
                requireValue(LLVMBuildCondBr(builder, fits, allocate, invalid), "allocation overflow branch");

                LLVMPositionBuilderAtEnd(builder, allocate);

                var bytes = LLVMBuildMul(builder, count, elementSize, "allocation_bytes");
                var allocated = call(builder, malloc, "allocated", bytes);

                requireValue(bytes, "allocation byte count");
                requireValue(LLVMBuildRet(builder, allocated), "allocation return");
            }

            LLVMPositionBuilderAtEnd(builder, invalid);
            requireValue(LLVMBuildRet(builder, nullPointer(pointerType, llvmContext)), "null allocation return");
        });
    }

    private static void lowerReallocate(
        IrFunction function,
        HostFunction realloc,
        HostFunction free,
        LlvmProgramLoweringContext context
    ) {
        var pointerType = validateReallocate(function);

        withBuilder(function, context, builder -> {
            var llvmContext = context.module().contextHandle();
            var handle = context.function(function.id()).value();
            var entry = appendBlock(llvmContext, handle, "memory.entry");
            var release = appendBlock(llvmContext, handle, "memory.release");
            var positiveCheck = appendBlock(llvmContext, handle, "memory.positive_check");
            var invalid = appendBlock(llvmContext, handle, "memory.invalid");
            var pointer = requireParameter(handle, 0, function);
            var count = requireParameter(handle, 1, function);
            var zero = LLVMConstInt(LLVMInt64TypeInContext(llvmContext), 0, 0);

            LLVMPositionBuilderAtEnd(builder, entry);

            var isZero = LLVMBuildICmp(builder, LLVMIntEQ, count, zero, "zero_count");

            requireValue(isZero, "zero reallocation count condition");
            requireValue(LLVMBuildCondBr(builder, isZero, release, positiveCheck), "zero reallocation branch");

            LLVMPositionBuilderAtEnd(builder, release);
            call(builder, free, "", pointer);
            requireValue(LLVMBuildRet(builder, nullPointer(pointerType, llvmContext)), "released reallocation return");

            LLVMPositionBuilderAtEnd(builder, positiveCheck);

            if (isZeroSized(pointerType.elementType())) {
                requireValue(LLVMBuildBr(builder, invalid), "zero-sized reallocation branch");
            } else {
                var sizeCheck = appendBlock(llvmContext, handle, "memory.size_check");
                var resize = appendBlock(llvmContext, handle, "memory.reallocate");
                var positive = LLVMBuildICmp(builder, LLVMIntSGT, count, zero, "positive_count");

                requireValue(positive, "positive reallocation count condition");
                requireValue(LLVMBuildCondBr(builder, positive, sizeCheck, invalid), "reallocation count branch");

                LLVMPositionBuilderAtEnd(builder, sizeCheck);

                var elementSize = LLVMSizeOf(LlvmTypeLowerer.lower(pointerType.elementType(), llvmContext));
                var maximum = LLVMConstInt(LLVMInt64TypeInContext(llvmContext), Long.MAX_VALUE, 0);
                var maximumCount = LLVMBuildSDiv(builder, maximum, elementSize, "maximum_count");
                var fits = LLVMBuildICmp(builder, LLVMIntSLE, count, maximumCount, "reallocation_fits");

                requireValue(elementSize, "reallocation element size");
                requireValue(maximumCount, "maximum reallocation count");
                requireValue(fits, "reallocation overflow condition");
                requireValue(LLVMBuildCondBr(builder, fits, resize, invalid), "reallocation overflow branch");

                LLVMPositionBuilderAtEnd(builder, resize);

                var bytes = LLVMBuildMul(builder, count, elementSize, "reallocation_bytes");
                var resized = call(builder, realloc, "resized", pointer, bytes);

                requireValue(bytes, "reallocation byte count");
                requireValue(LLVMBuildRet(builder, resized), "reallocation return");
            }

            LLVMPositionBuilderAtEnd(builder, invalid);
            requireValue(LLVMBuildRet(builder, nullPointer(pointerType, llvmContext)), "invalid reallocation return");
        });
    }

    private static void lowerFree(IrFunction function, HostFunction free, LlvmProgramLoweringContext context) {
        validateFree(function);

        withBuilder(function, context, builder -> {
            var handle = context.function(function.id()).value();
            var entry = appendBlock(context.module().contextHandle(), handle, "memory.entry");

            LLVMPositionBuilderAtEnd(builder, entry);
            call(builder, free, "", requireParameter(handle, 0, function));
            requireValue(LLVMBuildRetVoid(builder), "memory release return");
        });
    }

    private static void lowerLoad(IrFunction function, boolean indexed, LlvmProgramLoweringContext context) {
        var pointerType = validateLoad(function, indexed);

        withBuilder(function, context, builder -> {
            var llvmContext = context.module().contextHandle();
            var handle = context.function(function.id()).value();
            var entry = appendBlock(llvmContext, handle, "memory.entry");
            var pointer = requireParameter(handle, 0, function);

            LLVMPositionBuilderAtEnd(builder, entry);

            var address = indexed
                ? indexedAddress(builder, pointerType.elementType(), pointer, requireParameter(handle, 1, function), llvmContext)
                : pointer;
            var loaded = LLVMBuildLoad2(builder, LlvmTypeLowerer.lower(pointerType.elementType(), llvmContext), address, "loaded");

            requireValue(loaded, "memory load");
            requireValue(LLVMBuildRet(builder, loaded), "memory load return");
        });
    }

    private static void lowerStore(IrFunction function, boolean indexed, LlvmProgramLoweringContext context) {
        var pointerType = validateStore(function, indexed);

        withBuilder(function, context, builder -> {
            var llvmContext = context.module().contextHandle();
            var handle = context.function(function.id()).value();
            var entry = appendBlock(llvmContext, handle, "memory.entry");
            var pointer = requireParameter(handle, 0, function);

            LLVMPositionBuilderAtEnd(builder, entry);

            var address = indexed
                ? indexedAddress(builder, pointerType.elementType(), pointer, requireParameter(handle, 1, function), llvmContext)
                : pointer;
            var valueIndex = indexed ? 2 : 1;

            requireValue(LLVMBuildStore(builder, requireParameter(handle, valueIndex, function), address), "memory store");
            requireValue(LLVMBuildRetVoid(builder), "memory store return");
        });
    }

    private static LLVMValueRef indexedAddress(
        LLVMBuilderRef builder,
        IrType elementType,
        LLVMValueRef pointer,
        LLVMValueRef index,
        LLVMContextRef context
    ) {
        final LLVMValueRef address;

        try (var indices = new PointerPointer<LLVMValueRef>(1)) {
            indices.put(0, index);
            address = LLVMBuildGEP2(builder, LlvmTypeLowerer.lower(elementType, context), pointer, indices, 1, "memory.index");
        }

        requireValue(address, "memory indexed address");
        return address;
    }

    private static IrPointerType validateAllocate(IrFunction function) {
        validateBodyless(function);
        if (function.parameters().size() != 1 || function.parameters().getFirst().type() != PrimitiveIrType.INT)
            throw invalidSignature(function, "must accept exactly one 'int' count");
        if (!(function.returnType() instanceof IrPointerType pointer))
            throw invalidSignature(function, "must return a pointer type");

        return pointer;
    }

    private static IrPointerType validateReallocate(IrFunction function) {
        validateBodyless(function);
        if (function.parameters().size() != 2)
            throw invalidSignature(function, "must accept a pointer and an 'int' count");
        if (!(function.parameters().getFirst().type() instanceof IrPointerType pointer))
            throw invalidSignature(function, "must accept a pointer as its first parameter");
        if (function.parameters().get(1).type() != PrimitiveIrType.INT)
            throw invalidSignature(function, "must accept an 'int' count as its second parameter");
        if (!pointer.equals(function.returnType()))
            throw invalidSignature(function, "must return the same pointer type it accepts");

        return pointer;
    }

    private static void validateFree(IrFunction function) {
        validateBodyless(function);
        if (function.parameters().size() != 1 || !(function.parameters().getFirst().type() instanceof IrPointerType))
            throw invalidSignature(function, "must accept exactly one pointer");
        if (function.returnType() != PrimitiveIrType.VOID)
            throw invalidSignature(function, "must return 'void'");
    }

    private static IrPointerType validateLoad(IrFunction function, boolean indexed) {
        validateBodyless(function);
        var expectedCount = indexed ? 2 : 1;

        if (function.parameters().size() != expectedCount || !(function.parameters().getFirst().type() instanceof IrPointerType pointer))
            throw invalidSignature(function, indexed ? "must accept a pointer and an 'int' index" : "must accept exactly one pointer");
        if (indexed && function.parameters().get(1).type() != PrimitiveIrType.INT)
            throw invalidSignature(function, "must accept an 'int' index as its second parameter");
        if (!pointer.elementType().equals(function.returnType()))
            throw invalidSignature(function, "must return the pointer element type");

        return pointer;
    }

    private static IrPointerType validateStore(IrFunction function, boolean indexed) {
        validateBodyless(function);
        var expectedCount = indexed ? 3 : 2;

        if (function.parameters().size() != expectedCount || !(function.parameters().getFirst().type() instanceof IrPointerType pointer))
            throw invalidSignature(function, indexed ? "must accept a pointer, an 'int' index, and a value" : "must accept a pointer and a value");
        if (indexed && function.parameters().get(1).type() != PrimitiveIrType.INT)
            throw invalidSignature(function, "must accept an 'int' index as its second parameter");
        var valueIndex = indexed ? 2 : 1;
        if (!pointer.elementType().equals(function.parameters().get(valueIndex).type()))
            throw invalidSignature(function, "must accept the pointer element type as its value");
        if (function.returnType() != PrimitiveIrType.VOID)
            throw invalidSignature(function, "must return 'void'");

        return pointer;
    }

    private static void validateBodyless(IrFunction function) {
        if (function.hasBody()) throw invalidSignature(function, "must be a bodyless standard-library declaration");
    }

    private static boolean isZeroSized(IrType type) {
        return type instanceof IrStructType struct && struct.fields().stream().allMatch(field -> isZeroSized(field.type()));
    }

    private static String sourceName(IrFunction function) {
        var separator = function.name().indexOf('$');

        return separator < 0 ? function.name() : function.name().substring(0, separator);
    }

    private static HostFunctions declareHostFunctions(LlvmProgramLoweringContext context) {
        var llvmContext = context.module().contextHandle();
        var pointer = LLVMPointerTypeInContext(llvmContext, 0);
        var i64 = LLVMInt64TypeInContext(llvmContext);
        var voidType = LLVMVoidTypeInContext(llvmContext);

        return new HostFunctions(
            declareHostFunction(context, "malloc", pointer, i64),
            declareHostFunction(context, "realloc", pointer, pointer, i64),
            declareHostFunction(context, "free", voidType, pointer)
        );
    }

    private static HostFunction declareHostFunction(
        LlvmProgramLoweringContext context,
        String name,
        LLVMTypeRef returnType,
        LLVMTypeRef... parameterTypes
    ) {
        final LLVMTypeRef functionType;

        try (var nativeParameters = new PointerPointer<LLVMTypeRef>(parameterTypes)) {
            functionType = LLVMFunctionType(returnType, nativeParameters, parameterTypes.length, 0);
        }

        requireValue(functionType, "host function type '%s'".formatted(name));

        var function = LLVMGetNamedFunction(context.module().moduleHandle(), name);

        if (Pointer.isNull(function)) function = LLVMAddFunction(context.module().moduleHandle(), name, functionType);

        requireValue(function, "host function declaration '%s'".formatted(name));

        return new HostFunction(functionType, function);
    }

    private static LLVMValueRef call(LLVMBuilderRef builder, HostFunction function, String name, LLVMValueRef... arguments) {
        final LLVMValueRef lowered;

        try (var nativeArguments = new PointerPointer<LLVMValueRef>(arguments)) {
            lowered = LLVMBuildCall2(builder, function.type(), function.value(), nativeArguments, arguments.length, name);
        }

        requireValue(lowered, "host memory call");

        return lowered;
    }

    private static LLVMValueRef requireParameter(LLVMValueRef function, int index, IrFunction source) {
        var parameter = LLVMGetParam(function, index);

        requireValue(parameter, "parameter %d of memory function '%s'".formatted(index, source.name()));

        return parameter;
    }

    private static LLVMValueRef nullPointer(IrPointerType type, LLVMContextRef context) {
        var value = LLVMConstNull(LlvmTypeLowerer.lower(type, context));

        requireValue(value, "typed null pointer");

        return value;
    }

    private static LLVMBasicBlockRef appendBlock(LLVMContextRef context, LLVMValueRef function, String name) {
        var block = LLVMAppendBasicBlockInContext(context, function, name);

        requireValue(block, "standard memory block '%s'".formatted(name));

        return block;
    }

    private static void withBuilder(IrFunction function, LlvmProgramLoweringContext context, BuilderAction action) {
        var builder = LLVMCreateBuilderInContext(context.module().contextHandle());

        requireValue(builder, "builder for standard memory function '%s'".formatted(function.name()));

        try {
            action.lower(builder);
        } finally {
            LLVMDisposeBuilder(builder);
            builder.setNull();
        }
    }

    private static LlvmBackendException invalidSignature(IrFunction function, String reason) {
        return new LlvmBackendException("Standard memory function '%s' %s.".formatted(function.name(), reason));
    }

    private static void requireValue(Pointer value, String description) {
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM failed to create %s.".formatted(description));
    }

    @FunctionalInterface
    private interface BuilderAction {
        void lower(LLVMBuilderRef builder);
    }

    private record HostFunctions(HostFunction malloc, HostFunction realloc, HostFunction free) {}

    private record HostFunction(LLVMTypeRef type, LLVMValueRef value) {
        private HostFunction {
            Objects.requireNonNull(type, "Host memory function type must not be null.");
            Objects.requireNonNull(value, "Host memory function value must not be null.");

            if (Pointer.isNull(type) || Pointer.isNull(value))
                throw new IllegalArgumentException("Host memory function pointers must not be null.");
        }
    }
}
