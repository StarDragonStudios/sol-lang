package io.github.stardragonstudios.sol.backend.llvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlvmTargetMachineTest {
    @Test
    void createsInspectableHostTargetMachine() {
        try (var machine = LlvmTargetMachine.createHost()) {
            assertFalse(machine.configuration().triple().isBlank());
            assertFalse(machine.targetName().isBlank());
            assertFalse(machine.dataLayout().isBlank());
            assertFalse(machine.isClosed());
        }
    }

    @Test
    void configuresModuleTripleAndDataLayout() {
        try (
            var module = LlvmModule.create("sol.target-configuration");

            var machine = LlvmTargetMachine.createHost()
        ) {
            machine.configure(module);

            var text = module.text();

            assertTrue(text.contains(
                """
                target datalayout = "%s"
                """.formatted(machine.dataLayout()).strip()
            ));

            assertTrue(text.contains(
                """
                target triple = "%s"
                """.formatted(machine.configuration().triple()).strip()
            ));

            module.verify();
        }
    }

    @Test
    void rejectsUnsupportedTargetTriplesClearly() {
        var configuration = new LlvmTargetConfiguration(
            "sol-invalid-unknown-none",
            "generic",
            "",
            LlvmTargetConfiguration.OptimizationLevel.DEFAULT,
            LlvmTargetConfiguration.RelocationModel.DEFAULT,
            LlvmTargetConfiguration.CodeModel.DEFAULT
        );

        var exception = assertThrows(
            LlvmBackendException.class,
            () -> LlvmTargetMachine.create(configuration)
        );

        assertTrue(exception.getMessage().contains("sol-invalid-unknown-none"));
    }

    @Test
    void closesTargetMachineIdempotently() {
        var machine = LlvmTargetMachine.createHost();

        machine.close();

        assertTrue(machine.isClosed());

        assertDoesNotThrow(machine::close);
    }

    @Test
    void rejectsOperationsAfterClosure() {
        var machine = LlvmTargetMachine.createHost();

        machine.close();

        try (var module = LlvmModule.create("sol.closed-target-machine")) {
            assertThrows(
                LlvmBackendException.class,
                () -> machine.configure(module)
            );
        }
    }
}
