package dev.pi.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.auth.ApiKeyCredential;
import dev.pi.ai.auth.CredentialResolver;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.SimpleStreamOptions;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.StreamOptions;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Usage;
import dev.pi.ai.provider.ApiProvider;
import dev.pi.ai.registry.ApiProviderRegistry;
import dev.pi.ai.registry.ModelRegistry;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PiAiClientTest {
    @Test
    void streamSimpleDelegatesToRegisteredProviderAndInjectsResolvedCredential() {
        var provider = new FakeApiProvider();
        var apiProviders = new ApiProviderRegistry();
        apiProviders.register(provider);
        var models = new ModelRegistry();
        var model = model();
        models.register(model);
        var client = new PiAiClient(
            apiProviders,
            models,
            new CredentialResolver(ignored -> Optional.of(new ApiKeyCredential("resolved-key")))
        );

        var stream = client.streamSimple(
            model,
            context(),
            SimpleStreamOptions.builder().reasoning(ThinkingLevel.MEDIUM).build()
        );

        assertThat(stream.result()).succeedsWithin(Duration.ofSeconds(1));
        assertThat(provider.lastSimpleOptions.apiKey()).isEqualTo("resolved-key");
        assertThat(provider.lastSimpleOptions.reasoning()).isEqualTo(ThinkingLevel.MEDIUM);
        assertThat(provider.lastModel).isEqualTo(model);
        assertThat(provider.lastContext).isEqualTo(context());
    }

    @Test
    void completeUsesExplicitApiKeyInsteadOfResolvedCredential() {
        var provider = new FakeApiProvider();
        var apiProviders = new ApiProviderRegistry();
        apiProviders.register(provider);
        var client = new PiAiClient(
            apiProviders,
            new ModelRegistry(),
            new CredentialResolver(ignored -> Optional.of(new ApiKeyCredential("resolved-key")))
        );

        var result = client.complete(
            model(),
            context(),
            StreamOptions.builder().apiKey("explicit-key").build()
        );

        assertThat(result).succeedsWithin(Duration.ofSeconds(1));
        assertThat(provider.lastStreamOptions.apiKey()).isEqualTo("explicit-key");
    }

    @Test
    void exposesRegisteredModelsThroughFacade() {
        var modelRegistry = new ModelRegistry();
        var model = model();
        modelRegistry.register(model);
        var client = new PiAiClient(new ApiProviderRegistry(), modelRegistry, CredentialResolver.defaultResolver());

        assertThat(client.getProviders()).containsExactly("fake-provider");
        assertThat(client.getModels("fake-provider")).containsExactly(model);
        assertThat(client.getModel("fake-provider", "fake-model")).isEqualTo(model);
    }

    @Test
    void throwsWhenNoProviderIsRegisteredForModelApi() {
        var client = new PiAiClient(new ApiProviderRegistry(), new ModelRegistry(), CredentialResolver.defaultResolver());

        assertThatThrownBy(() -> client.stream(model(), context(), StreamOptions.builder().build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fake-api");
    }

    private static Context context() {
        return new Context(
            "You are helpful.",
            List.of(new Message.UserMessage(List.of(new TextContent("Hello", null)), 1_741_398_400_000L)),
            List.of()
        );
    }

    private static Model model() {
        return new Model(
            "fake-model",
            "Fake Model",
            "fake-api",
            "fake-provider",
            "https://example.com",
            true,
            List.of("text"),
            new Usage.Cost(0.1, 0.2, 0.0, 0.0, 0.3),
            200_000,
            32_000,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Message.AssistantMessage responseMessage() {
        return new Message.AssistantMessage(
            List.of(new TextContent("Hello back", null)),
            "fake-api",
            "fake-provider",
            "fake-model",
            new Usage(10, 20, 0, 0, 30, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)),
            StopReason.STOP,
            null,
            1_741_398_401_000L
        );
    }

    private static final class FakeApiProvider implements ApiProvider {
        private Model lastModel;
        private Context lastContext;
        private StreamOptions lastStreamOptions;
        private SimpleStreamOptions lastSimpleOptions;

        @Override
        public String api() {
            return "fake-api";
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            lastModel = model;
            lastContext = context;
            lastStreamOptions = options;
            return completedStream(responseMessage());
        }

        @Override
        public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
            lastModel = model;
            lastContext = context;
            lastSimpleOptions = options;
            return completedStream(responseMessage());
        }

        private static AssistantMessageEventStream completedStream(Message.AssistantMessage message) {
            var stream = new AssistantMessageEventStream();
            stream.push(new dev.pi.ai.model.AssistantMessageEvent.Done(StopReason.STOP, message));
            return stream;
        }
    }
}
