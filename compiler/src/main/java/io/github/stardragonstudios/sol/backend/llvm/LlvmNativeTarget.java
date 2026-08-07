package io.github.stardragonstudios.sol.backend.llvm;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.global.LLVM;

import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage;
import static org.bytedeco.llvm.global.LLVM.LLVMGetDefaultTargetTriple;
import static org.bytedeco.llvm.global.LLVM.LLVMGetHostCPUFeatures;
import static org.bytedeco.llvm.global.LLVM.LLVMGetHostCPUName;
import static org.bytedeco.llvm.global.LLVM.LLVMInitializeNativeAsmPrinter;
import static org.bytedeco.llvm.global.LLVM.LLVMInitializeNativeTarget;

final class LlvmNativeTarget {
    private static volatile boolean initialized;

    private LlvmNativeTarget() {}

    static void initialize() {
        if (initialized) return;

        synchronized (LlvmNativeTarget.class) {
            if (initialized) return;

            try {
                Loader.load(LLVM.class);

                if (LLVMInitializeNativeTarget() != 0) throw new LlvmBackendException("LLVM failed to initialize the native target.");
                if (LLVMInitializeNativeAsmPrinter() != 0) throw new LlvmBackendException("LLVM failed to initialize the native assembly printer.");

                initialized = true;
            } catch (LlvmBackendException exception) {
                throw exception;
            } catch (RuntimeException | LinkageError exception) {
                throw new LlvmBackendException("Failed to initialize native LLVM target support.", exception);
            }
        }
    }

    static LlvmTargetConfiguration hostConfiguration() {
        initialize();

        return new LlvmTargetConfiguration(
            copyOwnedString(LLVMGetDefaultTargetTriple(), "default host target triple"),
            copyOwnedString(LLVMGetHostCPUName(), "host CPU name"),
            copyOwnedString(LLVMGetHostCPUFeatures(), "host CPU feature string"),
            LlvmTargetConfiguration.OptimizationLevel.DEFAULT,
            LlvmTargetConfiguration.RelocationModel.POSITION_INDEPENDENT,
            LlvmTargetConfiguration.CodeModel.DEFAULT
        );
    }

    private static String copyOwnedString(BytePointer nativeValue, String description) {
        if (Pointer.isNull(nativeValue)) throw new LlvmBackendException("LLVM failed to obtain the %s.".formatted(description));

        try {
            var value = nativeValue.getString();

            if (value == null || value.isBlank()) {
                /*
                 * An empty feature string is valid when LLVM reports that
                 * the host has no explicitly enabled or disabled features.
                 */
                if (description.equals("host CPU feature string")) return "";

                throw new LlvmBackendException("LLVM returned an empty %s.".formatted(description));
            }

            return value;
        } finally {
            LLVMDisposeMessage(nativeValue);

            nativeValue.setNull();
        }
    }
}
