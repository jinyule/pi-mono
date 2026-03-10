package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiCliModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiCliModule();

        assertThat(module.id()).isEqualTo("pi-cli");
        assertThat(module.description()).contains("CLI entrypoint");
        assertThat(module.parser()).isNotNull();
    }
}
