package dev.pi.ai.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.Usage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelRegistryTest {
    @Test
    void registersQueriesAndListsModelsByProvider() {
        var registry = new ModelRegistry();
        var first = model("openai", "openai-responses", "gpt-5");
        var second = model("openai", "openai-completions", "gpt-4o");
        var third = model("anthropic", "anthropic-messages", "claude-sonnet");

        registry.registerAll(List.of(first, second, third));

        assertThat(registry.getProviders()).containsExactly("openai", "anthropic");
        assertThat(registry.getModel("openai", "gpt-5")).contains(first);
        assertThat(registry.getModels("openai")).containsExactly(first, second);
        assertThat(registry.getModels("anthropic")).containsExactly(third);
    }

    @Test
    void throwsWhenModelIsMissing() {
        var registry = new ModelRegistry();

        assertThatThrownBy(() -> registry.require("openai", "missing-model"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing-model");
    }

    private static Model model(String provider, String api, String id) {
        return new Model(
            id,
            id,
            api,
            provider,
            "https://example.com",
            true,
            List.of("text"),
            new Usage.Cost(0.1, 0.2, 0.0, 0.0, 0.3),
            200_000,
            32_000,
            java.util.Map.of("X-Test", "true"),
            JsonNodeFactory.instance.objectNode().put("compat", true)
        );
    }
}
