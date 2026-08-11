package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrType;
import io.github.stardragonstudios.sol.ir.IrStructType;
import io.github.stardragonstudios.sol.ir.IrPointerType;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMDoubleTypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt1TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt32TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMInt64TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMVoidTypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMStructTypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPointerTypeInContext;

final class LlvmTypeLowerer {
    private LlvmTypeLowerer() {}

    static LLVMTypeRef lower(IrType type, LLVMContextRef context) {
        Objects.requireNonNull(type, "Lowered Sol IR type must not be null.");
        Objects.requireNonNull(context, "LLVM type-lowering context must not be null.");

        if (Pointer.isNull(context)) throw new LlvmBackendException("LLVM type-lowering context must not be a null native pointer.");

        if (type instanceof IrStructType structType) return lowerStruct(structType, context);
        if (type instanceof IrPointerType) return requireType(
            LLVMPointerTypeInContext(context, 0),
            type
        );

        if (!(type instanceof PrimitiveIrType primitive)) throw new LlvmBackendException("Unsupported Sol IR type implementation '%s'.".formatted(type.getClass().getName()));

        var lowered = switch (primitive) {
            case INT -> LLVMInt64TypeInContext(context);
            case FLOAT -> LLVMDoubleTypeInContext(context);
            case BOOLEAN -> LLVMInt1TypeInContext(context);
            case CHAR -> LLVMInt32TypeInContext(context);
            case VOID -> LLVMVoidTypeInContext(context);
            case STRING -> LlvmStringLowerer.type(context);
        };

        if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to create a representation for Sol IR type '%s'.".formatted(type.displayName()));

        return lowered;
    }

    private static LLVMTypeRef requireType(LLVMTypeRef lowered, IrType source) {
        if (Pointer.isNull(lowered)) throw new LlvmBackendException(
            "LLVM failed to create a representation for Sol IR type '%s'.".formatted(source.displayName())
        );

        return lowered;
    }

    private static LLVMTypeRef lowerStruct(IrStructType type, LLVMContextRef context) {
        var fieldTypes = new LLVMTypeRef[type.fields().size()];

        for (var index = 0; index < fieldTypes.length; index++) fieldTypes[index] = lower(type.fields().get(index).type(), context);

        try (var nativeFieldTypes = new PointerPointer<LLVMTypeRef>(fieldTypes)) {
            var lowered = LLVMStructTypeInContext(context, nativeFieldTypes, fieldTypes.length, 0);

            if (Pointer.isNull(lowered)) throw new LlvmBackendException("LLVM failed to create a representation for Sol IR struct type '%s'.".formatted(type.displayName()));

            return lowered;
        }
    }
}
