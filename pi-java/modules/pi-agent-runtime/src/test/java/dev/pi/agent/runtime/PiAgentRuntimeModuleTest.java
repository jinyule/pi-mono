package dev.pi.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiAgentRuntimeModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiAgentRuntimeModule();

        assertThat(module.id()).isEqualTo("pi-agent-runtime");
        assertThat(module.description()).contains("agent loop");
    }
}

