package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrStringConstant;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildGlobalStringPtr;
import static org.bytedeco.llvm.global.LLVM.LLVMConstInt;
import static org.bytedeco.llvm.global.LLVM.LLVMConstStructInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt8TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt64TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPointerType;
import static org.bytedeco.llvm.global.LLVM.LLVMStructTypeInContext;

final class LlvmStringLowerer {
    private LlvmStringLowerer() {}

    /*
     * Current native Sol string representation:
     *
     *     { ptr, i64, i64 }
     *
     * The pointer addresses UTF-8 data and the integer stores the
     * number of UTF-8 bytes. The final integer stores the number of
     * Unicode scalar values. The byte length deliberately excludes any
     * implementation NUL terminator used for static LLVM storage.
     *
     * Keeping the length explicit avoids defining Sol strings as
     * C strings and gives later runtime work a bounded byte view.
     */
    static LLVMTypeRef type(LLVMContextRef context) {
        Objects.requireNonNull(context, "LLVM string context must not be null.");

        if (Pointer.isNull(context)) throw new LlvmBackendException("LLVM string context must not be a null native pointer.");

        var fields = new LLVMTypeRef[] {
            LLVMPointerType(LLVMInt8TypeInContext(context), 0),
            LLVMInt64TypeInContext(context),
            LLVMInt64TypeInContext(context)
        };

        try (var nativeFields = new PointerPointer<LLVMTypeRef>(fields)) {
            var type = LLVMStructTypeInContext(context, nativeFields, fields.length, 0);

            if (Pointer.isNull(type)) throw new LlvmBackendException("LLVM failed to create the native Sol string type.");

            return type;
        }
    }

    static LLVMValueRef lower(IrStringConstant constant, LlvmFunctionLoweringContext context) {
        Objects.requireNonNull(constant, "Lowered Sol IR string constant must not be null.");
        Objects.requireNonNull(context, "LLVM string-lowering context must not be null.");

        var globalName = "sol.string.%s.value%d".formatted(context.function().id(), constant.id().index());

        /*
         * LLVMBuildGlobalStringPtr creates immutable module-level
         * storage for the bytes and returns a pointer to its first
         * element.
         *
         * The LLVM helper includes a trailing NUL for interoperability,
         * but that terminator is not part of the Sol string length.
         */
        var data = LLVMBuildGlobalStringPtr(context.builder(), constant.value(), globalName);

        if (Pointer.isNull(data)) throw new LlvmBackendException("LLVM failed to create storage for Sol IR string constant '%s'.".formatted(constant.id()));

        var byteLength = constant.value().getBytes(StandardCharsets.UTF_8).length;
        var length = LLVMConstInt(LLVMInt64TypeInContext(context.llvmContext()), byteLength, 0);
        var scalarCount = constant.value().codePointCount(0, constant.value().length());
        var scalars = LLVMConstInt(LLVMInt64TypeInContext(context.llvmContext()), scalarCount, 0);

        if (Pointer.isNull(length)) throw new LlvmBackendException("LLVM failed to create the length of Sol IR string constant '%s'.".formatted(constant.id()));
        if (Pointer.isNull(scalars)) throw new LlvmBackendException("LLVM failed to create the scalar count of Sol IR string constant '%s'.".formatted(constant.id()));

        var fields = new LLVMValueRef[] {data, length, scalars};

        try (var nativeFields = new PointerPointer<LLVMValueRef>(fields)) {
            var lowered = LLVMConstStructInContext(context.llvmContext(), nativeFields, fields.length, 0);

            if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to lower Sol IR string constant '%s'.".formatted(constant.id()));

            return context.registerValue(constant, lowered);
        }
    }
}
