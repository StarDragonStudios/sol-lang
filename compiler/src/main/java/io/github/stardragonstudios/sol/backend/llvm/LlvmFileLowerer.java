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
import static org.bytedeco.llvm.global.LLVM.LLVMBuildAnd;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildArrayAlloca;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCall2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildExtractValue;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildGEP2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildGlobalStringPtr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildICmp;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildIsNull;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildMemCpy;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRet;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildStore;
import static org.bytedeco.llvm.global.LLVM.LLVMConstInt;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBuilderInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBuilder;
import static org.bytedeco.llvm.global.LLVM.LLVMFunctionType;
import static org.bytedeco.llvm.global.LLVM.LLVMGetParam;
import static org.bytedeco.llvm.global.LLVM.LLVMInt1TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt32TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt64TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt8TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMIntEQ;
import static org.bytedeco.llvm.global.LLVM.LLVMPointerType;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;

final class LlvmFileLowerer {
    private static final String FILE_MODULE = "std.file";

    private static final String EXISTS = "exists";
    private static final String WRITE_TEXT = "write_text";
    private static final String APPEND_TEXT = "append_text";

    private LlvmFileLowerer() {}

    static void lower(IrModule module, LlvmProgramLoweringContext context) {
        Objects.requireNonNull(module, "Lowered file IR module must not be null.");
        Objects.requireNonNull(context, "LLVM file lowering context must not be null.");

        if (!module.name().qualifiedName().equals(FILE_MODULE)) throw new LlvmBackendException("LLVM file lowerer cannot lower module '%s'.".formatted(module.name().qualifiedName()));

        var hostFunctions = new HostFunctions(declareFopen(context), declareFclose(context), declareFwrite(context));

        for (var function : module.functions()) {
            switch (function.name()) {
                case EXISTS -> lowerExists(function, hostFunctions, context);
                case WRITE_TEXT -> lowerWrite(function, false, hostFunctions, context);
                case APPEND_TEXT -> lowerWrite(function, true, hostFunctions, context);

                default -> {
                    /*
                     * Future std.file operations can be introduced
                     * independently.
                     */
                }
            }
        }
    }

    private static void lowerExists(IrFunction function, HostFunctions hostFunctions, LlvmProgramLoweringContext context) {
        validateExists(function);

        var handle = context.function(function.id());

        LLVMBuilderRef builder = LLVMCreateBuilderInContext(context.module().contextHandle());

        if (Pointer.isNull(builder)) throw new LlvmBackendException("LLVM failed to create a builder for standard file function '%s'.".formatted(function.name()));

        try {
            lowerExistsBody(function, handle, hostFunctions, builder, context);
        } finally {
            LLVMDisposeBuilder(builder);
            builder.setNull();
        }
    }

    private static void lowerExistsBody(
        IrFunction function,
        LlvmFunctionHandle handle,
        HostFunctions hostFunctions,
        LLVMBuilderRef builder,
        LlvmProgramLoweringContext context
    ) {
        var llvmContext = context.module().contextHandle();
        var entry = appendBlock(llvmContext, handle.value(), "file.entry");
        var opened = appendBlock(llvmContext, handle.value(), "file.opened");
        var missing = appendBlock(llvmContext, handle.value(), "file.missing");

        LLVMPositionBuilderAtEnd(builder, entry);

        var pathValue = LLVMGetParam(handle.value(), 0);

        requireValue(pathValue, "path parameter of standard file function '%s'".formatted(function.name()));

        var pathData = LLVMBuildExtractValue(builder, pathValue, 0, "path_data");

        requireValue(pathData, "path data of standard file function '%s'".formatted(function.name()));

        var pathLength = LLVMBuildExtractValue(builder, pathValue, 1, "path_length");

        requireValue(pathLength, "path length of standard file function '%s'".formatted(function.name()));

        var cPath = lowerCString(builder, pathData, pathLength, context);
        var mode = LLVMBuildGlobalStringPtr(builder, "rb", "sol.file.mode.read");

        requireValue(mode, "read mode string for standard file function '%s'".formatted(function.name()));

        var file = call(builder, hostFunctions.fopen(), "file_handle", cPath, mode);
        var isMissing = LLVMBuildIsNull(builder, file, "file_missing");

        requireValue(isMissing, "file existence condition");
        requireValue(LLVMBuildCondBr(builder, isMissing, missing, opened), "file existence branch");

        LLVMPositionBuilderAtEnd(builder, opened);

        call(builder, hostFunctions.fclose(), "close_result", file);

        requireValue(LLVMBuildRet(builder, booleanConstant(llvmContext, true)), "successful return of standard file function '%s'".formatted(function.name()));

        LLVMPositionBuilderAtEnd(builder, missing);

        requireValue(LLVMBuildRet(builder, booleanConstant(llvmContext, false)), "missing-file return of standard file function '%s'".formatted(function.name()));
    }

    private static void lowerWrite(IrFunction function, boolean append, HostFunctions hostFunctions, LlvmProgramLoweringContext context) {
        validateWrite(function);

        var handle = context.function(function.id());

        LLVMBuilderRef builder = LLVMCreateBuilderInContext(context.module().contextHandle());

        if (Pointer.isNull(builder)) throw new LlvmBackendException("LLVM failed to create a builder for standard file function '%s'.".formatted(function.name()));

        try {
            lowerWriteBody(function, handle, append, hostFunctions, builder, context);
        } finally {
            LLVMDisposeBuilder(builder);
            builder.setNull();
        }
    }

    private static void lowerWriteBody(
        IrFunction function,
        LlvmFunctionHandle handle,
        boolean append,
        HostFunctions hostFunctions,
        LLVMBuilderRef builder,
        LlvmProgramLoweringContext context
    ) {
        var llvmContext = context.module().contextHandle();
        var entry = appendBlock(llvmContext, handle.value(), "file.entry");
        var opened = appendBlock(llvmContext, handle.value(), "file.opened");
        var openFailed = appendBlock(llvmContext, handle.value(), "file.open_failed");

        LLVMPositionBuilderAtEnd(builder, entry);

        var pathValue = LLVMGetParam(handle.value(), 0);

        requireValue(pathValue, "path parameter of standard file function '%s'".formatted(function.name()));

        var contentValue = LLVMGetParam(handle.value(), 1);

        requireValue(contentValue, "content parameter of standard file function '%s'".formatted(function.name()));

        var pathData = LLVMBuildExtractValue(builder, pathValue, 0, "path_data");

        requireValue(pathData, "path data of standard file function '%s'".formatted(function.name()));

        var pathLength = LLVMBuildExtractValue(builder, pathValue, 1, "path_length");

        requireValue(pathLength, "path length of standard file function '%s'".formatted(function.name()));

        var cPath = lowerCString(builder, pathData, pathLength, context);
        var mode = LLVMBuildGlobalStringPtr(builder, append ? "ab" : "wb", append ? "sol.file.mode.append" : "sol.file.mode.write");

        requireValue(mode, "%s mode string for standard file function '%s'".formatted(append ? "append" : "write", function.name()));

        var file = call(builder, hostFunctions.fopen(), "file_handle", cPath, mode);
        var failedToOpen = LLVMBuildIsNull(builder, file, "file_open_failed");

        requireValue(failedToOpen, "file open condition");
        requireValue(LLVMBuildCondBr(builder, failedToOpen, openFailed, opened), "file open branch");

        LLVMPositionBuilderAtEnd(builder, opened);

        var contentData = LLVMBuildExtractValue(builder, contentValue, 0, "content_data");

        requireValue(contentData, "content data of standard file function '%s'".formatted(function.name()));

        var contentLength = LLVMBuildExtractValue(builder, contentValue, 1, "content_length");

        requireValue(contentLength, "content length of standard file function '%s'".formatted(function.name()));

        var i64 = LLVMInt64TypeInContext(llvmContext);
        var one = LLVMConstInt(i64, 1, 0);

        requireValue(one, "file write element size");

        /*
         * fwrite(content.data, 1, content.length, file)
         *
         * Sol strings remain bounded byte sequences. No terminating NUL
         * is required or included in the written data.
         */
        var written = call(builder, hostFunctions.fwrite(), "written_bytes", contentData, one, contentLength, file);
        var wroteEverything = LLVMBuildICmp(builder, LLVMIntEQ, written, contentLength, "all_bytes_written");

        requireValue(wroteEverything, "complete file write condition");

        var closeResult = call(builder, hostFunctions.fclose(), "close_result", file);
        var zero = LLVMConstInt(LLVMInt32TypeInContext(llvmContext), 0, 0);

        requireValue(zero, "successful fclose result");

        var closedSuccessfully = LLVMBuildICmp(builder, LLVMIntEQ, closeResult, zero, "file_closed");

        requireValue(closedSuccessfully, "successful file close condition");

        var succeeded = LLVMBuildAnd(builder, wroteEverything, closedSuccessfully, "file_write_succeeded");

        requireValue(succeeded, "standard file write result");
        requireValue(LLVMBuildRet(builder, succeeded), "return of standard file function '%s'".formatted(function.name()));

        LLVMPositionBuilderAtEnd(builder, openFailed);

        requireValue(LLVMBuildRet(builder, booleanConstant(llvmContext, false)), "failed-open return of standard file function '%s'".formatted(function.name()));
    }

    /*
     * Converts a bounded Sol UTF-8 string:
     *
     *     { ptr, i64, i64 }
     *
     * into a temporary NUL-terminated buffer suitable for host C APIs.
     *
     * The original Sol string remains length-based and is not modified.
     */
    private static LLVMValueRef lowerCString(
        LLVMBuilderRef builder,
        LLVMValueRef data,
        LLVMValueRef length,
        LlvmProgramLoweringContext context
    ) {
        var llvmContext = context.module().contextHandle();
        var i8 = LLVMInt8TypeInContext(llvmContext);
        var i64 = LLVMInt64TypeInContext(llvmContext);
        var one = LLVMConstInt(i64, 1, 0);

        requireValue(one, "C string terminator size");

        var capacity = LLVMBuildAdd(builder, length, one, "path_capacity");

        requireValue(capacity, "C path buffer capacity");

        var buffer = LLVMBuildArrayAlloca(builder, i8, capacity, "path_buffer");

        requireValue(buffer, "C path buffer");
        requireValue(LLVMBuildMemCpy(builder, buffer, 1, data, 1, length), "copy into C path buffer");

        final LLVMValueRef terminator;

        try (var indices = new PointerPointer<LLVMValueRef>(1)) {
            indices.put(0, length);
            terminator = LLVMBuildGEP2(builder, i8, buffer, indices, 1, "path_terminator");
        }

        requireValue(terminator, "C path terminator location");

        var zero = LLVMConstInt(i8, 0, 0);

        requireValue(zero, "C path terminator");
        requireValue(LLVMBuildStore(builder, zero, terminator), "C path terminator store");

        return buffer;
    }

    private static LLVMValueRef booleanConstant(LLVMContextRef context, boolean value) {
        var constant = LLVMConstInt(LLVMInt1TypeInContext(context), value ? 1 : 0, 0);

        requireValue(constant, "boolean file result");

        return constant;
    }

    private static HostFunction declareFopen(LlvmProgramLoweringContext context) {
        var llvmContext = context.module().contextHandle();
        var pointer = LLVMPointerType(LLVMInt8TypeInContext(llvmContext), 0);

        return declareHostFunction(context, "fopen", pointer, pointer, pointer);
    }

    private static HostFunction declareFclose(LlvmProgramLoweringContext context) {
        var llvmContext = context.module().contextHandle();
        var pointer = LLVMPointerType(LLVMInt8TypeInContext(llvmContext), 0);

        return declareHostFunction(context, "fclose", LLVMInt32TypeInContext(llvmContext), pointer);
    }

    private static HostFunction declareFwrite(LlvmProgramLoweringContext context) {
        var llvmContext = context.module().contextHandle();
        var pointer = LLVMPointerType(LLVMInt8TypeInContext(llvmContext), 0);
        var size = LLVMInt64TypeInContext(llvmContext);

        return declareHostFunction(context, "fwrite", size, pointer, size, size, pointer);
    }

    private static HostFunction declareHostFunction(LlvmProgramLoweringContext context, String name, LLVMTypeRef returnType, LLVMTypeRef... parameterTypes) {
        final LLVMTypeRef functionType;

        try (var nativeParameterTypes = new PointerPointer<LLVMTypeRef>(parameterTypes)) {
            functionType = LLVMFunctionType(returnType, nativeParameterTypes, parameterTypes.length, 0);
        }

        if (Pointer.isNull(functionType)) throw new LlvmBackendException("LLVM failed to create the host '%s' function type.".formatted(name));

        var function = LLVMAddFunction(context.module().moduleHandle(), name, functionType);

        if (Pointer.isNull(function)) throw new LlvmBackendException("LLVM failed to declare host function '%s'.".formatted(name));

        return new HostFunction(functionType, function);
    }

    private static LLVMValueRef call(LLVMBuilderRef builder, HostFunction function, String name, LLVMValueRef... arguments) {
        final LLVMValueRef call;

        try (var nativeArguments = new PointerPointer<LLVMValueRef>(arguments.length)) {
            for (var index = 0; index < arguments.length; index++) nativeArguments.put(index, arguments[index]);

            call = LLVMBuildCall2(builder, function.type(), function.value(), nativeArguments, arguments.length, name);
        }

        requireValue(call, "host function call");

        return call;
    }

    private static LLVMBasicBlockRef appendBlock(LLVMContextRef context, LLVMValueRef function, String name) {
        var block = LLVMAppendBasicBlockInContext(context, function, name);

        if (Pointer.isNull(block)) throw new LlvmBackendException("LLVM failed to create standard file block '%s'.".formatted(name));

        return block;
    }

    private static void validateExists(IrFunction function) {
        if (function.hasBody()) throw invalidSignature(function, "must be a bodyless standard-library declaration");
        if (function.parameters().size() != 1) throw invalidSignature(function, "must declare exactly one parameter");
        if (function.parameters().getFirst().type() != PrimitiveIrType.STRING) throw invalidSignature(function, "must accept exactly one 'string' parameter");
        if (function.returnType() != PrimitiveIrType.BOOLEAN) throw invalidSignature(function, "must return 'boolean'");
    }

    private static void validateWrite(IrFunction function) {
        if (function.hasBody()) throw invalidSignature(function, "must be a bodyless standard-library declaration");
        if (function.parameters().size() != 2) throw invalidSignature(function, "must declare exactly two parameters");
        if (function.parameters().get(0).type() != PrimitiveIrType.STRING) throw invalidSignature(function, "must accept 'string' as its path parameter");
        if (function.parameters().get(1).type() != PrimitiveIrType.STRING) throw invalidSignature(function, "must accept 'string' as its content parameter");
        if (function.returnType() != PrimitiveIrType.BOOLEAN) throw invalidSignature(function, "must return 'boolean'");
    }

    private static LlvmBackendException invalidSignature(IrFunction function, String reason) {
        return new LlvmBackendException("Standard file function '%s' %s.".formatted(function.name(), reason));
    }

    private static void requireValue(Pointer value, String description) {
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM failed to create %s.".formatted(description));
    }

    private record HostFunctions(HostFunction fopen, HostFunction fclose, HostFunction fwrite) {
        private HostFunctions {
            Objects.requireNonNull(fopen, "Host fopen function must not be null.");
            Objects.requireNonNull(fclose, "Host fclose function must not be null.");
            Objects.requireNonNull(fwrite, "Host fwrite function must not be null.");
        }
    }

    private record HostFunction(LLVMTypeRef type, LLVMValueRef value) {
        private HostFunction {
            Objects.requireNonNull(type, "Host function type must not be null.");
            Objects.requireNonNull(value, "Host function value must not be null.");

            if (Pointer.isNull(type) || Pointer.isNull(value)) throw new IllegalArgumentException("Host function pointers must not be null.");
        }
    }
}
