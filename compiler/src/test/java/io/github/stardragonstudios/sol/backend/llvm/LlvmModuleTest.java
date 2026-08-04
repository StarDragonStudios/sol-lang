package io.github.stardragonstudios.sol.backend.llvm;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;

import org.junit.jupiter.api.Test;


import static org.bytedeco.llvm.global.LLVM.LLVMAddFunction;
import static org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlockInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildRetVoid;
import static org.bytedeco.llvm.global.LLVM.LLVMCreateBuilderInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMDisposeBuilder;
import static org.bytedeco.llvm.global.LLVM.LLVMFunctionType;
import static org.bytedeco.llvm.global.LLVM.LLVMInt64TypeInContext;
import static org.bytedeco.llvm.global.LLVM.LLVMPositionBuilderAtEnd;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmModuleTest {
    @Test
    void createsVerifiesAndPrintsEmptyModules() {
        try (var module = LlvmModule.create("sol.test")) {
            assertEquals("sol.test", module.name());
            assertFalse(module.isClosed());
            assertDoesNotThrow(module::verify);

            assertEquals(
                """
                ; ModuleID = 'sol.test'
                source_filename = "sol.test"
                """,
                normalizeNewlines(module.text())
            );
        }
    }

    @Test
    void isolatesNativeStateBetweenModules() {
        try (
            var first = LlvmModule.create("sol.first");

            var second = LlvmModule.create("sol.second")
        ) {
            assertNotEquals(first.contextHandle().address(), second.contextHandle().address());
            assertNotEquals(first.moduleHandle().address(), second.moduleHandle().address());
            assertTrue(first.text().contains("sol.first"));
            assertTrue(second.text().contains("sol.second"));
        }
    }

    @Test
    void closesModulesDeterministicallyAndIdempotently() {
        var module = LlvmModule.create("sol.closed");
        module.close();
        module.close();

        assertTrue(module.isClosed());

        var textException = assertThrows(LlvmBackendException.class, module::text);

        assertEquals("LLVM module 'sol.closed' has already been closed.", textException.getMessage());
        assertThrows(LlvmBackendException.class, module::verify);
        assertThrows(LlvmBackendException.class, module::contextHandle);
        assertThrows(LlvmBackendException.class, module::moduleHandle);
    }

    @Test
    void rejectsInvalidModuleNames() {
        assertThrows(
            NullPointerException.class,
            () -> LlvmModule.create(null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> LlvmModule.create("   ")
        );
    }

    @Test
    void reportsInvalidModulesAsDeterministicBackendFailures() {
        try (
            var module = LlvmModule.create("sol.invalid-verification");
            var parameterTypes = new PointerPointer<LLVMTypeRef>(0)
        ) {
            var functionType = LLVMFunctionType(LLVMInt64TypeInContext(module.contextHandle()), parameterTypes, 0, 0);
            var function = LLVMAddFunction(module.moduleHandle(), "invalid", functionType);
            var block = LLVMAppendBasicBlockInContext(module.contextHandle(), function, "entry");
            var builder = LLVMCreateBuilderInContext(module.contextHandle());

            assertFalse(Pointer.isNull(functionType));
            assertFalse(Pointer.isNull(function));
            assertFalse(Pointer.isNull(block));
            assertFalse(Pointer.isNull(builder));

            try {
                LLVMPositionBuilderAtEnd(builder, block);

                var invalidReturn = LLVMBuildRetVoid(builder);

                assertFalse(Pointer.isNull(invalidReturn));
            } finally {
                LLVMDisposeBuilder(builder);

                builder.setNull();
            }

            var exception = assertThrows(LlvmBackendException.class, module::verify);
            var prefix = "LLVM module 'sol.invalid-verification' failed verification:";

            assertTrue(exception.getMessage().startsWith(prefix));
            assertTrue(exception.getMessage().length() > prefix.length());
        }
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }
}
