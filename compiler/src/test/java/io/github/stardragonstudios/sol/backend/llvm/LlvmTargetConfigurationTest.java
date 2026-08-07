package io.github.stardragonstudios.sol.backend.llvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class LlvmTargetConfigurationTest {
    @Test
    void discoversInspectableHostConfiguration() {
        var configuration = LlvmTargetConfiguration.host();

        assertFalse(configuration.triple().isBlank());
        assertFalse(configuration.cpu().isBlank());
        assertSame(LlvmTargetConfiguration.OptimizationLevel.DEFAULT, configuration.optimizationLevel());
        assertSame(LlvmTargetConfiguration.RelocationModel.POSITION_INDEPENDENT, configuration.relocationModel());
        assertSame(LlvmTargetConfiguration.CodeModel.DEFAULT, configuration.codeModel());
    }

    @Test
    void hostDiscoveryIsRepeatable() {
        var first = LlvmTargetConfiguration.host();
        var second = LlvmTargetConfiguration.host();

        assertFalse(first.triple().isBlank());
        assertFalse(second.triple().isBlank());
    }
}
