package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrBasicBlock;
import io.github.stardragonstudios.sol.ir.IrBlockId;
import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.IrFunctionId;
import io.github.stardragonstudios.sol.ir.IrIntConstant;
import io.github.stardragonstudios.sol.ir.IrModule;
import io.github.stardragonstudios.sol.ir.IrModuleName;
import io.github.stardragonstudios.sol.ir.IrProgram;
import io.github.stardragonstudios.sol.ir.IrReturnTerminator;
import io.github.stardragonstudios.sol.ir.IrValueId;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBinaryRef;
import org.bytedeco.llvm.LLVM.LLVMMemoryBufferRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.bytedeco.llvm.global.LLVM.LLVMAddFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryGetType;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeCOFF;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeELF32B;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeELF32L;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeELF64B;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeELF64L;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeMachO32B;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeMachO32L;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeMachO64B;
import static org.bytedeco.llvm.global.LLVM.LLVMBinaryTypeMachO64L;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRetVoid;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBinary;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBuilderInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateMemoryBufferWithContentsOfFile;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBinary;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBuilder;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMemoryBuffer;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage;
import static org.bytedeco.llvm.global.LLVM.LLVMFunctionType;
import static org.bytedeco.llvm.global.LLVM.LLVMInt64TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmObjectEmitterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void emitsHostNativeObjectFileToNormalizedDestination() throws Exception {
        var requestedDestination =
            temporaryDirectory.resolve(
                    "nested"
                )
                .resolve(
                    ".."
                )
                .resolve(
                    "objects"
                )
                .resolve(
                    "answer.o"
                );

        try (
            var module =
                generateAnswerModule();

            var targetMachine =
                LlvmTargetMachine.createHost()
        ) {
            var emitted =
                LlvmObjectEmitter.emit(
                    module,
                    targetMachine,
                    requestedDestination
                );

            assertEquals(
                requestedDestination.toAbsolutePath()
                    .normalize(),
                emitted
            );

            assertTrue(
                Files.isRegularFile(
                    emitted
                )
            );

            assertTrue(
                Files.size(
                    emitted
                )
                    > 0
            );

            assertHostObjectType(
                readBinaryType(
                    emitted,
                    module
                ),
                targetMachine.configuration()
                    .triple()
            );

            var text =
                module.text();

            assertTrue(
                text.contains(
                    """
                    target datalayout = "%s"
                    """
                        .formatted(
                            targetMachine.dataLayout()
                        )
                        .strip()
                )
            );

            assertTrue(
                text.contains(
                    """
                    target triple = "%s"
                    """
                        .formatted(
                            targetMachine.configuration()
                                .triple()
                        )
                        .strip()
                )
            );
        }
    }

    @Test
    void acceptsToolchainManagedTemporaryDestination() throws Exception {
        var temporaryObject =
            Files.createTempFile(
                temporaryDirectory,
                "sol-object-",
                ".o"
            );

        assertEquals(
            0,
            Files.size(
                temporaryObject
            )
        );

        try (
            var module =
                generateAnswerModule();

            var targetMachine =
                LlvmTargetMachine.createHost()
        ) {
            var emitted =
                LlvmObjectEmitter.emit(
                    module,
                    targetMachine,
                    temporaryObject
                );

            assertEquals(
                temporaryObject.toAbsolutePath()
                    .normalize(),
                emitted
            );

            assertTrue(
                Files.size(
                    emitted
                )
                    > 0
            );
        }
    }

    @Test
    void rejectsDirectoryDestinations() throws Exception {
        var destination =
            Files.createDirectory(
                temporaryDirectory.resolve(
                    "object-directory"
                )
            );

        try (
            var module =
                generateAnswerModule();

            var targetMachine =
                LlvmTargetMachine.createHost()
        ) {
            var exception =
                assertThrows(
                    LlvmBackendException.class,
                    () ->
                        LlvmObjectEmitter.emit(
                            module,
                            targetMachine,
                            destination
                        )
                );

            assertTrue(
                exception.getMessage()
                    .contains(
                        "is a directory"
                    )
            );
        }
    }

    @Test
    void verifiesModuleBeforeEmissionAndLeavesNoNewFile() {
        var destination =
            temporaryDirectory.resolve(
                "invalid.o"
            );

        try (
            var module =
                createInvalidModule();

            var targetMachine =
                LlvmTargetMachine.createHost()
        ) {
            var exception =
                assertThrows(
                    LlvmBackendException.class,
                    () ->
                        LlvmObjectEmitter.emit(
                            module,
                            targetMachine,
                            destination
                        )
                );

            assertTrue(
                exception.getMessage()
                    .contains(
                        "failed verification"
                    )
            );

            assertFalse(
                Files.exists(
                    destination
                )
            );
        }
    }

    @Test
    void replacesExistingRegularDestination() throws Exception {
        var destination =
            temporaryDirectory.resolve(
                "existing.o"
            );

        var previousContents =
            "previous contents"
                .getBytes();

        Files.write(
            destination,
            previousContents
        );

        try (
            var module =
                generateAnswerModule();

            var targetMachine =
                LlvmTargetMachine.createHost()
        ) {
            LlvmObjectEmitter.emit(
                module,
                targetMachine,
                destination
            );

            var emittedContents =
                Files.readAllBytes(
                    destination
                );

            assertTrue(
                emittedContents.length
                    > 0
            );

            assertFalse(
                java.util.Arrays.equals(
                    previousContents,
                    emittedContents
                )
            );
        }
    }

    @Test
    void preservesExistingDestinationWhenVerificationFails() throws Exception {
        var destination =
            temporaryDirectory.resolve(
                "preserved.o"
            );

        Files.writeString(
            destination,
            "preserve this file"
        );

        try (
            var module =
                createInvalidModule();

            var targetMachine =
                LlvmTargetMachine.createHost()
        ) {
            assertThrows(
                LlvmBackendException.class,
                () ->
                    LlvmObjectEmitter.emit(
                        module,
                        targetMachine,
                        destination
                    )
            );

            assertEquals(
                "preserve this file",
                Files.readString(
                    destination
                )
            );
        }
    }

    private static LlvmModule generateAnswerModule() {
        var answer =
            new IrIntConstant(
                new IrValueId(
                    0
                ),
                42
            );

        var function =
            IrFunction.definition(
                new IrFunctionId(
                    0
                ),
                "answer",
                List.of(),
                PrimitiveIrType.INT,
                List.of(
                    new IrBasicBlock(
                        new IrBlockId(
                            0
                        ),
                        List.of(),
                        IrReturnTerminator.returning(
                            answer
                        )
                    )
                )
            );

        var program =
            IrProgram.library(
                List.of(
                    new IrModule(
                        new IrModuleName(
                            List.of(
                                "object"
                            )
                        ),
                        List.of(
                            function
                        )
                    )
                )
            );

        return LlvmBackend.generate(
            program,
            "sol.object"
        );
    }

    private static LlvmModule createInvalidModule() {
        var module =
            LlvmModule.create(
                "sol.invalid-object"
            );

        try (
            var parameterTypes =
                new PointerPointer<LLVMTypeRef>(
                    0
                )
        ) {
            var functionType =
                LLVMFunctionType(
                    LLVMInt64TypeInContext(
                        module.contextHandle()
                    ),
                    parameterTypes,
                    0,
                    0
                );

            var function =
                LLVMAddFunction(
                    module.moduleHandle(),
                    "invalid",
                    functionType
                );

            var block =
                LLVMAppendBasicBlockInContext(
                    module.contextHandle(),
                    function,
                    "entry"
                );

            var builder =
                LLVMCreateBuilderInContext(
                    module.contextHandle()
                );

            try {
                LLVMPositionBuilderAtEnd(
                    builder,
                    block
                );

                LLVMBuildRetVoid(
                    builder
                );
            } finally {
                LLVMDisposeBuilder(
                    builder
                );

                builder.setNull();
            }

            return module;
        } catch (
            RuntimeException
            | LinkageError exception
        ) {
            module.close();

            throw exception;
        }
    }

    private static int readBinaryType(
        Path objectFile,
        LlvmModule module
    ) {
        LLVMMemoryBufferRef buffer = null;
        LLVMBinaryRef binary = null;

        try {
            buffer =
                readMemoryBuffer(
                    objectFile
                );

            try (
                var errorPointer =
                    new PointerPointer<BytePointer>(
                        1
                    )
            ) {
                errorPointer.put(
                    0,
                    (Pointer) null
                );

                binary =
                    LLVMCreateBinary(
                        buffer,
                        module.contextHandle(),
                        errorPointer
                    );

                var nativeError =
                    errorPointer.get(
                        BytePointer.class
                    );

                try {
                    if (Pointer.isNull(binary)) {
                        throw new AssertionError(
                            "LLVM could not parse emitted object file: "
                                + errorDetails(
                                nativeError
                            )
                        );
                    }
                } finally {
                    disposeMessage(
                        nativeError
                    );
                }
            }

            return LLVMBinaryGetType(
                binary
            );
        } finally {
            if (!Pointer.isNull(binary)) {
                LLVMDisposeBinary(
                    binary
                );

                binary.setNull();
            }

            if (!Pointer.isNull(buffer)) {
                LLVMDisposeMemoryBuffer(
                    buffer
                );

                buffer.setNull();
            }
        }
    }

    private static LLVMMemoryBufferRef readMemoryBuffer(
        Path objectFile
    ) {
        var buffer =
            new LLVMMemoryBufferRef();

        try (
            var nativePath =
                new BytePointer(
                    objectFile.toString()
                );

            var errorPointer =
                new PointerPointer<BytePointer>(
                    1
                )
        ) {
            errorPointer.put(
                0,
                (Pointer) null
            );

            var status =
                LLVMCreateMemoryBufferWithContentsOfFile(
                    nativePath,
                    buffer,
                    errorPointer
                );

            var nativeError =
                errorPointer.get(
                    BytePointer.class
                );

            try {
                if (status != 0) {
                    throw new AssertionError(
                        "LLVM could not read emitted object file: "
                            + errorDetails(
                            nativeError
                        )
                    );
                }
            } finally {
                disposeMessage(
                    nativeError
                );
            }

            return buffer;
        } catch (
            RuntimeException
            | LinkageError exception
        ) {
            if (!Pointer.isNull(buffer)) {
                LLVMDisposeMemoryBuffer(
                    buffer
                );

                buffer.setNull();
            }

            throw exception;
        }
    }

    private static void assertHostObjectType(
        int binaryType,
        String targetTriple
    ) {
        var normalizedTriple =
            targetTriple.toLowerCase(
                Locale.ROOT
            );

        if (
            normalizedTriple.contains(
                "apple"
            )
                || normalizedTriple.contains(
                "darwin"
            )
        ) {
            assertTrue(
                binaryType
                    == LLVMBinaryTypeMachO32L
                    || binaryType
                    == LLVMBinaryTypeMachO32B
                    || binaryType
                    == LLVMBinaryTypeMachO64L
                    || binaryType
                    == LLVMBinaryTypeMachO64B
            );

            return;
        }

        if (
            normalizedTriple.contains(
                "windows"
            )
                || normalizedTriple.contains(
                "mingw"
            )
                || normalizedTriple.contains(
                "msvc"
            )
        ) {
            assertEquals(
                LLVMBinaryTypeCOFF,
                binaryType
            );

            return;
        }

        assertTrue(
            binaryType
                == LLVMBinaryTypeELF32L
                || binaryType
                == LLVMBinaryTypeELF32B
                || binaryType
                == LLVMBinaryTypeELF64L
                || binaryType
                == LLVMBinaryTypeELF64B
        );
    }

    private static String errorDetails(
        BytePointer nativeError
    ) {
        if (Pointer.isNull(nativeError)) {
            return "LLVM supplied no diagnostic.";
        }

        var details =
            nativeError.getString();

        if (
            details == null
                || details.isBlank()
        ) {
            return "LLVM supplied no diagnostic.";
        }

        return details.strip();
    }

    private static void disposeMessage(
        BytePointer message
    ) {
        if (Pointer.isNull(message)) {
            return;
        }

        LLVMDisposeMessage(
            message
        );

        message.setNull();
    }
}
