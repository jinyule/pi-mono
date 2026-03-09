package dev.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.pi.ai.model.AssistantContent;
import dev.pi.ai.model.CacheRetention;
import dev.pi.ai.model.Context;
import dev.pi.ai.model.ImageContent;
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
import dev.pi.ai.model.UserContent;
import dev.pi.ai.provider.ApiProvider;
import dev.pi.ai.stream.AssistantMessageAssembler;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class OpenAiResponsesProvider implements ApiProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenAiResponsesTransport transport;
    private final Clock clock;

    public OpenAiResponsesProvider() {
        this(new HttpOpenAiResponsesTransport(), Clock.systemUTC());
    }

    public OpenAiResponsesProvider(OpenAiResponsesTransport transport) {
        this(transport, Clock.systemUTC());
    }

    OpenAiResponsesProvider(OpenAiResponsesTransport transport, Clock clock) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String api() {
        return "openai-responses";
    }

    @Override
    public AssistantMessageEventStream stream(
        Model model,
        Context context,
        StreamOptions options
    ) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? StreamOptions.builder().build() : options;
        requireApiKey(effectiveOptions.apiKey(), model.provider());

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("openai-responses-provider").start(() ->
            executeStream(model, context, effectiveOptions, null, stream)
        );
        return stream;
    }

    @Override
    public AssistantMessageEventStream streamSimple(
        Model model,
        Context context,
        SimpleStreamOptions options
    ) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? SimpleStreamOptions.builder().build() : options;
        requireApiKey(effectiveOptions.apiKey(), model.provider());

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("openai-responses-provider").start(() ->
            executeStream(model, context, effectiveOptions, effectiveOptions.reasoning(), stream)
        );
        return stream;
    }

    private void executeStream(
        Model model,
        Context context,
        StreamOptions options,
        ThinkingLevel reasoningEffort,
        AssistantMessageEventStream stream
    ) {
        var assembler = new AssistantMessageAssembler(model, clock.millis());
        stream.push(assembler.start());

        try {
            var request = buildRequest(model, context, options, reasoningEffort);
            var state = new ProcessingState();

            transport.stream(request, event -> processEvent(event, assembler, stream, state));

            if (!state.completed) {
                throw new IllegalStateException("OpenAI Responses stream ended before response.completed");
            }
        } catch (Exception exception) {
            if (!stream.isClosed()) {
                var partial = assembler.partial();
                var reason = exception.getMessage() != null && exception.getMessage().toLowerCase(Locale.ROOT).contains("abort")
                    ? StopReason.ABORTED
                    : StopReason.ERROR;
                stream.push(assembler.error(reason, partial.usage(), exception.getMessage()));
            }
        }
    }

    private OpenAiResponsesTransport.OpenAiResponsesRequest buildRequest(
        Model model,
        Context context,
        StreamOptions options,
        ThinkingLevel reasoningEffort
    ) {
        var payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", model.id());
        payload.set("input", convertMessages(model, context));
        payload.put("stream", true);
        payload.put("store", false);

        if (options.cacheRetention() != CacheRetention.NONE && options.sessionId() != null) {
            payload.put("prompt_cache_key", options.sessionId());
        }
        if (options.cacheRetention() == CacheRetention.LONG && model.baseUrl().contains("api.openai.com")) {
            payload.put("prompt_cache_retention", "24h");
        }

        if (options.maxTokens() != null) {
            payload.put("max_output_tokens", options.maxTokens());
        }
        if (options.temperature() != null) {
            payload.put("temperature", options.temperature());
        }
        if (!context.tools().isEmpty()) {
            payload.set("tools", convertTools(context.tools()));
        }

        if (model.reasoning() && reasoningEffort != null) {
            var reasoning = payload.putObject("reasoning");
            reasoning.put("effort", reasoningEffort.value());
            reasoning.put("summary", "auto");
            payload.set("include", OBJECT_MAPPER.createArrayNode().add("reasoning.encrypted_content"));
        } else if (model.reasoning() && model.id().startsWith("gpt-5")) {
            appendZeroJuiceDeveloperMessage((ArrayNode) payload.get("input"));
        }

        return new OpenAiResponsesTransport.OpenAiResponsesRequest(
            resolveResponsesUri(model.baseUrl()),
            options.apiKey(),
            model.headers(),
            payload
        );
    }

    private ArrayNode convertMessages(Model model, Context context) {
        var input = OBJECT_MAPPER.createArrayNode();

        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            var role = model.reasoning() ? "developer" : "system";
            input.add(OBJECT_MAPPER.createObjectNode()
                .put("role", role)
                .put("content", context.systemPrompt()));
        }

        for (Message message : context.messages()) {
            switch (message) {
                case Message.UserMessage userMessage -> {
                    var content = convertUserContent(model, userMessage.content());
                    if (!content.isEmpty()) {
                        input.add(OBJECT_MAPPER.createObjectNode()
                            .put("role", "user")
                            .set("content", content));
                    }
                }
                case Message.AssistantMessage assistantMessage -> convertAssistantMessage(input, assistantMessage);
                case Message.ToolResultMessage toolResultMessage -> convertToolResultMessage(input, model, toolResultMessage);
            }
        }

        return input;
    }

    private ArrayNode convertUserContent(Model model, List<UserContent> blocks) {
        var content = OBJECT_MAPPER.createArrayNode();
        for (UserContent block : blocks) {
            switch (block) {
                case TextContent textContent -> content.add(OBJECT_MAPPER.createObjectNode()
                    .put("type", "input_text")
                    .put("text", textContent.text()));
                case ImageContent imageContent -> {
                    if (model.input().contains("image")) {
                        content.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "input_image")
                            .put("detail", "auto")
                            .put("image_url", "data:%s;base64,%s".formatted(imageContent.mimeType(), imageContent.data())));
                    }
                }
                default -> {
                }
            }
        }
        return content;
    }

    private void convertAssistantMessage(ArrayNode input, Message.AssistantMessage message) {
        int textIndex = 0;
        for (AssistantContent block : message.content()) {
            switch (block) {
                case ThinkingContent thinkingContent -> {
                    if (thinkingContent.thinkingSignature() != null && !thinkingContent.thinkingSignature().isBlank()) {
                        try {
                            input.add(OBJECT_MAPPER.readTree(thinkingContent.thinkingSignature()));
                        } catch (Exception ignored) {
                        }
                    }
                }
                case TextContent textContent -> {
                    var outputText = OBJECT_MAPPER.createObjectNode()
                        .put("type", "output_text")
                        .put("text", textContent.text());
                    outputText.set("annotations", OBJECT_MAPPER.createArrayNode());

                    var item = OBJECT_MAPPER.createObjectNode()
                        .put("type", "message")
                        .put("role", "assistant")
                        .put("status", "completed")
                        .put("id", textContent.textSignature() == null ? "msg_" + textIndex++ : textContent.textSignature());
                    item.set("content", OBJECT_MAPPER.createArrayNode().add(outputText));
                    input.add(item);
                }
                case ToolCall toolCall -> {
                    var ids = splitToolCallId(toolCall.id());
                    var item = OBJECT_MAPPER.createObjectNode()
                        .put("type", "function_call")
                        .put("call_id", ids.callId())
                        .put("name", toolCall.name())
                        .put("arguments", stringifyJson(toolCall.arguments()));
                    if (ids.itemId() != null) {
                        item.put("id", ids.itemId());
                    }
                    input.add(item);
                }
                default -> {
                }
            }
        }
    }

    private void convertToolResultMessage(ArrayNode input, Model model, Message.ToolResultMessage message) {
        var joinedText = message.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");

        input.add(OBJECT_MAPPER.createObjectNode()
            .put("type", "function_call_output")
            .put("call_id", splitToolCallId(message.toolCallId()).callId())
            .put("output", joinedText.isBlank() ? "(see attached image)" : joinedText));

        var hasImages = message.content().stream().anyMatch(ImageContent.class::isInstance);
        if (!hasImages || !model.input().contains("image")) {
            return;
        }

        var content = OBJECT_MAPPER.createArrayNode();
        content.add(OBJECT_MAPPER.createObjectNode()
            .put("type", "input_text")
            .put("text", "Attached image(s) from tool result:"));
        for (UserContent block : message.content()) {
            if (block instanceof ImageContent imageContent) {
                content.add(OBJECT_MAPPER.createObjectNode()
                    .put("type", "input_image")
                    .put("detail", "auto")
                    .put("image_url", "data:%s;base64,%s".formatted(imageContent.mimeType(), imageContent.data())));
            }
        }
        input.add(OBJECT_MAPPER.createObjectNode()
            .put("role", "user")
            .set("content", content));
    }

    private ArrayNode convertTools(List<Tool> tools) {
        var array = OBJECT_MAPPER.createArrayNode();
        for (Tool tool : tools) {
            var item = OBJECT_MAPPER.createObjectNode()
                .put("type", "function")
                .put("name", tool.name())
                .put("description", tool.description())
                .put("strict", false);
            item.set("parameters", tool.parametersSchema().deepCopy());
            array.add(item);
        }
        return array;
    }

    private void processEvent(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var type = event.path("type").asText();
        switch (type) {
            case "response.output_item.added" -> handleOutputItemAdded(event.path("item"), assembler, stream, state);
            case "response.reasoning_summary_part.added", "response.content_part.added" -> {
            }
            case "response.reasoning_summary_text.delta" -> {
                if (state.currentBlock != null && "reasoning".equals(state.currentBlock.type())) {
                    stream.push(assembler.appendThinking(state.currentBlock.contentIndex(), event.path("delta").asText("")));
                }
            }
            case "response.reasoning_summary_part.done" -> {
                if (state.currentBlock != null && "reasoning".equals(state.currentBlock.type())) {
                    stream.push(assembler.appendThinking(state.currentBlock.contentIndex(), "\n\n"));
                }
            }
            case "response.output_text.delta", "response.refusal.delta" -> {
                if (state.currentBlock != null && "message".equals(state.currentBlock.type())) {
                    stream.push(assembler.appendText(state.currentBlock.contentIndex(), event.path("delta").asText("")));
                }
            }
            case "response.function_call_arguments.delta" -> {
                if (state.currentBlock != null && "function_call".equals(state.currentBlock.type())) {
                    stream.push(assembler.appendToolCallArguments(state.currentBlock.contentIndex(), event.path("delta").asText("")));
                }
            }
            case "response.function_call_arguments.done" -> {
                if (state.currentBlock != null && "function_call".equals(state.currentBlock.type())) {
                    assembler.replaceToolCallArguments(state.currentBlock.contentIndex(), event.path("arguments").asText(""));
                }
            }
            case "response.output_item.done" -> handleOutputItemDone(event.path("item"), assembler, stream, state);
            case "response.completed" -> {
                var response = event.path("response");
                var stopReason = mapStopReason(response.path("status").asText());
                if (state.sawToolCall && stopReason == StopReason.STOP) {
                    stopReason = StopReason.TOOL_USE;
                }
                if (stopReason == StopReason.ERROR || stopReason == StopReason.ABORTED) {
                    throw new IllegalStateException("OpenAI Responses request did not complete successfully");
                }
                stream.push(assembler.done(stopReason, mapUsage(response.path("usage"))));
                state.completed = true;
            }
            case "error" -> throw new IllegalStateException(
                "Error Code " + event.path("code").asText("unknown") + ": " + event.path("message").asText("Unknown error")
            );
            case "response.failed" -> throw new IllegalStateException("OpenAI Responses request failed");
            default -> {
            }
        }
    }

    private void handleOutputItemAdded(
        JsonNode item,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var contentIndex = assembler.partial().content().size();
        switch (item.path("type").asText()) {
            case "reasoning" -> {
                stream.push(assembler.startThinking(contentIndex));
                state.currentBlock = new CurrentBlock("reasoning", contentIndex);
            }
            case "message" -> {
                stream.push(assembler.startText(contentIndex));
                state.currentBlock = new CurrentBlock("message", contentIndex);
            }
            case "function_call" -> {
                var callId = item.path("call_id").asText();
                var itemId = item.path("id").asText(null);
                var toolCallId = itemId == null || itemId.isBlank() ? callId : callId + "|" + itemId;
                stream.push(assembler.startToolCall(contentIndex, toolCallId, item.path("name").asText()));
                var initialArguments = item.path("arguments").asText("");
                if (!initialArguments.isBlank()) {
                    assembler.replaceToolCallArguments(contentIndex, initialArguments);
                }
                state.currentBlock = new CurrentBlock("function_call", contentIndex);
                state.sawToolCall = true;
            }
            default -> state.currentBlock = null;
        }
    }

    private void handleOutputItemDone(
        JsonNode item,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        if (state.currentBlock == null) {
            return;
        }

        switch (item.path("type").asText()) {
            case "reasoning" -> {
                stream.push(assembler.endThinking(state.currentBlock.contentIndex(), stringifyJson(item)));
                state.currentBlock = null;
            }
            case "message" -> {
                stream.push(assembler.endText(state.currentBlock.contentIndex(), item.path("id").asText(null)));
                state.currentBlock = null;
            }
            case "function_call" -> {
                assembler.replaceToolCallArguments(state.currentBlock.contentIndex(), item.path("arguments").asText(""));
                stream.push(assembler.endToolCall(state.currentBlock.contentIndex()));
                state.currentBlock = null;
            }
            default -> {
            }
        }
    }

    private static Usage mapUsage(JsonNode usageNode) {
        var cachedTokens = usageNode.path("input_tokens_details").path("cached_tokens").asInt(0);
        var inputTokens = usageNode.path("input_tokens").asInt(0);
        var outputTokens = usageNode.path("output_tokens").asInt(0);
        return new Usage(
            Math.max(0, inputTokens - cachedTokens),
            outputTokens,
            cachedTokens,
            0,
            usageNode.path("total_tokens").asInt(inputTokens + outputTokens),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)
        );
    }

    private static StopReason mapStopReason(String status) {
        return switch (status) {
            case "", "completed", "queued", "in_progress" -> StopReason.STOP;
            case "incomplete" -> StopReason.LENGTH;
            case "failed", "cancelled" -> StopReason.ERROR;
            default -> throw new IllegalArgumentException("Unhandled OpenAI Responses status: " + status);
        };
    }

    private static URI resolveResponsesUri(String baseUrl) {
        var normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(normalized + "responses");
    }

    private static void appendZeroJuiceDeveloperMessage(ArrayNode input) {
        input.add(OBJECT_MAPPER.createObjectNode()
            .put("role", "developer")
            .set("content", OBJECT_MAPPER.createArrayNode().add(
                OBJECT_MAPPER.createObjectNode()
                    .put("type", "input_text")
                    .put("text", "# Juice: 0 !important")
            )));
    }

    private static ToolCallIds splitToolCallId(String value) {
        var separator = value.indexOf('|');
        if (separator < 0) {
            return new ToolCallIds(value, null);
        }
        return new ToolCallIds(value.substring(0, separator), value.substring(separator + 1));
    }

    private static String stringifyJson(JsonNode jsonNode) {
        try {
            return OBJECT_MAPPER.writeValueAsString(jsonNode);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private static void requireApiKey(String apiKey, String provider) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("No API key for provider: " + provider);
        }
    }

    private record ToolCallIds(String callId, String itemId) {}

    private record CurrentBlock(String type, int contentIndex) {}

    private static final class ProcessingState {
        private CurrentBlock currentBlock;
        private boolean sawToolCall;
        private boolean completed;
    }
}
