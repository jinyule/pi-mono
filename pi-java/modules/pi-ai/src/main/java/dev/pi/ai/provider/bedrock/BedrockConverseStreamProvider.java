package dev.pi.ai.provider.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import dev.pi.ai.model.ThinkingBudgets;
import dev.pi.ai.model.ThinkingContent;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Tool;
import dev.pi.ai.model.ToolCall;
import dev.pi.ai.model.Usage;
import dev.pi.ai.model.UserContent;
import dev.pi.ai.provider.ApiProvider;
import dev.pi.ai.provider.MessageHistoryCompat;
import dev.pi.ai.stream.AssistantMessageAssembler;
import dev.pi.ai.stream.AssistantMessageEventStream;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class BedrockConverseStreamProvider implements ApiProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern REGION_PATTERN = Pattern.compile("bedrock-runtime(?:-fips)?[.-]([a-z0-9-]+)\\.amazonaws\\.com");
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String INTERLEAVED_THINKING_BETA = "interleaved-thinking-2025-05-14";

    private final BedrockConverseStreamTransport transport;
    private final Clock clock;

    public BedrockConverseStreamProvider() {
        this(new AwsBedrockConverseStreamTransport(), Clock.systemUTC());
    }

    public BedrockConverseStreamProvider(BedrockConverseStreamTransport transport) {
        this(transport, Clock.systemUTC());
    }

    BedrockConverseStreamProvider(BedrockConverseStreamTransport transport, Clock clock) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String api() {
        return "bedrock-converse-stream";
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? StreamOptions.builder().build() : options;

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("bedrock-converse-stream-provider").start(() ->
            executeStream(model, context, effectiveOptions, ThinkingConfig.disabled(effectiveOptions.maxTokens()), stream)
        );
        return stream;
    }

    @Override
    public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? SimpleStreamOptions.builder().build() : options;

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("bedrock-converse-stream-provider").start(() ->
            executeStream(model, context, effectiveOptions, resolveThinkingConfig(model, effectiveOptions), stream)
        );
        return stream;
    }

    private void executeStream(
        Model model,
        Context context,
        StreamOptions options,
        ThinkingConfig thinkingConfig,
        AssistantMessageEventStream stream
    ) {
        var assembler = new AssistantMessageAssembler(model, clock.millis());
        stream.push(assembler.start());

        try {
            var request = buildRequest(model, context, options, thinkingConfig);
            var state = new ProcessingState();

            transport.stream(request, event -> processEvent(event, assembler, stream, state));

            finishOpenBlocks(assembler, stream, state);
            if (state.stopReason == StopReason.ERROR || state.stopReason == StopReason.ABORTED) {
                throw new IllegalStateException("Bedrock ConverseStream request did not complete successfully");
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

    private BedrockConverseStreamTransport.BedrockConverseStreamRequest buildRequest(
        Model model,
        Context context,
        StreamOptions options,
        ThinkingConfig thinkingConfig
    ) {
        var cachePoint = resolveCachePoint(model, options.cacheRetention());
        var payload = OBJECT_MAPPER.createObjectNode();
        payload.set("messages", convertMessages(context.messages(), model, cachePoint));

        var system = convertSystemPrompt(context.systemPrompt(), cachePoint);
        if (!system.isEmpty()) {
            payload.set("system", system);
        }

        var inferenceConfig = OBJECT_MAPPER.createObjectNode();
        if (thinkingConfig.maxTokens() != null) {
            inferenceConfig.put("maxTokens", thinkingConfig.maxTokens());
        }
        if (options.temperature() != null) {
            inferenceConfig.put("temperature", options.temperature());
        }
        if (!inferenceConfig.isEmpty()) {
            payload.set("inferenceConfig", inferenceConfig);
        }

        if (!context.tools().isEmpty()) {
            payload.set("toolConfig", convertToolConfig(context.tools()));
        }
        if (thinkingConfig.additionalModelRequestFields() != null && !thinkingConfig.additionalModelRequestFields().isEmpty()) {
            payload.set("additionalModelRequestFields", thinkingConfig.additionalModelRequestFields());
        }

        return new BedrockConverseStreamTransport.BedrockConverseStreamRequest(
            model.id(),
            resolveRegion(model.baseUrl(), options.metadata()),
            resolveProfile(options.metadata()),
            resolveEndpointOverride(model.baseUrl()),
            payload
        );
    }

    private void processEvent(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var type = event.path("type").asText();
        switch (type) {
            case "messageStart" -> requireAssistantRole(event.path("role").asText(null));
            case "contentBlockStart" -> handleBlockStart(event, assembler, stream, state);
            case "contentBlockDelta" -> handleBlockDelta(event, assembler, stream, state);
            case "contentBlockStop" -> handleBlockStop(event, assembler, stream, state);
            case "messageStop" -> state.stopReason = mapStopReason(event.path("stopReason").asText(""));
            case "metadata" -> state.usage = mapUsage(event.path("usage"), state.usage);
            case "error" -> throw new IllegalStateException(extractErrorMessage(event));
            default -> {
            }
        }
    }

    private void handleBlockStart(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        int blockIndex = event.path("contentBlockIndex").asInt();
        var toolUse = event.path("start").path("toolUse");
        if (!toolUse.isObject()) {
            return;
        }

        var contentIndex = assembler.partial().content().size();
        stream.push(assembler.startToolCall(
            contentIndex,
            toolUse.path("toolUseId").asText(),
            toolUse.path("name").asText()
        ));
        state.blocks.put(blockIndex, BlockState.toolCall(contentIndex));
    }

    private void handleBlockDelta(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        int blockIndex = event.path("contentBlockIndex").asInt();
        var delta = event.path("delta");

        var text = delta.path("text");
        if (!text.isMissingNode() && !text.isNull()) {
            var blockState = ensureBlock("text", blockIndex, assembler, stream, state);
            stream.push(assembler.appendText(blockState.contentIndex, text.asText("")));
        }

        var toolUse = delta.path("toolUse");
        if (toolUse.isObject()) {
            var blockState = state.blocks.get(blockIndex);
            if (blockState == null || !"toolCall".equals(blockState.kind)) {
                throw new IllegalStateException("Received tool use delta before tool use start for block " + blockIndex);
            }
            stream.push(assembler.appendToolCallArguments(blockState.contentIndex, toolUse.path("input").asText("")));
        }

        var reasoningContent = delta.path("reasoningContent");
        if (reasoningContent.isObject()) {
            var blockState = ensureBlock("thinking", blockIndex, assembler, stream, state);
            var reasoningText = reasoningContent.path("text");
            if (!reasoningText.isMissingNode() && !reasoningText.isNull()) {
                stream.push(assembler.appendThinking(blockState.contentIndex, reasoningText.asText("")));
            }
            blockState.signature = appendSignature(blockState.signature, blankToNull(reasoningContent.path("signature").asText(null)));
        }
    }

    private void handleBlockStop(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var blockState = state.blocks.remove(event.path("contentBlockIndex").asInt());
        if (blockState == null) {
            return;
        }

        switch (blockState.kind) {
            case "text" -> stream.push(assembler.endText(blockState.contentIndex, null));
            case "thinking" -> stream.push(assembler.endThinking(blockState.contentIndex, blockState.signature));
            case "toolCall" -> stream.push(assembler.endToolCall(blockState.contentIndex));
            default -> {
            }
        }
    }

    private BlockState ensureBlock(
        String kind,
        int blockIndex,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var existing = state.blocks.get(blockIndex);
        if (existing != null) {
            if (!kind.equals(existing.kind)) {
                throw new IllegalStateException("Bedrock content block kind changed from " + existing.kind + " to " + kind);
            }
            return existing;
        }

        var contentIndex = assembler.partial().content().size();
        if ("thinking".equals(kind)) {
            stream.push(assembler.startThinking(contentIndex));
            existing = BlockState.thinking(contentIndex);
        } else {
            stream.push(assembler.startText(contentIndex));
            existing = BlockState.text(contentIndex);
        }
        state.blocks.put(blockIndex, existing);
        return existing;
    }

    private ArrayNode convertMessages(
        List<Message> messages,
        Model model,
        ObjectNode cachePoint
    ) {
        var transformed = transformMessages(messages, model, clock.millis());
        var result = OBJECT_MAPPER.createArrayNode();

        for (int index = 0; index < transformed.size(); index++) {
            var message = transformed.get(index);
            switch (message) {
                case Message.UserMessage userMessage -> {
                    var content = convertUserMessage(model, userMessage.content());
                    if (!content.isEmpty()) {
                        result.add(OBJECT_MAPPER.createObjectNode()
                            .put("role", "user")
                            .set("content", content));
                    }
                }
                case Message.AssistantMessage assistantMessage -> {
                    var content = convertAssistantMessage(model, assistantMessage);
                    if (!content.isEmpty()) {
                        result.add(OBJECT_MAPPER.createObjectNode()
                            .put("role", "assistant")
                            .set("content", content));
                    }
                }
                case Message.ToolResultMessage ignored -> {
                    var content = OBJECT_MAPPER.createArrayNode();
                    while (index < transformed.size() && transformed.get(index) instanceof Message.ToolResultMessage toolResultMessage) {
                        content.add(convertToolResultBlock(model, toolResultMessage));
                        index++;
                    }
                    index--;
                    if (!content.isEmpty()) {
                        result.add(OBJECT_MAPPER.createObjectNode()
                            .put("role", "user")
                            .set("content", content));
                    }
                }
            }
        }

        if (cachePoint != null) {
            appendCachePoint(result, cachePoint);
        }

        return result;
    }

    private ArrayNode convertSystemPrompt(String systemPrompt, ObjectNode cachePoint) {
        var result = OBJECT_MAPPER.createArrayNode();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return result;
        }

        result.add(OBJECT_MAPPER.createObjectNode().put("text", systemPrompt));
        if (cachePoint != null) {
            result.add(OBJECT_MAPPER.createObjectNode().set("cachePoint", cachePoint.deepCopy()));
        }
        return result;
    }

    private ArrayNode convertUserMessage(Model model, List<UserContent> content) {
        var blocks = OBJECT_MAPPER.createArrayNode();
        boolean hasText = false;
        boolean hasImage = false;

        for (UserContent block : content) {
            switch (block) {
                case TextContent textContent -> {
                    if (!textContent.text().isBlank()) {
                        hasText = true;
                        blocks.add(OBJECT_MAPPER.createObjectNode().put("text", textContent.text()));
                    }
                }
                case ImageContent imageContent -> {
                    if (model.input().contains("image")) {
                        hasImage = true;
                        blocks.add(OBJECT_MAPPER.createObjectNode().set("image", createImageBlock(imageContent)));
                    }
                }
                default -> {
                }
            }
        }

        if (hasImage && !hasText) {
            blocks.insert(0, OBJECT_MAPPER.createObjectNode().put("text", "(see attached image)"));
        }
        return blocks;
    }

    private ArrayNode convertAssistantMessage(Model model, Message.AssistantMessage assistantMessage) {
        var blocks = OBJECT_MAPPER.createArrayNode();
        var sameProviderAndModel =
            assistantMessage.provider().equals(model.provider()) &&
            assistantMessage.api().equals(model.api()) &&
            assistantMessage.model().equals(model.id());

        for (AssistantContent block : assistantMessage.content()) {
            switch (block) {
                case TextContent textContent -> {
                    if (!textContent.text().isBlank()) {
                        blocks.add(OBJECT_MAPPER.createObjectNode().put("text", textContent.text()));
                    }
                }
                case ThinkingContent thinkingContent -> {
                    if (thinkingContent.redacted()) {
                        if (sameProviderAndModel && supportsThinkingSignature(model.id()) && thinkingContent.thinkingSignature() != null) {
                            blocks.add(OBJECT_MAPPER.createObjectNode()
                                .set("reasoningContent", OBJECT_MAPPER.createObjectNode()
                                    .set("redactedContent", OBJECT_MAPPER.createObjectNode().put("data", thinkingContent.thinkingSignature()))));
                        }
                        continue;
                    }
                    if (thinkingContent.thinking().isBlank()) {
                        continue;
                    }
                    if (sameProviderAndModel) {
                        var reasoningText = OBJECT_MAPPER.createObjectNode().put("text", thinkingContent.thinking());
                        if (supportsThinkingSignature(model.id()) && thinkingContent.thinkingSignature() != null && !thinkingContent.thinkingSignature().isBlank()) {
                            reasoningText.put("signature", thinkingContent.thinkingSignature());
                        }
                        blocks.add(OBJECT_MAPPER.createObjectNode()
                            .set("reasoningContent", OBJECT_MAPPER.createObjectNode().set("reasoningText", reasoningText)));
                    } else {
                        blocks.add(OBJECT_MAPPER.createObjectNode().put("text", thinkingContent.thinking()));
                    }
                }
                case ToolCall toolCall -> blocks.add(OBJECT_MAPPER.createObjectNode()
                    .set("toolUse", OBJECT_MAPPER.createObjectNode()
                        .put("toolUseId", toolCall.id())
                        .put("name", toolCall.name())
                        .set("input", toolCall.arguments().deepCopy())));
                default -> {
                }
            }
        }

        return blocks;
    }

    private ObjectNode convertToolResultBlock(Model model, Message.ToolResultMessage toolResultMessage) {
        var content = OBJECT_MAPPER.createArrayNode();
        boolean sawImage = false;

        for (var block : toolResultMessage.content()) {
            switch (block) {
                case TextContent textContent -> {
                    if (!textContent.text().isBlank()) {
                        content.add(OBJECT_MAPPER.createObjectNode().put("text", textContent.text()));
                    }
                }
                case ImageContent imageContent -> {
                    sawImage = true;
                    if (model.input().contains("image")) {
                        content.add(OBJECT_MAPPER.createObjectNode().set("image", createImageBlock(imageContent)));
                    }
                }
                default -> {
                }
            }
        }

        if (content.isEmpty() && sawImage) {
            content.add(OBJECT_MAPPER.createObjectNode().put("text", "(see attached image)"));
        }

        return OBJECT_MAPPER.createObjectNode()
            .set("toolResult", OBJECT_MAPPER.createObjectNode()
                .put("toolUseId", toolResultMessage.toolCallId())
                .put("status", toolResultMessage.isError() ? "ERROR" : "SUCCESS")
                .set("content", content));
    }

    private ObjectNode createImageBlock(ImageContent imageContent) {
        return OBJECT_MAPPER.createObjectNode()
            .put("format", toBedrockImageFormat(imageContent.mimeType()))
            .set("source", OBJECT_MAPPER.createObjectNode().put("bytes", imageContent.data()));
    }

    private ObjectNode convertToolConfig(List<Tool> tools) {
        var toolNodes = OBJECT_MAPPER.createArrayNode();
        for (Tool tool : tools) {
            toolNodes.add(OBJECT_MAPPER.createObjectNode()
                .set("toolSpec", OBJECT_MAPPER.createObjectNode()
                    .put("name", tool.name())
                    .put("description", tool.description())
                    .set("inputSchema", OBJECT_MAPPER.createObjectNode().set("json", normalizeToolSchema(tool.parametersSchema())))));
        }
        return OBJECT_MAPPER.createObjectNode().set("tools", toolNodes);
    }

    private List<Message> transformMessages(List<Message> messages, Model model, long syntheticTimestamp) {
        return MessageHistoryCompat.transformMessages(messages, model, syntheticTimestamp, this::transformAssistantMessage);
    }

    private Message.AssistantMessage transformAssistantMessage(
        Message.AssistantMessage assistantMessage,
        Model model,
        MessageHistoryCompat.CompatContext compatContext
    ) {
        var sameModel = compatContext.sameProviderAndModel(assistantMessage, model);

        var transformedContent = new ArrayList<AssistantContent>(assistantMessage.content().size());
        for (AssistantContent block : assistantMessage.content()) {
            switch (block) {
                case TextContent textContent -> transformedContent.add(sameModel
                    ? textContent
                    : new TextContent(textContent.text(), null));
                case ThinkingContent thinkingContent -> {
                    if (thinkingContent.redacted()) {
                        if (sameModel) {
                            transformedContent.add(thinkingContent);
                        }
                        continue;
                    }
                    if (thinkingContent.thinking().isBlank()) {
                        continue;
                    }
                    transformedContent.add(sameModel
                        ? thinkingContent
                        : new TextContent(thinkingContent.thinking(), null));
                }
                case ToolCall toolCall -> {
                    var normalized = sameModel
                        ? toolCall
                        : new ToolCall(toolCall.id(), toolCall.name(), toolCall.arguments(), null);
                    if (!sameModel) {
                        var normalizedId = compatContext.normalizeToolCallId(toolCall.id());
                        if (!normalizedId.equals(toolCall.id())) {
                            compatContext.remapToolCallId(toolCall.id(), normalizedId);
                            normalized = new ToolCall(normalizedId, normalized.name(), normalized.arguments(), normalized.thoughtSignature());
                        }
                    }
                    transformedContent.add(normalized);
                }
                default -> transformedContent.add(block);
            }
        }

        return MessageHistoryCompat.rebuildAssistantMessage(assistantMessage, transformedContent);
    }

    private static void appendCachePoint(ArrayNode messages, ObjectNode cachePoint) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            if (!"user".equals(message.path("role").asText())) {
                continue;
            }
            if (message instanceof ObjectNode messageObject && messageObject.path("content") instanceof ArrayNode content) {
                content.add(OBJECT_MAPPER.createObjectNode().set("cachePoint", cachePoint.deepCopy()));
                return;
            }
        }
    }

    private static ObjectNode resolveCachePoint(Model model, CacheRetention requestedRetention) {
        var retention = resolveCacheRetention(requestedRetention);
        if (retention == CacheRetention.NONE || !supportsPromptCaching(model.id())) {
            return null;
        }

        var cachePoint = OBJECT_MAPPER.createObjectNode().put("type", "DEFAULT");
        if (retention == CacheRetention.LONG) {
            cachePoint.put("ttl", "ONE_HOUR");
        }
        return cachePoint;
    }

    private static CacheRetention resolveCacheRetention(CacheRetention requestedRetention) {
        if (requestedRetention != null) {
            return requestedRetention;
        }
        var envValue = System.getenv("PI_CACHE_RETENTION");
        if ("long".equalsIgnoreCase(envValue)) {
            return CacheRetention.LONG;
        }
        if ("none".equalsIgnoreCase(envValue)) {
            return CacheRetention.NONE;
        }
        return CacheRetention.SHORT;
    }

    private static ThinkingConfig resolveThinkingConfig(Model model, SimpleStreamOptions options) {
        int baseMaxTokens = options.maxTokens() != null ? options.maxTokens() : Math.min(model.maxTokens(), 32_000);
        if (options.reasoning() == null || !model.reasoning() || !isAnthropicClaudeModel(model.id())) {
            return ThinkingConfig.disabled(baseMaxTokens);
        }
        if (supportsAdaptiveThinking(model.id())) {
            return ThinkingConfig.adaptive(baseMaxTokens, mapThinkingLevelToEffort(options.reasoning(), model.id()));
        }

        int thinkingBudget = resolveThinkingBudget(options.reasoning(), options.thinkingBudgets());
        int maxTokens = Math.min(baseMaxTokens + thinkingBudget, model.maxTokens());
        if (maxTokens <= thinkingBudget) {
            thinkingBudget = Math.max(0, maxTokens - 1_024);
        }
        return ThinkingConfig.enabled(maxTokens, thinkingBudget);
    }

    private static int resolveThinkingBudget(ThinkingLevel reasoning, ThinkingBudgets budgets) {
        if (budgets == null) {
            return defaultThinkingBudget(reasoning);
        }
        return switch (reasoning) {
            case MINIMAL -> budgets.minimal() != null ? budgets.minimal() : defaultThinkingBudget(reasoning);
            case LOW -> budgets.low() != null ? budgets.low() : defaultThinkingBudget(reasoning);
            case MEDIUM -> budgets.medium() != null ? budgets.medium() : defaultThinkingBudget(reasoning);
            case HIGH, XHIGH -> budgets.high() != null ? budgets.high() : defaultThinkingBudget(reasoning);
        };
    }

    private static int defaultThinkingBudget(ThinkingLevel reasoning) {
        return switch (reasoning) {
            case MINIMAL -> 1_024;
            case LOW -> 2_048;
            case MEDIUM -> 8_192;
            case HIGH, XHIGH -> 16_384;
        };
    }

    private static String mapThinkingLevelToEffort(ThinkingLevel level, String modelId) {
        return switch (level) {
            case MINIMAL, LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case XHIGH -> isOpus46(modelId) ? "max" : "high";
        };
    }

    private static boolean supportsAdaptiveThinking(String modelId) {
        var normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("opus-4-6") ||
            normalized.contains("opus-4.6") ||
            normalized.contains("sonnet-4-6") ||
            normalized.contains("sonnet-4.6");
    }

    private static boolean isOpus46(String modelId) {
        var normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("opus-4-6") || normalized.contains("opus-4.6");
    }

    private static boolean isAnthropicClaudeModel(String modelId) {
        return modelId.toLowerCase(Locale.ROOT).contains("claude");
    }

    private static boolean supportsThinkingSignature(String modelId) {
        return isAnthropicClaudeModel(modelId);
    }

    private static boolean supportsPromptCaching(String modelId) {
        var normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("claude-4") ||
            normalized.contains("claude-sonnet-4") ||
            normalized.contains("claude-opus-4") ||
            normalized.contains("claude-3-7") ||
            normalized.contains("claude-3.7") ||
            normalized.contains("claude-3-5-haiku") ||
            normalized.contains("claude-3.5-haiku");
    }

    private static Usage mapUsage(JsonNode usageNode, Usage fallback) {
        var input = intOrFallback(usageNode, "inputTokens", fallback.input());
        var output = intOrFallback(usageNode, "outputTokens", fallback.output());
        var cacheRead = intOrFallback(usageNode, "cacheReadInputTokens", fallback.cacheRead());
        var cacheWrite = intOrFallback(usageNode, "cacheWriteInputTokens", fallback.cacheWrite());
        var totalTokens = intOrFallback(usageNode, "totalTokens", input + output + cacheRead + cacheWrite);
        return new Usage(
            input,
            output,
            cacheRead,
            cacheWrite,
            totalTokens,
            fallback.cost()
        );
    }

    private static StopReason mapStopReason(String reason) {
        return switch (reason) {
            case "", "END_TURN", "STOP_SEQUENCE" -> StopReason.STOP;
            case "MAX_TOKENS", "MODEL_CONTEXT_WINDOW_EXCEEDED" -> StopReason.LENGTH;
            case "TOOL_USE" -> StopReason.TOOL_USE;
            default -> StopReason.ERROR;
        };
    }

    private static void finishOpenBlocks(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        for (BlockState blockState : state.blocks.values()) {
            switch (blockState.kind) {
                case "text" -> stream.push(assembler.endText(blockState.contentIndex, null));
                case "thinking" -> stream.push(assembler.endThinking(blockState.contentIndex, blockState.signature));
                case "toolCall" -> stream.push(assembler.endToolCall(blockState.contentIndex));
                default -> {
                }
            }
        }
        state.blocks.clear();
    }

    private static URI resolveEndpointOverride(String baseUrl) {
        return baseUrl == null || baseUrl.isBlank() ? null : URI.create(baseUrl);
    }

    private static String resolveRegion(String baseUrl, Map<String, Object> metadata) {
        var explicit = metadataString(metadata, "aws_region", "region");
        if (explicit != null) {
            return explicit;
        }

        if (baseUrl != null) {
            var matcher = REGION_PATTERN.matcher(baseUrl);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return DEFAULT_REGION;
    }

    private static String resolveProfile(Map<String, Object> metadata) {
        return metadataString(metadata, "aws_profile", "profile");
    }

    private static String metadataString(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            var value = metadata.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    private static ObjectNode normalizeToolSchema(JsonNode parametersSchema) {
        if (parametersSchema != null && parametersSchema.isObject()) {
            return parametersSchema.deepCopy();
        }
        return OBJECT_MAPPER.createObjectNode().put("type", "object");
    }

    private static int intOrFallback(JsonNode node, String field, int fallback) {
        var value = node.path(field);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private static void requireAssistantRole(String role) {
        if (role == null || role.isBlank()) {
            return;
        }
        if (!"assistant".equalsIgnoreCase(role)) {
            throw new IllegalStateException("Unexpected Bedrock message role: " + role);
        }
    }

    private static String extractErrorMessage(JsonNode event) {
        var nested = blankToNull(event.path("error").path("message").asText(null));
        if (nested != null) {
            return nested;
        }
        var message = blankToNull(event.path("message").asText(null));
        return message == null ? "Bedrock ConverseStream request failed" : message;
    }

    private static String toBedrockImageFormat(String mimeType) {
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "JPEG";
            case "image/png" -> "PNG";
            case "image/gif" -> "GIF";
            case "image/webp" -> "WEBP";
            default -> throw new IllegalArgumentException("Unsupported Bedrock image MIME type: " + mimeType);
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String appendSignature(String currentSignature, String delta) {
        if (delta == null || delta.isBlank()) {
            return currentSignature;
        }
        if (currentSignature == null || currentSignature.isBlank()) {
            return delta;
        }
        return currentSignature + delta;
    }

    private record ThinkingConfig(
        Integer maxTokens,
        ObjectNode additionalModelRequestFields
    ) {
        private static ThinkingConfig disabled(Integer maxTokens) {
            return new ThinkingConfig(maxTokens, null);
        }

        private static ThinkingConfig adaptive(int maxTokens, String effort) {
            var fields = OBJECT_MAPPER.createObjectNode();
            fields.set("thinking", OBJECT_MAPPER.createObjectNode().put("type", "adaptive"));
            fields.set("output_config", OBJECT_MAPPER.createObjectNode().put("effort", effort));
            return new ThinkingConfig(maxTokens, fields);
        }

        private static ThinkingConfig enabled(int maxTokens, int budgetTokens) {
            var fields = OBJECT_MAPPER.createObjectNode();
            fields.set("thinking", OBJECT_MAPPER.createObjectNode()
                .put("type", "enabled")
                .put("budget_tokens", budgetTokens));
            fields.set("anthropic_beta", OBJECT_MAPPER.createArrayNode().add(INTERLEAVED_THINKING_BETA));
            return new ThinkingConfig(maxTokens, fields);
        }
    }

    private static final class BlockState {
        private final String kind;
        private final int contentIndex;
        private String signature;

        private BlockState(String kind, int contentIndex) {
            this.kind = kind;
            this.contentIndex = contentIndex;
        }

        private static BlockState text(int contentIndex) {
            return new BlockState("text", contentIndex);
        }

        private static BlockState thinking(int contentIndex) {
            return new BlockState("thinking", contentIndex);
        }

        private static BlockState toolCall(int contentIndex) {
            return new BlockState("toolCall", contentIndex);
        }
    }

    private static final class ProcessingState {
        private final Map<Integer, BlockState> blocks = new LinkedHashMap<>();
        private Usage usage = new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
        private StopReason stopReason = StopReason.STOP;
    }
}
