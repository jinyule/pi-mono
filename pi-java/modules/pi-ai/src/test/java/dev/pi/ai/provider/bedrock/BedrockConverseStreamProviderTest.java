package dev.pi.ai.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

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

class BedrockConverseStreamProviderTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void streamSimpleBuildsBudgetThinkingPayloadAndNormalizesFixtureEvents() throws Exception {
        var transport = new FakeTransport(loadFixture("/fixtures/providers/bedrock-converse-stream/response-stream.jsonl"));
        var provider = new BedrockConverseStreamProvider(
            transport,
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );
        var events = new CopyOnWriteArrayList<AssistantMessageEvent>();

        var stream = provider.streamSimple(
            budgetModel(),
            context(),
            SimpleStreamOptions.builder()
                .temperature(0.2)
                .maxTokens(4_096)
                .cacheRetention(CacheRetention.LONG)
                .reasoning(ThinkingLevel.HIGH)
                .metadata("aws_region", "us-west-2")
                .build()
        );

        try (var ignored = stream.subscribe(events::add)) {
            var message = stream.result().get();

            assertThat(transport.capturedRequest.modelId()).isEqualTo("anthropic.claude-3-7-sonnet-20250219-v1:0");
            assertThat(transport.capturedRequest.region()).isEqualTo("us-west-2");
            assertThat(transport.capturedRequest.endpointOverride().toString()).isEqualTo("https://bedrock-runtime.us-west-2.amazonaws.com");
            assertThat(transport.capturedRequest.body().path("inferenceConfig").path("maxTokens").asInt()).isEqualTo(20_480);
            assertThat(transport.capturedRequest.body().path("inferenceConfig").path("temperature").asDouble()).isEqualTo(0.2);
            assertThat(transport.capturedRequest.body().path("additionalModelRequestFields").path("thinking").path("type").asText())
                .isEqualTo("enabled");
            assertThat(transport.capturedRequest.body().path("additionalModelRequestFields").path("thinking").path("budget_tokens").asInt())
                .isEqualTo(16_384);
            assertThat(transport.capturedRequest.body().path("additionalModelRequestFields").path("anthropic_beta").get(0).asText())
                .isEqualTo("interleaved-thinking-2025-05-14");
            assertThat(transport.capturedRequest.body().path("system").get(0).path("text").asText()).isEqualTo("You are a helpful coding agent.");
            assertThat(transport.capturedRequest.body().path("system").get(1).path("cachePoint").path("ttl").asText()).isEqualTo("ONE_HOUR");
            assertThat(transport.capturedRequest.body().path("messages").get(0).path("role").asText()).isEqualTo("user");
            assertThat(transport.capturedRequest.body().path("messages").get(0).path("content").get(0).path("text").asText()).isEqualTo("Read README.md");
            assertThat(transport.capturedRequest.body().path("messages").get(0).path("content").get(1).path("cachePoint").path("type").asText())
                .isEqualTo("DEFAULT");
            assertThat(transport.capturedRequest.body().path("toolConfig").path("tools").get(0).path("toolSpec").path("name").asText())
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
                "toolcall_delta",
                "toolcall_end",
                "done"
            );

            assertThat(message.provider()).isEqualTo("bedrock");
            assertThat(message.api()).isEqualTo("bedrock-converse-stream");
            assertThat(message.model()).isEqualTo("anthropic.claude-3-7-sonnet-20250219-v1:0");
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
                {"type":"messageStart","role":"assistant"}
                """),
            json("""
                {"type":"messageStop","stopReason":"END_TURN"}
                """),
            json("""
                {"type":"metadata","usage":{"inputTokens":10,"outputTokens":5,"cacheReadInputTokens":0,"cacheWriteInputTokens":0,"totalTokens":15}}
                """)
        ));
        var provider = new BedrockConverseStreamProvider(
            transport,
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        var stream = provider.streamSimple(
            adaptiveModel(),
            context(),
            SimpleStreamOptions.builder()
                .maxTokens(4_096)
                .reasoning(ThinkingLevel.XHIGH)
                .build()
        );

        assertThat(stream.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.STOP);

        assertThat(transport.capturedRequest.body().path("inferenceConfig").path("maxTokens").asInt()).isEqualTo(4_096);
        assertThat(transport.capturedRequest.body().path("additionalModelRequestFields").path("thinking").path("type").asText())
            .isEqualTo("adaptive");
        assertThat(transport.capturedRequest.body().path("additionalModelRequestFields").path("output_config").path("effort").asText())
            .isEqualTo("max");
    }

    @Test
    void streamDoesNotRequireApiKey() {
        var transport = new FakeTransport(List.of(
            json("""
                {"type":"messageStart","role":"assistant"}
                """),
            json("""
                {"type":"messageStop","stopReason":"END_TURN"}
                """),
            json("""
                {"type":"metadata","usage":{"inputTokens":10,"outputTokens":5,"cacheReadInputTokens":0,"cacheWriteInputTokens":0,"totalTokens":15}}
                """)
        ));
        var provider = new BedrockConverseStreamProvider(
            transport,
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        var stream = provider.stream(
            budgetModel(),
            context(),
            StreamOptions.builder().build()
        );

        assertThat(stream.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.STOP);

        assertThat(transport.capturedRequest.region()).isEqualTo("us-west-2");
    }

    @Test
    void streamEmitsErrorEventWhenTransportFails() {
        var provider = new BedrockConverseStreamProvider(
            (request, onEvent) -> {
                throw new IllegalStateException("network down");
            },
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        var stream = provider.stream(
            budgetModel(),
            context(),
            StreamOptions.builder().build()
        );

        assertThat(stream.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.ERROR && "network down".equals(message.errorMessage()));
    }

    private static Model budgetModel() {
        return new Model(
            "anthropic.claude-3-7-sonnet-20250219-v1:0",
            "anthropic.claude-3-7-sonnet-20250219-v1:0",
            "bedrock-converse-stream",
            "bedrock",
            "https://bedrock-runtime.us-west-2.amazonaws.com",
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
            "anthropic.claude-opus-4-6-20251102-v1:0",
            "anthropic.claude-opus-4-6-20251102-v1:0",
            "bedrock-converse-stream",
            "bedrock",
            "https://bedrock-runtime.us-west-2.amazonaws.com",
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
        var resource = BedrockConverseStreamProviderTest.class.getResourceAsStream(path);
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

    private static final class FakeTransport implements BedrockConverseStreamTransport {
        private final List<JsonNode> events;
        private BedrockConverseStreamRequest capturedRequest;

        private FakeTransport(List<JsonNode> events) {
            this.events = List.copyOf(events);
        }

        @Override
        public void stream(BedrockConverseStreamRequest request, java.util.function.Consumer<JsonNode> onEvent) {
            capturedRequest = request;
            for (var event : events) {
                onEvent.accept(event.deepCopy());
            }
        }
    }
}
