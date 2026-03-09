package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiTuiModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiTuiModule();

        assertThat(module.id()).isEqualTo("pi-tui");
        assertThat(module.description()).contains("Terminal");
    }
}

