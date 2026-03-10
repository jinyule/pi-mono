
package dev.pi.ai.provider.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class GoogleGenerativeAiProvider implements ApiProvider {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern BASE64_SIGNATURE_PATTERN = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    private final GoogleGenerativeAiTransport transport;
    private final Clock clock;

    public GoogleGenerativeAiProvider() {
        this(new HttpGoogleGenerativeAiTransport(), Clock.systemUTC());
    }

    public GoogleGenerativeAiProvider(GoogleGenerativeAiTransport transport) {
        this(transport, Clock.systemUTC());
    }

    GoogleGenerativeAiProvider(GoogleGenerativeAiTransport transport, Clock clock) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String api() {
        return "google-generative-ai";
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        var effectiveOptions = options == null ? StreamOptions.builder().build() : options;
        requireApiKey(effectiveOptions.apiKey(), model.provider());

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("google-generative-ai-provider").start(() ->
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

        var stream = new AssistantMessageEventStream();
        Thread.ofVirtual().name("google-generative-ai-provider").start(() ->
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
            var state = new ProcessingState(clock.millis());

            transport.stream(request, event -> processEvent(event, assembler, stream, state));

            closeOpenBlock(assembler, stream, state);
            if (state.stopReason == StopReason.ERROR || state.stopReason == StopReason.ABORTED) {
                throw new IllegalStateException("Google Generative AI request did not complete successfully");
            }
            stream.push(assembler.done(state.sawToolCall && state.stopReason == StopReason.STOP ? StopReason.TOOL_USE : state.stopReason, state.usage));
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

    private GoogleGenerativeAiTransport.GoogleGenerativeAiRequest buildRequest(
        Model model,
        Context context,
        StreamOptions options,
        ThinkingConfig thinkingConfig
    ) {
        var payload = OBJECT_MAPPER.createObjectNode();
        payload.set("contents", convertMessages(model, context));

        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            payload.set("systemInstruction", createContent("user", OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("text", context.systemPrompt()))));
        }

        if (!context.tools().isEmpty()) {
            payload.set("tools", convertTools(context.tools()));
        }

        var generationConfig = OBJECT_MAPPER.createObjectNode();
        if (options.temperature() != null) {
            generationConfig.put("temperature", options.temperature());
        }
        if (options.maxTokens() != null) {
            generationConfig.put("maxOutputTokens", options.maxTokens());
        }
        if (thinkingConfig != null) {
            var thinkingNode = generationConfig.putObject("thinkingConfig");
            thinkingNode.put("includeThoughts", thinkingConfig.enabled());
            if (thinkingConfig.level() != null) {
                thinkingNode.put("thinkingLevel", thinkingConfig.level());
            }
            if (thinkingConfig.budgetTokens() != null) {
                thinkingNode.put("thinkingBudget", thinkingConfig.budgetTokens());
            }
        }
        if (!generationConfig.isEmpty()) {
            payload.set("generationConfig", generationConfig);
        }

        return new GoogleGenerativeAiTransport.GoogleGenerativeAiRequest(
            resolveUri(model.baseUrl(), model.id()),
            options.apiKey(),
            buildHeaders(model, options),
            payload
        );
    }

    private void processEvent(
        JsonNode event,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        if (event.path("error").isObject()) {
            throw new IllegalStateException(extractErrorMessage(event));
        }

        var usageMetadata = event.path("usageMetadata");
        if (usageMetadata.isObject()) {
            state.usage = mapUsage(usageMetadata);
        }

        var candidates = event.path("candidates");
        if (!candidates.isArray()) {
            return;
        }

        for (JsonNode candidate : candidates) {
            var parts = candidate.path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    handlePart(part, assembler, stream, state);
                }
            }

            var finishReason = candidate.path("finishReason").asText(null);
            if (finishReason != null && !finishReason.isBlank()) {
                closeOpenBlock(assembler, stream, state);
                state.stopReason = mapStopReason(finishReason);
            }
        }
    }

    private void handlePart(
        JsonNode part,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var functionCall = part.path("functionCall");
        if (functionCall.isObject()) {
            closeOpenBlock(assembler, stream, state);
            handleFunctionCall(part, functionCall, assembler, stream, state);
            return;
        }

        var text = part.path("text").asText("");
        if (text.isEmpty() && part.path("thoughtSignature").isMissingNode()) {
            return;
        }

        var kind = part.path("thought").asBoolean(false) ? "thinking" : "text";
        openBlock(kind, assembler, stream, state);
        state.currentBlock.signature = retainThoughtSignature(state.currentBlock.signature, blankToNull(part.path("thoughtSignature").asText(null)));

        if (text.isEmpty()) {
            return;
        }
        if ("thinking".equals(kind)) {
            stream.push(assembler.appendThinking(state.currentBlock.contentIndex, text));
        } else {
            stream.push(assembler.appendText(state.currentBlock.contentIndex, text));
        }
    }

    private void handleFunctionCall(
        JsonNode part,
        JsonNode functionCall,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        var name = functionCall.path("name").asText("");
        if (name.isBlank()) {
            return;
        }

        var toolCallId = resolveToolCallId(name, blankToNull(functionCall.path("id").asText(null)), state);
        var contentIndex = assembler.partial().content().size();
        stream.push(assembler.startToolCall(
            contentIndex,
            toolCallId,
            name,
            blankToNull(part.path("thoughtSignature").asText(null))
        ));

        var arguments = functionCall.path("args");
        var argumentsJson = stringifyJson(arguments.isMissingNode() || arguments.isNull() ? JsonNodeFactory.instance.objectNode() : arguments);
        stream.push(assembler.appendToolCallArguments(contentIndex, argumentsJson));
        stream.push(assembler.endToolCall(contentIndex));
        state.sawToolCall = true;
    }
    private ArrayNode convertMessages(Model model, Context context) {
        var contents = OBJECT_MAPPER.createArrayNode();
        var transformedMessages = transformMessages(context.messages(), model, clock.millis());

        for (int index = 0; index < transformedMessages.size(); index++) {
            var message = transformedMessages.get(index);
            switch (message) {
                case Message.UserMessage userMessage -> {
                    var parts = convertUserMessage(model, userMessage.content());
                    if (!parts.isEmpty()) {
                        contents.add(createContent("user", parts));
                    }
                }
                case Message.AssistantMessage assistantMessage -> {
                    var parts = convertAssistantMessage(model, assistantMessage);
                    if (!parts.isEmpty()) {
                        contents.add(createContent("model", parts));
                    }
                }
                case Message.ToolResultMessage ignored -> {
                    var parts = OBJECT_MAPPER.createArrayNode();
                    var imageFollowUps = new ArrayList<ArrayNode>();
                    while (index < transformedMessages.size() && transformedMessages.get(index) instanceof Message.ToolResultMessage toolResultMessage) {
                        parts.add(convertToolResultPart(model, toolResultMessage, imageFollowUps));
                        index++;
                    }
                    index--;

                    if (!parts.isEmpty()) {
                        contents.add(createContent("user", parts));
                    }
                    for (ArrayNode imageFollowUp : imageFollowUps) {
                        contents.add(createContent("user", imageFollowUp));
                    }
                }
            }
        }

        return contents;
    }

    private ArrayNode convertUserMessage(Model model, List<UserContent> content) {
        var parts = OBJECT_MAPPER.createArrayNode();
        for (UserContent block : content) {
            switch (block) {
                case TextContent textContent -> {
                    if (!textContent.text().isBlank()) {
                        parts.add(OBJECT_MAPPER.createObjectNode().put("text", textContent.text()));
                    }
                }
                case ImageContent imageContent -> {
                    if (model.input().contains("image")) {
                        parts.add(OBJECT_MAPPER.createObjectNode()
                            .set("inlineData", OBJECT_MAPPER.createObjectNode()
                                .put("mimeType", imageContent.mimeType())
                                .put("data", imageContent.data())));
                    }
                }
                default -> {
                }
            }
        }
        return parts;
    }

    private ArrayNode convertAssistantMessage(Model model, Message.AssistantMessage assistantMessage) {
        var parts = OBJECT_MAPPER.createArrayNode();
        var sameProviderAndModel =
            assistantMessage.provider().equals(model.provider()) &&
            assistantMessage.api().equals(model.api()) &&
            assistantMessage.model().equals(model.id());

        for (AssistantContent block : assistantMessage.content()) {
            switch (block) {
                case TextContent textContent -> {
                    if (textContent.text().isBlank()) {
                        continue;
                    }
                    var part = OBJECT_MAPPER.createObjectNode().put("text", textContent.text());
                    var signature = resolveThoughtSignature(sameProviderAndModel, textContent.textSignature());
                    if (signature != null) {
                        part.put("thoughtSignature", signature);
                    }
                    parts.add(part);
                }
                case ThinkingContent thinkingContent -> {
                    if (thinkingContent.thinking().isBlank()) {
                        continue;
                    }
                    var signature = resolveThoughtSignature(sameProviderAndModel, thinkingContent.thinkingSignature());
                    if (sameProviderAndModel) {
                        var part = OBJECT_MAPPER.createObjectNode()
                            .put("thought", true)
                            .put("text", thinkingContent.thinking());
                        if (signature != null) {
                            part.put("thoughtSignature", signature);
                        }
                        parts.add(part);
                    } else {
                        parts.add(OBJECT_MAPPER.createObjectNode().put("text", thinkingContent.thinking()));
                    }
                }
                case ToolCall toolCall -> {
                    var signature = resolveThoughtSignature(sameProviderAndModel, toolCall.thoughtSignature());
                    if (isGemini3Model(model.id()) && signature == null) {
                        parts.add(OBJECT_MAPPER.createObjectNode()
                            .put(
                                "text",
                                "[Historical context: a different model called tool \"%s\" with arguments: %s. Do not mimic this format - use proper function calling.]"
                                    .formatted(toolCall.name(), prettyJson(toolCall.arguments()))
                            ));
                        continue;
                    }

                    var functionCall = OBJECT_MAPPER.createObjectNode();
                    functionCall.put("name", toolCall.name());
                    functionCall.set("args", toolCall.arguments().deepCopy());
                    if (requiresToolCallId(model.id())) {
                        functionCall.put("id", toolCall.id());
                    }

                    var part = OBJECT_MAPPER.createObjectNode();
                    part.set("functionCall", functionCall);
                    if (signature != null) {
                        part.put("thoughtSignature", signature);
                    }
                    parts.add(part);
                }
                default -> {
                }
            }
        }

        return parts;
    }

    private ObjectNode convertToolResultPart(Model model, Message.ToolResultMessage toolResultMessage, List<ArrayNode> imageFollowUps) {
        var textResult = toolResultMessage.content().stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");

        var imageBlocks = model.input().contains("image")
            ? toolResultMessage.content().stream()
                .filter(ImageContent.class::isInstance)
                .map(ImageContent.class::cast)
                .toList()
            : List.<ImageContent>of();

        boolean hasText = !textResult.isBlank();
        boolean hasImages = !imageBlocks.isEmpty();
        boolean supportsMultimodalFunctionResponse = isGemini3Model(model.id());

        var responseValue = hasText ? textResult : hasImages ? "(see attached image)" : "";
        var response = OBJECT_MAPPER.createObjectNode()
            .put(toolResultMessage.isError() ? "error" : "output", responseValue);

        var functionResponse = OBJECT_MAPPER.createObjectNode();
        functionResponse.put("name", toolResultMessage.toolName());
        functionResponse.set("response", response);
        if (requiresToolCallId(model.id())) {
            functionResponse.put("id", toolResultMessage.toolCallId());
        }
        if (hasImages && supportsMultimodalFunctionResponse) {
            var imageParts = OBJECT_MAPPER.createArrayNode();
            for (ImageContent imageBlock : imageBlocks) {
                imageParts.add(OBJECT_MAPPER.createObjectNode()
                    .set("inlineData", OBJECT_MAPPER.createObjectNode()
                        .put("mimeType", imageBlock.mimeType())
                        .put("data", imageBlock.data())));
            }
            functionResponse.set("parts", imageParts);
        }

        if (hasImages && !supportsMultimodalFunctionResponse) {
            var imageContent = OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("text", "Tool result image:"));
            for (ImageContent imageBlock : imageBlocks) {
                imageContent.add(OBJECT_MAPPER.createObjectNode()
                    .set("inlineData", OBJECT_MAPPER.createObjectNode()
                        .put("mimeType", imageBlock.mimeType())
                        .put("data", imageBlock.data())));
            }
            imageFollowUps.add(imageContent);
        }

        return OBJECT_MAPPER.createObjectNode().set("functionResponse", functionResponse);
    }

    private ArrayNode convertTools(List<Tool> tools) {
        var declarations = OBJECT_MAPPER.createArrayNode();
        for (Tool tool : tools) {
            var declaration = OBJECT_MAPPER.createObjectNode()
                .put("name", tool.name())
                .put("description", tool.description());
            declaration.set("parametersJsonSchema", tool.parametersSchema().deepCopy());
            declarations.add(declaration);
        }

        return OBJECT_MAPPER.createArrayNode()
            .add(OBJECT_MAPPER.createObjectNode().set("functionDeclarations", declarations));
    }

    private List<Message> transformMessages(List<Message> messages, Model model, long syntheticTimestamp) {
        return MessageHistoryCompat.transformMessages(messages, model, syntheticTimestamp, this::transformAssistantMessage);
    }

    private Message.AssistantMessage transformAssistantMessage(
        Message.AssistantMessage assistantMessage,
        Model model,
        MessageHistoryCompat.CompatContext compatContext
    ) {
        var sameProviderAndModel = compatContext.sameProviderAndModel(assistantMessage, model);

        var transformedContent = new ArrayList<AssistantContent>(assistantMessage.content().size());
        for (AssistantContent block : assistantMessage.content()) {
            switch (block) {
                case TextContent textContent -> transformedContent.add(sameProviderAndModel
                    ? textContent
                    : new TextContent(textContent.text(), null));
                case ThinkingContent thinkingContent -> {
                    if (thinkingContent.redacted()) {
                        continue;
                    }
                    if (thinkingContent.thinking().isBlank()) {
                        continue;
                    }
                    if (sameProviderAndModel) {
                        transformedContent.add(thinkingContent);
                    } else {
                        transformedContent.add(new TextContent(thinkingContent.thinking(), null));
                    }
                }
                case ToolCall toolCall -> transformedContent.add(sameProviderAndModel
                    ? toolCall
                    : new ToolCall(toolCall.id(), toolCall.name(), toolCall.arguments(), null));
                default -> transformedContent.add(block);
            }
        }

        return MessageHistoryCompat.rebuildAssistantMessage(assistantMessage, transformedContent);
    }

    private void openBlock(
        String kind,
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        if (state.currentBlock != null && kind.equals(state.currentBlock.kind)) {
            return;
        }

        closeOpenBlock(assembler, stream, state);
        var contentIndex = assembler.partial().content().size();
        if ("thinking".equals(kind)) {
            stream.push(assembler.startThinking(contentIndex));
        } else {
            stream.push(assembler.startText(contentIndex));
        }
        state.currentBlock = new BlockState(kind, contentIndex);
    }

    private void closeOpenBlock(
        AssistantMessageAssembler assembler,
        AssistantMessageEventStream stream,
        ProcessingState state
    ) {
        if (state.currentBlock == null) {
            return;
        }

        if ("thinking".equals(state.currentBlock.kind)) {
            stream.push(assembler.endThinking(state.currentBlock.contentIndex, state.currentBlock.signature));
        } else {
            stream.push(assembler.endText(state.currentBlock.contentIndex, state.currentBlock.signature));
        }
        state.currentBlock = null;
    }

    private static LinkedHashMap<String, String> buildHeaders(Model model, StreamOptions options) {
        var headers = new LinkedHashMap<String, String>();
        headers.putAll(model.headers());
        headers.putAll(options.headers());
        return headers;
    }

    private static ThinkingConfig resolveThinkingConfig(Model model, SimpleStreamOptions options) {
        if (options.reasoning() == null || !model.reasoning()) {
            return ThinkingConfig.disabled();
        }
        if (isGemini3ProModel(model.id()) || isGemini3FlashModel(model.id())) {
            return ThinkingConfig.level(getGemini3ThinkingLevel(options.reasoning(), model.id()));
        }
        return ThinkingConfig.budget(getGoogleBudget(model.id(), options.reasoning(), options.thinkingBudgets()));
    }

    private static String getGemini3ThinkingLevel(ThinkingLevel level, String modelId) {
        var clamped = clampReasoning(level);
        if (isGemini3ProModel(modelId)) {
            return switch (clamped) {
                case MINIMAL, LOW -> "LOW";
                case MEDIUM, HIGH, XHIGH -> "HIGH";
            };
        }
        return switch (clamped) {
            case MINIMAL -> "MINIMAL";
            case LOW -> "LOW";
            case MEDIUM -> "MEDIUM";
            case HIGH, XHIGH -> "HIGH";
        };
    }

    private static int getGoogleBudget(String modelId, ThinkingLevel level, ThinkingBudgets customBudgets) {
        var clamped = clampReasoning(level);
        if (customBudgets != null) {
            var custom = switch (clamped) {
                case MINIMAL -> customBudgets.minimal();
                case LOW -> customBudgets.low();
                case MEDIUM -> customBudgets.medium();
                case HIGH, XHIGH -> customBudgets.high();
            };
            if (custom != null) {
                return custom;
            }
        }

        if (modelId.contains("2.5-pro")) {
            return switch (clamped) {
                case MINIMAL -> 128;
                case LOW -> 2_048;
                case MEDIUM -> 8_192;
                case HIGH, XHIGH -> 32_768;
            };
        }
        if (modelId.contains("2.5-flash")) {
            return switch (clamped) {
                case MINIMAL -> 128;
                case LOW -> 2_048;
                case MEDIUM -> 8_192;
                case HIGH, XHIGH -> 24_576;
            };
        }
        return -1;
    }

    private static ThinkingLevel clampReasoning(ThinkingLevel level) {
        return level == ThinkingLevel.XHIGH ? ThinkingLevel.HIGH : level;
    }

    private static Usage mapUsage(JsonNode usageMetadata) {
        var input = usageMetadata.path("promptTokenCount").asInt(0);
        var output = usageMetadata.path("candidatesTokenCount").asInt(0) + usageMetadata.path("thoughtsTokenCount").asInt(0);
        var cacheRead = usageMetadata.path("cachedContentTokenCount").asInt(0);
        return new Usage(
            input,
            output,
            cacheRead,
            0,
            usageMetadata.path("totalTokenCount").asInt(input + output + cacheRead),
            new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0)
        );
    }

    private static StopReason mapStopReason(String reason) {
        return switch (reason) {
            case "", "STOP" -> StopReason.STOP;
            case "MAX_TOKENS" -> StopReason.LENGTH;
            default -> StopReason.ERROR;
        };
    }

    private static String extractErrorMessage(JsonNode event) {
        var errorMessage = event.path("error").path("message").asText(null);
        if (errorMessage != null && !errorMessage.isBlank()) {
            return errorMessage;
        }
        var message = event.path("message").asText(null);
        return message == null || message.isBlank() ? "Google Generative AI request failed" : message;
    }

    private static URI resolveUri(String baseUrl, String modelId) {
        var normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        var normalizedModelId = modelId.startsWith("models/") ? modelId : "models/" + modelId;
        return URI.create(normalizedBaseUrl + normalizedModelId + ":streamGenerateContent?alt=sse");
    }

    private static boolean isGemini3Model(String modelId) {
        return modelId.toLowerCase(Locale.ROOT).contains("gemini-3");
    }

    private static boolean isGemini3ProModel(String modelId) {
        return modelId.contains("3-pro");
    }

    private static boolean isGemini3FlashModel(String modelId) {
        return modelId.contains("3-flash");
    }

    private static boolean requiresToolCallId(String modelId) {
        return modelId.startsWith("claude-") || modelId.startsWith("gpt-oss-");
    }

    private static String resolveToolCallId(String name, String providedId, ProcessingState state) {
        if (providedId != null && state.toolCallIds.add(providedId)) {
            return providedId;
        }

        String generated;
        do {
            generated = name + "_" + state.timestamp + "_" + ++state.toolCallCounter;
        } while (!state.toolCallIds.add(generated));
        return generated;
    }

    private static String retainThoughtSignature(String existing, String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return existing;
    }

    private static String resolveThoughtSignature(boolean sameProviderAndModel, String signature) {
        if (!sameProviderAndModel || !isValidThoughtSignature(signature)) {
            return null;
        }
        return signature;
    }

    private static boolean isValidThoughtSignature(String signature) {
        return signature != null &&
            !signature.isBlank() &&
            signature.length() % 4 == 0 &&
            BASE64_SIGNATURE_PATTERN.matcher(signature).matches();
    }

    private static ObjectNode createContent(String role, ArrayNode parts) {
        return OBJECT_MAPPER.createObjectNode()
            .put("role", role)
            .set("parts", parts);
    }

    private static String prettyJson(JsonNode jsonNode) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private static String stringifyJson(JsonNode jsonNode) {
        try {
            return OBJECT_MAPPER.writeValueAsString(jsonNode);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void requireApiKey(String apiKey, String provider) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("No API key for provider: " + provider);
        }
    }

    private record ThinkingConfig(
        boolean enabled,
        String level,
        Integer budgetTokens
    ) {
        private static ThinkingConfig disabled() {
            return new ThinkingConfig(false, null, null);
        }

        private static ThinkingConfig level(String level) {
            return new ThinkingConfig(true, level, null);
        }

        private static ThinkingConfig budget(int budgetTokens) {
            return new ThinkingConfig(true, null, budgetTokens);
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
    }

    private static final class ProcessingState {
        private final long timestamp;
        private final Set<String> toolCallIds = new LinkedHashSet<>();
        private Usage usage = new Usage(0, 0, 0, 0, 0, new Usage.Cost(0.0, 0.0, 0.0, 0.0, 0.0));
        private StopReason stopReason = StopReason.STOP;
        private BlockState currentBlock;
        private boolean sawToolCall;
        private int toolCallCounter;

        private ProcessingState(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
