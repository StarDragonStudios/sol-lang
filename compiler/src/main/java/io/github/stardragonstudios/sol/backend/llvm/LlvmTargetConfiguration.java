package io.github.stardragonstudios.sol.backend.llvm;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMCodeGenLevelAggressive;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeGenLevelDefault;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeGenLevelLess;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeGenLevelNone;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeModelDefault;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeModelJITDefault;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeModelKernel;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeModelLarge;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeModelMedium;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeModelSmall;
import static org.bytedeco.llvm.global.LLVM.LLVMCodeModelTiny;
import static org.bytedeco.llvm.global.LLVM.LLVMRelocDefault;
import static org.bytedeco.llvm.global.LLVM.LLVMRelocDynamicNoPic;
import static org.bytedeco.llvm.global.LLVM.LLVMRelocPIC;
import static org.bytedeco.llvm.global.LLVM.LLVMRelocROPI;
import static org.bytedeco.llvm.global.LLVM.LLVMRelocROPI_RWPI;
import static org.bytedeco.llvm.global.LLVM.LLVMRelocRWPI;
import static org.bytedeco.llvm.global.LLVM.LLVMRelocStatic;

public record LlvmTargetConfiguration(String triple, String cpu, String features, OptimizationLevel optimizationLevel, RelocationModel relocationModel, CodeModel codeModel) {
    public LlvmTargetConfiguration {
        triple = requireNonBlank(triple, "LLVM target triple");
        cpu = requireNonBlank(cpu, "LLVM target CPU");

        Objects.requireNonNull(features, "LLVM target feature string must not be null.");

        features = features.strip();

        Objects.requireNonNull(optimizationLevel, "LLVM target optimization level must not be null.");
        Objects.requireNonNull(relocationModel, "LLVM target relocation model must not be null.");
        Objects.requireNonNull(codeModel, "LLVM target code model must not be null.");
    }

    public static LlvmTargetConfiguration host() {
        return LlvmNativeTarget.hostConfiguration();
    }

    private static String requireNonBlank(String value, String description) {
        Objects.requireNonNull(value, description + " must not be null.");

        var normalized = value.strip();

        if (normalized.isEmpty()) throw new IllegalArgumentException(description + " must not be blank.");

        return normalized;
    }

    public enum OptimizationLevel {
        NONE(LLVMCodeGenLevelNone),
        LESS(LLVMCodeGenLevelLess),
        DEFAULT(LLVMCodeGenLevelDefault),
        AGGRESSIVE(LLVMCodeGenLevelAggressive);

        private final int llvmValue;

        OptimizationLevel(int llvmValue) {
            this.llvmValue = llvmValue;
        }

        int llvmValue() {
            return llvmValue;
        }
    }

    public enum RelocationModel {
        DEFAULT(LLVMRelocDefault),
        STATIC(LLVMRelocStatic),
        POSITION_INDEPENDENT(LLVMRelocPIC),
        DYNAMIC_NO_PIC(LLVMRelocDynamicNoPic),
        READ_ONLY_POSITION_INDEPENDENT(LLVMRelocROPI),
        READ_WRITE_POSITION_INDEPENDENT(LLVMRelocRWPI),
        READ_ONLY_AND_READ_WRITE_POSITION_INDEPENDENT(LLVMRelocROPI_RWPI);

        private final int llvmValue;

        RelocationModel(int llvmValue) {
            this.llvmValue = llvmValue;
        }

        int llvmValue() {
            return llvmValue;
        }
    }

    public enum CodeModel {
        DEFAULT(LLVMCodeModelDefault),
        JIT_DEFAULT(LLVMCodeModelJITDefault),
        TINY(LLVMCodeModelTiny),
        SMALL(LLVMCodeModelSmall),
        KERNEL(LLVMCodeModelKernel),
        MEDIUM(LLVMCodeModelMedium),
        LARGE(LLVMCodeModelLarge);

        private final int llvmValue;

        CodeModel(int llvmValue) {
            this.llvmValue = llvmValue;
        }

        int llvmValue() {
            return llvmValue;
        }
    }
}
