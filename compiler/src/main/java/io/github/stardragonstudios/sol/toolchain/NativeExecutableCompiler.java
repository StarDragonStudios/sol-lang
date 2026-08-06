package io.github.stardragonstudios.sol.toolchain;

import io.github.stardragonstudios.sol.backend.llvm.LlvmObjectBackend;
import io.github.stardragonstudios.sol.ir.IrProgram;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class NativeExecutableCompiler {
    private final NativeLinker linker;
    private final HostObjectEmitter objectEmitter;

    public NativeExecutableCompiler(NativeLinker linker) {
        this(linker, LlvmObjectBackend::emitHostObject);
    }

    NativeExecutableCompiler(NativeLinker linker, HostObjectEmitter objectEmitter) {
        this.linker = Objects.requireNonNull(linker, "Native executable linker must not be null.");
        this.objectEmitter = Objects.requireNonNull(objectEmitter, "Native executable object emitter must not be null.");
    }

    public static NativeExecutableCompiler host() {
        return new NativeExecutableCompiler(new NativeLinker(NativeLinkerDiscovery.discover()));
    }

    public NativeExecutableResult compile(IrProgram program, String moduleName, Path output) {
        return compile(program, moduleName, output, TemporaryObjectPolicy.DELETE);
    }

    public NativeExecutableResult compile(IrProgram program, String moduleName, Path output, TemporaryObjectPolicy objectPolicy) {
        Objects.requireNonNull(program, "Compiled executable Sol IR program must not be null.");
        Objects.requireNonNull(moduleName, "Compiled executable LLVM module name must not be null.");
        Objects.requireNonNull(objectPolicy, "Temporary native object policy must not be null.");

        if (moduleName.isBlank()) throw new IllegalArgumentException("Compiled executable LLVM module name must not be blank.");
        if (!program.hasEntryPoint()) throw new NativeToolchainException("Native executable compilation requires a Sol IR entry point.");

        var layout = NativeExecutableLayout.host(output);

        prepareLayout(layout);

        NativeLinkResult linkResult;

        try {
            var emittedObject = normalizeEmittedObject(objectEmitter.emit(program, moduleName, layout.objectFile()));

            if (!emittedObject.equals(layout.objectFile()))
                throw new NativeToolchainException("Native object emitter returned unexpected path '%s'; expected '%s'.".formatted(emittedObject, layout.objectFile()));

            linkResult = linker.link(List.of(emittedObject), layout.executable());
        } catch (RuntimeException | LinkageError failure) {
            if (objectPolicy == TemporaryObjectPolicy.DELETE) deleteTemporaryObject(layout.objectFile(), failure);

            throw failure;
        }

        if (objectPolicy == TemporaryObjectPolicy.DELETE) deleteTemporaryObject(layout.objectFile(), null);

        return new NativeExecutableResult(linkResult, layout.objectFile(), objectPolicy);
    }

    private static void prepareLayout(NativeExecutableLayout layout) {
        try {
            var parent = layout.executable().getParent();

            if (parent != null) Files.createDirectories(parent);

            if (Files.exists(layout.objectFile()) && !Files.isRegularFile(layout.objectFile()))
                throw new NativeToolchainException("Temporary native object path '%s' already exists and is not a regular file.".formatted(layout.objectFile()));

            Files.deleteIfExists(layout.objectFile());
        } catch (IOException exception) {
            throw new NativeToolchainException("Failed to prepare native executable output '%s'.".formatted(layout.executable()), exception);
        }
    }

    private static Path normalizeEmittedObject(Path emittedObject) {
        return Objects.requireNonNull(emittedObject, "Native object emitter must not return null.").toAbsolutePath().normalize();
    }

    private static void deleteTemporaryObject(Path objectFile, Throwable failure) {
        try {
            Files.deleteIfExists(objectFile);
        } catch (IOException cleanupFailure) {
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);

                return;
            }

            throw new NativeToolchainException("Failed to delete temporary native object file '%s'.".formatted(objectFile), cleanupFailure);
        }
    }
}

@FunctionalInterface
interface HostObjectEmitter {
    Path emit(IrProgram program, String moduleName, Path destination);
}
