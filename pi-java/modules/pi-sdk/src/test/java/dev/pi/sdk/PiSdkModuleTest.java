package dev.pi.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiSdkModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiSdkModule();

        assertThat(module.id()).isEqualTo("pi-sdk");
        assertThat(module.description()).contains("Embeddable facade");
    }
}

