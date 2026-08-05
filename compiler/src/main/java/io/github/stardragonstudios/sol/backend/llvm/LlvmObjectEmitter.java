package io.github.stardragonstudios.sol.backend.llvm;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage;
import static org.bytedeco.llvm.global.LLVM.LLVMObjectFile;
import static org.bytedeco.llvm.global.LLVM.LLVMTargetMachineEmitToFile;

public final class LlvmObjectEmitter {
    private LlvmObjectEmitter() {}

    public static Path emit(LlvmModule module, LlvmTargetMachine targetMachine, Path destination) {
        Objects.requireNonNull(module, "Emitted LLVM module must not be null.");
        Objects.requireNonNull(targetMachine, "LLVM target machine must not be null.");
        Objects.requireNonNull(destination, "Native object destination must not be null.");

        var normalizedDestination = prepareDestination(destination);
        var existedBefore = Files.exists(normalizedDestination);

        try {
            /*
             * Apply the exact target triple and data layout before
             * verifying the final module that will reach code generation.
             */
            targetMachine.configure(module);

            module.verify();

            emitNativeObject(module, targetMachine, normalizedDestination);
            validateEmittedFile(normalizedDestination);

            return normalizedDestination;
        } catch (LlvmBackendException exception) {
            deleteNewIncompleteFile(normalizedDestination, existedBefore, exception);

            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            var wrapped = new LlvmBackendException("Failed to emit native object file '%s'.".formatted(normalizedDestination), exception);

            deleteNewIncompleteFile(normalizedDestination, existedBefore, wrapped);

            throw wrapped;
        }
    }

    private static Path prepareDestination(Path destination) {
        var normalized = destination.toAbsolutePath().normalize();

        if (normalized.getFileName() == null) throw new IllegalArgumentException("Native object destination must identify a file.");

        try {
            if (Files.isDirectory(normalized)) throw new LlvmBackendException("Native object destination '%s' is a directory.".formatted(normalized));

            var parent = normalized.getParent();

            if (parent != null) Files.createDirectories(parent);

            return normalized;
        } catch (IOException exception) {
            throw new LlvmBackendException("Failed to prepare native object destination '%s'.".formatted(normalized), exception);
        }
    }

    private static void emitNativeObject(LlvmModule module, LlvmTargetMachine targetMachine, Path destination) {
        try (
            var nativeDestination = new BytePointer(destination.toString());

            var errorPointer = new PointerPointer<BytePointer>(1)
        ) {
            var status = LLVMTargetMachineEmitToFile(targetMachine.machineHandle(), module.moduleHandle(), nativeDestination, LLVMObjectFile, errorPointer);
            var nativeError = errorPointer.get(BytePointer.class);

            try {
                if (status == 0) return;

                throw new LlvmBackendException(
                    "LLVM failed to emit native object file '%s' for target '%s': %s".formatted(destination, targetMachine.configuration().triple(), errorDetails(nativeError))
                );
            } finally {

                disposeMessage(nativeError);
            }
        }
    }

    private static void validateEmittedFile(Path destination) {
        try {
            if (!Files.isRegularFile(destination)) throw new LlvmBackendException(
                "LLVM reported successful object emission, but '%s' is not a regular file.".formatted(destination)
            );

            if (Files.size(destination) == 0) throw new LlvmBackendException("LLVM emitted an empty native object file at '%s'.".formatted(destination));
        } catch (IOException exception) {
            throw new LlvmBackendException("Failed to inspect emitted native object file '%s'.".formatted(destination), exception);
        }
    }

    private static String errorDetails(BytePointer nativeError) {
        if (Pointer.isNull(nativeError)) return "LLVM did not provide object-emission details.";

        var details = nativeError.getString();

        if (details == null || details.isBlank()) return "LLVM did not provide object-emission details.";

        return details.strip().replaceAll("\\R+", " ");
    }

    private static void disposeMessage(BytePointer message) {
        if (Pointer.isNull(message)) return;

        LLVMDisposeMessage(message);

        message.setNull();
    }

    private static void deleteNewIncompleteFile(Path destination, boolean existedBefore, Throwable failure) {
        if (existedBefore) return;

        try {
            Files.deleteIfExists(destination);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
