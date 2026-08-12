package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMAddFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildAdd;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildAlloca;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCall2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildExtractValue;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildGEP2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildICmp;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildLoad2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRetVoid;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStore;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildZExt;
import static org.bytedeco.llvm.global.LLVM.LLVMConstInt;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBuilderInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBuilder;
import static org.bytedeco.llvm.global.LLVM.LLVMFunctionType;
import static org.bytedeco.llvm.global.LLVM.LLVMGetParam;
import static org.bytedeco.llvm.global.LLVM.LLVMInt32TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt64TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt8TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMIntULT;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;

final class LlvmConsoleLowerer {
    private static final String CONSOLE_MODULE = "std.console";
    private static final String PRINT = "print";
    private static final String PRINT_LINE = "print_line";

    private LlvmConsoleLowerer() {}

    static void lower(IrModule module, LlvmProgramLoweringContext context) {
        Objects.requireNonNull(module, "Lowered console IR module must not be null.");
        Objects.requireNonNull(context, "LLVM console lowering context must not be null.");

        if (!module.name().qualifiedName().equals(CONSOLE_MODULE))
            throw new LlvmBackendException("LLVM console lowerer cannot lower module '%s'.".formatted(module.name().qualifiedName()));

        var putchar = declarePutchar(context);

        for (var function : module.functions()) {
            switch (function.name()) {
                case PRINT -> lowerOutputFunction(function, false, putchar, context);
                case PRINT_LINE -> lowerOutputFunction(function, true, putchar, context);

                default -> {
                    /*
                     * Unknown future std.console functions are left alone.
                     * Their implementation can be added independently.
                     */
                }
            }
        }
    }

    private static void lowerOutputFunction(IrFunction function, boolean appendNewline, HostFunction putchar, LlvmProgramLoweringContext context) {
        validateOutputFunction(function);

        var handle = context.function(function.id());

        LLVMBuilderRef builder = LLVMCreateBuilderInContext(context.module().contextHandle());

        if (Pointer.isNull(builder)) throw new LlvmBackendException("LLVM failed to create a builder for standard console function '%s'.".formatted(function.name()));

        try {
            lowerOutputFunctionBody(function, handle, appendNewline, putchar, builder, context);
        } finally {
            LLVMDisposeBuilder(builder);

            builder.setNull();
        }
    }

    private static void lowerOutputFunctionBody(
        IrFunction function,
        LlvmFunctionHandle handle,
        boolean appendNewline,
        HostFunction putchar,
        LLVMBuilderRef builder,
        LlvmProgramLoweringContext context
    ) {
        var llvmContext = context.module().contextHandle();
        var entry = appendBlock(llvmContext, handle.value(), "console.entry");
        var condition = appendBlock(llvmContext, handle.value(), "console.condition");
        var body = appendBlock(llvmContext, handle.value(), "console.body");
        var exit = appendBlock(llvmContext, handle.value(), "console.exit");
        var i8 = LLVMInt8TypeInContext(llvmContext);
        var i32 = LLVMInt32TypeInContext(llvmContext);
        var i64 = LLVMInt64TypeInContext(llvmContext);

        /*
         * entry:
         *
         *     { ptr, i64, i64 } value
         *
         * becomes:
         *
         *     data   = value.0
         *     length = value.1
         *     index  = 0
         */
        LLVMPositionBuilderAtEnd(builder, entry);

        var stringValue = LLVMGetParam(handle.value(), 0);

        requireValue(stringValue, "parameter of standard console function '%s'".formatted(function.name()));

        var data = LLVMBuildExtractValue(builder, stringValue, 0, "data");

        requireValue(data, "data pointer of standard console function '%s'".formatted(function.name()));

        var length = LLVMBuildExtractValue(builder, stringValue, 1, "length");

        requireValue(length, "length of standard console function '%s'".formatted(function.name()));

        var indexSlot = LLVMBuildAlloca(builder, i64, "index");

        requireValue(indexSlot, "index storage of standard console function '%s'".formatted(function.name()));

        var zero = LLVMConstInt(i64, 0, 0);

        requireValue(zero, "zero index constant");

        requireValue(LLVMBuildStore(builder, zero, indexSlot), "initial index store");
        requireValue(LLVMBuildBr(builder, condition), "console entry branch");

        /*
         * condition:
         *
         *     if index < length
         *         body
         *     else
         *         exit
         */
        LLVMPositionBuilderAtEnd(builder, condition);

        var index = LLVMBuildLoad2(builder, i64, indexSlot, "current_index");

        requireValue(index, "current console byte index");

        var hasMore = LLVMBuildICmp(builder, LLVMIntULT, index, length, "has_more");

        requireValue(hasMore, "console loop condition");

        requireValue(LLVMBuildCondBr(builder, hasMore, body, exit), "console conditional branch");

        /*
         * body:
         *
         *     byte = data[index]
         *     putchar((int) byte)
         *     index++
         */
        LLVMPositionBuilderAtEnd(builder, body);

        final LLVMValueRef bytePointer;

        try (var indices = new PointerPointer<LLVMValueRef>(1)) {
            indices.put(0, index);

            bytePointer = LLVMBuildGEP2(builder, i8, data, indices, 1, "byte_pointer");
        }

        requireValue(bytePointer, "console byte pointer");

        var byteValue = LLVMBuildLoad2(builder, i8, bytePointer, "byte");

        requireValue(byteValue, "console byte");

        var character = LLVMBuildZExt(builder, byteValue, i32, "character");

        requireValue(character, "console character");
        callPutchar(builder, putchar, character);

        var one = LLVMConstInt(i64, 1, 0);

        requireValue(one, "console index increment");

        var nextIndex = LLVMBuildAdd(builder, index, one, "next_index");

        requireValue(nextIndex, "next console byte index");
        requireValue(LLVMBuildStore(builder, nextIndex, indexSlot), "updated console byte index");
        requireValue(LLVMBuildBr(builder, condition), "console loop branch");

        /*
         * exit:
         *
         * print_line additionally emits '\n'.
         */
        LLVMPositionBuilderAtEnd(builder, exit);

        if (appendNewline) {
            var newline = LLVMConstInt(i32, '\n', 0);

            requireValue(newline, "console newline constant");
            callPutchar(builder, putchar, newline);
        }

        requireValue(LLVMBuildRetVoid(builder), "return of standard console function '%s'".formatted(function.name()));
    }

    private static HostFunction declarePutchar(LlvmProgramLoweringContext context) {
        var llvmContext = context.module().contextHandle();
        var i32 = LLVMInt32TypeInContext(llvmContext);
        var parameterTypes = new LLVMTypeRef[] {i32};

        final LLVMTypeRef functionType;

        try (var nativeParameterTypes = new PointerPointer<LLVMTypeRef>(parameterTypes)) {
            functionType = LLVMFunctionType(i32, nativeParameterTypes, parameterTypes.length, 0);
        }

        if (Pointer.isNull(functionType)) throw new LlvmBackendException("LLVM failed to create the host 'putchar' function type.");

        var function = LLVMAddFunction(context.module().moduleHandle(), "putchar", functionType);

        if (Pointer.isNull(function)) throw new LlvmBackendException("LLVM failed to declare host function 'putchar'.");

        return new HostFunction(functionType, function);
    }

    private static void callPutchar(LLVMBuilderRef builder, HostFunction putchar, LLVMValueRef character) {
        final LLVMValueRef call;

        try (var arguments = new PointerPointer<LLVMValueRef>(1)) {
            arguments.put(0, character);

            call = LLVMBuildCall2(builder, putchar.type(), putchar.value(), arguments, 1, "");
        }

        requireValue(call, "call to host function 'putchar'");
    }

    private static LLVMBasicBlockRef appendBlock(LLVMContextRef context, LLVMValueRef function, String name) {
        var block = LLVMAppendBasicBlockInContext(context, function, name);

        if (Pointer.isNull(block)) throw new LlvmBackendException("LLVM failed to create standard console block '%s'.".formatted(name));

        return block;
    }

    private static void validateOutputFunction(IrFunction function) {
        if (function.hasBody()) throw invalidSignature(function, "must be a bodyless standard-library declaration");
        if (function.parameters().size() != 1) throw invalidSignature(function, "must declare exactly one parameter");
        if (function.parameters().getFirst().type() != PrimitiveIrType.STRING) throw invalidSignature(function, "must accept exactly one 'string' parameter");
        if (function.returnType() != PrimitiveIrType.VOID) throw invalidSignature(function, "must return 'void'");
    }

    private static LlvmBackendException invalidSignature(IrFunction function, String reason) {
        return new LlvmBackendException("Standard console function '%s' %s.".formatted(function.name(), reason));
    }

    private static void requireValue(Pointer value, String description) {
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM failed to create %s.".formatted(description));
    }

    private record HostFunction(LLVMTypeRef type, LLVMValueRef value) {
        private HostFunction {
            Objects.requireNonNull(type, "Host function type must not be null.");
            Objects.requireNonNull(value, "Host function value must not be null.");

            if (Pointer.isNull(type) || Pointer.isNull(value)) throw new IllegalArgumentException("Host function pointers must not be null.");
        }
    }
}
