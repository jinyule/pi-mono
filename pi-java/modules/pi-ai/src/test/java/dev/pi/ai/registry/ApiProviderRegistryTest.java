package dev.pi.ai.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pi.ai.model.Context;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.SimpleStreamOptions;
import dev.pi.ai.model.StreamOptions;
import dev.pi.ai.provider.ApiProvider;
import dev.pi.ai.stream.AssistantMessageEventStream;
import org.junit.jupiter.api.Test;

class ApiProviderRegistryTest {
    @Test
    void registersLooksUpAndListsProviders() {
        var registry = new ApiProviderRegistry();
        var provider = new FakeApiProvider("fake-api");

        registry.register(provider);

        assertThat(registry.get("fake-api")).containsSame(provider);
        assertThat(registry.getProviders()).containsExactly(provider);
    }

    @Test
    void removesProvidersBySourceId() {
        var registry = new ApiProviderRegistry();
        var first = new FakeApiProvider("first-api");
        var second = new FakeApiProvider("second-api");

        registry.register(first, "plugin-a");
        registry.register(second, "plugin-b");
        registry.unregisterSource("plugin-a");

        assertThat(registry.get("first-api")).isEmpty();
        assertThat(registry.get("second-api")).containsSame(second);
    }

    @Test
    void throwsWhenProviderIsMissing() {
        var registry = new ApiProviderRegistry();

        assertThatThrownBy(() -> registry.require("missing-api"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing-api");
    }

    private static final class FakeApiProvider implements ApiProvider {
        private final String api;

        private FakeApiProvider(String api) {
            this.api = api;
        }

        @Override
        public String api() {
            return api;
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            return new AssistantMessageEventStream();
        }

        @Override
        public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
            return new AssistantMessageEventStream();
        }
    }
}
