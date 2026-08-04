package io.github.stardragonstudios.sol.backend.llvm;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.LLVMContextCreate;
import static org.bytedeco.llvm.global.LLVM.LLVMContextDispose;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeModule;
import static org.bytedeco.llvm.global.LLVM.LLVMModuleCreateWithNameInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToString;
import static org.bytedeco.llvm.global.LLVM.LLVMReturnStatusAction;
import static org.bytedeco.llvm.global.LLVM.LLVMVerifyModule;

public final class LlvmModule
    implements AutoCloseable {

    private final String name;

    private final LLVMContextRef context;
    private final LLVMModuleRef module;

    private boolean closed;

    private LlvmModule(String name, LLVMContextRef context, LLVMModuleRef module) {
        this.name = name;
        this.context = context;
        this.module = module;
    }

    public static LlvmModule create(String name) {
        validateName(name);

        LLVMContextRef context = null;
        LLVMModuleRef module = null;

        try {
            Loader.load(LLVM.class);

            context = LLVMContextCreate();

            if (Pointer.isNull(context)) throw new LlvmBackendException("LLVM failed to create a context for module '%s'.".formatted(name));

            module = LLVMModuleCreateWithNameInContext(name, context);

            if (Pointer.isNull(module)) throw new LlvmBackendException("LLVM failed to create module '%s'.".formatted(name));

            return new LlvmModule(name, context, module);
        } catch (LlvmBackendException exception) {
            disposeCreatedResources(module, context);

            throw exception;
        } catch ( RuntimeException | LinkageError exception) {
            disposeCreatedResources(module, context);

            throw new LlvmBackendException("Failed to initialize LLVM module '%s'.".formatted(name), exception);
        }
    }

    public String name() {
        return name;
    }

    public String text() {
        ensureOpen();

        var nativeText = LLVMPrintModuleToString(module);

        if (Pointer.isNull(nativeText)) throw new LlvmBackendException("LLVM failed to print module '%s'.".formatted(name));

        try {
            return nativeText.getString();
        } finally {
            disposeMessage(nativeText);
        }
    }

    public void verify() {
        ensureOpen();

        try (var messagePointer = new PointerPointer<BytePointer>(1)) {
            var status = LLVMVerifyModule(module, LLVMReturnStatusAction, messagePointer);
            var nativeMessage = messagePointer.get(BytePointer.class);

            try {
                if (status == 0) return;

                var details = Pointer.isNull(nativeMessage) ? "LLVM did not provide verification details." : normalizeMessage(nativeMessage.getString());

                throw new LlvmBackendException("LLVM module '%s' failed verification: %s".formatted(name, details));
            } finally {
                if (!Pointer.isNull(nativeMessage)) disposeMessage(nativeMessage);
            }
        }
    }

    LLVMContextRef contextHandle() {
        ensureOpen();

        return context;
    }

    LLVMModuleRef moduleHandle() {
        ensureOpen();

        return module;
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;

        closed = true;

        try {
            if (!Pointer.isNull(module)) {
                LLVMDisposeModule(module);

                module.setNull();
            }
        } finally {
            if (!Pointer.isNull(context)) {
                LLVMContextDispose(context);

                context.setNull();
            }
        }
    }

    private void ensureOpen() {
        if (closed) throw new LlvmBackendException("LLVM module '%s' has already been closed.".formatted(name));
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "LLVM module name must not be null.");

        if (name.isBlank()) throw new IllegalArgumentException("LLVM module name must not be blank.");
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) return "LLVM did not provide verification details.";

        return message.strip().replaceAll("\\R+", " ");
    }

    private static void disposeMessage(BytePointer message) {
        LLVMDisposeMessage(message);

        message.setNull();
    }

    private static void disposeCreatedResources(LLVMModuleRef module, LLVMContextRef context) {
        try {
            if (!Pointer.isNull(module)) {
                LLVMDisposeModule(module);

                module.setNull();
            }
        } finally {
            if (!Pointer.isNull(context)) {
                LLVMContextDispose(context);

                context.setNull();
            }
        }
    }
}