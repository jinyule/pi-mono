package dev.pi.ai.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.AssistantMessageEvent;
import dev.pi.ai.model.CacheRetention;
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

class AnthropicMessagesProviderTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void streamSimpleBuildsBudgetThinkingPayloadAndNormalizesFixtureEvents() throws Exception {
        var transport = new FakeTransport(loadFixture("/fixtures/providers/anthropic-messages/response-stream.jsonl"));
        var provider = new AnthropicMessagesProvider(
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
                .cacheRetention(CacheRetention.LONG)
                .reasoning(ThinkingLevel.HIGH)
                .build()
        );

        try (var ignored = stream.subscribe(events::add)) {
            var message = stream.result().get();

            assertThat(transport.capturedRequest.uri().toString()).isEqualTo("https://api.anthropic.com/v1/messages");
            assertThat(transport.capturedRequest.headers().get("X-Api-Key")).isEqualTo("test-key");
            assertThat(transport.capturedRequest.headers().get("anthropic-version")).isEqualTo("2023-06-01");
            assertThat(transport.capturedRequest.headers().get("anthropic-beta"))
                .contains("fine-grained-tool-streaming-2025-05-14")
                .contains("interleaved-thinking-2025-05-14");
            assertThat(transport.capturedRequest.body().path("model").asText()).isEqualTo("claude-3-7-sonnet-20250219");
            assertThat(transport.capturedRequest.body().path("stream").asBoolean()).isTrue();
            assertThat(transport.capturedRequest.body().path("max_tokens").asInt()).isEqualTo(20_480);
            assertThat(transport.capturedRequest.body().path("thinking").path("type").asText()).isEqualTo("enabled");
            assertThat(transport.capturedRequest.body().path("thinking").path("budget_tokens").asInt()).isEqualTo(16_384);
            assertThat(transport.capturedRequest.body().path("temperature").isMissingNode()).isTrue();
            assertThat(transport.capturedRequest.body().path("system").get(0).path("text").asText()).isEqualTo("You are a helpful coding agent.");
            assertThat(transport.capturedRequest.body().path("system").get(0).path("cache_control").path("ttl").asText()).isEqualTo("1h");
            assertThat(transport.capturedRequest.body().path("messages").get(0).path("role").asText()).isEqualTo("user");
            assertThat(transport.capturedRequest.body().path("messages").get(0).path("content").get(0).path("text").asText()).isEqualTo("Read README.md");
            assertThat(transport.capturedRequest.body().path("tools").get(0).path("name").asText()).isEqualTo("read");

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
                "toolcall_delta",
                "toolcall_end",
                "done"
            );

            assertThat(message.provider()).isEqualTo("anthropic");
            assertThat(message.api()).isEqualTo("anthropic-messages");
            assertThat(message.model()).isEqualTo("claude-3-7-sonnet-20250219");
            assertThat(message.stopReason()).isEqualTo(StopReason.TOOL_USE);
            assertThat(message.usage().input()).isEqualTo(120);
            assertThat(message.usage().cacheRead()).isEqualTo(20);
            assertThat(message.usage().cacheWrite()).isEqualTo(10);
            assertThat(message.usage().output()).isEqualTo(64);
            assertThat(message.usage().totalTokens()).isEqualTo(214);
            assertThat(message.content()).hasSize(3);
            assertThat(message.content().get(0)).isInstanceOf(ThinkingContent.class);
            assertThat(message.content().get(1)).isInstanceOf(TextContent.class);
            assertThat(message.content().get(2)).isInstanceOf(ToolCall.class);

            var toolCall = (ToolCall) message.content().get(2);
            assertThat(toolCall.id()).isEqualTo("toolu_1");
            assertThat(toolCall.name()).isEqualTo("read");
            assertThat(toolCall.arguments()).isEqualTo(JsonNodeFactory.instance.objectNode().put("path", "README.md"));
        }
    }

    @Test
    void streamSimpleUsesAdaptiveThinkingForOpus46() {
        var transport = new FakeTransport(List.of(
            json("""
                {"type":"message_start","message":{"usage":{"input_tokens":10,"output_tokens":0,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}}
                """),
            json("""
                {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"input_tokens":10,"output_tokens":5,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}
                """),
            json("""
                {"type":"message_stop"}
                """)
        ));
        var provider = new AnthropicMessagesProvider(
            transport,
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        var stream = provider.streamSimple(
            adaptiveModel(),
            context(),
            SimpleStreamOptions.builder()
                .apiKey("test-key")
                .maxTokens(4_096)
                .reasoning(ThinkingLevel.XHIGH)
                .build()
        );

        assertThat(stream.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.STOP);

        assertThat(transport.capturedRequest.body().path("max_tokens").asInt()).isEqualTo(4_096);
        assertThat(transport.capturedRequest.body().path("thinking").path("type").asText()).isEqualTo("adaptive");
        assertThat(transport.capturedRequest.body().path("output_config").path("effort").asText()).isEqualTo("max");
    }

    @Test
    void streamEmitsErrorEventWhenTransportFails() {
        var provider = new AnthropicMessagesProvider(
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
        var provider = new AnthropicMessagesProvider(
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
            "claude-3-7-sonnet-20250219",
            "claude-3-7-sonnet-20250219",
            "anthropic-messages",
            "anthropic",
            "https://api.anthropic.com/v1",
            true,
            List.of("text", "image"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            200_000,
            64_000,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Model adaptiveModel() {
        return new Model(
            "claude-opus-4-6",
            "claude-opus-4-6",
            "anthropic-messages",
            "anthropic",
            "https://api.anthropic.com/v1",
            true,
            List.of("text"),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0),
            200_000,
            64_000,
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
        var resource = AnthropicMessagesProviderTest.class.getResourceAsStream(path);
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

    private static final class FakeTransport implements AnthropicMessagesTransport {
        private final List<JsonNode> events;
        private AnthropicMessagesRequest capturedRequest;

        private FakeTransport(List<JsonNode> events) {
            this.events = List.copyOf(events);
        }

        @Override
        public void stream(AnthropicMessagesRequest request, java.util.function.Consumer<JsonNode> onEvent) {
            capturedRequest = request;
            for (var event : events) {
                onEvent.accept(event.deepCopy());
            }
        }
    }
}
