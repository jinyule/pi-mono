package dev.pi.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimpleStreamOptionsTest {
    @Test
    void builderCarriesSharedAndReasoningOptions() {
        var options = SimpleStreamOptions.builder()
            .temperature(0.2)
            .maxTokens(4_096)
            .apiKey("test-key")
            .transport(Transport.SSE)
            .cacheRetention(CacheRetention.LONG)
            .sessionId("session-123")
            .maxRetryDelayMs(5_000L)
            .header("X-Test", "true")
            .metadata("user_id", "abc")
            .reasoning(ThinkingLevel.MEDIUM)
            .thinkingBudgets(new ThinkingBudgets(32, 256, 1_024, 2_048))
            .build();

        assertThat(options.temperature()).isEqualTo(0.2);
        assertThat(options.maxTokens()).isEqualTo(4_096);
        assertThat(options.apiKey()).isEqualTo("test-key");
        assertThat(options.transport()).isEqualTo(Transport.SSE);
        assertThat(options.cacheRetention()).isEqualTo(CacheRetention.LONG);
        assertThat(options.sessionId()).isEqualTo("session-123");
        assertThat(options.maxRetryDelayMs()).isEqualTo(5_000L);
        assertThat(options.headers()).containsEntry("X-Test", "true");
        assertThat(options.metadata()).containsEntry("user_id", "abc");
        assertThat(options.reasoning()).isEqualTo(ThinkingLevel.MEDIUM);
        assertThat(options.thinkingBudgets().high()).isEqualTo(2_048);
    }
}

