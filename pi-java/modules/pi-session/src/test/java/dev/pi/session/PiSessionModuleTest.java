package dev.pi.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiSessionModuleTest {
    @Test
    void exposesStableModuleMetadata() {
        var module = new PiSessionModule();

        assertThat(module.id()).isEqualTo("pi-session");
        assertThat(module.description()).contains("Session persistence");
    }

    @Test
    void shipsSeedSessionFixtureFromTypeScriptRepo() {
        assertThat(getClass().getResource("/fixtures/ts/large-session.jsonl")).isNotNull();
    }
}

