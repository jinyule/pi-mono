package dev.pi.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.StopReason;
import dev.pi.ai.model.StreamOptions;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ThinkingContent;
import dev.pi.ai.model.Tool;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Usage;
import dev.pi.ai.provider.anthropic.AnthropicMessagesProvider;
import dev.pi.ai.provider.anthropic.AnthropicMessagesTransport;
import dev.pi.ai.provider.bedrock.BedrockConverseStreamProvider;
import dev.pi.ai.provider.bedrock.BedrockConverseStreamTransport;
import dev.pi.ai.provider.google.GoogleGenerativeAiProvider;
import dev.pi.ai.provider.google.GoogleGenerativeAiTransport;
import dev.pi.ai.provider.openai.OpenAiCompletionsProvider;
import dev.pi.ai.provider.openai.OpenAiCompletionsTransport;
import dev.pi.ai.provider.openai.OpenAiResponsesProvider;
import dev.pi.ai.provider.openai.OpenAiResponsesTransport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderBehaviorMatrixTest {
    private static final String IMAGE_DATA = "ZmFrZS1pbWFnZQ==";

    @Test
    void openAiResponsesNormalizesHandoffAndImageInput() {
        var transport = new OpenAiResponsesCaptureTransport();
        var provider = new OpenAiResponsesProvider(transport);

        var message = provider.stream(openAiResponsesModel(), handoffContext(), apiKeyOptions())
            .result()
            .join();

        assertThat(message.stopReason()).isEqualTo(StopReason.STOP);
        assertThat(transport.request).isNotNull();

        var body = transport.request.body();
        assertThat(body.toString()).contains("No result provided");
        assertThat(body.toString()).contains(IMAGE_DATA);
        assertThat(body.toString()).doesNotContain("Request was aborted");

        var functionCall = findFirst(body.path("input"), item -> "function_call".equals(item.path("type").asText()));
        assertThat(functionCall.path("call_id").asText()).isEqualTo("call-1");
        assertThat(functionCall.path("id").asText()).isEqualTo("fc_bad_item");

        var functionCallOutput = findFirst(body.path("input"), item -> "function_call_output".equals(item.path("type").asText()));
        assertThat(functionCallOutput.path("call_id").asText()).isEqualTo("call-1");
        assertThat(functionCallOutput.path("output").asText()).isEqualTo("No result provided");

        var imageMessage = findLast(body.path("input"), item -> "user".equals(item.path("role").asText()));
        assertThat(imageMessage.path("content").toString()).contains("input_image");
    }

    @Test
    void openAiCompletionsNormalizesHandoffAndImageInput() {
        var transport = new OpenAiCompletionsCaptureTransport();
        var provider = new OpenAiCompletionsProvider(transport);

        var message = provider.stream(openAiCompletionsModel(), handoffContext(), apiKeyOptions())
            .result()
            .join();

        assertThat(message.stopReason()).isEqualTo(StopReason.STOP);
        assertThat(transport.request).isNotNull();

        var body = transport.request.body();
        assertThat(body.toString()).contains("No result provided");
        assertThat(body.toString()).contains(IMAGE_DATA);
        assertThat(body.toString()).doesNotContain("Request was aborted");

        var assistantMessage = findFirst(body.path("messages"), item -> "assistant".equals(item.path("role").asText()));
        assertThat(assistantMessage.path("tool_calls").get(0).path("id").asText()).isEqualTo("call-1");

        var toolMessage = findFirst(body.path("messages"), item -> "tool".equals(item.path("role").asText()));
        assertThat(toolMessage.path("tool_call_id").asText()).isEqualTo("call-1");
        assertThat(toolMessage.path("content").asText()).isEqualTo("No result provided");

        var imageMessage = findLast(body.path("messages"), item -> "user".equals(item.path("role").asText()));
        assertThat(imageMessage.path("content").toString()).contains("image_url");
    }

    @Test
    void anthropicNormalizesHandoffAndImageInput() {
        var transport = new AnthropicCaptureTransport();
        var provider = new AnthropicMessagesProvider(transport);

        var message = provider.stream(anthropicModel(), handoffContext(), apiKeyOptions())
            .result()
            .join();

        assertThat(message.stopReason()).isEqualTo(StopReason.STOP);
        assertThat(transport.request).isNotNull();

        var body = transport.request.body();
        assertThat(body.toString()).contains("No result provided");
        assertThat(body.toString()).contains(IMAGE_DATA);
        assertThat(body.toString()).doesNotContain("Request was aborted");

        var assistantMessage = findFirst(body.path("messages"), item -> "assistant".equals(item.path("role").asText()));
        assertThat(assistantMessage.toString()).contains("\"id\":\"call-1_bad_item\"");

        var toolResultMessage = findFirst(
            body.path("messages"),
            item -> "user".equals(item.path("role").asText()) && item.toString().contains("\"tool_result\"")
        );
        assertThat(toolResultMessage.toString()).contains("\"tool_use_id\":\"call-1_bad_item\"");

        var imageMessage = findLast(body.path("messages"), item -> "user".equals(item.path("role").asText()));
        assertThat(imageMessage.toString()).contains("\"image\"");
    }

    @Test
    void googleNormalizesHandoffAndImageInput() {
        var transport = new GoogleCaptureTransport();
        var provider = new GoogleGenerativeAiProvider(transport);

        var message = provider.stream(googleModel(), handoffContext(), apiKeyOptions())
            .result()
            .join();

        assertThat(message.stopReason()).isEqualTo(StopReason.STOP);
        assertThat(transport.request).isNotNull();

        var body = transport.request.body();
        assertThat(body.toString()).contains("No result provided");
        assertThat(body.toString()).contains(IMAGE_DATA);
        assertThat(body.toString()).doesNotContain("Request was aborted");

        var modelMessage = findFirst(body.path("contents"), item -> "model".equals(item.path("role").asText()));
        assertThat(modelMessage.toString()).contains("\"functionCall\"");

        var toolResultMessage = findFirst(
            body.path("contents"),
            item -> "user".equals(item.path("role").asText()) && item.toString().contains("\"functionResponse\"")
        );
        assertThat(toolResultMessage.toString()).contains("\"error\":\"No result provided\"");

        var imageMessage = findLast(body.path("contents"), item -> "user".equals(item.path("role").asText()));
        assertThat(imageMessage.toString()).contains("\"inlineData\"");
    }

    @Test
    void bedrockNormalizesHandoffAndImageInput() {
        var transport = new BedrockCaptureTransport();
        var provider = new BedrockConverseStreamProvider(transport);

        var message = provider.stream(bedrockModel(), handoffContext(), StreamOptions.builder().build())
            .result()
            .join();

        assertThat(message.stopReason()).isEqualTo(StopReason.STOP);
        assertThat(transport.request).isNotNull();

        var body = transport.request.body();
        assertThat(body.toString()).contains("No result provided");
        assertThat(body.toString()).contains(IMAGE_DATA);
        assertThat(body.toString()).doesNotContain("Request was aborted");

        var assistantMessage = findFirst(body.path("messages"), item -> "assistant".equals(item.path("role").asText()));
        assertThat(assistantMessage.toString()).contains("\"toolUseId\":\"call-1_bad_item\"");

        var toolResultMessage = findFirst(
            body.path("messages"),
            item -> "user".equals(item.path("role").asText()) && item.toString().contains("\"toolResult\"")
        );
        assertThat(toolResultMessage.toString()).contains("\"toolUseId\":\"call-1_bad_item\"");

        var imageMessage = findLast(body.path("messages"), item -> "user".equals(item.path("role").asText()));
        assertThat(imageMessage.toString()).contains("\"format\":\"PNG\"");
    }

    @Test
    void providersMapAbortFailuresToAbortedStopReason() {
        assertThat(new OpenAiResponsesProvider((request, onEvent) -> {
            throw new IllegalStateException("Request was aborted");
        }).stream(openAiResponsesModel(), handoffContext(), apiKeyOptions()).result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.ABORTED);

        assertThat(new OpenAiCompletionsProvider((request, onEvent) -> {
            throw new IllegalStateException("Request was aborted");
        }).stream(openAiCompletionsModel(), handoffContext(), apiKeyOptions()).result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.ABORTED);

        assertThat(new AnthropicMessagesProvider((request, onEvent) -> {
            throw new IllegalStateException("Request was aborted");
        }).stream(anthropicModel(), handoffContext(), apiKeyOptions()).result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.ABORTED);

        assertThat(new GoogleGenerativeAiProvider((request, onEvent) -> {
            throw new IllegalStateException("Request was aborted");
        }).stream(googleModel(), handoffContext(), apiKeyOptions()).result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.ABORTED);

        assertThat(new BedrockConverseStreamProvider((request, onEvent) -> {
            throw new IllegalStateException("Request was aborted");
        }).stream(bedrockModel(), handoffContext(), StreamOptions.builder().build()).result())
            .succeedsWithin(Duration.ofSeconds(1))
            .matches(message -> message.stopReason() == StopReason.ABORTED);
    }

    private static StreamOptions apiKeyOptions() {
        return StreamOptions.builder().apiKey("test-key").build();
    }

    private static Context handoffContext() {
        return new Context(
            "You are a helpful coding agent.",
            List.of(
                new Message.UserMessage(List.of(new TextContent("Read README.md", null)), 10L),
                new Message.AssistantMessage(
                    List.of(
                        new ThinkingContent("Need to inspect README.md", "source-signature", false),
                        new ToolCall("call-1|bad/item", "read", JsonNodeFactory.instance.objectNode().put("path", "README.md"), "thought-signature")
                    ),
                    "source-api",
                    "source-provider",
                    "source-model",
                    zeroUsage(),
                    StopReason.TOOL_USE,
                    null,
                    20L
                ),
                new Message.UserMessage(List.of(new TextContent("Never mind, continue.", null)), 30L),
                new Message.AssistantMessage(
                    List.of(new TextContent("Request was aborted", null)),
                    "source-api",
                    "source-provider",
                    "source-model",
                    zeroUsage(),
                    StopReason.ABORTED,
                    "Request was aborted",
                    40L
                ),
                new Message.UserMessage(
                    List.of(
                        new TextContent("What is in this image?", null),
                        new ImageContent(IMAGE_DATA, "image/png")
                    ),
                    50L
                )
            ),
            List.of(new Tool("read", "Read a file from disk", JsonNodeFactory.instance.objectNode().put("type", "object")))
        );
    }

    private static Model openAiResponsesModel() {
        return new Model(
            "gpt-5.1-codex",
            "gpt-5.1-codex",
            "openai-responses",
            "openai",
            "https://api.openai.com/v1",
            false,
            List.of("text", "image"),
            zeroUsage().cost(),
            200_000,
            32_000,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Model openAiCompletionsModel() {
        return new Model(
            "gpt-4o-mini",
            "gpt-4o-mini",
            "openai-completions",
            "openai",
            "https://api.openai.com/v1",
            false,
            List.of("text", "image"),
            zeroUsage().cost(),
            200_000,
            32_000,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Model anthropicModel() {
        return new Model(
            "claude-3-7-sonnet-20250219",
            "claude-3-7-sonnet-20250219",
            "anthropic-messages",
            "anthropic",
            "https://api.anthropic.com/v1",
            false,
            List.of("text", "image"),
            zeroUsage().cost(),
            200_000,
            64_000,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Model googleModel() {
        return new Model(
            "gemini-2.5-pro",
            "gemini-2.5-pro",
            "google-generative-ai",
            "google",
            "https://generativelanguage.googleapis.com/v1beta",
            false,
            List.of("text", "image"),
            zeroUsage().cost(),
            1_048_576,
            65_536,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Model bedrockModel() {
        return new Model(
            "anthropic.claude-3-7-sonnet-20250219-v1:0",
            "anthropic.claude-3-7-sonnet-20250219-v1:0",
            "bedrock-converse-stream",
            "bedrock",
            "https://bedrock-runtime.us-west-2.amazonaws.com",
            false,
            List.of("text", "image"),
            zeroUsage().cost(),
            200_000,
            64_000,
            java.util.Map.of(),
            JsonNodeFactory.instance.objectNode()
        );
    }

    private static Usage zeroUsage() {
        return new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static JsonNode findFirst(JsonNode array, java.util.function.Predicate<JsonNode> predicate) {
        for (JsonNode item : array) {
            if (predicate.test(item)) {
                return item;
            }
        }
        throw new IllegalStateException("No matching node found: " + array);
    }

    private static JsonNode findLast(JsonNode array, java.util.function.Predicate<JsonNode> predicate) {
        JsonNode last = null;
        for (JsonNode item : array) {
            if (predicate.test(item)) {
                last = item;
            }
        }
        if (last == null) {
            throw new IllegalStateException("No matching node found: " + array);
        }
        return last;
    }

    private static final class OpenAiResponsesCaptureTransport implements OpenAiResponsesTransport {
        private OpenAiResponsesRequest request;

        @Override
        public void stream(OpenAiResponsesRequest request, java.util.function.Consumer<JsonNode> onEvent) {
            this.request = request;
            onEvent.accept(JsonNodeFactory.instance.objectNode()
                .put("type", "response.completed")
                .set("response", JsonNodeFactory.instance.objectNode()
                    .put("status", "completed")
                    .set("usage", JsonNodeFactory.instance.objectNode()
                        .put("input_tokens", 1)
                        .put("output_tokens", 1)
                        .put("total_tokens", 2)
                        .set("input_tokens_details", JsonNodeFactory.instance.objectNode().put("cached_tokens", 0)))));
        }
    }

    private static final class OpenAiCompletionsCaptureTransport implements OpenAiCompletionsTransport {
        private OpenAiCompletionsRequest request;

        @Override
        public void stream(OpenAiCompletionsRequest request, java.util.function.Consumer<JsonNode> onChunk) {
            this.request = request;
            var usage = JsonNodeFactory.instance.objectNode()
                .put("prompt_tokens", 1)
                .put("completion_tokens", 1);
            usage.set("prompt_tokens_details", JsonNodeFactory.instance.objectNode().put("cached_tokens", 0));
            usage.set("completion_tokens_details", JsonNodeFactory.instance.objectNode().put("reasoning_tokens", 0));
            var chunk = JsonNodeFactory.instance.objectNode();
            chunk.set("usage", usage);
            chunk.set("choices", JsonNodeFactory.instance.arrayNode().add(
                JsonNodeFactory.instance.objectNode()
                    .put("finish_reason", "stop")
                    .set("delta", JsonNodeFactory.instance.objectNode())
            ));

            onChunk.accept(chunk);
        }
    }

    private static final class AnthropicCaptureTransport implements AnthropicMessagesTransport {
        private AnthropicMessagesRequest request;

        @Override
        public void stream(AnthropicMessagesRequest request, java.util.function.Consumer<JsonNode> onEvent) {
            this.request = request;
            onEvent.accept(json("""
                {"type":"message_start","message":{"usage":{"input_tokens":1,"output_tokens":0,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}}
                """));
            onEvent.accept(json("""
                {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":1,"output_tokens":1,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}
                """));
            onEvent.accept(json("""
                {"type":"message_stop"}
                """));
        }
    }

    private static final class GoogleCaptureTransport implements GoogleGenerativeAiTransport {
        private GoogleGenerativeAiRequest request;

        @Override
        public void stream(GoogleGenerativeAiRequest request, java.util.function.Consumer<JsonNode> onEvent) {
            this.request = request;
            onEvent.accept(json("""
                {
                  "candidates": [{"finishReason": "STOP"}],
                  "usageMetadata": {
                    "promptTokenCount": 1,
                    "candidatesTokenCount": 1,
                    "thoughtsTokenCount": 0,
                    "cachedContentTokenCount": 0,
                    "totalTokenCount": 2
                  }
                }
                """));
        }
    }

    private static final class BedrockCaptureTransport implements BedrockConverseStreamTransport {
        private BedrockConverseStreamRequest request;

        @Override
        public void stream(BedrockConverseStreamRequest request, java.util.function.Consumer<JsonNode> onEvent) {
            this.request = request;
            onEvent.accept(json("""
                {"type":"messageStart","role":"assistant"}
                """));
            onEvent.accept(json("""
                {"type":"messageStop","stopReason":"END_TURN"}
                """));
            onEvent.accept(json("""
                {"type":"metadata","usage":{"inputTokens":1,"outputTokens":1,"cacheReadInputTokens":0,"cacheWriteInputTokens":0,"totalTokens":2}}
                """));
        }
    }

    private static JsonNode json(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
