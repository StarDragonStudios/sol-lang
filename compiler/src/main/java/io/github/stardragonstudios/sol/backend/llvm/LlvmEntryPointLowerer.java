package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrProgram;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMAddFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCall2;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRet;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildTrunc;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBuilderInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBuilder;
import static org.bytedeco.llvm.global.LLVM.LLVMFunctionType;
import static org.bytedeco.llvm.global.LLVM.LLVMInt32TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;

final class LlvmEntryPointLowerer {
    private LlvmEntryPointLowerer() {}

    static void validate(IrProgram program) {
        Objects.requireNonNull(program, "Validated Sol IR program must not be null.");

        var entryFunction = program.entryFunction().orElse(null);

        if (entryFunction == null || entryFunction.parameters().isEmpty()) return;

        throw new LlvmBackendException(
            "Native startup argument binding is not yet supported for Sol IR entry point '%s' with %d parameter(s). Entry-point parameters remain valid Sol IR.".formatted(
                entryFunction.name(),
                entryFunction.parameters().size()
            )
        );
    }

    static void lower(IrProgram program, LlvmProgramLoweringContext context) {
        Objects.requireNonNull(program, "Lowered Sol IR program must not be null.");
        Objects.requireNonNull(context, "LLVM program lowering context must not be null.");

        validate(program);

        var entryFunction = program.entryFunction().orElse(null);

        if (entryFunction == null) return;

        var target = context.function(entryFunction.id());
        var llvmContext = context.module().contextHandle();
        var parameterTypes = new LLVMTypeRef[0];
        var arguments = new LLVMValueRef[0];

        try (
            var nativeParameterTypes = new PointerPointer<LLVMTypeRef>(parameterTypes);

            var nativeArguments = new PointerPointer<LLVMValueRef>(arguments)
        ) {
            var nativeStatusType = LLVMInt32TypeInContext(llvmContext);

            if (Pointer.isNull(nativeStatusType)) throw new LlvmBackendException("LLVM failed to create the native process-status type.");

            var nativeMainType = LLVMFunctionType(nativeStatusType, nativeParameterTypes, 0, 0);

            if (Pointer.isNull(nativeMainType)) throw new LlvmBackendException("LLVM failed to create the native entry function type.");

            var nativeMain = LLVMAddFunction(context.module().moduleHandle(), "main", nativeMainType);

            if (Pointer.isNull(nativeMain)) throw new LlvmBackendException("LLVM failed to declare the native entry function.");

            var entryBlock = LLVMAppendBasicBlockInContext(llvmContext, nativeMain, "entry");

            if (Pointer.isNull(entryBlock)) throw new LlvmBackendException("LLVM failed to create the native entry block.");

            var builder = LLVMCreateBuilderInContext(llvmContext);

            if (Pointer.isNull(builder)) throw new LlvmBackendException("LLVM failed to create the native entry builder.");

            try {
                LLVMPositionBuilderAtEnd(builder, entryBlock);

                var result = LLVMBuildCall2(builder, target.functionType(), target.value(), nativeArguments, 0, "sol.entry.result");

                if (Pointer.isNull(result)) throw new LlvmBackendException("LLVM failed to call the Sol entry function.");

                var status = LLVMBuildTrunc(builder, result, nativeStatusType, "sol.entry.status");

                if (Pointer.isNull(status)) throw new LlvmBackendException("LLVM failed to convert the Sol entry result to a native process status.");

                var returned = LLVMBuildRet(builder, status);

                if (Pointer.isNull(returned)) throw new LlvmBackendException("LLVM failed to return the native process status.");
            } finally {
                LLVMDisposeBuilder(builder);

                builder.setNull();
            }
        }
    }
}
