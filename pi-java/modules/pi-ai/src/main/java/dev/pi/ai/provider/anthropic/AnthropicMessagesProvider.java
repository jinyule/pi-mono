package dev.pi.ai.provider.anthropic;

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

public final class AnthropicMessagesProvider implements ApiProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CLAUDE_CODE_IDENTITY = "You are Claude Code, Anthropic's official CLI for Claude.";
    private static final String CLAUDE_CODE_VERSION = "2.1.62";
    private static final List<String> CLAUDE_CODE_TOOLS = List.of(
        "Read",
        "Write",
        "Edit",
        "Bash",
        "Grep",
        "Glob",
        "AskUserQuestion",
        "EnterPlanMode",
        "ExitPlanMode",
        "KillShell",
        "NotebookEdit",
        "Skill",
        "Task",
        "TaskOutput",
        "TodoWrite",
        "WebFetch",
        "WebSearch"
    );
    private static final Map<String, String> CLAUDE_CODE_TOOL_LOOKUP = buildClaudeCodeToolLookup();

    private final AnthropicMessagesTransport transport;
    private final Clock clock;

    public AnthropicMessagesProvider() {
        this(new HttpAnthropicMessagesTransport(), Clock.systemUTC());
    }

    public AnthropicMessagesProvider(AnthropicMessagesTransport transport) {
        this(transport, Clock.systemUTC());
    }

    AnthropicMessagesProvider(AnthropicMessagesTransport transport, Clock clock) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String api() {
        return "anthropic-messages";
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? StreamOptions.builder().build() : options;
        requireApiKey(effectiveOptions.apiKey(), model.provider());

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("anthropic-messages-provider").start(() ->
            executeStream(model, context, effectiveOptions, ThinkingConfig.disabled(resolveStreamMaxTokens(model, effectiveOptions)), stream)
        );
        return stream;
    }

    @Override
    public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? SimpleStreamOptions.builder().build() : options;
        requireApiKey(effectiveOptions.apiKey(), model.provider());

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("anthropic-messages-provider").start(() ->
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

            transport.stream(request.request(), event -> processEvent(event, assembler, stream, state, request.oauthToken(), context.tools()));

            finishOpenBlocks(assembler, stream, state);

            if (state.stopReason == StopReason.ERROR || state.stopReason == StopReason.ABORTED) {
                throw new IllegalStateException("Anthropic Messages request did not complete successfully");
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

    private AnthropicRequest buildRequest(
        Model model,
        Context context,
        StreamOptions options,
        ThinkingConfig thinkingConfig
    ) {
        var apiKey = options.apiKey();
        var oauthToken = isOAuthToken(apiKey);
        var copilot = "github-copilot".equals(model.provider());
        var cacheControl = resolveCacheControl(model.baseUrl(), options.cacheRetention());
        var payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", model.id());
        payload.put("stream", true);
        payload.put("max_tokens", thinkingConfig.maxTokens());
        payload.set("messages", convertMessages(context.messages(), model, oauthToken, cacheControl));

        var system = convertSystemPrompt(context.systemPrompt(), oauthToken, cacheControl);
        if (!system.isEmpty()) {
            payload.set("system", system);
        }

        if (options.temperature() != null && !thinkingConfig.enabled()) {
            payload.put("temperature", options.temperature());
        }
        if (!context.tools().isEmpty()) {
            payload.set("tools", convertTools(context.tools(), oauthToken));
        }
        if (thinkingConfig.thinking() != null && model.reasoning()) {
            payload.set("thinking", thinkingConfig.thinking());
        }
        if (thinkingConfig.effort() != null && model.reasoning()) {
            payload.set("output_config", OBJECT_MAPPER.createObjectNode().put("effort", thinkingConfig.effort()));
        }
        var userId = options.metadata().get("user_id");
        if (userId instanceof String value && !value.isBlank()) {
            payload.set("metadata", OBJECT_MAPPER.createObjectNode().put("user_id", value));
        }

        return new AnthropicRequest(
            new AnthropicMessagesTransport.AnthropicMessagesRequest(
                resolveMessagesUri(model.baseUrl()),
                buildHeaders(model, options, apiKey, oauthToken, copilot),
                payload
            ),
            oauthToken
        );
    }

    private void processEvent(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state,
        boolean oauthToken,
        List<Tool> tools
    ) {
        var type = event.path("type").asText();
        switch (type) {
            case "message_start" -> state.usage = mapMessageStartUsage(event.path("message").path("usage"), state.usage);
            case "content_block_start" -> handleBlockStart(event, assembler, stream, state, oauthToken, tools);
            case "content_block_delta" -> handleBlockDelta(event, assembler, stream, state);
            case "content_block_stop" -> handleBlockStop(event, assembler, stream, state);
            case "message_delta" -> {
                var stopReason = event.path("delta").path("stop_reason");
                if (!stopReason.isMissingNode() && !stopReason.isNull()) {
                    state.stopReason = mapStopReason(stopReason.asText());
                }
                state.usage = mapMessageDeltaUsage(event.path("usage"), state.usage);
            }
            case "message_stop", "ping" -> {
            }
            case "error" -> throw new IllegalStateException(extractErrorMessage(event));
            default -> {
            }
        }
    }

    private void handleBlockStart(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state,
        boolean oauthToken,
        List<Tool> tools
    ) {
        int blockIndex = event.path("index").asInt();
        var contentBlock = event.path("content_block");
        var contentIndex = assembler.partial().content().size();
        var blockType = contentBlock.path("type").asText();
        switch (blockType) {
            case "text" -> {
                stream.push(assembler.startText(contentIndex));
                state.blocks.put(blockIndex, BlockState.text(contentIndex));
            }
            case "thinking" -> {
                stream.push(assembler.startThinking(contentIndex));
                state.blocks.put(blockIndex, BlockState.thinking(contentIndex, contentBlock.path("signature").asText(null)));
            }
            case "redacted_thinking" -> {
                stream.push(assembler.startThinking(contentIndex));
                stream.push(assembler.appendThinking(contentIndex, "[Reasoning redacted]"));
                state.blocks.put(blockIndex, BlockState.thinking(contentIndex, contentBlock.path("data").asText(null)));
            }
            case "tool_use" -> {
                var name = contentBlock.path("name").asText();
                if (oauthToken) {
                    name = fromClaudeCodeName(name, tools);
                }
                stream.push(assembler.startToolCall(contentIndex, contentBlock.path("id").asText(), name));
                state.blocks.put(blockIndex, BlockState.toolCall(contentIndex, contentBlock.path("input")));
            }
            default -> {
            }
        }
    }

    private void handleBlockDelta(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var blockState = state.blocks.get(event.path("index").asInt());
        if (blockState == null) {
            return;
        }

        var delta = event.path("delta");
        switch (delta.path("type").asText()) {
            case "text_delta" -> {
                if ("text".equals(blockState.kind)) {
                    stream.push(assembler.appendText(blockState.contentIndex, delta.path("text").asText("")));
                }
            }
            case "thinking_delta" -> {
                if ("thinking".equals(blockState.kind)) {
                    stream.push(assembler.appendThinking(blockState.contentIndex, delta.path("thinking").asText("")));
                }
            }
            case "input_json_delta" -> {
                if ("toolCall".equals(blockState.kind)) {
                    stream.push(assembler.appendToolCallArguments(blockState.contentIndex, delta.path("partial_json").asText("")));
                }
            }
            case "signature_delta" -> {
                if ("thinking".equals(blockState.kind)) {
                    blockState.signature = appendSignature(blockState.signature, delta.path("signature").asText(""));
                }
            }
            default -> {
            }
        }
    }

    private void handleBlockStop(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var blockState = state.blocks.remove(event.path("index").asInt());
        if (blockState == null) {
            return;
        }

        switch (blockState.kind) {
            case "text" -> stream.push(assembler.endText(blockState.contentIndex, null));
            case "thinking" -> stream.push(assembler.endThinking(blockState.contentIndex, blockState.signature));
            case "toolCall" -> {
                if (blockState.initialToolInput != null && hasStructuredContent(blockState.initialToolInput)) {
                    var current = assembler.partial().content().get(blockState.contentIndex);
                    if (current instanceof ToolCall toolCall && isEmptyObject(toolCall.arguments())) {
                        assembler.replaceToolCallArguments(blockState.contentIndex, stringifyJson(blockState.initialToolInput));
                    }
                }
                stream.push(assembler.endToolCall(blockState.contentIndex));
            }
            default -> {
            }
        }
    }

    private ArrayNode convertMessages(
        List<Message> messages,
        Model model,
        boolean oauthToken,
        ObjectNode cacheControl
    ) {
        var transformed = transformMessages(messages, model, clock.millis());
        var params = OBJECT_MAPPER.createArrayNode();

        for (int index = 0; index < transformed.size(); index++) {
            var message = transformed.get(index);
            switch (message) {
                case Message.UserMessage userMessage -> {
                    var content = convertUserMessage(userMessage.content(), model);
                    if (!content.isEmpty()) {
                        params.add(OBJECT_MAPPER.createObjectNode()
                            .put("role", "user")
                            .set("content", content));
                    }
                }
                case Message.AssistantMessage assistantMessage -> {
                    var content = convertAssistantMessage(assistantMessage, oauthToken);
                    if (!content.isEmpty()) {
                        params.add(OBJECT_MAPPER.createObjectNode()
                            .put("role", "assistant")
                            .set("content", content));
                    }
                }
                case Message.ToolResultMessage ignored -> {
                    var content = OBJECT_MAPPER.createArrayNode();
                    while (index < transformed.size() && transformed.get(index) instanceof Message.ToolResultMessage toolResultMessage) {
                        content.add(convertToolResultBlock(toolResultMessage, model));
                        index++;
                    }
                    index--;
                    if (!content.isEmpty()) {
                        params.add(OBJECT_MAPPER.createObjectNode()
                            .put("role", "user")
                            .set("content", content));
                    }
                }
            }
        }

        if (cacheControl != null && !params.isEmpty()) {
            var lastMessage = params.get(params.size() - 1);
            if ("user".equals(lastMessage.path("role").asText()) && lastMessage.path("content").isArray() && !lastMessage.path("content").isEmpty()) {
                var lastBlock = lastMessage.path("content").get(lastMessage.path("content").size() - 1);
                if (lastBlock instanceof ObjectNode lastBlockObject) {
                    lastBlockObject.set("cache_control", cacheControl.deepCopy());
                }
            }
        }

        return params;
    }

    private ArrayNode convertSystemPrompt(String systemPrompt, boolean oauthToken, ObjectNode cacheControl) {
        var system = OBJECT_MAPPER.createArrayNode();
        if (oauthToken) {
            system.add(createSystemText(CLAUDE_CODE_IDENTITY, cacheControl));
        }
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            system.add(createSystemText(systemPrompt, cacheControl));
        }
        return system;
    }

    private ObjectNode createSystemText(String text, ObjectNode cacheControl) {
        var node = OBJECT_MAPPER.createObjectNode()
            .put("type", "text")
            .put("text", text);
        if (cacheControl != null) {
            node.set("cache_control", cacheControl.deepCopy());
        }
        return node;
    }

    private ArrayNode convertUserMessage(List<UserContent> content, Model model) {
        var blocks = OBJECT_MAPPER.createArrayNode();
        boolean hasText = false;
        boolean hasImage = false;

        for (UserContent block : content) {
            switch (block) {
                case TextContent textContent -> {
                    if (!textContent.text().isBlank()) {
                        hasText = true;
                        blocks.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "text")
                            .put("text", textContent.text()));
                    }
                }
                case ImageContent imageContent -> {
                    if (model.input().contains("image")) {
                        hasImage = true;
                        blocks.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "image")
                            .set("source", OBJECT_MAPPER.createObjectNode()
                                .put("type", "base64")
                                .put("media_type", imageContent.mimeType())
                                .put("data", imageContent.data())));
                    }
                }
                default -> {
                }
            }
        }

        if (hasImage && !hasText) {
            blocks.insert(0, OBJECT_MAPPER.createObjectNode()
                .put("type", "text")
                .put("text", "(see attached image)"));
        }

        return blocks;
    }

    private ArrayNode convertAssistantMessage(Message.AssistantMessage message, boolean oauthToken) {
        var blocks = OBJECT_MAPPER.createArrayNode();
        for (AssistantContent block : message.content()) {
            switch (block) {
                case TextContent textContent -> {
                    if (!textContent.text().isBlank()) {
                        blocks.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "text")
                            .put("text", textContent.text()));
                    }
                }
                case ThinkingContent thinkingContent -> {
                    if (thinkingContent.redacted() && thinkingContent.thinkingSignature() != null && !thinkingContent.thinkingSignature().isBlank()) {
                        blocks.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "redacted_thinking")
                            .put("data", thinkingContent.thinkingSignature()));
                    } else if (!thinkingContent.thinking().isBlank()) {
                        if (thinkingContent.thinkingSignature() == null || thinkingContent.thinkingSignature().isBlank()) {
                            blocks.add(OBJECT_MAPPER.createObjectNode()
                                .put("type", "text")
                                .put("text", thinkingContent.thinking()));
                        } else {
                            blocks.add(OBJECT_MAPPER.createObjectNode()
                                .put("type", "thinking")
                                .put("thinking", thinkingContent.thinking())
                                .put("signature", thinkingContent.thinkingSignature()));
                        }
                    }
                }
                case ToolCall toolCall -> {
                    var name = oauthToken ? toClaudeCodeName(toolCall.name()) : toolCall.name();
                    blocks.add(OBJECT_MAPPER.createObjectNode()
                        .put("type", "tool_use")
                        .put("id", toolCall.id())
                        .put("name", name)
                        .set("input", toolCall.arguments().deepCopy()));
                }
                default -> {
                }
            }
        }
        return blocks;
    }

    private ObjectNode convertToolResultBlock(Message.ToolResultMessage message, Model model) {
        var block = OBJECT_MAPPER.createObjectNode()
            .put("type", "tool_result")
            .put("tool_use_id", message.toolCallId())
            .put("is_error", message.isError());
        block.set("content", convertToolResultContent(message.content(), model));
        return block;
    }

    private JsonNode convertToolResultContent(List<UserContent> content, Model model) {
        boolean hasImages = content.stream().anyMatch(ImageContent.class::isInstance);
        if (!hasImages) {
            var text = content.stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
            return JsonNodeFactory.instance.textNode(text);
        }

        var blocks = OBJECT_MAPPER.createArrayNode();
        boolean hasText = false;
        for (UserContent block : content) {
            switch (block) {
                case TextContent textContent -> {
                    if (!textContent.text().isBlank()) {
                        hasText = true;
                        blocks.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "text")
                            .put("text", textContent.text()));
                    }
                }
                case ImageContent imageContent -> {
                    if (model.input().contains("image")) {
                        blocks.add(OBJECT_MAPPER.createObjectNode()
                            .put("type", "image")
                            .set("source", OBJECT_MAPPER.createObjectNode()
                                .put("type", "base64")
                                .put("media_type", imageContent.mimeType())
                                .put("data", imageContent.data())));
                    }
                }
                default -> {
                }
            }
        }

        if (!hasText) {
            blocks.insert(0, OBJECT_MAPPER.createObjectNode()
                .put("type", "text")
                .put("text", "(see attached image)"));
        }

        return blocks;
    }

    private ArrayNode convertTools(List<Tool> tools, boolean oauthToken) {
        var result = OBJECT_MAPPER.createArrayNode();
        for (Tool tool : tools) {
            var item = OBJECT_MAPPER.createObjectNode();
            item.put("name", oauthToken ? toClaudeCodeName(tool.name()) : tool.name());
            item.put("description", tool.description());
            item.set("input_schema", normalizeToolSchema(tool.parametersSchema()));
            result.add(item);
        }
        return result;
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
                    if (sameModel && thinkingContent.thinkingSignature() != null && !thinkingContent.thinkingSignature().isBlank()) {
                        transformedContent.add(thinkingContent);
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
                    var normalized = toolCall;
                    if (!sameModel) {
                        if (toolCall.thoughtSignature() != null) {
                            normalized = new ToolCall(toolCall.id(), toolCall.name(), toolCall.arguments(), null);
                        }
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

    private static LinkedHashMap<String, String> buildHeaders(
        Model model,
        StreamOptions options,
        String apiKey,
        boolean oauthToken,
        boolean copilot
    ) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "text/event-stream");
        headers.put("Content-Type", "application/json");
        headers.put("anthropic-version", "2023-06-01");
        headers.put("anthropic-dangerous-direct-browser-access", "true");

        var betaFeatures = new ArrayList<String>();
        if (oauthToken) {
            betaFeatures.add("claude-code-20250219");
            betaFeatures.add("oauth-2025-04-20");
        }
        if (!copilot) {
            betaFeatures.add("fine-grained-tool-streaming-2025-05-14");
        }
        if (!supportsAdaptiveThinking(model.id())) {
            betaFeatures.add("interleaved-thinking-2025-05-14");
        }
        if (!betaFeatures.isEmpty()) {
            headers.put("anthropic-beta", String.join(",", betaFeatures));
        }

        if (oauthToken || copilot) {
            headers.put("Authorization", "Bearer " + apiKey);
        } else {
            headers.put("X-Api-Key", apiKey);
        }
        if (oauthToken) {
            headers.put("user-agent", "claude-cli/" + CLAUDE_CODE_VERSION);
            headers.put("x-app", "cli");
        }

        headers.putAll(model.headers());
        headers.putAll(options.headers());
        return headers;
    }

    private static ThinkingConfig resolveThinkingConfig(Model model, SimpleStreamOptions options) {
        int baseMaxTokens = options.maxTokens() != null ? options.maxTokens() : Math.min(model.maxTokens(), 32_000);
        if (options.reasoning() == null) {
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

    private static int resolveStreamMaxTokens(Model model, StreamOptions options) {
        return options.maxTokens() != null ? options.maxTokens() : Math.max(1, model.maxTokens() / 3);
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

    private static boolean supportsAdaptiveThinking(String modelId) {
        return modelId.contains("opus-4-6") ||
            modelId.contains("opus-4.6") ||
            modelId.contains("sonnet-4-6") ||
            modelId.contains("sonnet-4.6");
    }

    private static String mapThinkingLevelToEffort(ThinkingLevel level, String modelId) {
        return switch (level) {
            case MINIMAL, LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case XHIGH -> modelId.contains("opus-4-6") || modelId.contains("opus-4.6") ? "max" : "high";
        };
    }

    private static ObjectNode resolveCacheControl(String baseUrl, CacheRetention cacheRetention) {
        var retention = cacheRetention == null ? CacheRetention.SHORT : cacheRetention;
        if (retention == CacheRetention.NONE) {
            return null;
        }
        var cacheControl = OBJECT_MAPPER.createObjectNode().put("type", "ephemeral");
        if (retention == CacheRetention.LONG && baseUrl.contains("api.anthropic.com")) {
            cacheControl.put("ttl", "1h");
        }
        return cacheControl;
    }

    private static ObjectNode normalizeToolSchema(JsonNode parametersSchema) {
        if (parametersSchema != null && parametersSchema.isObject()) {
            return parametersSchema.deepCopy();
        }
        return OBJECT_MAPPER.createObjectNode().put("type", "object");
    }

    private static Usage mapMessageStartUsage(JsonNode usageNode, Usage fallback) {
        var usage = new Usage(
            intOrFallback(usageNode, "input_tokens", fallback.input()),
            intOrFallback(usageNode, "output_tokens", fallback.output()),
            intOrFallback(usageNode, "cache_read_input_tokens", fallback.cacheRead()),
            intOrFallback(usageNode, "cache_creation_input_tokens", fallback.cacheWrite()),
            0,
            fallback.cost()
        );
        return new Usage(
            usage.input(),
            usage.output(),
            usage.cacheRead(),
            usage.cacheWrite(),
            usage.input() + usage.output() + usage.cacheRead() + usage.cacheWrite(),
            usage.cost()
        );
    }

    private static Usage mapMessageDeltaUsage(JsonNode usageNode, Usage fallback) {
        var usage = new Usage(
            intOrFallback(usageNode, "input_tokens", fallback.input()),
            intOrFallback(usageNode, "output_tokens", fallback.output()),
            intOrFallback(usageNode, "cache_read_input_tokens", fallback.cacheRead()),
            intOrFallback(usageNode, "cache_creation_input_tokens", fallback.cacheWrite()),
            0,
            fallback.cost()
        );
        return new Usage(
            usage.input(),
            usage.output(),
            usage.cacheRead(),
            usage.cacheWrite(),
            usage.input() + usage.output() + usage.cacheRead() + usage.cacheWrite(),
            usage.cost()
        );
    }

    private static int intOrFallback(JsonNode node, String field, int fallback) {
        var value = node.path(field);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private static StopReason mapStopReason(String reason) {
        return switch (reason) {
            case "", "end_turn", "pause_turn", "stop_sequence" -> StopReason.STOP;
            case "max_tokens", "model_context_window_exceeded" -> StopReason.LENGTH;
            case "tool_use" -> StopReason.TOOL_USE;
            case "refusal", "sensitive" -> StopReason.ERROR;
            default -> throw new IllegalArgumentException("Unhandled Anthropic stop reason: " + reason);
        };
    }

    private static String extractErrorMessage(JsonNode event) {
        var error = event.path("error");
        if (error.isObject()) {
            var message = error.path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        var message = event.path("message").asText(null);
        return message == null || message.isBlank() ? "Anthropic Messages request failed" : message;
    }

    private static URI resolveMessagesUri(String baseUrl) {
        var normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(normalized + "messages");
    }

    private static boolean isOAuthToken(String apiKey) {
        return apiKey != null && apiKey.contains("sk-ant-oat");
    }

    private static String toClaudeCodeName(String name) {
        return CLAUDE_CODE_TOOL_LOOKUP.getOrDefault(name.toLowerCase(Locale.ROOT), name);
    }

    private static String fromClaudeCodeName(String name, List<Tool> tools) {
        var lowerName = name.toLowerCase(Locale.ROOT);
        for (Tool tool : tools) {
            if (tool.name().toLowerCase(Locale.ROOT).equals(lowerName)) {
                return tool.name();
            }
        }
        return name;
    }

    private static LinkedHashMap<String, String> buildClaudeCodeToolLookup() {
        var lookup = new LinkedHashMap<String, String>();
        for (String tool : CLAUDE_CODE_TOOLS) {
            lookup.put(tool.toLowerCase(Locale.ROOT), tool);
        }
        return lookup;
    }

    private static String stringifyJson(JsonNode jsonNode) {
        try {
            return OBJECT_MAPPER.writeValueAsString(jsonNode);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private static boolean hasStructuredContent(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() &&
            (!node.isObject() || !node.isEmpty()) &&
            (!node.isArray() || !node.isEmpty());
    }

    private static boolean isEmptyObject(JsonNode node) {
        return node != null && node.isObject() && node.isEmpty();
    }

    private static String appendSignature(String currentSignature, String delta) {
        if (delta == null || delta.isEmpty()) {
            return currentSignature;
        }
        if (currentSignature == null || currentSignature.isBlank()) {
            return delta;
        }
        return currentSignature + delta;
    }

    private static void requireApiKey(String apiKey, String provider) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("No API key for provider: " + provider);
        }
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

    private record ThinkingConfig(
        int maxTokens,
        boolean enabled,
        ObjectNode thinking,
        String effort
    ) {
        private static ThinkingConfig disabled(int maxTokens) {
            return new ThinkingConfig(maxTokens, false, null, null);
        }

        private static ThinkingConfig adaptive(int maxTokens, String effort) {
            return new ThinkingConfig(
                maxTokens,
                true,
                OBJECT_MAPPER.createObjectNode().put("type", "adaptive"),
                effort
            );
        }

        private static ThinkingConfig enabled(int maxTokens, int budgetTokens) {
            return new ThinkingConfig(
                maxTokens,
                true,
                OBJECT_MAPPER.createObjectNode()
                    .put("type", "enabled")
                    .put("budget_tokens", budgetTokens),
                null
            );
        }
    }

    private record AnthropicRequest(
        AnthropicMessagesTransport.AnthropicMessagesRequest request,
        boolean oauthToken
    ) {}

    private static final class BlockState {
        private final String kind;
        private final int contentIndex;
        private final JsonNode initialToolInput;
        private String signature;

        private BlockState(String kind, int contentIndex, JsonNode initialToolInput, String signature) {
            this.kind = kind;
            this.contentIndex = contentIndex;
            this.initialToolInput = initialToolInput == null ? null : initialToolInput.deepCopy();
            this.signature = signature;
        }

        private static BlockState text(int contentIndex) {
            return new BlockState("text", contentIndex, null, null);
        }

        private static BlockState thinking(int contentIndex, String signature) {
            return new BlockState("thinking", contentIndex, null, signature);
        }

        private static BlockState toolCall(int contentIndex, JsonNode initialToolInput) {
            return new BlockState("toolCall", contentIndex, initialToolInput, null);
        }
    }

    private static final class ProcessingState {
        private final Map<Integer, BlockState> blocks = new LinkedHashMap<>();
        private Usage usage = new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
        private StopReason stopReason = StopReason.STOP;
    }
}
