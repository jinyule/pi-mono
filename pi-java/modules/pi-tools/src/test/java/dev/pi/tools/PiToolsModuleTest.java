package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiToolsModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiToolsModule();

        assertThat(module.id()).isEqualTo("pi-tools");
        assertThat(module.description()).contains("Built-in tools");
    }
}

