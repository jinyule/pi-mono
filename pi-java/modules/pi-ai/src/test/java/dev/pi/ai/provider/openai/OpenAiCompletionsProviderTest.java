package dev.pi.ai.provider.openai;

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

class OpenAiCompletionsProviderTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void streamSimpleBuildsPayloadAndNormalizesFixtureChunks() throws Exception {
        var transport = new FakeTransport(loadFixture("/fixtures/providers/openai-completions/response-stream.jsonl"));
        var provider = new OpenAiCompletionsProvider(
            transport,
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );
        var events = new CopyOnWriteArrayList<AssistantMessageEvent>();

        var stream = provider.streamSimple(
            model(),
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
            assertThat(transport.capturedRequest.uri().toString()).isEqualTo("https://api.openai.com/v1/chat/completions");
            assertThat(transport.capturedRequest.body().path("model").asText()).isEqualTo("gpt-5.1-codex");
            assertThat(transport.capturedRequest.body().path("stream").asBoolean()).isTrue();
            assertThat(transport.capturedRequest.body().path("store").asBoolean()).isFalse();
            assertThat(transport.capturedRequest.body().path("stream_options").path("include_usage").asBoolean()).isTrue();
            assertThat(transport.capturedRequest.body().path("max_completion_tokens").asInt()).isEqualTo(4_096);
            assertThat(transport.capturedRequest.body().path("temperature").asDouble()).isEqualTo(0.2);
            assertThat(transport.capturedRequest.body().path("reasoning_effort").asText()).isEqualTo("high");
            assertThat(transport.capturedRequest.body().path("messages").get(0).path("role").asText()).isEqualTo("developer");
            assertThat(transport.capturedRequest.body().path("messages").get(1).path("role").asText()).isEqualTo("user");
            assertThat(transport.capturedRequest.body().path("tools").get(0).path("function").path("name").asText()).isEqualTo("read");

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

            assertThat(message.provider()).isEqualTo("openai");
            assertThat(message.api()).isEqualTo("openai-completions");
            assertThat(message.model()).isEqualTo("gpt-5.1-codex");
            assertThat(message.stopReason()).isEqualTo(StopReason.TOOL_USE);
            assertThat(message.usage().input()).isEqualTo(100);
            assertThat(message.usage().cacheRead()).isEqualTo(28);
            assertThat(message.usage().output()).isEqualTo(64);
            assertThat(message.usage().totalTokens()).isEqualTo(192);
            assertThat(message.content()).hasSize(3);
            assertThat(message.content().get(0)).isInstanceOf(ThinkingContent.class);
            assertThat(message.content().get(1)).isInstanceOf(TextContent.class);
            assertThat(message.content().get(2)).isInstanceOf(ToolCall.class);

            var toolCall = (ToolCall) message.content().get(2);
            assertThat(toolCall.id()).isEqualTo("call-1");
            assertThat(toolCall.name()).isEqualTo("read");
            assertThat(toolCall.arguments()).isEqualTo(JsonNodeFactory.instance.objectNode().put("path", "README.md"));
        }
    }

    @Test
    void streamEmitsErrorEventWhenTransportFails() {
        var provider = new OpenAiCompletionsProvider(
            (request, onChunk) -> {
                throw new IllegalStateException("network down");
            },
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        var stream = provider.stream(
            model(),
            context(),
            StreamOptions.builder().apiKey("test-key").build()
        );

        assertThat(stream.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.ERROR && "network down".equals(message.errorMessage()));
    }

    @Test
    void requiresApiKey() {
        var provider = new OpenAiCompletionsProvider(
            (request, onChunk) -> {
            },
            java.time.Clock.fixed(Instant.ofEpochMilli(1_741_398_400_000L), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> provider.stream(model(), context(), StreamOptions.builder().build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No API key");
    }

    private static Model model() {
        return new Model(
            "gpt-5.1-codex",
            "gpt-5.1-codex",
            "openai-completions",
            "openai",
            "https://api.openai.com/v1",
            true,
            List.of("text"),
            new Usage.Cost(0.1, 0.2, 0.0, 0.0, 0.3),
            200_000,
            32_000,
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
        var resource = OpenAiCompletionsProviderTest.class.getResourceAsStream(path);
        assertThat(resource).isNotNull();

        var chunks = new ArrayList<JsonNode>();
        try (var reader = new BufferedReader(new InputStreamReader(resource))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    chunks.add(OBJECT_MAPPER.readTree(line));
                }
            }
        }
        return chunks;
    }

    private static final class FakeTransport implements OpenAiCompletionsTransport {
        private final List<JsonNode> chunks;
        private OpenAiCompletionsRequest capturedRequest;

        private FakeTransport(List<JsonNode> chunks) {
            this.chunks = List.copyOf(chunks);
        }

        @Override
        public void stream(OpenAiCompletionsRequest request, java.util.function.Consumer<JsonNode> onChunk) {
            capturedRequest = request;
            for (var chunk : chunks) {
                onChunk.accept(chunk.deepCopy());
            }
        }
    }
}
