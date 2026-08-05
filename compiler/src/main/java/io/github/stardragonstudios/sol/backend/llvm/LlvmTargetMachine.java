package io.github.stardragonstudios.sol.backend.llvm;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTargetDataRef;
import org.bytedeco.llvm.LLVM.LLVMTargetMachineRef;
import org.bytedeco.llvm.LLVM.LLVMTargetRef;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMCopyStringRepOfTargetData;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateTargetDataLayout;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateTargetMachine;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeTargetData;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeTargetMachine;
import static org.bytedeco.llvm.global.LLVM.LLVMGetTargetFromTriple;
import static org.bytedeco.llvm.global.LLVM.LLVMGetTargetName;
import static org.bytedeco.llvm.global.LLVM.LLVMSetModuleDataLayout;
import static org.bytedeco.llvm.global.LLVM.LLVMSetTarget;
import static org.bytedeco.llvm.global.LLVM.LLVMTargetHasAsmBackend;
import static org.bytedeco.llvm.global.LLVM.LLVMTargetHasTargetMachine;

public final class LlvmTargetMachine
    implements AutoCloseable {

    private final LlvmTargetConfiguration configuration;
    private final String targetName;
    private final String dataLayout;

    private final LLVMTargetMachineRef machine;

    private boolean closed;

    private LlvmTargetMachine(LlvmTargetConfiguration configuration, String targetName, String dataLayout, LLVMTargetMachineRef machine) {
        this.configuration = configuration;
        this.targetName = targetName;
        this.dataLayout = dataLayout;
        this.machine = machine;
    }

    public static LlvmTargetMachine createHost() {
        return create(LlvmTargetConfiguration.host());
    }

    public static LlvmTargetMachine create(LlvmTargetConfiguration configuration) {
        Objects.requireNonNull(configuration, "LLVM target configuration must not be null.");

        LlvmNativeTarget.initialize();
        LLVMTargetMachineRef machine = null;

        try (
            var nativeTriple = new BytePointer(configuration.triple());
            var target = new LLVMTargetRef();
            var errorPointer = new PointerPointer<BytePointer>(1)
        ) {
            var status = LLVMGetTargetFromTriple(nativeTriple, target, errorPointer);
            var nativeError = errorPointer.get(BytePointer.class);

            try {
                if (status != 0) throw new LlvmBackendException("LLVM does not support target triple '%s': %s".formatted(configuration.triple(), errorDetails(nativeError)));
            } finally {
                disposeMessage(nativeError);
            }

            if (Pointer.isNull(target)) throw new LlvmBackendException("LLVM resolved target triple '%s' to a null target.".formatted(configuration.triple()));
            if (LLVMTargetHasTargetMachine(target) == 0) throw new LlvmBackendException("LLVM target '%s' has no target-machine implementation.".formatted(configuration.triple()));
            if (LLVMTargetHasAsmBackend(target) == 0) throw new LlvmBackendException("LLVM target '%s' cannot emit native object files.".formatted(configuration.triple()));

            var nativeTargetName = LLVMGetTargetName(target);

            if (Pointer.isNull(nativeTargetName)) throw new LlvmBackendException("LLVM target '%s' has no target name.".formatted(configuration.triple()));

            var targetName = nativeTargetName.getString();

            if (targetName == null || targetName.isBlank()) throw new LlvmBackendException("LLVM target '%s' has an empty target name.".formatted(configuration.triple()));

            machine = LLVMCreateTargetMachine(
                target,
                configuration.triple(),
                configuration.cpu(),
                configuration.features(),
                configuration.optimizationLevel().llvmValue(),
                configuration.relocationModel().llvmValue(),
                configuration.codeModel().llvmValue()
            );

            if (Pointer.isNull(machine)) throw new LlvmBackendException("LLVM failed to create a target machine for '%s'.".formatted(configuration.triple()));

            var dataLayout = copyDataLayout(machine, configuration.triple());

            return new LlvmTargetMachine(configuration, targetName, dataLayout, machine);
        } catch (LlvmBackendException exception) {
            disposeCreatedMachine(machine);

            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            disposeCreatedMachine(machine);

            throw new LlvmBackendException("Failed to create LLVM target machine for '%s'.".formatted(configuration.triple()), exception);
        }
    }

    public LlvmTargetConfiguration configuration() {
        return configuration;
    }

    public String targetName() {
        return targetName;
    }

    public String dataLayout() {
        return dataLayout;
    }

    public void configure(LlvmModule module) {
        Objects.requireNonNull(module, "Configured LLVM module must not be null.");

        ensureOpen();

        LLVMTargetDataRef targetData = null;

        try {
            targetData = LLVMCreateTargetDataLayout(machine);

            if (Pointer.isNull(targetData)) throw new LlvmBackendException("LLVM failed to create target data for '%s'.".formatted(configuration.triple()));

            LLVMSetTarget(module.moduleHandle(), configuration.triple());
            LLVMSetModuleDataLayout(module.moduleHandle(), targetData);
        } finally {
            if (!Pointer.isNull(targetData)) {
                LLVMDisposeTargetData(targetData);

                targetData.setNull();
            }
        }
    }

    LLVMTargetMachineRef machineHandle() {
        ensureOpen();

        return machine;
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;

        closed = true;

        if (!Pointer.isNull(machine)) {
            LLVMDisposeTargetMachine(machine);

            machine.setNull();
        }
    }

    private void ensureOpen() {
        if (closed) throw new LlvmBackendException("LLVM target machine for '%s' has already been closed.".formatted(configuration.triple()));
    }

    private static String copyDataLayout(LLVMTargetMachineRef machine, String triple) {
        LLVMTargetDataRef targetData = null;
        BytePointer nativeLayout = null;

        try {
            targetData = LLVMCreateTargetDataLayout(machine);

            if (Pointer.isNull(targetData)) throw new LlvmBackendException("LLVM failed to create the data layout for '%s'.".formatted(triple));

            nativeLayout = LLVMCopyStringRepOfTargetData(targetData);

            if (Pointer.isNull(nativeLayout)) throw new LlvmBackendException("LLVM failed to print the data layout for '%s'.".formatted(triple));

            var layout = nativeLayout.getString();

            if (layout == null || layout.isBlank()) throw new LlvmBackendException("LLVM returned an empty data layout for '%s'.".formatted(triple));

            return layout;
        } finally {
            disposeMessage(nativeLayout);

            if (!Pointer.isNull(targetData)) {
                LLVMDisposeTargetData(targetData);

                targetData.setNull();
            }
        }
    }

    private static String errorDetails(BytePointer nativeError) {
        if (Pointer.isNull(nativeError)) return "LLVM did not provide target lookup details.";

        var details = nativeError.getString();

        if (details == null || details.isBlank()) return "LLVM did not provide target lookup details.";

        return details.strip().replaceAll("\\R+", " ");
    }

    private static void disposeMessage(BytePointer message) {
        if (Pointer.isNull(message)) return;

        LLVMDisposeMessage(message);

        message.setNull();
    }

    private static void disposeCreatedMachine(LLVMTargetMachineRef machine) {
        if (Pointer.isNull(machine)) return;

        LLVMDisposeTargetMachine(machine);

        machine.setNull();
    }
}
