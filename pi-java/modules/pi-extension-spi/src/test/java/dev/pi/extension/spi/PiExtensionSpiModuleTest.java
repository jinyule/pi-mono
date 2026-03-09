package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiExtensionSpiModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiExtensionSpiModule();

        assertThat(module.id()).isEqualTo("pi-extension-spi");
        assertThat(module.description()).contains("extension SPI");
    }
}

