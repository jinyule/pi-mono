package dev.pi.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiAiModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiAiModule();

        assertThat(module.id()).isEqualTo("pi-ai");
        assertThat(module.description()).contains("LLM provider");
    }

    @Test
    void shipsSeedFixtureFromTypeScriptRepo() {
        assertThat(getClass().getResource("/fixtures/ts/assistant-message-with-thinking-code.json"))
            .isNotNull();
    }
}

