package dev.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.ai.model.AssistantContent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class OpenAiCompletionsProvider implements ApiProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenAiCompletionsTransport transport;
    private final Clock clock;

    public OpenAiCompletionsProvider() {
        this(new HttpOpenAiCompletionsTransport(), Clock.systemUTC());
    }

    public OpenAiCompletionsProvider(OpenAiCompletionsTransport transport) {
        this(transport, Clock.systemUTC());
    }

    OpenAiCompletionsProvider(OpenAiCompletionsTransport transport, Clock clock) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String api() {
        return "openai-completions";
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? StreamOptions.builder().build() : options;
        requireApiKey(effectiveOptions.apiKey(), model.provider());

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("openai-completions-provider").start(() ->
            executeStream(model, context, effectiveOptions, null, stream)
        );
        return stream;
    }

    @Override
    public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? SimpleStreamOptions.builder().build() : options;
        requireApiKey(effectiveOptions.apiKey(), model.provider());

        var reasoning = supportsXhigh(model) ? effectiveOptions.reasoning() : clampReasoning(effectiveOptions.reasoning());
        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("openai-completions-provider").start(() ->
            executeStream(model, context, effectiveOptions, reasoning, stream)
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

            transport.stream(request, chunk -> processChunk(chunk, assembler, stream, state));

            finishOpenBlocks(assembler, stream, state);

            if (state.stopReason == StopReason.ABORTED || state.stopReason == StopReason.ERROR) {
                throw new IllegalStateException("OpenAI Completions request did not complete successfully");
            }

            stream.push(assembler.done(state.stopReason, state.usage));
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

    private OpenAiCompletionsTransport.OpenAiCompletionsRequest buildRequest(
        Model model,
        Context context,
        StreamOptions options,
        ThinkingLevel reasoningEffort
    ) {
        var compat = resolveCompat(model);
        var payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", model.id());
        payload.set("messages", convertMessages(model, context, compat));
        payload.put("stream", true);

        if (compat.supportsUsageInStreaming()) {
            payload.set("stream_options", OBJECT_MAPPER.createObjectNode().put("include_usage", true));
        }
        if (compat.supportsStore()) {
            payload.put("store", false);
        }

        if (options.maxTokens() != null) {
            payload.put(compat.maxTokensField(), options.maxTokens());
        }
        if (options.temperature() != null) {
            payload.put("temperature", options.temperature());
        }

        if (!context.tools().isEmpty()) {
            payload.set("tools", convertTools(context.tools(), compat));
        }

        if (reasoningEffort != null && model.reasoning()) {
            if ("zai".equals(compat.thinkingFormat()) || "qwen".equals(compat.thinkingFormat())) {
                payload.put("enable_thinking", true);
            } else if (compat.supportsReasoningEffort()) {
                payload.put("reasoning_effort", reasoningEffort.value());
            }
        }

        return new OpenAiCompletionsTransport.OpenAiCompletionsRequest(
            resolveCompletionsUri(model.baseUrl()),
            options.apiKey(),
            model.headers(),
            payload
        );
    }

    private ArrayNode convertMessages(Model model, Context context, Compat compat) {
        var messages = OBJECT_MAPPER.createArrayNode();
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            var role = model.reasoning() && compat.supportsDeveloperRole() ? "developer" : "system";
            messages.add(OBJECT_MAPPER.createObjectNode()
                .put("role", role)
                .put("content", context.systemPrompt()));
        }

        String lastRole = null;
        for (Message message : context.messages()) {
            if (compat.requiresAssistantAfterToolResult() && "toolResult".equals(lastRole) && message instanceof Message.UserMessage) {
                messages.add(OBJECT_MAPPER.createObjectNode()
                    .put("role", "assistant")
                    .put("content", "I have processed the tool results."));
            }

            switch (message) {
                case Message.UserMessage userMessage -> messages.add(convertUserMessage(model, userMessage));
                case Message.AssistantMessage assistantMessage -> {
                    var converted = convertAssistantMessage(model, assistantMessage, compat);
                    if (converted != null) {
                        messages.add(converted);
                    }
                }
                case Message.ToolResultMessage toolResultMessage -> {
                    convertToolResultMessage(messages, model, toolResultMessage, compat);
                }
            }
            lastRole = message.role();
        }

        return messages;
    }

    private ObjectNode convertUserMessage(Model model, Message.UserMessage userMessage) {
        if (userMessage.content().size() == 1 && userMessage.content().get(0) instanceof TextContent textContent) {
            return OBJECT_MAPPER.createObjectNode()
                .put("role", "user")
                .put("content", textContent.text());
        }

        var content = OBJECT_MAPPER.createArrayNode();
        for (UserContent block : userMessage.content()) {
            switch (block) {
                case TextContent textContent -> content.add(OBJECT_MAPPER.createObjectNode()
                    .put("type", "text")
                    .put("text", textContent.text()));
                case ImageContent imageContent -> {
                    if (model.input().contains("image")) {
                        content.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "image_url")
                            .set("image_url", OBJECT_MAPPER.createObjectNode()
                                .put("url", "data:%s;base64,%s".formatted(imageContent.mimeType(), imageContent.data()))));
                    }
                }
                default -> {
                }
            }
        }
        return OBJECT_MAPPER.createObjectNode()
            .put("role", "user")
            .set("content", content);
    }

    private ObjectNode convertAssistantMessage(Model model, Message.AssistantMessage assistantMessage, Compat compat) {
        var node = OBJECT_MAPPER.createObjectNode().put("role", "assistant");
        if (compat.requiresAssistantAfterToolResult()) {
            node.put("content", "");
        } else {
            node.putNull("content");
        }

        var contentParts = OBJECT_MAPPER.createArrayNode();
        for (AssistantContent block : assistantMessage.content()) {
            switch (block) {
                case TextContent textContent -> {
                    if (!textContent.text().isBlank()) {
                        contentParts.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "text")
                            .put("text", textContent.text()));
                    }
                }
                case ThinkingContent thinkingContent -> {
                    if (thinkingContent.thinking().isBlank()) {
                        continue;
                    }
                    if (compat.requiresThinkingAsText()) {
                        contentParts.insert(0, OBJECT_MAPPER.createObjectNode()
                            .put("type", "text")
                            .put("text", thinkingContent.thinking()));
                    } else if (thinkingContent.thinkingSignature() != null && !thinkingContent.thinkingSignature().isBlank()) {
                        node.put(thinkingContent.thinkingSignature(), thinkingContent.thinking());
                    }
                }
                case ToolCall toolCall -> {
                    var toolCalls = node.withArray("tool_calls");
                    toolCalls.add(OBJECT_MAPPER.createObjectNode()
                        .put("id", normalizeToolCallId(toolCall.id(), model, compat))
                        .put("type", "function")
                        .set("function", OBJECT_MAPPER.createObjectNode()
                            .put("name", toolCall.name())
                            .put("arguments", stringifyJson(toolCall.arguments()))));
                }
                default -> {
                }
            }
        }

        if (!contentParts.isEmpty()) {
            node.set("content", model.provider().equals("github-copilot")
                ? OBJECT_MAPPER.getNodeFactory().textNode(joinTextParts(contentParts))
                : contentParts);
        }

        var hasContent = !node.path("content").isNull() && !node.path("content").isMissingNode() &&
            (!(node.path("content").isTextual()) || !node.path("content").asText().isEmpty()) &&
            (!(node.path("content").isArray()) || !node.path("content").isEmpty());
        if (!hasContent && node.path("tool_calls").isMissingNode()) {
            return null;
        }
        return node;
    }

    private void convertToolResultMessage(ArrayNode messages, Model model, Message.ToolResultMessage toolResultMessage, Compat compat) {
        var textResult = toolResultMessage.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");

        var toolNode = OBJECT_MAPPER.createObjectNode()
            .put("role", "tool")
            .put("content", textResult.isBlank() ? "(see attached image)" : textResult)
            .put("tool_call_id", normalizeToolCallId(toolResultMessage.toolCallId(), model, compat));
        if (compat.requiresToolResultName() && toolResultMessage.toolName() != null) {
            toolNode.put("name", toolResultMessage.toolName());
        }
        messages.add(toolNode);

        var hasImages = toolResultMessage.content().stream().anyMatch(ImageContent.class::isInstance);
        if (!hasImages || !model.input().contains("image")) {
            return;
        }

        if (compat.requiresAssistantAfterToolResult()) {
            messages.add(OBJECT_MAPPER.createObjectNode()
                .put("role", "assistant")
                .put("content", "I have processed the tool results."));
        }

        var content = OBJECT_MAPPER.createArrayNode();
        content.add(OBJECT_MAPPER.createObjectNode().put("type", "text").put("text", "Attached image(s) from tool result:"));
        for (UserContent block : toolResultMessage.content()) {
            if (block instanceof ImageContent imageContent) {
                content.add(OBJECT_MAPPER.createObjectNode()
                    .put("type", "image_url")
                    .set("image_url", OBJECT_MAPPER.createObjectNode()
                        .put("url", "data:%s;base64,%s".formatted(imageContent.mimeType(), imageContent.data()))));
            }
        }
        messages.add(OBJECT_MAPPER.createObjectNode().put("role", "user").set("content", content));
    }

    private ArrayNode convertTools(List<Tool> tools, Compat compat) {
        var array = OBJECT_MAPPER.createArrayNode();
        for (Tool tool : tools) {
            var functionNode = OBJECT_MAPPER.createObjectNode();
            functionNode.put("name", tool.name());
            functionNode.put("description", tool.description());
            functionNode.set("parameters", tool.parametersSchema().deepCopy());
            if (compat.supportsStrictMode()) {
                functionNode.put("strict", false);
            }
            array.add(OBJECT_MAPPER.createObjectNode()
                .put("type", "function")
                .set("function", functionNode));
        }
        return array;
    }

    private void processChunk(
        JsonNode chunk,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        if (chunk.path("usage").isObject()) {
            state.usage = mapUsage(chunk.path("usage"));
        }

        var choice = chunk.path("choices").isArray() && chunk.path("choices").size() > 0 ? chunk.path("choices").get(0) : null;
        if (choice == null || choice.isMissingNode()) {
            return;
        }

        var finishReason = choice.path("finish_reason");
        if (!finishReason.isMissingNode() && !finishReason.isNull()) {
            state.stopReason = mapStopReason(finishReason.asText());
        }

        var delta = choice.path("delta");
        if (!delta.isObject()) {
            return;
        }

        var content = delta.path("content");
        if (!content.isMissingNode() && !content.isNull() && !content.asText().isEmpty()) {
            switchToText(assembler, stream, state);
            stream.push(assembler.appendText(state.currentTextIndex, content.asText()));
        }

        var reasoningDelta = firstReasoningDelta(delta);
        if (reasoningDelta != null && !reasoningDelta.isEmpty()) {
            switchToThinking(assembler, stream, state, delta);
            stream.push(assembler.appendThinking(state.currentThinkingIndex, reasoningDelta));
        }

        var toolCalls = delta.path("tool_calls");
        if (toolCalls.isArray()) {
            finishOpenTextAndThinking(assembler, stream, state);
            for (JsonNode toolCallDelta : toolCalls) {
                processToolCallDelta(toolCallDelta, chunk, assembler, stream, state);
            }
        }
    }

    private void switchToText(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        if (state.currentTextIndex != null) {
            return;
        }
        finishOpenThinking(assembler, stream, state);
        finishOpenToolCalls(assembler, stream, state);
        state.currentTextIndex = assembler.partial().content().size();
        stream.push(assembler.startText(state.currentTextIndex));
    }

    private void switchToThinking(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state,
        JsonNode delta
    ) {
        if (state.currentThinkingIndex != null) {
            return;
        }
        finishOpenText(assembler, stream, state);
        finishOpenToolCalls(assembler, stream, state);
        state.currentThinkingIndex = assembler.partial().content().size();
        stream.push(assembler.startThinking(state.currentThinkingIndex));
    }

    private void processToolCallDelta(
        JsonNode toolCallDelta,
        JsonNode chunk,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        int slot = toolCallDelta.path("index").asInt(state.toolCallSlots.size());
        Integer contentIndex = state.toolCallSlots.get(slot);
        if (contentIndex == null) {
            contentIndex = assembler.partial().content().size();
            var id = toolCallDelta.path("id").asText();
            var name = toolCallDelta.path("function").path("name").asText();
            stream.push(assembler.startToolCall(contentIndex, id, name));
            state.toolCallSlots.put(slot, contentIndex);
        }

        var argumentsDelta = toolCallDelta.path("function").path("arguments").asText("");
        stream.push(assembler.appendToolCallArguments(contentIndex, argumentsDelta));

        var reasoningDetails = chunk.path("choices").get(0).path("delta").path("reasoning_details");
        if (reasoningDetails.isArray()) {
            for (JsonNode detail : reasoningDetails) {
                if ("reasoning.encrypted".equals(detail.path("type").asText())
                    && detail.path("id").asText("").equals(toolCallDelta.path("id").asText(""))) {
                    assembler.replaceToolCallArguments(contentIndex, assembler.partial().content().get(contentIndex) instanceof ToolCall toolCall
                        ? stringifyJson(toolCall.arguments())
                        : "{}");
                }
            }
        }
    }

    private void finishOpenBlocks(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        finishOpenThinking(assembler, stream, state);
        finishOpenText(assembler, stream, state);
        finishOpenToolCalls(assembler, stream, state);
    }

    private void finishOpenTextAndThinking(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        finishOpenThinking(assembler, stream, state);
        finishOpenText(assembler, stream, state);
    }

    private void finishOpenText(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        if (state.currentTextIndex != null) {
            stream.push(assembler.endText(state.currentTextIndex, null));
            state.currentTextIndex = null;
        }
    }

    private void finishOpenThinking(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        if (state.currentThinkingIndex != null) {
            stream.push(assembler.endThinking(state.currentThinkingIndex, state.currentThinkingSignature));
            state.currentThinkingIndex = null;
            state.currentThinkingSignature = null;
        }
    }

    private void finishOpenToolCalls(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        for (Integer contentIndex : state.toolCallSlots.values()) {
            stream.push(assembler.endToolCall(contentIndex));
        }
        if (!state.toolCallSlots.isEmpty()) {
            state.stopReason = StopReason.TOOL_USE;
        }
        state.toolCallSlots.clear();
    }

    private static Usage mapUsage(JsonNode usageNode) {
        var cachedTokens = usageNode.path("prompt_tokens_details").path("cached_tokens").asInt(0);
        var reasoningTokens = usageNode.path("completion_tokens_details").path("reasoning_tokens").asInt(0);
        var inputTokens = usageNode.path("prompt_tokens").asInt(0) - cachedTokens;
        var outputTokens = usageNode.path("completion_tokens").asInt(0) + reasoningTokens;
        return new Usage(
            Math.max(0, inputTokens),
            outputTokens,
            cachedTokens,
            0,
            inputTokens + outputTokens + cachedTokens,
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)
        );
    }

    private static StopReason mapStopReason(String reason) {
        return switch (reason) {
            case "", "stop" -> StopReason.STOP;
            case "length" -> StopReason.LENGTH;
            case "function_call", "tool_calls" -> StopReason.TOOL_USE;
            case "content_filter" -> StopReason.ERROR;
            default -> throw new IllegalArgumentException("Unhandled stop reason: " + reason);
        };
    }

    private static String firstReasoningDelta(JsonNode delta) {
        for (var fieldName : List.of("reasoning_content", "reasoning", "reasoning_text")) {
            var value = delta.path(fieldName);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isEmpty()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String clampThinkingFormat(String value) {
        return value == null || value.isBlank() ? "openai" : value;
    }

    private static ThinkingLevel clampReasoning(ThinkingLevel effort) {
        return effort == ThinkingLevel.XHIGH ? ThinkingLevel.HIGH : effort;
    }

    private static boolean supportsXhigh(Model model) {
        return model.id().contains("gpt-5.2") || model.id().contains("gpt-5.3");
    }

    private static String joinTextParts(ArrayNode contentParts) {
        var builder = new StringBuilder();
        for (JsonNode part : contentParts) {
            if (part.path("type").asText().equals("text")) {
                builder.append(part.path("text").asText());
            }
        }
        return builder.toString();
    }

    private static String normalizeToolCallId(String id, Model model, Compat compat) {
        if (compat.requiresMistralToolIds()) {
            return normalizeMistralToolId(id);
        }
        if (id.contains("|")) {
            var callId = id.substring(0, id.indexOf('|'));
            return callId.replaceAll("[^a-zA-Z0-9_-]", "_").substring(0, Math.min(40, callId.length()));
        }
        if ("openai".equals(model.provider()) && id.length() > 40) {
            return id.substring(0, 40);
        }
        return id;
    }

    private static String normalizeMistralToolId(String id) {
        var normalized = id.replaceAll("[^a-zA-Z0-9]", "");
        if (normalized.length() < 9) {
            normalized = normalized + "ABCDEFGHI".substring(0, 9 - normalized.length());
        } else if (normalized.length() > 9) {
            normalized = normalized.substring(0, 9);
        }
        return normalized;
    }

    private static Compat resolveCompat(Model model) {
        var detected = detectCompat(model);
        var compat = model.compat();
        if (compat == null || compat.isNull()) {
            return detected;
        }
        return new Compat(
            booleanCompat(compat, "supportsStore", detected.supportsStore()),
            booleanCompat(compat, "supportsDeveloperRole", detected.supportsDeveloperRole()),
            booleanCompat(compat, "supportsReasoningEffort", detected.supportsReasoningEffort()),
            booleanCompat(compat, "supportsUsageInStreaming", detected.supportsUsageInStreaming()),
            stringCompat(compat, "maxTokensField", detected.maxTokensField()),
            booleanCompat(compat, "requiresToolResultName", detected.requiresToolResultName()),
            booleanCompat(compat, "requiresAssistantAfterToolResult", detected.requiresAssistantAfterToolResult()),
            booleanCompat(compat, "requiresThinkingAsText", detected.requiresThinkingAsText()),
            booleanCompat(compat, "requiresMistralToolIds", detected.requiresMistralToolIds()),
            clampThinkingFormat(stringCompat(compat, "thinkingFormat", detected.thinkingFormat())),
            booleanCompat(compat, "supportsStrictMode", detected.supportsStrictMode())
        );
    }

    private static Compat detectCompat(Model model) {
        var provider = model.provider();
        var baseUrl = model.baseUrl();
        var isZai = "zai".equals(provider) || baseUrl.contains("api.z.ai");
        var isNonStandard =
            "cerebras".equals(provider) ||
            baseUrl.contains("cerebras.ai") ||
            "xai".equals(provider) ||
            baseUrl.contains("api.x.ai") ||
            "mistral".equals(provider) ||
            baseUrl.contains("mistral.ai") ||
            baseUrl.contains("chutes.ai") ||
            baseUrl.contains("deepseek.com") ||
            isZai ||
            "opencode".equals(provider) ||
            baseUrl.contains("opencode.ai");
        var isGrok = "xai".equals(provider) || baseUrl.contains("api.x.ai");
        var useMaxTokens = "mistral".equals(provider) || baseUrl.contains("mistral.ai") || baseUrl.contains("chutes.ai");
        var isMistral = "mistral".equals(provider) || baseUrl.contains("mistral.ai");
        return new Compat(
            !isNonStandard,
            !isNonStandard,
            !isGrok && !isZai,
            true,
            useMaxTokens ? "max_tokens" : "max_completion_tokens",
            isMistral,
            false,
            isMistral,
            isMistral,
            isZai ? "zai" : "openai",
            true
        );
    }

    private static boolean booleanCompat(JsonNode compat, String field, boolean fallback) {
        var value = compat.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asBoolean();
    }

    private static String stringCompat(JsonNode compat, String field, String fallback) {
        var value = compat.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    private static URI resolveCompletionsUri(String baseUrl) {
        var normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(normalized + "chat/completions");
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

    private record Compat(
        boolean supportsStore,
        boolean supportsDeveloperRole,
        boolean supportsReasoningEffort,
        boolean supportsUsageInStreaming,
        String maxTokensField,
        boolean requiresToolResultName,
        boolean requiresAssistantAfterToolResult,
        boolean requiresThinkingAsText,
        boolean requiresMistralToolIds,
        String thinkingFormat,
        boolean supportsStrictMode
    ) {}

    private static final class ProcessingState {
        private Integer currentTextIndex;
        private Integer currentThinkingIndex;
        private String currentThinkingSignature = "reasoning_content";
        private final Map<Integer, Integer> toolCallSlots = new HashMap<>();
        private Usage usage = new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
        private StopReason stopReason = StopReason.STOP;
    }
}
