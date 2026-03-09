package dev.pi.ai.provider.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.SimpleStreamOptions;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.StreamOptions;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ThinkingContent;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Tool;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Usage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class GoogleGenerativeAiProviderTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void streamSimpleBuildsBudgetThinkingPayloadAndNormalizesFixtureEvents() throws Exception {
        var transport = new FakeTransport(loadFixture("/fixtures/providers/google-generative-ai/response-stream.jsonl"));
        var provider = new GoogleGenerativeAiProvider(
            transport,
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );
        var events = new CopyOnWriteArrayList<AssistantMessageEvent>();

        var stream = provider.streamSimple(
            budgetModel(),
            context(),
            SimpleStreamOptions.builder()
                .apiKey("test-key")
                .temperature(0.2)
                .maxTokens(4_096)
                .reasoning(ThinkingLevel.HIGH)
                .build()
        );

        try (var ignored = stream.subscribe(events::add)) {
            var message = stream.result().get();

            assertThat(transport.capturedRequest.apiKey()).isEqualTo("test-key");
            assertThat(transport.capturedRequest.uri().toString())
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:streamGenerateContent?alt=sse");
            assertThat(transport.capturedRequest.body().path("contents").get(0).path("role").asText()).isEqualTo("user");
            assertThat(transport.capturedRequest.body().path("contents").get(0).path("parts").get(0).path("text").asText()).isEqualTo("Read README.md");
            assertThat(transport.capturedRequest.body().path("systemInstruction").path("parts").get(0).path("text").asText())
                .isEqualTo("You are a helpful coding agent.");
            assertThat(transport.capturedRequest.body().path("generationConfig").path("temperature").asDouble()).isEqualTo(0.2);
            assertThat(transport.capturedRequest.body().path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(4_096);
            assertThat(transport.capturedRequest.body().path("generationConfig").path("thinkingConfig").path("includeThoughts").asBoolean()).isTrue();
            assertThat(transport.capturedRequest.body().path("generationConfig").path("thinkingConfig").path("thinkingBudget").asInt())
                .isEqualTo(32_768);
            assertThat(transport.capturedRequest.body().path("tools").get(0).path("functionDeclarations").get(0).path("name").asText())
                .isEqualTo("read");

            assertThat(events).extracting(AssistantMessageEvent::type).containsExactly(
                "start",
                "thinking_start",
                "thinking_delta",
                "thinking_end",
                "text_start",
                "text_delta",
                "text_end",
                "toolcall_start",
                "toolcall_delta",
                "toolcall_end",
                "done"
            );

            assertThat(message.provider()).isEqualTo("google");
            assertThat(message.api()).isEqualTo("google-generative-ai");
            assertThat(message.model()).isEqualTo("gemini-2.5-pro");
            assertThat(message.stopReason()).isEqualTo(StopReason.TOOL_USE);
            assertThat(message.usage().input()).isEqualTo(80);
            assertThat(message.usage().cacheRead()).isEqualTo(8);
            assertThat(message.usage().output()).isEqualTo(32);
            assertThat(message.usage().totalTokens()).isEqualTo(112);
            assertThat(message.content()).hasSize(3);
            assertThat(message.content().get(0)).isInstanceOf(ThinkingContent.class);
            assertThat(message.content().get(1)).isInstanceOf(TextContent.class);
            assertThat(message.content().get(2)).isInstanceOf(ToolCall.class);

            var thinking = (ThinkingContent) message.content().get(0);
            assertThat(thinking.thinking()).isEqualTo("Need to inspect README.md");
            assertThat(thinking.thinkingSignature()).isEqualTo("c2lnbmF0dXJlMQ==");

            var text = (TextContent) message.content().get(1);
            assertThat(text.text()).isEqualTo("I'll read README.md next.");
            assertThat(text.textSignature()).isEqualTo("dGV4dC1zaWduYXR1cmU=");

            var toolCall = (ToolCall) message.content().get(2);
            assertThat(toolCall.name()).isEqualTo("read");
            assertThat(toolCall.arguments()).isEqualTo(JsonNodeFactory.instance.objectNode().put("path", "README.md"));
        }
    }

    @Test
    void streamSimpleUsesGemini3ThinkingLevel() {
        var transport = new FakeTransport(List.of(json("""
            {
              "candidates": [{"finishReason": "STOP"}],
              "usageMetadata": {
                "promptTokenCount": 10,
                "candidatesTokenCount": 5,
                "thoughtsTokenCount": 0,
                "cachedContentTokenCount": 0,
                "totalTokenCount": 15
              }
            }
            """)));
        var provider = new GoogleGenerativeAiProvider(
            transport,
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        var stream = provider.streamSimple(
            gemini3ProModel(),
            context(),
            SimpleStreamOptions.builder()
                .apiKey("test-key")
                .maxTokens(4_096)
                .reasoning(ThinkingLevel.MEDIUM)
                .build()
        );

        assertThat(stream.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.STOP);

        assertThat(transport.capturedRequest.body().path("generationConfig").path("thinkingConfig").path("includeThoughts").asBoolean()).isTrue();
        assertThat(transport.capturedRequest.body().path("generationConfig").path("thinkingConfig").path("thinkingLevel").asText())
            .isEqualTo("HIGH");
    }

    @Test
    void streamEmitsErrorEventWhenTransportFails() {
        var provider = new GoogleGenerativeAiProvider(
            (request, onEvent) -> {
                throw new IllegalStateException("network down");
            },
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        var stream = provider.stream(
            budgetModel(),
            context(),
            StreamOptions.builder().apiKey("test-key").build()
        );

        assertThat(stream.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.ERROR && "network down".equals(message.errorMessage()));
    }

    @Test
    void requiresApiKey() {
        var provider = new GoogleGenerativeAiProvider(
            (request, onEvent) -> {
            },
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> provider.stream(budgetModel(), context(), StreamOptions.builder().build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No API key");
    }

    private static Model budgetModel() {
        return new Model(
            "gemini-2.5-pro",
            "gemini-2.5-pro",
            "google-generative-ai",
            "google",
            "https://generativelanguage.googleapis.com/v1beta",
            true,
            List.of("text", "image"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            1_048_576,
            65_536,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Model gemini3ProModel() {
        return new Model(
            "gemini-3-pro",
            "gemini-3-pro",
            "google-generative-ai",
            "google",
            "https://generativelanguage.googleapis.com/v1beta",
            true,
            List.of("text"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            1_048_576,
            65_536,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Context context() {
        return new Context(
            "You are a helpful coding agent.",
            List.of(new Message.UserMessage(List.of(new TextContent("Read README.md", null)), 1_741_398_400_000L)),
            List.of(new Tool("read", "Read a file from disk", JsonNodeFactory.instance.objectNode().put("type", "object")))
        );
    }

    private static List<JsonNode> loadFixture(String path) throws Exception {
        var resource = GoogleGenerativeAiProviderTest.class.getResourceAsStream(path);
        assertThat(resource).isNotNull();

        var events = new ArrayList<JsonNode>();
        try (var reader = new BufferedReader(new InputStreamReader(resource))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    events.add(OBJECT_MAPPER.readTree(line));
                }
            }
        }
        return events;
    }

    private static JsonNode json(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class FakeTransport implements GoogleGenerativeAiTransport {
        private final List<JsonNode> events;
        private GoogleGenerativeAiRequest capturedRequest;

        private FakeTransport(List<JsonNode> events) {
            this.events = List.copyOf(events);
        }

        @Override
        public void stream(GoogleGenerativeAiRequest request, java.util.function.Consumer<JsonNode> onEvent) {
            capturedRequest = request;
            for (var event : events) {
                onEvent.accept(event.deepCopy());
            }
        }
    }
}
